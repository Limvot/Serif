package xyz.room409.serif.serif_shared
import xyz.room409.serif.serif_shared.db.*
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
        saveSession(this.db, username, access_token, transactionId)
    } 
    
    fun updateSession(access_token : String, transactionId : Long) {
        updateSession(this.db, access_token, transactionId) 
    }

    fun getStoredSessions() : List<Triple<String,String,Long>> {
        return getStoredSessions(this.db)
    }

    fun deleteAllSessions() {
        deleteAllSessions(this.db)
    }
}
