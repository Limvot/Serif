package xyz.room409.serif.serif_shared

import java.io.File

expect object Platform {
    val platform: String
    fun getFile(): File
}
