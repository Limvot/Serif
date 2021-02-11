package xyz.room409.serif.serif_shared.db
import xyz.room409.serif.serif_shared.db.SessionDb
import xyz.room409.serif.serif_shared.db.*
import com.squareup.sqldelight.db.SqlDriver

expect class DriverFactory {
    fun createDriver(): SqlDriver
}

fun saveSession(db : SessionDb?, username : String, access_token : String, transactionId : Long) {
    if(db != null) {
        val sessionQueries = db.sessionDbQueries
        sessionQueries.insertSession(username, access_token, transactionId)
    }
}

fun updateSession(db : SessionDb?, auth_tok : String, transactionId : Long) {
    if(db != null) {
        val sessionQueries = db.sessionDbQueries
        sessionQueries.updateSession(transactionId, auth_tok)
    }
}

fun getStoredSessions(db : SessionDb?) : List<Triple<String,String,Long>> {
    if(db != null) {
        val sessionQueries = db.sessionDbQueries
        val saved_sessions = sessionQueries.selectAllSessions { user : String, auth_tok : String, transactionId : Long ->
            Triple(user,auth_tok,transactionId) }.executeAsList()
        return saved_sessions
    } else {
        return listOf()
    }
}

fun deleteAllSessions(db : SessionDb?) {
    if(db != null) {
        val sessionQueries = db.sessionDbQueries
        sessionQueries.deleteAllSessions()
    }
}
