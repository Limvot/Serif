package xyz.room409.serif.serif_shared

import io.ktor.http.*
import io.ktor.client.*

import java.io.File

expect object Platform {
    val platform: String
    fun getFile(): File
    fun makeHttpClient(): HttpClient
    fun getOpenUrl(): ((url: String) -> Unit)?
}
