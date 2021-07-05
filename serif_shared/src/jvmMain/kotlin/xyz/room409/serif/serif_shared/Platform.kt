package xyz.room409.serif.serif_shared

import java.io.File

actual object Platform {
    actual val platform: String = "JVM"
    actual fun getFile(): File {
        val cache_path = File(System.getProperty("user.dir") + "/cache/")
        cache_path.mkdirs()
        return File.createTempFile("serif_media_", "", cache_path)
    }
}
