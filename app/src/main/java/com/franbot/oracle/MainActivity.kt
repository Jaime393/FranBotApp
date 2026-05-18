package com.franbot.oracle

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.File

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val modelFile = File(filesDir, "modelo.gguf")
        if (!modelFile.exists()) {
            startActivity(Intent(this, DownloadActivity::class.java))
            finish()
            return
        }
        startService(Intent(this, LlamaService::class.java))
        lifecycleScope.launch {
            while (!isServerReady()) delay(1000)
            startActivity(Intent(this@MainActivity, ChatActivity::class.java))
            finish()
        }
    }
    private fun isServerReady(): Boolean {
        return try {
            val url = java.net.URL("http://127.0.0.1:8081/health")
            (url.openConnection() as java.net.HttpURLConnection).responseCode == 200
        } catch (e: Exception) { false }
    }
}
