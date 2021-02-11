package xyz.room409.serif.serif_shared
import xyz.room409.serif.serif_shared.db.*
import xyz.room409.serif.serif_shared.db.SessionDb
import xyz.room409.serif.serif_shared.db.DriverFactory

object Database {
    //See platform specific code in serif_shared for the
    //different implementations of DriverFactory and the
    //specific database drivers
    var db : SessionDb? = null
    fun initDb(driverFactory : DriverFactory) {
        this.db = SessionDb(driverFactory.createDriver())
    }

    fun saveSession(username : String, access_token : String, transactionId : Long) {
        this.db?.sessionDbQueries?.insertSession(username, access_token, transactionId)
    } 
    
    fun updateSession(access_token : String, transactionId : Long) {
        this.db?.sessionDbQueries?.updateSession(transactionId, access_token)
    }

    fun getStoredSessions() : List<Triple<String,String,Long>> {
        val saved_sessions = this.db?.sessionDbQueries?.selectAllSessions(
            { user : String, auth_tok : String, transactionId : Long ->
                Triple(user,auth_tok,transactionId) })?.executeAsList() ?: listOf()
        return saved_sessions
    }

    fun deleteAllSessions() {
        this.db?.sessionDbQueries?.deleteAllSessions()
    }
}
