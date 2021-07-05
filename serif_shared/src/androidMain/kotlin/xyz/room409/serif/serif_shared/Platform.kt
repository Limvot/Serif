package xyz.room409.serif.serif_shared

import android.content.Context
import java.io.File

actual object Platform {
    actual val platform: String = "Android ${android.os.Build.VERSION.SDK_INT}"
    var context: Context? = null
    actual fun getFile(): File {
        return File.createTempFile("serif_media_", "", context!!.filesDir)
    }
}
