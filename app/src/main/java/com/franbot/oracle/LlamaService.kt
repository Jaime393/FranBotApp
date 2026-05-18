package com.franbot.oracle

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

class LlamaService : Service() {
    private var process: Process? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())
        startServer()
        return START_STICKY
    }

    private fun startServer() {
        val modelFile = File(filesDir, "modelo.gguf")
        val binFile = File(filesDir, "llama-server")
        if (!binFile.exists()) {
            stopSelf()
            return
        }
        if (!modelFile.exists()) {
            stopSelf()
            return
        }
        val cmd = arrayOf(
            binFile.absolutePath,
            "-m", modelFile.absolutePath,
            "-c", "1024",
            "-t", "4",
            "--host", "127.0.0.1",
            "--port", "8081",
            "--mlock"
        )
        process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
    }

    private fun createNotification(): Notification {
        val channelId = "franbot_service"
        val channel = NotificationChannel(channelId, "FranBot", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("FranBot activo")
            .setContentText("Modelo cargado")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onDestroy() {
        process?.destroy()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
