package xyz.room409.serif.serif_shared

import io.ktor.client.*
//import io.ktor.client.engine.cio.*
import io.ktor.client.engine.okhttp.*
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread

actual object Platform {
    actual val platform: String = "JVM"
    actual fun getFile(): File {
        val cache_path = File(System.getProperty("user.dir") + "/cache/")
        cache_path.mkdirs()
        return File.createTempFile("serif_media_", "", cache_path)
    }
    // 35 seconds, to comfortably handle the 30 second sync
    // timeout we send to the server (recommended Matrix default)
    //actual fun makeHttpClient() = HttpClient(CIO) {
    actual fun makeHttpClient() = HttpClient(OkHttp) {
        engine {
            preconfigured = OkHttpClient.Builder()
                                        .connectTimeout(10, TimeUnit.SECONDS)
                                        .writeTimeout(10, TimeUnit.SECONDS)
                                        .readTimeout(35, TimeUnit.SECONDS)
                                        .build();
        }
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
    actual fun getOpenUrl(): ((url: String) -> Unit)? = { url: String ->
        thread(start = true) {
            // We have to try using xdg-open first,
            // since PinePhone somehow implements the
            // Desktop API but has the same problem with the
            // GTK_BACKEND var
            try {
                println("Trying to open $url with exec 'xdg-open $url'")
                val pb = ProcessBuilder("xdg-open", url)
                // Somehow this environment variable gets set for pb
                // when it's NOT in System.getenv(). And of course, this
                // is the one that makes xdg-open try to launch an X version
                // of Firefox, giving the dreaded Firefox is already running
                // message if you've got a Wayland version running already.
                pb.environment().clear()
                pb.environment().putAll(System.getenv())
                pb.redirectErrorStream(true)
                val process = pb.start()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                while (reader.readLine() != null) {}
                process.waitFor()
                println("done trying to open url")
            } catch (e1: Exception) {
                try {
                    println("Trying to open $url with Desktop")
                    java.awt.Desktop.getDesktop().browse(java.net.URI(url))
                } catch (e2: Exception) {
                    println("Couldn't get ProcessBuilder('xdg-open $url') or Desktop, problem was $e1 then $e2")
                }
            }
        }
    }
}
