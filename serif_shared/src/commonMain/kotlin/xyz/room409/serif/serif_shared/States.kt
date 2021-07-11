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
            //is Success -> { return MatrixRooms(msession = loginResult.value, message = "Logged in! Waiting on sync...") }
            is Success -> { return MatrixChatRoom(
                                        msession = loginResult.value,
                                        listOf("Room List"),
                                        20,
                                        null,
                                        0) }

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
            //is Success -> { return MatrixRooms(msession = loginResult.value, message = "Logged in! Maybe try syncing?") }
            is Success -> { return MatrixChatRoom(
                                        msession = loginResult.value,
                                        listOf("Room List"),
                                        20,
                                        null,
                                        0) }
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
abstract class SharedUiMessage() {
    abstract val sender: String
    abstract val message: String
    abstract val id: String
    abstract val timestamp: Long
    abstract val replied_event: SharedUiMessage?
    abstract val reactions: Map<String, Set<String>>
}

data class SharedUiRoom(
    override val sender: String = "<system>",
    override val message: String,
    override val id: String,
    override val timestamp: Long = 0,
    override val reactions: Map<String, Set<String>> = mapOf(),
    override val replied_event: SharedUiMessage? = null,

    val unreadCount: Int,
    val highlightCount: Int,
    val lastMessage: SharedUiMessage?
) : SharedUiMessage()

data class SharedUiMessagePlain(
    override val sender: String,
    override val message: String,
    override val id: String,
    override val timestamp: Long,
    override val reactions: Map<String, Set<String>>,
    override val replied_event: SharedUiMessage? = null
) : SharedUiMessage()
class SharedUiImgMessage(
    override val sender: String,
    override val message: String,
    override val id: String,
    override val timestamp: Long,
    override val reactions: Map<String, Set<String>>,
    override val replied_event: SharedUiMessage? = null,
    val url: String
) : SharedUiMessage()
class SharedUiAudioMessage(
    override val sender: String,
    override val message: String,
    override val id: String,
    override val timestamp: Long,
    override val reactions: Map<String, Set<String>>,
    override val replied_event: SharedUiMessage? = null,
    val url: String
) : SharedUiMessage()
class SharedUiVideoMessage(
    override val sender: String,
    override val message: String,
    override val id: String,
    override val timestamp: Long,
    override val reactions: Map<String, Set<String>>,
    override val replied_event: SharedUiMessage? = null,
    val url: String
) : SharedUiMessage()
class SharedUiFileMessage(
    override val sender: String,
    override val message: String,
    override val id: String,
    override val timestamp: Long,
    override val reactions: Map<String, Set<String>>,
    val filename: String,
    val mimetype: String,
    val url: String,
    override val replied_event: SharedUiMessage? = null,
) : SharedUiMessage()
class SharedUiLocationMessage(
    override val sender: String,
    override val message: String,
    override val id: String,
    override val timestamp: Long,
    override val reactions: Map<String, Set<String>>,
    val location: String,
    override val replied_event: SharedUiMessage? = null,
) : SharedUiMessage()

