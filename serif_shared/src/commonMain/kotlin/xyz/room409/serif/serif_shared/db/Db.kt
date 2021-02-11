package xyz.room409.serif.serif_shared.db
import xyz.room409.serif.serif_shared.db.SessionDb
import com.squareup.sqldelight.db.SqlDriver

expect class DriverFactory {
    fun createDriver(): SqlDriver
}
