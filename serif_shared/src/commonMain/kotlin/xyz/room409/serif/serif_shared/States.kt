package xyz.room409.serif.serif_shared
import kotlin.collections.*
import kotlin.math.*

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
class MatrixRooms(private val msession: MatrixSession, val message: String) : MatrixState() {
    val rooms: List<SharedUiRoom> = msession.mapRooms { id, name, unread_notif, unread_highlight, last_event ->
        SharedUiRoom(
            id,
            name,
            unread_notif,
            unread_highlight,
            last_event?.let { SharedUiMessagePlain(it.sender, it.content.body, it.event_id, it.origin_server_ts, mapOf()) }
        )
    }.sortedBy { -(it.lastMessage?.timestamp ?: 0) }
    override fun refresh(): MatrixState = MatrixRooms(
        msession = msession,
        message = "updated...\n"
    )
    fun createRoom(name: String, room_alias_name: String, topic: String) = msession.createRoom(name, room_alias_name, topic)

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
    abstract val displayname: String?
    abstract val avatar_file_path: String?
    abstract val message: String
    abstract val id: String
    abstract val timestamp: Long
    abstract val replied_event: String
    abstract val reactions: Map<String, Set<String>>
}
data class SharedUiMessagePlain(
    override val sender: String,
    override val message: String,
    override val displayname: String?,
    override val message: String,
    override val id: String,
    override val timestamp: Long,
    override val reactions: Map<String, Set<String>>,
    override val replied_event: String = ""
) : SharedUiMessage()
class SharedUiImgMessage(
    override val sender: String,
    override val message: String,
    override val displayname: String?,
    override val message: String,
    override val id: String,
    override val timestamp: Long,
    override val reactions: Map<String, Set<String>>,
    override val replied_event: String = "",
    val url: String
) : SharedUiMessage()
class SharedUiAudioMessage(
    override val sender: String,
    override val message: String,
    override val displayname: String?,
    override val message: String,
    override val id: String,
    override val timestamp: Long,
    override val reactions: Map<String, Set<String>>,
    override val replied_event: String = "",
    val url: String
) : SharedUiMessage()
class SharedUiVideoMessage(
    override val sender: String,
    override val message: String,
    override val displayname: String?,
    override val message: String,
    override val id: String,
    override val timestamp: Long,
    override val reactions: Map<String, Set<String>>,
    override val replied_event: String = "",
    val url: String
) : SharedUiMessage()
class SharedUiFileMessage(
    override val sender: String,
    override val message: String,
    override val displayname: String?,
    override val message: String,
    override val id: String,
    override val timestamp: Long,
    override val reactions: Map<String, Set<String>>,
    val filename: String,
    val mimetype: String,
    val url: String,
    override val replied_event: String = "",
) : SharedUiMessage()
class SharedUiLocationMessage(
    override val sender: String,
    override val message: String,
    override val displayname: String?,
    override val message: String,
    override val id: String,
    override val timestamp: Long,
    override val reactions: Map<String, Set<String>>,
    val location: String,
    override val replied_event: String = "",
) : SharedUiMessage()


class MatrixChatRoom(private val msession: MatrixSession, val room_id: String, val name: String, val window_back_length: Int, message_window_base_in: String?, window_forward_length_in: Int) : MatrixState() {
    val username = msession.user

