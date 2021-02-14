package xyz.room409.serif.serif_shared.db
import android.content.Context
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver

// Implementation of DriverFactory for Android version of Serif
actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(SessionDb.Schema, context, "test.db")
    }
}
