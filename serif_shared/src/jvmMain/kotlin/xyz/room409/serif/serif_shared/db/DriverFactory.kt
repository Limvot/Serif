package xyz.room409.serif.serif_shared.db
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver

// Implementation of DriverFactory for the JVM version of Serif
// (Desktop, Pinephone, etc)
actual class DriverFactory {
    actual fun createDriver(): SqlDriver {
        // TODO(marcus): this location will generate a test.db
        // in the current directory, we will want to have
        // a proper location for this eventually.
        val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:test.db")
        SessionDb.Schema.create(driver)
        return driver
    }
}
