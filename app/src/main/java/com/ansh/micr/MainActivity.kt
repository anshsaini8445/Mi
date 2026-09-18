package com.ansh.micr

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<CardView>(R.id.cardSend).setOnClickListener {
            startActivity(Intent(this, SendActivity::class.java))
        }

        findViewById<CardView>(R.id.cardReceive).setOnClickListener {
            startActivity(Intent(this, ReceiveActivity::class.java))
        }

        findViewById<CardView>(R.id.cardWebShare).setOnClickListener {
            startActivity(Intent(this, WebShareActivity::class.java))
        }

        findViewById<CardView>(R.id.cardHistory).setOnClickListener {
            Toast.makeText(this, "Transfer History: 0 records", Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.btnMenu).setOnClickListener {
            val model = Build.MODEL ?: "Samsung Galaxy M13 5G"
            val device = Build.DEVICE ?: "SM_M135FU"
            AlertDialog.Builder(this)
                .setTitle("$device ($model)")
                .setMessage("Storage: 18.44 / 50.82 GB\nMode: 5GHz (100 MB/s Turbo)\nBackground Engine: Active\nStatus: Ready")
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
