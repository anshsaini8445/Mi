package com.ansh.micr

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class WebShareActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_share)

        findViewById<TextView>(R.id.btnBackWeb).setOnClickListener { finish() }

        findViewById<LinearLayout>(R.id.optHotspotMode).setOnClickListener {
            showWebAddressDialog("Hotspot Mode", "192.168.43.1")
        }

        findViewById<LinearLayout>(R.id.optWifiMode).setOnClickListener {
            showWebAddressDialog("Wi-Fi Mode", "192.168.1.100")
        }
    }

    private fun showWebAddressDialog(title: String, ip: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("अपने PC या iPhone ब्राउज़र में खोलें:\n👉 http://$ip:8888")
            .setPositiveButton("OK", null)
            .show()
    }
}
