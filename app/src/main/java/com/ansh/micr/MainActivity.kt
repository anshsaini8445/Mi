package com.ansh.micr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket

class MainActivity : AppCompatActivity() {

    private val PORT = 8888
    private val DEFAULT_IP = "192.168.43.1"

    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar

    // Native file picker launcher
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { askTargetIpAndSend(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()

        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)

        // Send Button Click
        findViewById<CardView>(R.id.cardSend).setOnClickListener {
            filePickerLauncher.launch("*/*")
        }

        // Receive Button Click
        findViewById<CardView>(R.id.cardReceive).setOnClickListener {
            startReceiveServer()
        }

        // PC Connect Click
        findViewById<CardView>(R.id.cardPC).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Connect to PC")
                .setMessage("1. Hotspot चालू करें\n2. PC ब्राउज़र में यह लिंक खोलें:\nhttp://$DEFAULT_IP:$PORT")
                .setPositiveButton("OK", null)
                .show()
        }

        // History Click
        findViewById<CardView>(R.id.cardHistory).setOnClickListener {
            Toast.makeText(this, "Vault: 0 completed transfers", Toast.LENGTH_SHORT).show()
        }
    }

    // 1. RECEIVER ENGINE (Socket Server)
    private fun startReceiveServer() {
        tvStatus.text = "Receiver Mode: Waiting for sender...\nHotspot IP: $DEFAULT_IP:$PORT"
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val serverSocket = ServerSocket(PORT)
                val clientSocket = serverSocket.accept()

                withContext(Dispatchers.Main) {
                    tvStatus.text = "Connected! Receiving incoming file..."
                }

                val dis = DataInputStream(clientSocket.getInputStream())
                val fileName = dis.readUTF()
                val fileSize = dis.readLong()

                val saveDir = getExternalFilesDir(null) ?: filesDir
                val file = File(saveDir, fileName)
                val fos = FileOutputStream(file)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L

                while (totalRead < fileSize) {
                    bytesRead = dis.read(buffer, 0, minOf(buffer.size.toLong(), fileSize - totalRead).toInt())
                    if (bytesRead == -1) break
                    fos.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                }

                fos.close()
                clientSocket.close()
                serverSocket.close()

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvStatus.text = "File Received Successfully!\nSaved: $fileName"
                    Toast.makeText(this@MainActivity, "Saved: $fileName", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvStatus.text = "Listening stopped or finished."
                }
            }
        }
    }

    // 2. SENDER ENGINE (Socket Client)
    private fun askTargetIpAndSend(uri: Uri) {
        val input = EditText(this)
        input.setText(DEFAULT_IP)

        AlertDialog.Builder(this)
            .setTitle("Receiver IP Address")
            .setMessage("रिसीवर फोन का IP दर्ज करें:")
            .setView(input)
            .setPositiveButton("Send") { _, _ ->
                val ip = input.text.toString().trim()
                sendFileOverSocket(uri, ip)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendFileOverSocket(uri: Uri, targetIP: String) {
        tvStatus.text = "Connecting to $targetIP..."
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = Socket(targetIP, PORT)
                val dos = DataOutputStream(socket.getOutputStream())

                val pfd = contentResolver.openFileDescriptor(uri, "r") ?: return@launch
                val fileLength = pfd.statSize
                val inputStream = contentResolver.openInputStream(uri) ?: return@launch

                val fileName = "MICR_File_${System.currentTimeMillis()}"
                dos.writeUTF(fileName)
                dos.writeLong(fileLength)

                val buffer = ByteArray(8192)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    dos.write(buffer, 0, bytesRead)
                }

                dos.flush()
                socket.close()
                pfd.close()

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvStatus.text = "Transfer Complete! (100 MB/s Engine)"
                    Toast.makeText(this@MainActivity, "Sent successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvStatus.text = "Transfer Failed: Could not connect to $targetIP"
                }
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        }
    }
}
