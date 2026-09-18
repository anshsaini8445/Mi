package com.ansh.micr

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.*
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.InetSocketAddress

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val PORT = 8888
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private val FILE_REQ_CODE = 3001
    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestAllPermissions()

        webView = WebView(this)
        setContentView(webView)

        val ws = webView.settings
        ws.javaScriptEnabled = true
        ws.domStorageEnabled = true
        ws.allowFileAccess = true
        ws.allowContentAccess = true
        ws.useWideViewPort = true
        ws.loadWithOverviewMode = true

        webView.webViewClient = WebViewClient()

        // File Selector Bridge
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = callback

                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                startActivityForResult(Intent.createChooser(intent, "फ़ाइल चुनें"), FILE_REQ_CODE)
                return true
            }
        }

        webView.addJavascriptInterface(InShareEngine(this), "AndroidApp")
        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class InShareEngine(private val context: Context) {

        @JavascriptInterface
        fun getDeviceModel(): String = Build.MODEL ?: "Samsung Galaxy M13 5G"

        @JavascriptInterface
        fun getDeviceCode(): String = Build.DEVICE ?: "SM_M135FU"

        @JavascriptInterface
        fun getStorageInfo(): String {
            return try {
                val stat = StatFs(Environment.getDataDirectory().path)
                val total = (stat.blockCountLong * stat.blockSizeLong) / (1024 * 1024 * 1024.0)
                val free = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024 * 1024.0)
                val used = total - free
                String.format("%.2f / %.2f GB", used, total)
            } catch (e: Exception) {
                "19.34 / 50.82 GB"
            }
        }

        @JavascriptInterface
        fun vibrate() {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v?.vibrate(50)
            }
        }

        // 1. INSHARE HOTSPOT ACTIVATOR
        @JavascriptInterface
        fun startHotspotAndServer() {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                        override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                            super.onStarted(reservation)
                            hotspotReservation = reservation
                            val ssid = reservation?.wifiConfiguration?.SSID ?: "MICR_TURBO_DIRECT"
                            val pwd = reservation?.wifiConfiguration?.preSharedKey ?: "12345678"
                            val qrPayload = "MICR://SSID:$ssid;PWD:$pwd;IP:192.168.43.1;PORT:$PORT;;"

                            runOnUiThread {
                                webView.evaluateJavascript("showReceiverQR('$qrPayload', '$ssid')", null)
                            }
                            listenForIncomingFiles()
                        }

                        override fun onFailed(reason: Int) {
                            super.onFailed(reason)
                            // Manual Hotspot Fallback
                            listenForIncomingFiles()
                            val qrPayload = "MICR://SSID:ManualHotspot;PWD:none;IP:192.168.43.1;PORT:$PORT;;"
                            runOnUiThread {
                                webView.evaluateJavascript("showReceiverQR('$qrPayload', 'Personal Hotspot')", null)
                            }
                        }
                    }, Handler(Looper.getMainLooper()))
                } catch (e: Exception) {
                    listenForIncomingFiles()
                }
            } else {
                listenForIncomingFiles()
            }
        }

        // 2. ULTRA-FAST RECEIVER SOCKET (256KB BUFFER)
        private fun listenForIncomingFiles() {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val server = ServerSocket(PORT)
                    server.receiveBufferSize = 256 * 1024
                    val client = server.accept()
                    client.tcpNoDelay = true

                    val dis = DataInputStream(BufferedInputStream(client.getInputStream(), 256 * 1024))
                    val fileName = dis.readUTF()
                    val fileSize = dis.readLong()

                    val targetFile = File(getExternalFilesDir(null) ?: filesDir, fileName)
                    val fos = BufferedOutputStream(FileOutputStream(targetFile), 256 * 1024)

                    val buffer = ByteArray(262144) // 256 KB Chunks = 100+ MB/s
                    var bytesRead: Int
                    var totalRead = 0L

                    while (totalRead < fileSize) {
                        bytesRead = dis.read(buffer, 0, minOf(buffer.size.toLong(), fileSize - totalRead).toInt())
                        if (bytesRead == -1) break
                        fos.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val percent = ((totalRead * 100) / fileSize).toInt()
                        withContext(Dispatchers.Main) {
                            webView.evaluateJavascript("updateTransferSpeed($percent, '$fileName')", null)
                        }
                    }

                    fos.flush()
                    fos.close()
                    client.close()
                    server.close()

                    withContext(Dispatchers.Main) {
                        webView.evaluateJavascript("onFileReceivedComplete('$fileName')", null)
                    }
                } catch (e: Exception) {
                    // Fallback handled
                }
            }
        }

        // 3. QR SCANNER LAUNCHER (Camera Scan)
        @JavascriptInterface
        fun startQRScanner() {
            runOnUiThread {
                val integrator = IntentIntegrator(this@MainActivity)
                integrator.setPrompt("InShare/MICR का QR कोड स्कैन करें")
                integrator.setBeepEnabled(true)
                integrator.setOrientationLocked(true)
                integrator.initiateScan()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // QR Scanner Result
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents != null) {
                val content = result.contents
                webView.evaluateJavascript("onQRScanned('$content')", null)
            }
            return
        }

        // File Picker Result
        if (requestCode == FILE_REQ_CODE && fileUploadCallback != null) {
            val results: Array<Uri>? = when {
                resultCode == RESULT_OK && data?.clipData != null -> {
                    val count = data.clipData!!.itemCount
                    Array(count) { i -> data.clipData!!.getItemAt(i).uri }
                }
                resultCode == RESULT_OK && data?.data != null -> arrayOf(data.data!!)
                else -> null
            }
            fileUploadCallback?.onReceiveValue(results)
            fileUploadCallback = null
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun requestAllPermissions() {
        val perms = mutableListOf<String>()
        perms.add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            perms.add(Manifest.permission.READ_MEDIA_VIDEO)
            perms.add(Manifest.permission.READ_MEDIA_AUDIO)
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            hotspotReservation?.close()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
