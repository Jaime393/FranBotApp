# FranBotApp

FranBotApp es una app Android (Kotlin) que corre un modelo de lenguaje en local
en el teléfono, sin depender de ningún servidor externo para responder.

## Cómo funciona

La app descarga lo que necesita en el primer arranque y luego habla con un
servidor local:

1. `MainActivity` comprueba si el modelo ya está descargado. Si falta, abre
   `DownloadActivity`.
2. `DownloadActivity` descarga dos cosas:
   - El binario `llama-server` y sus 6 librerías `.so`, desde el tarball
     `llama-b12192-bin-android-arm64.tar.gz` de llama.cpp (b12192, ~78 MB).
   - El modelo TinyLlama-1.1B Q4_K_M, desde Hugging Face
     (`TheBloke/TinyLlama-1.1B-GGUF`).
3. `LlamaService` arranca `llama-server` en local con el modelo:
   `llama-server -m modelo.gguf -c 1024 -t 4 --host 127.0.0.1 --port 8081 --mlock`.
4. `ChatActivity` envía cada mensaje como petición HTTP a
   `http://127.0.0.1:8081/v1/chat/completions` (temperature 0.7, max_tokens 500)
   y muestra la respuesta.

Todo el chat ocurre contra `127.0.0.1:8081`, dentro del propio teléfono. No hay
JNI: `NativeBridge` es solo un marcador y el servidor se lanza con
`ProcessBuilder`.

## Estructura del proyecto

```
FranBotApp/
├── README.md                 # Este archivo
├── app/
│   ├── build.gradle          # compileSdk 34, minSdk 24, versionName 8.1
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/franbot/oracle/
│       │   ├── MainActivity.kt       # Entrada: modelo presente o descarga
│       │   ├── DownloadActivity.kt   # Descarga tarball + modelo, extrae
│       │   ├── LlamaService.kt       # Servicio: arranca llama-server
│       │   ├── ChatActivity.kt       # Chat contra 127.0.0.1:8081
│       │   └── NativeBridge.kt       # Marcador: no se usa JNI
│       └── res/                      # layout y strings
├── .github/workflows/build.yml       # CI: construye el APK de release
├── build.gradle
├── settings.gradle
├── gradle.properties
└── gradlew
```

## Construir el APK

```bash
./gradlew assembleRelease
```

Requisitos: JDK 17 y Android SDK (compileSdk 34, minSdk 24). El APK sale en
`app/build/outputs/apk/release/`.

### Firma

El APK de release se firma solo si hay un keystore disponible. En CI, el
workflow decodifica el keystore desde el secreto `ANDROID_KEYSTORE_BASE64` y
pasa las claves como variables de entorno:

- `ANDROID_KEYSTORE_BASE64`: keystore codificado en base64
- `KEYSTORE_PASSWORD`: contraseña del keystore
- `KEY_ALIAS`: alias de la clave
- `KEY_PASSWORD`: contraseña de la clave

Sin keystore, `assembleRelease` sigue funcionando pero el APK sale **sin
firmar** y no es instalable por vía normal. El keystore y los secretos los crea
un humano; el código solo los lee.

## Estado

La app estaba rota en dos puntos: la URL del binario era un placeholder
(`bXXXX`) que apuntaba a un asset que no existe, y el APK salía sin firmar.
Ambos están corregidos en esta revisión (ruta A: descarga y extracción del
tarball en el primer arranque; firma por keystore en CI).

Nota: la URL del modelo TinyLlama en Hugging Face no se pudo verificar en vivo
desde el entorno de preparación (Hugging Face devolvía 401 en todas las rutas
probadas). Es la ruta pública estándar de TheBloke y el código ya la contiene;
la confirmación en vivo queda para el momento de aplicar.
