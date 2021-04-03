package xyz.room409.serif.serif_shared
import kotlin.collections.*

sealed class MatrixState {
    val version: String
        get() {
            return "Serif Matrix client, pre-alpha on ${Platform().platform}"
        }
    abstract fun refresh(): MatrixState
}
class MatrixLogin(val login_message: String, val mclient: MatrixClient) : MatrixState() {
    constructor() : this(
        login_message = "Please enter your username and password\n",
        mclient = MatrixClient()
    )
    // No need to refresh
    override fun refresh(): MatrixState = this
    fun login(username: String, password: String, onSync: () -> Unit): MatrixState {
        when (val loginResult = mclient.login(username, password, onSync)) {
            is Success -> { return MatrixRooms(msession = loginResult.value, message = "Logged in! Waiting on sync...") }
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
            is Success -> { return MatrixRooms(msession = loginResult.value, message = "Logged in! Maybe try syncing?") }
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
data class SharedUiRoom(val id: String, val name: String, val unreadCount: Int, val highlightCount: Int, val lastMessage: SharedUiMessage?)
fun determineRoomName(room: Room, id: String): String {
    return room.state.events.firstStateEventContentOfType<RoomNameContent>()?.name
        ?: room.state.events.firstStateEventContentOfType<RoomCanonicalAliasContent>()?.alias
        ?: room.summary.heroes?.joinToString(", ")
        ?: "<no room name - $id>"
}
class MatrixRooms(private val msession: MatrixSession, val message: String) : MatrixState() {
    val rooms: List<SharedUiRoom> = msession.mapRooms { id, room ->
        SharedUiRoom(
            id,
            determineRoomName(room, id),
            room.unread_notifications?.notification_count ?: 0,
            room.unread_notifications?.highlight_count ?: 0,
            room.timeline.events.findLast { it as? RoomMessageEvent != null }?.let {
                val it = it as RoomMessageEvent
                SharedUiMessagePlain(it.sender, it.content.body, it.event_id, it.origin_server_ts)
            }
        )
    }.sortedBy { -(it.lastMessage?.timestamp ?: 0) }
    override fun refresh(): MatrixState = MatrixRooms(
        msession = msession,
        message = "updated...\n"
    )
    fun getRoom(id: String, window_back_length: Int, message_window_base: String?, window_forward_length: Int): MatrixState {
        return MatrixChatRoom(
            msession,
            id,
            this.rooms.find({ room -> room.id == id })!!.name,
            window_back_length,
            message_window_base,
            window_forward_length
        )
    }
    fun fake_logout(): MatrixState {
        msession.closeSession()
        return MatrixLogin("Closing session, returning to the login prompt for now\n", MatrixClient())
    }
}
abstract class SharedUiMessage() {
    abstract val sender: String
    abstract val message: String
    abstract val id: String
    abstract val timestamp: Long
    abstract val replied_event: String
}
data class SharedUiMessagePlain(
    override val sender: String,
    override val message: String,
    override val id: String,
    override val timestamp: Long,
    override val replied_event: String = ""
) : SharedUiMessage()
class SharedUiImgMessage(
    override val sender: String,
    override val message: String,
    override val id: String,
    override val timestamp: Long,
    override val replied_event: String = "",
    val url: String
) : SharedUiMessage()
class SharedUiAudioMessage(
    override val sender: String,
    override val message: String,
    override val id: String,
    override val timestamp: Long,
    override val replied_event: String = "",
    val url: String
) : SharedUiMessage()

class MatrixChatRoom(private val msession: MatrixSession, val room_id: String, val name: String, val window_back_length: Int, val message_window_base: String?, val window_forward_length: Int) : MatrixState() {
    val username = msession.user

    private val is_edit_content = { msg_content: TextRMEC ->
        ((msg_content.new_content != null) && (msg_content.relates_to?.event_id != null))
    }
    private val edits: Map<String,ArrayList<SharedUiMessage>> = {
        val edit_maps: MutableMap<String,ArrayList<SharedUiMessage>> = mutableMapOf()
        msession.getRoomEvents(room_id).forEach {
            if (it as? RoomMessageEvent != null) {
                val msg_content = it.content
                if (msg_content is TextRMEC) {
                    if(is_edit_content(msg_content)) {
                        //This is an edit
                        val replaced_id = msg_content!!.relates_to!!.event_id!!
                        val edit_msg = SharedUiMessagePlain(it.sender, msg_content!!.new_content!!.body,
                            it.event_id, it.origin_server_ts)

                        if(edit_maps.contains(replaced_id)) {
                            edit_maps.get(replaced_id)!!.add(edit_msg)
                        } else {
                            edit_maps.put(replaced_id, arrayListOf(edit_msg))
                        }
                    }
                }
            }
        }
        edit_maps.toMap()
    }()
    val messages: List<SharedUiMessage> = msession.getRoomEvents(room_id).map {
        if (it as? RoomMessageEvent != null) {
            val msg_content = it.content
            var generate_media_msg = { url: String, func: (String,String,String,Long,String,String) -> SharedUiMessage ->
                when (val url_local = msession.getLocalMediaPathFromUrl(url)) {
                    is Success -> {
                        func(
                            it.sender, it.content.body, it.event_id,
                            it.origin_server_ts, "", url_local.value
                        )
                    }
                    is Error -> {
                        SharedUiMessagePlain(
                            it.sender, "Failed to load media ${url}",
                            it.event_id, it.origin_server_ts, ""
                        )
                    }
                }
            }
            when (msg_content) {
                is TextRMEC -> {
                    val normal_msg_builder = {
                        SharedUiMessagePlain(it.sender, it.content.body, it.event_id, it.origin_server_ts, msg_content.relates_to?.in_reply_to?.event_id ?: "")
                    }
                    if((msg_content.new_content != null) && (msg_content.relates_to?.event_id == null)) {
                        //This is a poorly formed edit
                        //No idea which event this edit is editing, just display fallback msg
                        SharedUiMessagePlain(it.sender, it.content.body, it.event_id, it.origin_server_ts)
                    } else {
                        if(is_edit_content(msg_content)) {
                            //Don't display edits
                            null
                        } else {
                            //This is a text message, check for any edits of this message
                            if(edits.contains(it.event_id)) {
                                val possible_edits = edits.get(it.event_id)!!
                                val edited = possible_edits.lastOrNull { it.sender.contains(username) }
                                if(edited != null) {
                                    SharedUiMessagePlain(
                                        it.sender,
                                        "${edited.message} (edited)",
                                        edited.id,
                                        it.origin_server_ts,
                                        msg_content.relates_to?.in_reply_to?.event_id ?: "")
                                } else {
                                    normal_msg_builder()
                                }
                            } else {
                                //No edits for this event
                                normal_msg_builder()
                            }
                        }
                    }
                }
                is ImageRMEC -> generate_media_msg(msg_content.url, ::SharedUiImgMessage)
                is AudioRMEC -> generate_media_msg(msg_content.url, ::SharedUiAudioMessage)
                else -> SharedUiMessagePlain(it.sender, "UNHANDLED EVENT!!! ${it.content.body}", it.event_id, it.origin_server_ts)
            }
        } else { null }
    }.filterNotNull().let { all_messages ->
        val base_idx = if (message_window_base == null) {
            all_messages.size - 1
        } else {
            all_messages.indexOfFirst({ it.id == message_window_base })
        }
        val desired_first_index = base_idx - window_back_length
        val first_index = if (desired_first_index < 0) {
            println("Backfilling from States becuase $desired_first_index is < 0")
            msession.requestBackfill(room_id)
            0
        } else {
            desired_first_index
        }
        // we don't have a forward-fill yet, and indeed can't get ourselves into that position (yet)
        val last_index = kotlin.math.min(all_messages.size - 1, base_idx + window_forward_length)
        all_messages.slice(first_index .. last_index)
    }
    fun sendMessage(msg: String): MatrixState {
        when (val sendMessageResult = msession.sendMessage(msg, room_id)) {
            is Success -> { println("${sendMessageResult.value}") }
            is Error -> { println("${sendMessageResult.message} - exception was ${sendMessageResult.cause}") }
        }
        return this
    }
    fun sendImageMessage(msg: String): MatrixState {
        when (val sendMessageResult = msession.sendImageMessage(msg, room_id)) {
            is Success -> { println("${sendMessageResult.value}") }
            is Error -> { println("${sendMessageResult.message} - exception was ${sendMessageResult.cause}") }
        }
        return this
    }
    fun sendReply(msg: String, replied: String): MatrixState {
        when (val sendMessageResult = msession.sendMessage(msg, room_id, replied)) {
            is Success -> { println("${sendMessageResult.value}") }
            is Error -> { println("${sendMessageResult.message} - exception was ${sendMessageResult.cause}") }
        }
        return this
    }
    fun sendEdit(msg: String, edited_id: String): MatrixState {
        when (val sendMessageResult = msession.sendEdit(msg, room_id, edited_id)) {
            is Success -> { println("${sendMessageResult.value}") }
            is Error -> { println("${sendMessageResult.message} - exception was ${sendMessageResult.cause}") }
        }
        return this
    }
    fun getEventSrc(msg_id: String): String {
        for(event in msession.getRoomEvents(room_id)) {
            if (event as? RoomMessageEvent != null) {
                if(event.event_id == msg_id) {
                    return event.raw_self.toString()
                            .replace("\",","\",\n")
                            .replace(",\"",",\n\"")
                            .replace("{","{\n")
                            .replace("}","\n}")
                            .replace("\" \"","\"\n\"")
                }
            }
        }
        return "No Source for $msg_id"
    }
    fun sendReceipt(eventID: String) {
        when (val readReceiptResult = msession.sendReadReceipt(eventID, room_id)){
            is Success -> println("read receipt sent")
            is Error -> println("read receipt failed because ${readReceiptResult.cause}")
        }
    }
    override fun refresh(): MatrixState = refresh(window_back_length, message_window_base, window_forward_length)
    fun refresh(new_window_back_length: Int, new_message_window_base: String?, new_window_forward_length: Int): MatrixState = MatrixChatRoom(
        msession,
        room_id,
        msession.mapRoom(room_id, { determineRoomName(it, room_id) }) ?: "<room gone?>",
        new_window_back_length,
        new_message_window_base,
        new_window_forward_length,
    )
    fun exitRoom(): MatrixState {
        return MatrixRooms(msession, "Back to rooms.")
    }
}
