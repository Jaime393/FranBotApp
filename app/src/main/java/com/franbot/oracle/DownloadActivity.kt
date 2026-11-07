package com.franbot.oracle

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

class DownloadActivity : AppCompatActivity() {
    private val modelUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-GGUF/resolve/main/tinyllama-1.1b.Q4_K_M.gguf"
    // llama.cpp b12192 no publica un binario suelto para Android; el tarball
    // contiene el lanzador llama-server y las 6 librerias .so que necesita.
    private val binTarballUrl = "https://github.com/ggml-org/llama.cpp/releases/download/b12192/llama-b12192-bin-android-arm64.tar.gz"
    private val binFiles = setOf(
        "llama-server",
        "libllama-server-impl.so",
        "libllama-common.so",
        "libmtmd.so",
        "libllama.so",
        "libggml.so",
        "libggml-base.so"
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48,48,48,48) }
        val status = TextView(this)
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        layout.addView(status); layout.addView(progress)
        setContentView(layout)
        lifecycleScope.launch(Dispatchers.IO) {
            if (!downloadBinaries(status, progress)) return@launch
            download(modelUrl, File(filesDir, "modelo.gguf"), status, progress)
            withContext(Dispatchers.Main) {
                startActivity(Intent(this@DownloadActivity, MainActivity::class.java))
                finish()
            }
        }
    }

    private fun binariesReady(): Boolean =
        binFiles.all { File(filesDir, it).exists() }

    private suspend fun downloadBinaries(status: TextView, progress: ProgressBar): Boolean {
        if (binariesReady()) return true
        val tarball = File(filesDir, "llama-b12192-bin-android-arm64.tar.gz")
        try {
            download(binTarballUrl, tarball, status, progress)
            status.text = "Extrayendo llama-server..."
            extractTarGz(tarball, filesDir, binFiles)
        } catch (e: Exception) {
            status.text = "Error al descargar llama-server: ${e.message}"
            return false
        }
        tarball.delete()
        File(filesDir, "llama-server").setExecutable(true)
        if (!binariesReady()) {
            status.text = "No se pudo extraer llama-server del tarball."
            return false
        }
        return true
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
                    if (total > 0) {
                        progress.progress = (downloaded * 100 / total).toInt()
                        status.text = "${dest.name} ${progress.progress}%"
                    }
                }
            }
        }
    }

    /** Extrae del .tar.gz solo los archivos cuyo nombre base esta en [wanted].
     *  Lector tar minimo sin dependencias: cabecera de 512 B, tamano en octal,
     *  nombre largo GNU ('L') y prefijo ustar. */
    private fun extractTarGz(tarGz: File, destDir: File, wanted: Set<String>) {
        GZIPInputStream(BufferedInputStream(FileInputStream(tarGz))).use { input ->
            val header = ByteArray(512)
            var longName: String? = null
            while (true) {
                if (!readFull(input, header)) return
                if (header.all { it == 0.toByte() }) return
                val name = longName ?: tarName(header)
                longName = null
                val size = tarSize(header)
                val type = header[156].toInt().toChar()
                when (type) {
                    'L' -> {
                        if (size > 4096) return
                        val nameBytes = ByteArray(size.toInt())
                        readFull(input, nameBytes)
                        longName = String(nameBytes, Charsets.UTF_8).trimEnd('\u0000')
                    }
                    '0', '\u0000' -> {
                        val base = File(name).name
                        if (base in wanted) {
                            FileOutputStream(File(destDir, base)).use { output ->
                                copyBytes(input, output, size)
                            }
                        } else {
                            skipBytes(input, size)
                        }
                    }
                    else -> skipBytes(input, size)
                }
                skipBlocks(input, size)
            }
        }
    }

    private fun tarName(header: ByteArray): String {
        val prefix = readString(header, 345, 155)
        val name = readString(header, 0, 100)
        return if (prefix.isEmpty()) name else "$prefix/$name"
    }

    private fun readString(header: ByteArray, offset: Int, length: Int): String {
        var end = offset
        while (end < offset + length && header[end] != 0.toByte()) end++
        return String(header, offset, end - offset, Charsets.UTF_8)
    }

    private fun tarSize(header: ByteArray): Long {
        val s = readString(header, 124, 12).trim()
        return if (s.isEmpty()) 0L else s.toLong(8)
    }

    private fun readFull(input: java.io.InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read == -1) return false
            offset += read
        }
        return true
    }

    private fun skipBytes(input: java.io.InputStream, size: Long) {
        var remaining = size
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read == -1) return
            remaining -= read
        }
    }

    private fun skipBlocks(input: java.io.InputStream, size: Long) {
        val pad = (512 - size % 512) % 512
        if (pad > 0) skipBytes(input, pad)
    }

    private fun copyBytes(input: java.io.InputStream, output: java.io.OutputStream, size: Long) {
        var remaining = size
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read == -1) return
            output.write(buffer, 0, read)
            remaining -= read
        }
    }
}
