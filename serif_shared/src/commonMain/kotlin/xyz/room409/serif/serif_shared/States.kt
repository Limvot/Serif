package xyz.room409.serif.serif_shared
import kotlin.collections.*

sealed class MatrixState {
    val version: String
        get() {
            return "Serif Matrix client, pre-alpha on ${Platform().platform}"
        }
}
class MatrixLogin(val login_message: String, val mclient: MatrixClient) : MatrixState() {
    constructor() : this(
        login_message = "Please enter your username and password\n",
        mclient = MatrixClient()
    )
    fun login(username: String, password: String, onSync: () -> Unit): MatrixState {
        when (val loginResult = mclient.login(username, password, onSync)) {
            is Success -> { return MatrixRooms(msession = loginResult.value, rooms = listOf(), message = "Logged in! Maybe try syncing?") }
            is Error -> {
                return MatrixLogin(
                    login_message = "${loginResult.message} - exception was ${loginResult.cause}, please login again...\n",
                    mclient = mclient
                )
            }
        }
    }
    fun loginFromSession(username: String, onSync: () -> Unit): MatrixState {
        when (val loginResult = mclient.loginFromSavedSession(username, onSync)) {
            is Success -> { return MatrixRooms(msession = loginResult.value, rooms = listOf(), message = "Logged in! Maybe try syncing?") }
            is Error -> {
                return MatrixLogin(
                    login_message = "${loginResult.message} - exception was ${loginResult.cause}, please login again...\n",
                    mclient = mclient
                )
            }
        }
    }

    fun getSessions(): List<String> {
        return mclient.getStoredSessions()
    }
}
class MatrixRooms(private val msession: MatrixSession, val rooms: List<Pair<String, String>>, val message: String) : MatrixState() {
    fun refresh(): MatrixState = MatrixRooms(
                                    msession = msession, rooms = msession.rooms,
                                    message = "updated...\n"
                                )
    fun getRoom(id: String): MatrixState {
        return MatrixChatRoom(
            msession,
            id,
            this.rooms.find({ (_id, _) -> _id == id })!!.second,
            msession.getRoomEvents(id).map {
                if (it as? RoomMessageEvent != null) {
                    Pair(it.sender, it.content.body)
                } else { null }
            }.filterNotNull()
        )
    }
    fun fake_logout(): MatrixState {
        msession.closeSession()
        return MatrixLogin("Closing session, returning to the login prompt for now\n", MatrixClient())
    }
}
class MatrixChatRoom(private val msession: MatrixSession, val room_id: String, val name: String, val messages: List<Pair<String,String>>) : MatrixState() {
    fun sendMessage(msg: String): MatrixState {
        when (val sendMessageResult = msession.sendMessage(msg, room_id)) {
            is Success -> { println("${sendMessageResult.value}") }
            is Error -> { println("${sendMessageResult.message} - exception was ${sendMessageResult.cause}") }
        }
        return this
    }
    fun refresh(): MatrixState = MatrixChatRoom(
                                    msession,
                                    room_id,
                                    msession.rooms.find({ (id, _) -> id == room_id })!!.second,
                                    msession.getRoomEvents(room_id).map {
                                        if (it as? RoomMessageEvent != null) {
                                            Pair(it.sender, it.content.body)
                                        } else { null }
                                    }.filterNotNull()
                                )
    fun exitRoom(): MatrixState {
        return MatrixRooms(msession, msession.rooms, "Back to rooms.")
    }
}
