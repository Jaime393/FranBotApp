package com.franbot.oracle

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject

class ChatActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private lateinit var chatLog: TextView
    private lateinit var input: EditText
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        chatLog = TextView(this).apply { text = "FranBot listo\n" }
        input = EditText(this).apply { hint = "Escribe..." }
        val btn = Button(this).apply { text = "Enviar" }
        layout.addView(chatLog); layout.addView(input); layout.addView(btn)
        setContentView(layout)
        btn.setOnClickListener {
            val msg = input.text.toString()
            if (msg.isNotBlank()) {
                chatLog.append("Tú: $msg\n")
                input.setText("")
                send(msg)
            }
        }
    }
    private fun send(msg: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("messages", org.json.JSONArray().put(JSONObject().put("role", "user").put("content", msg)))
                    put("temperature", 0.7)
                    put("max_tokens", 500)
                }.toString()
                val request = Request.Builder()
                    .url("http://127.0.0.1:8081/v1/chat/completions")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                val response = client.newCall(request).execute()
                val json = JSONObject(response.body!!.string())
                val reply = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                withContext(Dispatchers.Main) { chatLog.append("FranBot: $reply\n") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { chatLog.append("Error: ${e.message}\n") }
            }
        }
    }
}
