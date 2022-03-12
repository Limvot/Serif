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
                    explicitNulls = false
                }
            )
        }
    }
}
