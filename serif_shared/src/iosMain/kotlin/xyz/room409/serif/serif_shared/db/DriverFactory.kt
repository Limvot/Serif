package xyz.room409.serif.serif_shared.db
import xyz.room409.serif.serif_shared.db.SessionDb
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.drivers.native.NativeSqliteDriver

//Implementation of DriverFactory for iOS version of Serif
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(SessionDb.Schema, "test.db")
    }
}
