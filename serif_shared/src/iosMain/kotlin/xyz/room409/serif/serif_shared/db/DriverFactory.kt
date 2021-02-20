package xyz.room409.serif.serif_shared.db
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.drivers.native.NativeSqliteDriver

// Implementation of DriverFactory for iOS version of Serif
actual class DriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(SessionDb.Schema, "test.db")
    }
}
