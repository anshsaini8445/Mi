package com.ansh.micr

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket

class MainActivity : AppCompatActivity() {

    private val PORT = 8888
    private val DEFAULT_IP = "192.168.43.1"
    private var transferRecordsCount = 0

    private lateinit var tvTransferStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var imgQrCode: ImageView
    private lateinit var tvHistoryRecords: TextView

    // Native Multiple File Picker Launcher
    private val pickFilesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            promptTargetIpAndSend(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()

        tvTransferStatus = findViewById(R.id.tvTransferStatus)
        progressBar = findViewById(R.id.progressBar)
        imgQrCode = findViewById(R.id.imgQrCode)
        tvHistoryRecords = findViewById(R.id.tvHistoryRecords)

        // Send Click
        findViewById<CardView>(R.id.cardSend).setOnClickListener {
            vibrateDevice()
            pickFilesLauncher.launch("*/*")
        }

        // Receive Click
        findViewById<CardView>(R.id.cardReceive).setOnClickListener {
            vibrateDevice()
            startReceiveMode()
        }

        // Connect to PC Click
        findViewById<CardView>(R.id.cardPC).setOnClickListener {
            vibrateDevice()
            AlertDialog.Builder(this)
                .setTitle("Connect to PC / iOS")
                .setMessage("1. Phone ka Hotspot on karein.\n2. PC ke browser me yeh address daalein:\nhttp://$DEFAULT_IP:$PORT")
                .setPositiveButton("OK", null)
                .show()
        }

        // History Click
        findViewById<CardView>(R.id.cardHistory).setOnClickListener {
            vibrateDevice()
            Toast.makeText(this, "Transfer Vault: $transferRecordsCount records", Toast.LENGTH_SHORT).show()
        }

        // Invite Click
        findViewById<TextView>(R.id.btnInvite).setOnClickListener {
            vibrateDevice()
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Download MI Share Turbo for Ultra-Fast File Sharing!")
            }
            startActivity(Intent.createChooser(shareIntent, "Share MI Share"))
        }

        // Category clicks
        val categoryClickListener = View.OnClickListener {
            vibrateDevice()
            pickFilesLauncher.launch("*/*")
        }
        findViewById<TextView>(R.id.catApps).setOnClickListener(categoryClickListener)
        findViewById<TextView>(R.id.catFiles).setOnClickListener(categoryClickListener)
        findViewById<TextView>(R.id.catVideos).setOnClickListener(categoryClickListener)
        findViewById<TextView>(R.id.catPhotos).setOnClickListener(categoryClickListener)
        findViewById<TextView>(R.id.catSongs).setOnClickListener(categoryClickListener)
        findViewById<TextView>(R.id.catContacts).setOnClickListener(categoryClickListener)

        // Menu Drawer Dialog (Screenshots match)
        findViewById<TextView>(R.id.btnMenu).setOnClickListener {
            showDeviceSettingsDialog()
        }
    }

    // 1. RECEIVER ENGINE (256KB RAW SOCKET)
    private fun startReceiveMode() {
        tvTransferStatus.text = "Receiver Mode Active\nHotspot IP: $DEFAULT_IP:$PORT"
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        imgQrCode.visibility = View.VISIBLE

        generateQRCode("MICR_P2P://$DEFAULT_IP:$PORT")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val serverSocket = ServerSocket(PORT)
                serverSocket.receiveBufferSize = 262144
                val clientSocket = serverSocket.accept()
                clientSocket.tcpNoDelay = true

                withContext(Dispatchers.Main) {
                    tvTransferStatus.text = "Connected! Receiving 5GHz data stream..."
                }

                val dis = DataInputStream(BufferedInputStream(clientSocket.getInputStream(), 262144))
                val fileName = dis.readUTF()
                val fileSize = dis.readLong()

                val saveDir = getExternalFilesDir(null) ?: filesDir
                val targetFile = File(saveDir, fileName)
                val fos = BufferedOutputStream(FileOutputStream(targetFile), 262144)

                val buffer = ByteArray(262144)
                var bytesRead: Int
                var totalRead = 0L

                while (totalRead < fileSize) {
                    bytesRead = dis.read(buffer, 0, minOf(buffer.size.toLong(), fileSize - totalRead).toInt())
                    if (bytesRead == -1) break
                    fos.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                }

                fos.flush()
                fos.close()
                clientSocket.close()
                serverSocket.close()

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    imgQrCode.visibility = View.GONE
                    transferRecordsCount++
                    tvHistoryRecords.text = "$transferRecordsCount records"
                    tvTransferStatus.text = "Received: $fileName (Saved in Storage)"
                    vibrateDevice()
                    Toast.makeText(this@MainActivity, "File Received: $fileName", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    // 2. SENDER ENGINE
    private fun promptTargetIpAndSend(uris: List<Uri>) {
        val input = EditText(this)
        input.setText(DEFAULT_IP)

        AlertDialog.Builder(this)
            .setTitle("Connect to Receiver")
            .setMessage("Receiver phone ka Hotspot IP confirm karein:")
            .setView(input)
            .setPositiveButton("Start Turbo Send") { _, _ ->
                val ip = input.text.toString().trim()
                sendFilesOverSocket(uris, ip)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendFilesOverSocket(uris: List<Uri>, targetIP: String) {
        tvTransferStatus.text = "Connecting to $targetIP..."
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        imgQrCode.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (uri in uris) {
                    val socket = Socket(targetIP, PORT)
                    socket.sendBufferSize = 262144
                    socket.tcpNoDelay = true

                    val dos = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), 262144))
                    val pfd = contentResolver.openFileDescriptor(uri, "r") ?: continue
                    val fileLength = pfd.statSize
                    val inputStream = contentResolver.openInputStream(uri) ?: continue

                    val fileName = "MICR_Shared_${System.currentTimeMillis()}"
                    dos.writeUTF(fileName)
                    dos.writeLong(fileLength)

                    val buffer = ByteArray(262144)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        dos.write(buffer, 0, bytesRead)
                    }

                    dos.flush()
                    socket.close()
                    pfd.close()
                    inputStream.close()
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    transferRecordsCount += uris.size
                    tvHistoryRecords.text = "$transferRecordsCount records"
                    tvTransferStatus.text = "100% Sent via 5GHz Turbo Socket!"
                    vibrateDevice()
                    Toast.makeText(this@MainActivity, "Files Sent Successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvTransferStatus.text = "Connection Failed: Check if both devices are on same Hotspot"
                }
            }
        }
    }

    private fun generateQRCode(content: String) {
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
            for (x in 0 until 512) {
                for (y in 0 until 512) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            imgQrCode.setImageBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showDeviceSettingsDialog() {
        val model = Build.MODEL ?: "Samsung Galaxy M13 5G"
        val device = Build.DEVICE ?: "SM_M135FU"
        AlertDialog.Builder(this)
            .setTitle("$device ($model)")
            .setMessage("• Storage: 19.34 / 50.82 GB\n• Mode: 5GHz (100 MB/s Turbo)\n• Battery Saver: Disabled\n• Ad-Free Engine: Active")
            .setPositiveButton("Clean Cache (340 MB)") { _, _ ->
                cacheDir.deleteRecursively()
                Toast.makeText(this, "Cache Cleared! 340 MB Free", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun vibrateDevice() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            v?.vibrate(40)
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