fun toSharedUiMessageList(msession: MatrixSession, username: String, room_id: String, window_back_length: Int, message_window_base: String?, window_forward_length: Int, force_event: Boolean): Pair<List<SharedUiMessage>, Boolean> {
    val edit_maps: MutableMap<String,ArrayList<SharedUiMessage>> = mutableMapOf()
    val reaction_maps: MutableMap<String, MutableMap<String, MutableSet<String>>> = mutableMapOf()

    val (event_range, tracking_live) = msession.getReleventRoomEventsForWindow(room_id, window_back_length, message_window_base, window_forward_length, force_event)

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
    val messages = event_range.map {
        if (it as? RoomMessageEvent != null) {
            val reactions = reaction_maps.get(it.event_id)?.entries?.map { (key, senders) -> Pair(key, senders?.toSet() ?: setOf())}?.toMap() ?: mapOf()
            val msg_content = it.content

            val in_reply_to = when(msg_content) {
                is TextRMEC -> msg_content.relates_to
                is AudioRMEC -> msg_content.relates_to
                is ImageRMEC -> msg_content.relates_to
                is FileRMEC -> msg_content.relates_to
                is LocationRMEC -> msg_content.relates_to
                is VideoRMEC -> msg_content.relates_to
                else -> null
            }?.in_reply_to?.event_id?.let { in_reply_to_id ->
                toSharedUiMessageList(msession, username, room_id, 0, in_reply_to_id, 0, true).first.firstOrNull()
            }

            var generate_media_msg = { url: String, func: (String,String,String,Long,Map<String,Set<String>>,SharedUiMessage?,String) -> SharedUiMessage ->
                when (val url_local = msession.getLocalMediaPathFromUrl(url)) {
                    is Success -> {
                        func(
                            it.sender, it.content.body, it.event_id,
                            it.origin_server_ts, reactions, in_reply_to, url_local.value
                        )
                    }
                    is Error -> {
                        SharedUiMessagePlain(
                            it.sender, "Failed to load media ${url}",
                            it.event_id, it.origin_server_ts, reactions, in_reply_to
                        )
                    }
                }
            }
            when (msg_content) {
                is TextRMEC -> {
                    val transform_body = { body_message: String ->
                        if (in_reply_to != null) {
                            var stripping = true
                            body_message.lines().filter {
                                if (stripping && it.startsWith("> ")) {
                                    false
                                } else {
                                    stripping = false
                                    true
                                }
                            }.joinToString("\n")
                        } else { body_message }
                    }
                    val normal_msg_builder = {
                        SharedUiMessagePlain(it.sender, transform_body(it.content.body), it.event_id, it.origin_server_ts, reactions, in_reply_to)
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
                                        transform_body("${edited.message} (edited)"),
                                        edited.id,
                                        it.origin_server_ts,
                                        reactions,
                                        in_reply_to)
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
        } else if (it.castToStateEventWithContentOfType<SpaceChildContent>() != null) {
            val event = it as StateEvent<SpaceChildContent>
            val summary = msession.getRoomSummary(event.state_key)
            SharedUiRoom(
                id=event.state_key,
                message=summary?.first ?: event.state_key,
                unreadCount=summary?.second?.first ?: 0,
                highlightCount=summary?.second?.first ?: 0,
                lastMessage=summary?.third?.let { toSharedUiMessageList(msession, username, event.state_key, 0, it, 0, true).first.firstOrNull() }
            )
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
    return Pair(messages, tracking_live)
}

class MatrixChatRoom(private val msession: MatrixSession, val room_ids: List<String>, val window_back_length: Int, message_window_base_in: String?, window_forward_length_in: Int) : MatrixState() {
    val room_id = room_ids.last()!!
    val name =  msession.getRoomSummary(room_id)?.first ?: room_id
    val username = msession.user

    val messages: List<SharedUiMessage>
    val message_window_base: String?
    val pinned: List<String>
    init {
        messages = if (room_id == "Room List" || room_id == "All Rooms" || msession.getRoomType(room_id) == "m.space") {
            pinned = listOf()
            message_window_base = null
            val (spaces, rooms) = msession.mapRooms { id, name, unread_notif, unread_highlight, last_event_id ->
                Pair(id, SharedUiRoom(
                    id=id,
                    message=name,
                    unreadCount=unread_notif,
                    highlightCount=unread_highlight,
                    lastMessage=last_event_id?.let { toSharedUiMessageList(msession, username, id, 0, it, 0, false).first.firstOrNull() }
                ))
            }.sortedBy { -(it.second.lastMessage?.timestamp ?: 0) }.partition { msession.getRoomType(it.second.id) == "m.space" }
            if (room_id == "All Rooms") {
                rooms.map { it.second }
            } else {
                val spaces = spaces.toMap()
                val childrenSet: MutableSet<String> = mutableSetOf()
                val spaceChildren: MutableMap<String, List<String>> = mutableMapOf()
                val liveMap = rooms.toMap().toMutableMap()
                fun resolveSpace(id: String) {
                    if (liveMap.containsKey(id)) {
                        // already done or circular space, break the link
                        return
                    }
                    val children = msession.getSpaceChildren(id)
                    spaceChildren.put(id, children)
                    for (child in children) {
                        if (spaces.containsKey(child)) {
                            childrenSet.add(child)
                            resolveSpace(child)
                        }
                    }
                    var total_unread = 0
                    var total_highlight = 0
                    val latest = children.map { liveMap[it] }.filterNotNull().minByOrNull {
                        total_unread += it.unreadCount
                        total_highlight += it.highlightCount
                        -(it.lastMessage?.timestamp ?: 0)
                    }
                    liveMap[id] = SharedUiRoom(
                        id=id,
                        message=spaces[id]!!.message,
                        unreadCount=total_unread,
                        highlightCount=total_highlight,
                        lastMessage=latest
                    )
                }
                for (space_id in spaces.keys) {
                    resolveSpace(space_id)
                }
                if (room_id == "Room List") {
                    listOf(SharedUiRoom(
                        id="All Rooms",
                        message="All Rooms",
                        unreadCount=0,
                        highlightCount=0,
                        lastMessage=null
                    )) + spaces.keys.filter { id -> !childrenSet.contains(id) }.map { liveMap[it]!! }.sortedBy { -(it.lastMessage?.timestamp ?: 0) }
                } else {
                    // it's a space! Grab this one from the liveMap and sort it
                    // Note it can be null, we're not necessarily in every room in the space
                    spaceChildren[room_id]!!.map { liveMap[it] }.filterNotNull().sortedBy { -(it.lastMessage?.timestamp ?: 0) }
                }
            }
        } else {
            pinned = msession.getPinnedEvents(room_id)
            val (got_messages, tracking_live) = toSharedUiMessageList(msession, username, room_id, window_back_length, message_window_base_in, window_forward_length_in, false)
            if (tracking_live) {
                message_window_base = null
            } else {
                message_window_base = message_window_base_in
            }
            got_messages
        }
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
        room_ids,
        new_window_back_length,
        new_message_window_base,
        new_window_forward_length,
    )
    fun exitRoom(): MatrixState {
        if (room_ids.size > 1) {
            return MatrixChatRoom(
                msession,
                room_ids.dropLast(1),
                window_back_length,
                message_window_base,
                window_forward_length,
            )
        } else {
            msession.closeSession()
            return MatrixLogin("Closing session, returning to the login prompt for now\n", MatrixClient())
        }
    }
    fun getRoom(new_id: String): MatrixState {
        return MatrixChatRoom(
            msession,
            room_ids.plusElement(new_id),
            window_back_length,
            message_window_base,
            window_forward_length
        )
    }
}
