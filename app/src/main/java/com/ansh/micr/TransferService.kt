package com.ansh.micr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.net.ServerSocket

class TransferService : Service() {

    private val CHANNEL_ID = "MICR_TRANSFER_CHANNEL"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isSender = intent?.getBooleanExtra("IS_SENDER", false) ?: false

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MI Share Turbo Engine Active")
            .setContentText(if (isSender) "Sending files at 100 MB/s..." else "Receiving files in background...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        startForeground(101, notification)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!isSender) {
                    val server = ServerSocket(8888)
                    server.receiveBufferSize = 262144
                    val client = server.accept()
                    client.close()
                    server.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Transfer",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
