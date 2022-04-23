package xyz.room409.serif.serif_shared
import kotlin.collections.*
import kotlin.math.*

sealed class MatrixState {
    val version: String
        get() {
            return "Serif Matrix client, pre-alpha on ${Platform.platform}"
        }
    abstract fun refresh(): MatrixState
}
class MatrixLogin(val login_message: String, val mclient: MatrixClient) : MatrixState() {
    constructor() : this(
        login_message = "Please enter your username and password\n",
        mclient = MatrixClient()
    )
    fun removeRoomCache() {
        synchronized(MatrixChatRoomCache) {
            MatrixChatRoomCache.spaces = null
            MatrixChatRoomCache.rooms = null
            MatrixChatRoomCache.childrenSet = null
            MatrixChatRoomCache.spaceChildren = null
            MatrixChatRoomCache.liveMap = null
        }
    }
    // No need to refresh
    override fun refresh(): MatrixState = this
    fun login(username: String, password: String, onSync: () -> Unit): MatrixState {
        when (val loginResult = mclient.login(username, password, { removeRoomCache(); onSync() })) {
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
        when (val loginResult = mclient.loginFromSavedSession(username, { removeRoomCache(); onSync() })) {
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
    abstract val displayname: String?
    abstract val avatar_file_path: String?
    abstract val message: String
    abstract val formatted_message: String?
    abstract val id: String
    abstract val timestamp: Long
    abstract val replied_event: SharedUiMessage?
    abstract val reactions: Map<String, Set<RoomMessageEvent>>
}

data class SharedUiRoom(
    override val sender: String = "<system>",
    override val displayname: String = "<system>",
    override val avatar_file_path: String? = null,
    override val message: String,
    override val formatted_message: String? = null,
    override val id: String,
    override val timestamp: Long = 0,
    override val reactions: Map<String, Set<RoomMessageEvent>> = mapOf(),
    override val replied_event: SharedUiMessage? = null,

    val unreadCount: Int,
    val highlightCount: Int,
    val lastMessage: SharedUiMessage?,
    val typing: List<String>
) : SharedUiMessage()

data class SharedUiMessagePlain(
    override val sender: String,
    override val displayname: String? = null,
    override val avatar_file_path: String? = null,
    override val message: String,
    override val formatted_message: String? = null,
    override val id: String,
    override val timestamp: Long,
    override val reactions: Map<String, Set<RoomMessageEvent>>,
    override val replied_event: SharedUiMessage? = null
) : SharedUiMessage()
class SharedUiImgMessage(
    override val sender: String,
    override val displayname: String? = null,
    override val avatar_file_path: String? = null,
    override val message: String,
    override val id: String,
    override val timestamp: Long,
    override val reactions: Map<String, Set<RoomMessageEvent>>,
    override val replied_event: SharedUiMessage? = null,
    val url: String,
    override val formatted_message: String? = null
) : SharedUiMessage()
class SharedUiAudioMessage(
    override val sender: String,
    override val displayname: String? = null,
    override val avatar_file_path: String? = null,
    override val message: String,
    override val id: String,
    override val timestamp: Long,
    override val reactions: Map<String, Set<RoomMessageEvent>>,
    override val replied_event: SharedUiMessage? = null,
    val url: String,
    override val formatted_message: String? = null
) : SharedUiMessage()
class SharedUiVideoMessage(
    override val sender: String,
    override val displayname: String? = null,
    override val avatar_file_path: String? = null,
    override val message: String,
    override val id: String,
    override val timestamp: Long,
    override val reactions: Map<String, Set<RoomMessageEvent>>,
    override val replied_event: SharedUiMessage? = null,
    val url: String,
    override val formatted_message: String? = null
) : SharedUiMessage()
class SharedUiFileMessage(
    override val sender: String,
    override val displayname: String? = null,
    override val avatar_file_path: String? = null,
    override val message: String,
    override val id: String,
    override val timestamp: Long,
    override val reactions: Map<String, Set<RoomMessageEvent>>,
    val filename: String,
    val mimetype: String,
    val url: String,
    override val replied_event: SharedUiMessage? = null,
    override val formatted_message: String? = null
) : SharedUiMessage()
class SharedUiLocationMessage(
    override val sender: String,
    override val displayname: String? = null,
    override val avatar_file_path: String? = null,
    override val message: String,
    override val id: String,
    override val timestamp: Long,
    override val reactions: Map<String, Set<RoomMessageEvent>>,
    val location: String,
    override val replied_event: SharedUiMessage? = null,
    override val formatted_message: String? = null
) : SharedUiMessage()

fun toSharedUiMessageList(msession: MatrixSession, username: String, room_id: String, window_back_length: Int, message_window_base: String?, window_forward_length: Int, force_event: Boolean): Pair<List<SharedUiMessage>, Boolean> {
    val edit_maps: MutableMap<String,ArrayList<SharedUiMessage>> = mutableMapOf()
    val reaction_maps: MutableMap<String, MutableMap<String, MutableSet<RoomMessageEvent>>> = mutableMapOf()

    val (event_range, tracking_live) = msession.getReleventRoomEventsForWindow(room_id, window_back_length, message_window_base, window_forward_length, force_event)

    event_range.forEach {
        if (it as? RoomMessageEvent != null) {
            val (displayname, avatar_file_path) = msession.getDiplayNameAndAvatarFilePath(it.sender, room_id)
            val msg_content = it.content
            if (msg_content is ReactionRMEC) {
                val relates_to = msg_content.relates_to.event_id!!
                val key = msg_content.relates_to.key!!
                val reactions_for_msg = reaction_maps.getOrPut(relates_to, { mutableMapOf() })
                reactions_for_msg.getOrPut(key, { mutableSetOf() }).add(it)
            } else if (msg_content is TextRMEC) {
                if (is_edit_content(msg_content)) {
                    // This is an edit
                    val replaced_id = msg_content.relates_to!!.event_id!!
                    val reactions = reaction_maps.get(replaced_id)?.entries?.map { (key, senders) -> Pair(key, senders?.toSet() ?: setOf())}?.toMap() ?: mapOf()
                    val edit_msg = SharedUiMessagePlain(it.sender, displayname, avatar_file_path,
                    msg_content.new_content!!.body, msg_content.new_content!!.formatted_body,
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
            val (displayname, avatar_file_path) = msession.getDiplayNameAndAvatarFilePath(it.sender, room_id)

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

            var generate_media_msg = { url: String, func: (String, String?, String?, String,String,Long,Map<String,Set<RoomMessageEvent>>,SharedUiMessage?,String) -> SharedUiMessage ->
                when (val url_local = msession.getLocalMediaPathFromUrl(url)) {
                    is Success -> {
                        func(
                            it.sender, displayname, avatar_file_path, it.content.body, it.event_id,
                            it.origin_server_ts, reactions, in_reply_to, url_local.value
                        )
                    }
                    is Error -> {
                        SharedUiMessagePlain(
                            it.sender, null, null, "Failed to load media ${url}", null,
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
                        SharedUiMessagePlain(it.sender, displayname, avatar_file_path, transform_body(it.content.body), msg_content.formatted_body, it.event_id, it.origin_server_ts, reactions, in_reply_to)
                    }
                    if((msg_content.new_content != null) && (msg_content.relates_to?.event_id == null)) {
                        //This is a poorly formed edit
                        //No idea which event this edit is editing, just display fallback msg
                        normal_msg_builder()
                    } else {
                        if(is_edit_content(msg_content)) {
                            //Don't display edits
                            null
                        } else {
                            //This is a text message, check for any edits of this message
                            if(edits.contains(it.event_id)) {
                                val possible_edits = edits.get(it.event_id)!!
                                val edited = possible_edits.lastOrNull { possible_edit -> possible_edit.sender == it.sender }
                                if(edited != null) {
                                    SharedUiMessagePlain(
                                        it.sender, 
                                        displayname, 
                                        avatar_file_path,
                                        transform_body("${edited.message} (edited)"),
                                        edited.formatted_message,
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
                        it.sender, displayname, avatar_file_path, it.content.body, it.event_id,
                        it.origin_server_ts, reactions, msg_content.filename,
                        msg_content.info.mimetype, msg_content.url
                    )
                }
                is LocationRMEC -> {
                    SharedUiLocationMessage(
                        it.sender, displayname, avatar_file_path, it.content.body, it.event_id,
                        it.origin_server_ts, reactions, msg_content.geo_uri
                    )
                }
                is ReactionRMEC -> null
                is RedactionRMEC -> {
                    SharedUiMessagePlain(it.sender, null, null, "A deletion was processed here", null,it.event_id, it.origin_server_ts, reactions)
                }
                else ->
                    if(it.unsigned?.redacted_because!=null) {
                        val details: RoomMessageEvent = it.unsigned.redacted_because as RoomMessageEvent
                        // Might be a FallbackRMEC if no reason is given
                        val reason = (details.content as? RedactionRMEC)?.reason ?: "No reason given"
                        SharedUiMessagePlain(it.sender, null, null, "Deleted by ${details.sender} because $reason", null, it.event_id, it.origin_server_ts, reactions)
                    }
                    else
                        SharedUiMessagePlain(it.sender, null, null, "UNHANDLED ROOM MESSAGE EVENT!!!  ${it.content.body}", null, it.event_id, it.origin_server_ts, reactions)
            }
        } else if (it.castToStateEventWithContentOfType<SpaceChildContent>() != null) {
            val event = it as StateEvent<SpaceChildContent>
            val summary = msession.getRoomSummary(event.state_key)
            SharedUiRoom(
                id=event.state_key,
                message=summary?.first ?: event.state_key,
                unreadCount=summary?.second?.first ?: 0,
                highlightCount=summary?.second?.first ?: 0,
                lastMessage=summary?.third?.let { toSharedUiMessageList(msession, username, event.state_key, 0, it, 0, true).first.firstOrNull() },
                typing=listOf()
            )
        } else if (it as? RoomEvent != null) {
            // This won't actually happen currently,
            // as all non RoomMessageEvents will be filtered out by the
            // isStandaloneEvent filter when pulling from the database.
            // In general, keeping the two up to date will require
            // some effort.
            // TODO: something else? Either always show, or always hide?
            println("unhandled room event $it")
            SharedUiMessagePlain(it.sender, null, null, "UNHANDLED ROOM EVENT!!! $it", null, it.event_id, it.origin_server_ts, mapOf())
        } else {
            println("IMPOSSIBLE unhandled non room event $it")
            throw Exception("IMPOSSIBLE unhandled non room event $it")
            SharedUiMessagePlain("impossible", null, null, "impossible", null, "impossible", 0, mapOf())
        }
    }.filterNotNull()
    return Pair(messages, tracking_live)
}

// This is a dumb cache, figure out our
// actual caching strategy (esp supporting
// multiple windows, etc)
object MatrixChatRoomCache {
    var spaces: Map<String,SharedUiRoom>? = null
    var rooms: List<Pair<String,SharedUiRoom>>? = null
    var childrenSet: Set<String>? = null
    var spaceChildren: Map<String, List<String>>? = null
    var liveMap: Map<String,SharedUiRoom>? = null
}
class MatrixChatRoom(private val msession: MatrixSession, val room_ids: List<String>, val window_back_length: Int, message_window_base_in: String?, window_forward_length_in: Int) : MatrixState() {
    val room_id = room_ids.last()!!
    val room_type = when (room_id) {
        "Room List" -> "m.space"
        "All Rooms" -> "m.space"
        else        -> msession.getRoomType(room_id)
    }
    val name =  msession.getRoomSummary(room_id)?.first ?: room_id
    val username = msession.user

    val messages: List<SharedUiMessage>
    val message_window_base: String?
    val pinned: List<String>
    val members: List<String>
    val avatar: String
    val roomName: String = msession.getRoomName(room_id)
    val roomTopic: String = msession.getRoomTopic(room_id)
    val encrypted: Boolean = msession.getRoomEncrypted(room_id)
    val typing = if(room_type == "m.space") { listOf() } else { normalizeTypingList(msession.getTypingStatusForRoom(room_id)) }
    val link_regex = Regex("<a href=\"https://matrix.to/#/[^:]*:[^>]*\">(.*)</a>")
    init {
        messages = if (room_id == "Room List" || room_id == "All Rooms" || msession.getRoomType(room_id) == "m.space") {
            members = listOf()
            pinned = listOf()
            message_window_base = null
            // quick and dirrrty
            var spaces: Map<String,SharedUiRoom>? = null
            var rooms: List<Pair<String,SharedUiRoom>>? = null
            var childrenSet: Set<String>? = null
            var spaceChildren: Map<String, List<String>>? = null
            var liveMap: Map<String,SharedUiRoom>? = null
            synchronized(MatrixChatRoomCache) {
                spaces = MatrixChatRoomCache.spaces
                rooms = MatrixChatRoomCache.rooms
                childrenSet = MatrixChatRoomCache.childrenSet
                spaceChildren = MatrixChatRoomCache.spaceChildren
                liveMap = MatrixChatRoomCache.liveMap
            }
            if (spaces == null) {
                val (__spaces, _rooms) = msession.mapRooms { id, name, unread_notif, unread_highlight, last_event_id ->
                    val typing = normalizeTypingList(msession.getTypingStatusForRoom(id))
                    Pair(id, SharedUiRoom(
                        id=id,
                        message=name,
                        unreadCount=unread_notif,
                        highlightCount=unread_highlight,
                        lastMessage=last_event_id?.let { toSharedUiMessageList(msession, username, id, 0, it, 0, false).first.firstOrNull() },
                        typing=typing
                    ))
                }.sortedBy { -(it.second.lastMessage?.timestamp ?: 0) }.partition { msession.getRoomType(it.second.id) == "m.space" }
                val _spaces = __spaces.toMap()
                val _childrenSet: MutableSet<String> = mutableSetOf()
                val _spaceChildren: MutableMap<String, List<String>> = mutableMapOf()
                val _liveMap = _rooms.toMap().toMutableMap()
                fun resolveSpace(id: String) {
                    if (_liveMap.containsKey(id)) {
                        // already done or circular space, break the link
                        return
                    }
                    val children = msession.getSpaceChildren(id)
                    _spaceChildren.put(id, children)
                    for (child in children) {
                        if (_spaces.containsKey(child)) {
                            _childrenSet.add(child)
                            resolveSpace(child)
                        }
                    }
                    var total_unread = 0
                    var total_highlight = 0
                    val latest = children.map { _liveMap[it] }.filterNotNull().minByOrNull {
                        total_unread += it.unreadCount
                        total_highlight += it.highlightCount
                        -(it.lastMessage?.timestamp ?: 0)
                    }
                    _liveMap[id] = SharedUiRoom(
                        id=id,
                        message=_spaces[id]!!.message,
                        unreadCount=total_unread,
                        highlightCount=total_highlight,
                        lastMessage=latest,
                        typing=listOf()
                    )
                }
                for (space_id in _spaces.keys) {
                    resolveSpace(space_id)
                }
                spaces = _spaces
                rooms = _rooms
                childrenSet = _childrenSet
                spaceChildren = _spaceChildren
                liveMap = _liveMap
                synchronized(MatrixChatRoomCache) {
                    MatrixChatRoomCache.spaces = spaces
                    MatrixChatRoomCache.rooms = rooms
                    MatrixChatRoomCache.childrenSet = childrenSet
                    MatrixChatRoomCache.spaceChildren = spaceChildren
                    MatrixChatRoomCache.liveMap = liveMap
                }
            }
            if (room_id == "All Rooms") {
                rooms!!.map { it.second }
            } else if (room_id == "Room List") {
                listOf(SharedUiRoom(
                    id="All Rooms",
                    message="All Rooms",
                    unreadCount=0,
                    highlightCount=0,
                    lastMessage=null,
                    typing=listOf()
                )) + spaces!!.keys.filter { id -> !childrenSet!!.contains(id) }.map { liveMap!![it]!! }.sortedBy { -(it.lastMessage?.timestamp ?: 0) }
            } else {
                // it's a space! Grab this one from the liveMap and sort it
                // Note it can be null, we're not necessarily in every room in the space
                spaceChildren!![room_id]!!.map { liveMap!![it] }.filterNotNull().sortedBy { -(it.lastMessage?.timestamp ?: 0) }
            }
        } else {
            members = msession.getRoomMembers(room_id)
            pinned = msession.getPinnedEvents(room_id)
            val (got_messages, tracking_live) = toSharedUiMessageList(msession, username, room_id, window_back_length, message_window_base_in, window_forward_length_in, false)
            if (tracking_live) {
                message_window_base = null
            } else {
                message_window_base = message_window_base_in
            }
            got_messages
        }
        val avatar_mxc_url = msession.getRoomAvatar(room_id)
        avatar = if(avatar_mxc_url != "") {
            when (val url_local = msession.getLocalMediaPathFromUrl(avatar_mxc_url)) {
                is Success -> { url_local.value }
                is Error -> { "" }
            }
        } else { "" }
    }
    private fun normalizeTypingList(typing: List<String>): List<String> {
        return typing.map { user ->
            val d = getDisplayNameForUser(user)
            if(d == "") { user } else { d }
        }
    }
    fun getDisplayNameForUser(sender: String) : String {
        val (displayname, _) = msession.getDiplayNameAndAvatarFilePath(sender, room_id)
        return displayname ?: ""
    }
    fun getAvatarFilePathForUser(sender: String) : String {
        val (_, afp) = msession.getDiplayNameAndAvatarFilePath(sender, room_id)
        return afp ?: ""
    }
    fun getUnformattedBody(formatted_body: String) : String {
        return formatted_body.replace(link_regex,"$1")
    }
    val window_forward_length: Int = if (message_window_base != null) { window_forward_length_in } else { 0 }
    fun sendMessage(msg: String): MatrixState {
        var body = getUnformattedBody(msg)
        when (val sendMessageResult = msession.sendMessage(body, room_id, "", msg)) {
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
    fun sendReply(msg: String, in_reply_to_id: String): MatrixState {
        val in_reply_to = toSharedUiMessageList(msession, username, room_id, 0, in_reply_to_id, 0, true).first.firstOrNull()
        var formatted_body = msg
        val body = getUnformattedBody(msg)
        val message = (in_reply_to?.let { event ->
            val server = msession.server
            val prev_sender = event.sender
            val prev_formatted_content = event.formatted_message
            val prev_content = if(prev_formatted_content != null) { prev_formatted_content.split("</mx-reply>")[1] } else { event.message }
            formatted_body = "<mx-reply><blockquote><a href=\"https://matrix.to/#/$room_id:$server/$in_reply_to_id?via=$server\">In reply to</a> <a href=\"https://matrix.to/#/$prev_sender\">$prev_sender</a><br />$prev_content</blockquote></mx-reply>$msg"
             event.message.lines().mapIndexed { i,line -> if (i == 0) { "> <${event.sender}> $line" } else { "> $line" } }.joinToString("\n")
        } ?: "> in reply to $in_reply_to_id") + "\n$body"
        when (val sendMessageResult = msession.sendMessage(message, room_id, in_reply_to_id, formatted_body)) {
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
    fun getPresenceForUser(user_id: String): PresenceState {
        when(val res = msession.getPresenceStatus(user_id)) {
            is Success -> return res.value.presence
            is Error -> {
                println("Failed to get Presence for $user_id because ${res.cause}")
                return PresenceState.unavailable
            }
        }
    }
    fun setPresenceStatus(presence: PresenceState, msg: String = "") {
        println("Setting presence to $presence")
        msession.sendPresenceStatus(presence, msg)
    }
    fun sendTypingStatus(typing: Boolean) {
        when (val res = msession.sendTypingStatus(room_id, typing)) {
            is Success -> println("Server Notified of typing status")
            is Error -> println("Failed send typing status because ${res.cause}")
        }
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
    fun sendRedaction(eventID:String): MatrixState {
        when (val sendMessageResult = msession.sendRedactEvent(room_id,eventID)) {
            is Success -> { println("${sendMessageResult.value}") }
            is Error -> { println("${sendMessageResult.message} - exception was ${sendMessageResult.cause}") }
        }
        return this
    }
    fun createRoom(name: String, room_alias_name: String, topic: String) = msession.createRoom(name, room_alias_name, topic)

    fun setRoomTopic(topic: String) {
        msession.setRoomTopic(room_id,topic)
    }
    fun setRoomName(name: String) {
        msession.setRoomName(room_id,name)
    }
    fun setRoomAvatar(local_path: String) {
        msession.setRoomAvatar(room_id,local_path)
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