    val messages: List<SharedUiMessage>
    val message_window_base: String?
    val pinned: List<String>
    init {
        pinned = msession.getPinnedEvents(room_id)

        val edit_maps: MutableMap<String,ArrayList<SharedUiMessage>> = mutableMapOf()
        val reaction_maps: MutableMap<String, MutableMap<String, MutableSet<String>>> = mutableMapOf()

        val (event_range, tracking_live) = msession.getReleventRoomEventsForWindow(room_id, window_back_length, message_window_base_in, window_forward_length_in)
        if (tracking_live) {
            message_window_base = null
        } else {
            message_window_base = message_window_base_in
        }

        event_range.forEach {
            if (it as? RoomMessageEvent != null) {
                val msg_content = it.content
                if (msg_content is ReactionRMEC) {
                    val relates_to = msg_content!!.relates_to!!.event_id!!
                    val key = msg_content!!.relates_to!!.key!!
                    val reactions_for_msg = reaction_maps.getOrPut(relates_to, { mutableMapOf() })
                    reactions_for_msg.getOrPut(key, { mutableSetOf() }).add(it.sender)
                } else if (msg_content is TextRMEC) {
                    if (is_edit_content(msg_content)) {
                        // This is an edit
                        val replaced_id = msg_content!!.relates_to!!.event_id!!
                        val reactions = reaction_maps.get(replaced_id)?.entries?.map { (key, senders) -> Pair(key, senders?.toSet() ?: setOf())}?.toMap() ?: mapOf()
                        val edit_msg = SharedUiMessagePlain(it.sender, msg_content!!.new_content!!.body,
                            it.event_id, it.origin_server_ts, reactions)

                        if (edit_maps.contains(replaced_id)) {
                            edit_maps.get(replaced_id)!!.add(edit_msg)
                        } else {
                            edit_maps.put(replaced_id, arrayListOf(edit_msg))
                        }
                    }
                }
            }
        }
        val edits: Map<String,ArrayList<SharedUiMessage>> = edit_maps.toMap()
        messages = event_range.map {
            if (it as? RoomMessageEvent != null) {
                val reactions = reaction_maps.get(it.event_id)?.entries?.map { (key, senders) -> Pair(key, senders?.toSet() ?: setOf())}?.toMap() ?: mapOf()
                val msg_content = it.content
                var generate_media_msg = { url: String, func: (String,String,String,Long,Map<String,Set<String>>,String,String) -> SharedUiMessage ->
                    when (val url_local = msession.getLocalMediaPathFromUrl(url)) {
                        is Success -> {
                            func(
                                it.sender, it.content.body, it.event_id,
                                it.origin_server_ts, reactions, "", url_local.value
                            )
                        }
                        is Error -> {
                            SharedUiMessagePlain(
                                it.sender, "Failed to load media ${url}",
                                it.event_id, it.origin_server_ts, reactions, ""
                            )
                        }
                    }
                }
                when (msg_content) {
                    is TextRMEC -> {
                        val normal_msg_builder = {
                            SharedUiMessagePlain(it.sender, it.content.body, it.event_id, it.origin_server_ts, reactions, msg_content.relates_to?.in_reply_to?.event_id ?: "")
                        }
                        if((msg_content.new_content != null) && (msg_content.relates_to?.event_id == null)) {
                            //This is a poorly formed edit
                            //No idea which event this edit is editing, just display fallback msg
                            SharedUiMessagePlain(it.sender, it.content.body, it.event_id, it.origin_server_ts, reactions)
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
                                            reactions,
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
                    is VideoRMEC -> generate_media_msg(msg_content.url, ::SharedUiVideoMessage)
                    is FileRMEC -> {
                        SharedUiFileMessage(
                            it.sender, it.content.body, it.event_id,
                            it.origin_server_ts, reactions, msg_content.filename,
                            msg_content.info.mimetype, msg_content.url
                        )
                    }
                    is LocationRMEC -> {
                        SharedUiLocationMessage(
                            it.sender, it.content.body, it.event_id,
                            it.origin_server_ts, reactions, msg_content.geo_uri
                        )
                    }
                    is ReactionRMEC -> null
                    else -> SharedUiMessagePlain(it.sender, "UNHANDLED ROOM MESSAGE EVENT!!! ${it.content.body}", it.event_id, it.origin_server_ts, reactions)
                }
            } else if (it as? RoomEvent != null) {
                // This won't actually happen currently,
                // as all non RoomMessageEvents will be filtered out by the
                // isStandaloneEvent filter when pulling from the database.
                // In general, keeping the two up to date will require
                // some effort.
                // TODO: something else? Either always show, or always hide?
                println("unhandled room event $it")
                SharedUiMessagePlain(it.sender, "UNHANDLED ROOM EVENT!!! $it", it.event_id, it.origin_server_ts, mapOf())
            } else {
                println("IMPOSSIBLE unhandled non room event $it")
                throw Exception("IMPOSSIBLE unhandled non room event $it")
                SharedUiMessagePlain("impossible", "impossible", "impossible", 0, mapOf())
            }
        }.filterNotNull()
    }
    val window_forward_length: Int = if (message_window_base != null) { window_forward_length_in } else { 0 }
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
    fun sendReaction(msg: String, reacted: String): MatrixState {
        when (val sendMessageResult = msession.sendReaction(msg, room_id, reacted)) {
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
    fun togglePinnedEvent(event_id: String) {
        if(pinned.contains(event_id)) {
            sendUnpinnedEvent(event_id)
        } else {
            sendPinnedEvent(event_id)
        }
        return
    }
    fun sendPinnedEvent(event_id: String) {
        when (val pin_res = msession.sendPinnedEvent(event_id, room_id)) {
            is Success -> println("Message $event_id pinned")
            is Error -> println("Failed to pin $event_id because ${pin_res.cause}")
        }
    }
    fun sendUnpinnedEvent(event_id: String) {
        when (val pin_res = msession.sendUnpinnedEvent(event_id, room_id)) {
            is Success -> println("Message $event_id unpinned")
            is Error -> println("Failed to unpin $event_id because ${pin_res.cause}")
        }
    }
    fun getPinnedEventPreviews(): List<String> {
        return pinned.map {
            val event = msession.getRoomEvent(room_id, it)
            if(event as? RoomMessageEvent != null) {
                "${event.sender}: ${event.content.body}".take(80)
            } else {
                null
            }
        }.filterNotNull()
    }
    fun getEventSrc(msg_id: String): String {
        val event = msession.getRoomEvent(room_id, msg_id)
        if (event as? RoomMessageEvent != null) {
            if (event.event_id == msg_id) {
                return event.raw_self.toString()
                    .replace("\",", "\",\n")
                    .replace(",\"", ",\n\"")
                    .replace("{", "{\n")
                    .replace("}", "\n}")
                    .replace("\" \"", "\"\n\"")
            }
        }
        return "No Source for $msg_id"
    }
    fun saveMediaToPath(path: String, url: String) = msession.saveMediaAtPathFromUrl(path,url)
    fun sendReceipt(eventID: String) {
        when (val readReceiptResult = msession.sendReadReceipt(eventID, room_id)) {
            is Success -> println("read receipt sent")
            is Error -> println("read receipt failed because ${readReceiptResult.cause}")
        }
    }
    override fun refresh(): MatrixState = refresh(window_back_length, message_window_base, window_forward_length)
    fun refresh(new_window_back_length: Int, new_message_window_base: String?, new_window_forward_length: Int): MatrixState = MatrixChatRoom(
        msession,
        room_id,
        msession.getRoomName(room_id) ?: room_id,
        new_window_back_length,
        new_message_window_base,
        new_window_forward_length,
    )
    fun exitRoom(): MatrixState {
        return MatrixRooms(msession, "Back to rooms.")
    }
}
