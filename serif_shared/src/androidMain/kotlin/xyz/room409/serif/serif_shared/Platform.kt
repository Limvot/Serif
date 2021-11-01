package xyz.room409.serif.serif_shared

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.engine.android.*
import android.content.Context
import java.io.File

actual object Platform {
    actual val platform: String = "Android ${android.os.Build.VERSION.SDK_INT}"
    var context: Context? = null
    actual fun getFile(): File {
        return File.createTempFile("serif_media_", "", context!!.filesDir)
    }
    // 35 seconds, to comfortably handle the 30 second sync
    // timeout we send to the server (recommended Matrix default)
    actual fun makeHttpClient() = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 35000
        }
        install(JsonFeature) {
            serializer = KotlinxSerializer(
                kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }
            )
        }
    }
    actual fun getOpenUrl(): ((url: String) -> Unit)? = null
}
