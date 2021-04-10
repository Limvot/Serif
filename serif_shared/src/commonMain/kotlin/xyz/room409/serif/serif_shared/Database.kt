package xyz.room409.serif.serif_shared
import xyz.room409.serif.serif_shared.db.*
import xyz.room409.serif.serif_shared.db.DriverFactory
import xyz.room409.serif.serif_shared.db.SessionDb
import java.io.File

object Database {
    // See platform specific code in serif_shared for the
    // different implementations of DriverFactory and the
    // specific database drivers
    var db: SessionDb? = null
    fun initDb(driverFactory: DriverFactory) {
        this.db = SessionDb(driverFactory.createDriver())
    }

    fun saveSession(username: String, access_token: String, transactionId: Long) {
        this.db?.sessionDbQueries?.insertSession(username, access_token, transactionId)
    }

    fun updateSession(access_token: String, transactionId: Long) {
        this.db?.sessionDbQueries?.updateSession(transactionId, access_token)
    }

    fun getStoredSessions(): List<Triple<String, String, Long>> {
        val saved_sessions = this.db?.sessionDbQueries?.selectAllSessions(
            { user: String, auth_tok: String, transactionId: Long ->
                Triple(user, auth_tok, transactionId)
            })?.executeAsList() ?: listOf()
        return saved_sessions
    }

    fun getUserSession(user: String): Triple<String, String, Long> {
        val saved_session = this.db?.sessionDbQueries?.selectUserSession(user) { user: String, auth_tok: String, transactionId: Long ->
            Triple(user, auth_tok, transactionId)
        }?.executeAsOne() ?: Triple("", "", 0L)
        return saved_session
    }

    fun deleteAllSessions() {
        this.db?.sessionDbQueries?.deleteAllSessions()
    }

    fun getMediaInCache(url: String): String? {
        return this.db?.sessionDbQueries?.selectCachedMedia(url) { _: String, localPath: String ->
            localPath
        }?.executeAsOneOrNull()
    }

    fun addMediaToCache(url: String, file_data: ByteArray, update: Boolean): String {
        val cache_path = File(System.getProperty("user.dir") + "/cache/")
        cache_path.mkdirs()
        val file = File.createTempFile("serif_media_", "", cache_path)
        file.outputStream().write(file_data)
        val local = file.toPath().toString()
        if (update) {
            this.db?.sessionDbQueries?.updateMedia(local, url)
        } else {
            this.db?.sessionDbQueries?.insertMedia(url, local)
        }
        return local
    }
}
