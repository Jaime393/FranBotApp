package com.franbot.oracle

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class DownloadActivity : AppCompatActivity() {
    private val modelUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-GGUF/resolve/main/tinyllama-1.1b.Q4_K_M.gguf"
    private val binUrl = "https://github.com/ggerganov/llama.cpp/releases/download/bXXXX/llama-server-android-arm64-v8a"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48,48,48,48) }
        val status = TextView(this)
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        layout.addView(status); layout.addView(progress)
        setContentView(layout)
        lifecycleScope.launch(Dispatchers.IO) {
            download(binUrl, File(filesDir, "llama-server"), status, progress)
            download(modelUrl, File(filesDir, "modelo.gguf"), status, progress)
            withContext(Dispatchers.Main) {
                startActivity(Intent(this@DownloadActivity, MainActivity::class.java))
                finish()
            }
        }
    }
    private suspend fun download(url: String, dest: File, status: TextView, progress: ProgressBar) {
        if (dest.exists()) return
        status.text = "Descargando ${dest.name}..."
        val request = Request.Builder().url(url).build()
        val response = OkHttpClient().newCall(request).execute()
        val total = response.body!!.contentLength()
        response.body!!.byteStream().use { input ->
            FileOutputStream(dest).use { output ->
                val buffer = ByteArray(8192)
                var downloaded = 0L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    progress.progress = (downloaded * 100 / total).toInt()
                    status.text = "${dest.name} ${progress.progress}%"
                }
            }
        }
        if (dest.name == "llama-server") dest.setExecutable(true)
    }
}
