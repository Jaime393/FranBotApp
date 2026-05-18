#!/data/data/com.termux/files/usr/bin/bash
set -e

echo "Generando proyecto Android FranBot..."

# Estructura de carpetas
mkdir -p app/src/main/assets
mkdir -p app/src/main/java/com/franbot/oracle
mkdir -p app/src/main/res/layout
mkdir -p app/src/main/res/values
mkdir -p .github/workflows

# ========== 2. Archivos Gradle ==========
cat > settings.gradle << 'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "FranBot"
include ':app'
EOF

cat > build.gradle << 'EOF'
buildscript {
    repositories { google(); mavenCentral() }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.1.0'
        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0'
    }
}
task clean(type: Delete) { delete rootProject.buildDir }
EOF

cat > app/build.gradle << 'EOF'
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}
android {
    namespace 'com.franbot.oracle'
    compileSdk 34
    defaultConfig {
        applicationId "com.franbot.oracle"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "8.1"
    }
    buildTypes {
        release {
            minifyEnabled false
            shrinkResources false
        }
    }
}
dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'org.json:json:20231013'
}
EOF

# ========== 3. Archivos Kotlin ==========
cat > app/src/main/java/com/franbot/oracle/NativeBridge.kt << 'EOF'
package com.franbot.oracle

class NativeBridge {
    // No se usa JNI, todo con ProcessBuilder
}
EOF

cat > app/src/main/java/com/franbot/oracle/LlamaService.kt << 'EOF'
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
EOF

cat > app/src/main/java/com/franbot/oracle/DownloadActivity.kt << 'EOF'
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
EOF

cat > app/src/main/java/com/franbot/oracle/MainActivity.kt << 'EOF'
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
EOF

cat > app/src/main/java/com/franbot/oracle/ChatActivity.kt << 'EOF'
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
EOF

cat > app/src/main/AndroidManifest.xml << 'EOF'
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/>
    <application android:allowBackup="true" android:icon="@mipmap/ic_launcher"
        android:label="FranBot Oracle" android:theme="@android:style/Theme.Material.Light.NoActionBar"
        android:usesCleartextTraffic="true">
        <activity android:name=".DownloadActivity" android:exported="true">
            <intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>
        </activity>
        <activity android:name=".MainActivity" android:exported="false"/>
        <activity android:name=".ChatActivity" android:exported="false"/>
        <service android:name=".LlamaService" android:foregroundServiceType="dataSync" android:exported="false"/>
    </application>
</manifest>
EOF

# ========== 4. Recursos vacíos (para que compile) ==========
echo '<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">FranBot Oracle</string>
</resources>' > app/src/main/res/values/strings.xml

echo '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">
</LinearLayout>' > app/src/main/res/layout/activity_main.xml

# ========== 5. Workflow de GitHub Actions ==========
cat > .github/workflows/build.yml << 'EOF'
name: Build APK (no NDK)
on: [push]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    - name: Setup JDK 17
      uses: actions/setup-java@v4
      with: { distribution: 'temurin', java-version: '17' }
    - name: Setup Android SDK
      uses: android-actions/setup-android@v3
    - name: Build APK
      run: |
        chmod +x gradlew
        ./gradlew assembleRelease
    - name: Upload APK
      uses: actions/upload-artifact@v4
      with: { name: FranBot-Release, path: app/build/outputs/apk/release/*.apk }
EOF

# ========== 6. Gradle wrapper ==========
wget https://raw.githubusercontent.com/gradle/gradle/v8.1.0/gradlew -O gradlew
chmod +x gradlew
mkdir -p gradle/wrapper
wget https://raw.githubusercontent.com/gradle/gradle/v8.1.0/gradle/wrapper/gradle-wrapper.jar -O gradle/wrapper/gradle-wrapper.jar
cat > gradle/wrapper/gradle-wrapper.properties << 'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.1-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF

echo "✅ Proyecto generado correctamente en $(pwd)"
