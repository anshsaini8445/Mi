package com.ansh.micr

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class ReceiveActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receive)

        findViewById<TextView>(R.id.btnBackReceive).setOnClickListener { finish() }

        val dev = Build.DEVICE ?: "SM_M135FU"
        findViewById<TextView>(R.id.tvDeviceHeader).text = dev

        val qrContent = "MICR://SSID:DIRECT-50-MICR;IP:192.168.43.1;PORT:8888;;"
        findViewById<ImageView>(R.id.ivQRCode).setImageBitmap(generateQRCodeBitmap(qrContent))

        findViewById<Button>(R.id.btnWifiDirectSwitch).setOnClickListener {
            showTransferModeDialog()
        }

        findViewById<Button>(R.id.btnReceiveFromPC).setOnClickListener {
            startActivity(Intent(this, WebShareActivity::class.java))
        }

        val serviceIntent = Intent(this, TransferService::class.java).apply {
            putExtra("IS_SENDER", false)
        }
        startService(serviceIntent)
    }

    private fun showTransferModeDialog() {
        val modes = arrayOf(
            "Wi-Fi Direct\nTurn on Wi-Fi to connect.",
            "Hotspot\nCreate a Hotspot to connect."
        )
        var selectedIndex = 0

        AlertDialog.Builder(this)
            .setTitle("Transfer mode")
            .setMessage("No Internet data is consumed in both two modes.")
            .setSingleChoiceItems(modes, 0) { _, which -> selectedIndex = which }
            .setPositiveButton("OK") { _, _ ->
                val chosen = if (selectedIndex == 0) "Wi-Fi Direct" else "Hotspot"
                Toast.makeText(this, "Active Mode: $chosen", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun generateQRCodeBitmap(content: String): Bitmap {
        val writer = QRCodeWriter()
        val matrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
        for (x in 0 until 512) {
            for (y in 0 until 512) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
