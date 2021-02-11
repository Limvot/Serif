package xyz.room409.serif.serif_shared
import kotlin.collections.*

sealed class MatrixState {
    val version: String
        get() {
            return "Serif Matrix client, pre-alpha on ${Platform().platform}"
        }
}
class MatrixLogin(val login_message: String, val mclient: MatrixClient): MatrixState() {
    constructor(): this(login_message="Please enter your username and password\n",
                        mclient=MatrixClient())
    fun login(username: String, password: String): MatrixState {
        when (val loginResult = mclient.login(username, password)) {
            is Success -> { return MatrixRooms(msession=loginResult.value, rooms=listOf(), message="Logged in! Maybe try syncing?") }
            is Error -> { return MatrixLogin(login_message="${loginResult.message} - exception was ${loginResult.cause}, please login again...\n",
                                             mclient=mclient) }
        }
    }
    fun loginFromSession(username: String): MatrixState {
        when (val loginResult = mclient.loginFromSavedSession(username)) {
            is Success -> { return MatrixRooms(msession=loginResult.value, rooms=listOf(), message="Logged in! Maybe try syncing?") }
            is Error -> { return MatrixLogin(login_message="${loginResult.message} - exception was ${loginResult.cause}, please login again...\n",
                                             mclient=mclient) }
        }
    }

    fun getSessions() : List<String> {
        return mclient.getStoredSessions()
    }
}
class MatrixRooms(val msession: MatrixSession, val rooms: List<Pair<String, String>>, val message: String): MatrixState() {
    fun sync(): MatrixState {
        when (val syncResult = msession.sync()) {
            is Success -> { return MatrixRooms(msession=msession, rooms=msession.rooms,
                                               message="Sync success\n") }
            is Error   -> { return MatrixRooms(msession=msession, rooms=rooms,
                                               message="${syncResult.message} - exception was ${syncResult.cause}, maybe try again?\n") }
        }
    }
    fun getRoom(id: String): MatrixState {
        return MatrixChatRoom(msession,
                              id,
                              this.rooms.find({(_id,_) -> _id == id })!!.second,
                              msession.sync_response!!.rooms.join[id]!!.timeline.events.map { (it as? RoomMessageEvent)?.content?.body }.filterNotNull())
    }
    fun fake_logout(): MatrixState {
        msession.closeSession()
        return MatrixLogin("Closing session, returning to the login prompt for now\n", MatrixClient())
    }
}
class MatrixChatRoom(val msession: MatrixSession, val room_id: String, val name : String, val messages: List<String>): MatrixState() {
    fun sendMessage(msg : String): MatrixState {
        when (val sendMessageResult = msession.sendMessage(msg, room_id)) {
            is Success -> { println("${sendMessageResult.value}") }
            is Error -> { println("${sendMessageResult.message} - exception was ${sendMessageResult.cause}") }
        }
        return this
    }
    fun exitRoom(): MatrixState {
        return MatrixRooms(msession, msession.rooms, "Back to rooms. You can still do a :sync...")
    }

    fun getRoomName(): String {
        return this.name
    }
}

