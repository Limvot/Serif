package xyz.room409.serif.serif_shared
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlin.concurrent.thread
import kotlin.synchronized
import java.io.File

sealed class Outcome<out T : Any>
data class Success<out T : Any>(val value: T) : Outcome<T>()
data class Error(val message: String, val cause: Exception? = null) : Outcome<Nothing>()

fun idBetween(a: String, b: String, increment: Boolean): String {
    if (a.length < b.length) {
        return idBetween(a + "a".repeat(b.length - a.length), b, increment)
    } else if (a.length > b.length) {
        return idBetween(a, b + "a".repeat(a.length - b.length), increment)
    } else if (increment) {
        // lets try to increment a
        var carry = true
        val a_incr = String(a.reversed().map { c ->
            if (carry) {
                if (c < 'z') {
                    carry = false
                    c + 1
                } else {
                    'b'
                }
            } else {
                c
            }
        }.reversed().toCharArray())
        // If this increment didn't work, we've got to extend the space
        if (a_incr >= b) {
            return idBetween(a + "a".repeat(a.length), b + "a".repeat(b.length), increment)
        } else {
            return a_incr
        }
    } else {
        // lets try to decrement b
        var carry = true
        var not_first = false
        val b_decr = String(b.reversed().map { c ->
            if (carry) {
                if (c > 'b' || (not_first && c > 'a')) {
                    carry = false
                    not_first = true
                    c - 1
                } else {
                    not_first = true
                    'z'
                }
            } else {
                c
            }
        }.reversed().toCharArray())
        // If this decrement didn't work, we've got to extend the space
        if (b_decr > b || b_decr <= a) {
            return idBetween(a + "a".repeat(a.length), b + "a".repeat(b.length), increment)
        } else {
            return b_decr
        }
    }
}

fun is_edit_content(msg_content: TextRMEC): Boolean = ((msg_content.new_content != null) && (msg_content.relates_to?.event_id != null))
fun isStandaloneEvent(e: Event): Boolean {
    if (e as? RoomMessageEvent != null) {
        return !(e.content is ReactionRMEC || (e.content is TextRMEC && is_edit_content(e.content)))
    } else {
        return e.castToStateEventWithContentOfType<SpaceChildContent>() != null
    }
}
fun getRelatedEvent(e: Event): String? {
    if (e as? RoomMessageEvent != null) {
        if (e.content is ReactionRMEC) {
            return e.content.relates_to.event_id!!
        } else if (e.content is TextRMEC && is_edit_content(e.content)) {
            return e.content.relates_to!!.event_id!!
        } else if (e.content is RedactionRMEC)
            return e.redacts!!
    }
    return null
}

// This can't just be a call to something like firstofType<StateEvent<T>> because the safe cast operator only works one level, and you'd
// still get ClassCastExceptions when it returns an Event that is indeed of type StateEvent<?> but not instantiated with
// the right T. Java generics are bad, yall, and Kotlin in trying to be nice sometimes makes things much worse.
inline fun <reified T> Event.castToStateEventWithContentOfType(): T? = ((this as? StateEvent<T>?)?.content) as? T?

class MatrixSession(val client: HttpClient, val server: String, val user: String, val session_id: Long, val access_token: String, var transactionId: Long, val onUpdate: () -> Unit) {
    private var in_flight_backfill_requests: MutableSet<Triple<String,String,String>> = mutableSetOf()
    private var in_flight_media_requests: MutableSet<String> = mutableSetOf()
    private var in_flight_user_requests: MutableSet<String> = mutableSetOf()
    private var sync_should_run = true
    private var sync_thread: Thread? = thread(start = true) {
        // 30 seconds is Matrix recommended, afaik
        // was mentioned in matrix-dev a bit ago
        val timeout_ms: Long = 30000
        while (sync_should_run) {
            val next_batch = Database.getSessionNextBatch(session_id)
            val url = if (next_batch != null) {
                "$server/_matrix/client/r0/sync?since=$next_batch&timeout=$timeout_ms&access_token=$access_token"
            } else {
                val limit = 5
                "$server/_matrix/client/r0/sync?filter={\"room\":{\"timeline\":{\"limit\":$limit}}}&access_token=$access_token"
            }
            try {
                mergeInSync(runBlocking { client.get<SyncResponse>(url) })
                onUpdate()
            } catch (e: Exception) {
                val backoff_ms = timeout_ms
                e.printStackTrace()
                println("This sync failed with an exception $e, waiting ${backoff_ms / 1000} seconds before trying again")
                Thread.sleep(backoff_ms)
            }
        }
    }

    fun <T> mapRooms(f: (String,String,Int,Int,String?) -> T): List<T> = Database.mapRooms(session_id, f)
    fun getReleventRoomEventsForWindow(room_id: String, window_back_length: Int, message_window_base: String?, window_forward_length: Int, force_event: Boolean): Pair<List<Event>, Boolean> {
        // if message_window_base is null, than no matter what
        // we are following live.
        val overrideCurrent = message_window_base == null
        val base_and_seqId_and_prevBatch = message_window_base?.let { Database.getRoomEventAndIdx(session_id, room_id, it) } ?: if (!force_event) { Database.getMostRecentRoomEventAndIdx(session_id, room_id) } else { null }
        // completely empty room!!
        if (base_and_seqId_and_prevBatch == null) {
            return Pair(listOf(), true)
        }
        if (base_and_seqId_and_prevBatch.third != null) {
            requestBackfill(room_id, base_and_seqId_and_prevBatch.first.event_id, base_and_seqId_and_prevBatch.third!!)
        }
        var base_seqId = base_and_seqId_and_prevBatch.second

        // For both forward and backward events, we keep
        // getting events in that direction from the database
        // and filtering them on isStandaloneEvent until
        // we have enough in each direction, then we
        // pull all the events related to those standaloneEvents
        // out of the database and sort them by seqId.
        // In this way, we get the exact number of displayable
        // events that we need with the exact relevent related
        // events all in the correct order.

        // If our base event isn't a standaloneEvent, we need
        // an extra standaloneEvent to make our quota
        // (the UI expects exactly window_back_length+1+window_forward_length
        // displayable events)
        val window_back_length = window_back_length + if (isStandaloneEvent(base_and_seqId_and_prevBatch.first)) { 0 } else { 1 }
        var back_base_seqId = base_and_seqId_and_prevBatch.second
        var back_events: List<Triple<RoomEvent,String,String?>> = listOf()
        while (back_events.size < window_back_length) {
            val new_events = Database.getRoomEventsBackwardsFromPoint(session_id, room_id, back_base_seqId, window_back_length.toLong() - back_events.size)
            back_events = new_events.filter { if (it.third != null) { requestBackfill(room_id, it.first.event_id, it.third!!); }; isStandaloneEvent(it.first) } + back_events
            if (new_events.size == 0) {
                break;
            }
            back_base_seqId = new_events.first().second
        }

        var fore_base_seqId = base_and_seqId_and_prevBatch.second
        var fore_events: List<Triple<RoomEvent,String,String?>> = listOf()
        while (fore_events.size < window_forward_length) {
            val new_events = Database.getRoomEventsForwardsFromPoint(session_id, room_id, fore_base_seqId, window_forward_length.toLong() - fore_events.size)
            fore_events = fore_events + new_events.filter { if (it.third != null) { requestBackfill(room_id, it.first.event_id, it.third!!); }; isStandaloneEvent(it.first) }
            if (new_events.size == 0) {
                break
            }
            fore_base_seqId = new_events.last().second
        }

        val prelim_total_events = back_events + listOf(base_and_seqId_and_prevBatch).filter { isStandaloneEvent(it.first) } + fore_events
        val relatedEvents = Database.getRelatedEvents(session_id, room_id, prelim_total_events.map { it.first.event_id })
        val total_events = (prelim_total_events.map { Pair(it.first, it.second) } + relatedEvents).sortedBy { it.second } .map { it.first }
        // in addition to overrrideCurrent, the other condition for following live
        // is that we have less events forward than requested, so our window overlaps
        // live.
        return Pair(total_events, overrideCurrent || fore_events.size < window_forward_length)
    }
    fun getRoomEvent(room_id: String, event_id: String): Event? = Database.getRoomEventAndIdx(session_id, room_id, event_id)?.first
    fun getRoomSummary(id: String) = Database.getRoomSummary(session_id, id)
    fun createRoom(name:String, room_alias_name: String, topic: String): Outcome<String> {
        try {
            val result = runBlocking {
                val creation_confirmation =
                client.post<String>("$server/_matrix/client/r0/createRoom?access_token=$access_token") {
                    contentType(ContentType.Application.Json)
                    body = CreateRoom(name, room_alias_name, topic)
                }
                Database.updateSessionTransactionId(session_id, transactionId)
                creation_confirmation
            }
            return Success("Our create create room event id is: $result")
        } catch (e: Exception){
            return Error("Create Room Failed", e)
        }
    }


    fun sendMessageImpl(message_content: RoomMessageEventContent, room_id: String): Outcome<String> {

        try {
            val event_type = if (message_content is ReactionRMEC) {
                "m.reaction"
            } else { "m.room.message" }
            val result = runBlocking {
                val message_confirmation =
                    client.put<EventIdResponse>("$server/_matrix/client/r0/rooms/$room_id/send/$event_type/$transactionId?access_token=$access_token") {
                        contentType(ContentType.Application.Json)
                        body = message_content
                    }
                transactionId++
                Database.updateSessionTransactionId(session_id, transactionId)
                message_confirmation.event_id
            }

            return Success("Our sent event id is: $result")
        } catch (e: Exception) {
            return Error("Message Send Failed", e)
        }
    }
    fun sendMessage(msg: String, room_id: String, reply_id: String = ""): Outcome<String> {
        val relation = if(reply_id != "") {
            RelationBlock(ReplyToRelation(reply_id))
        } else {
            null
        }
        val body = TextRMEC(msg, relation)
        return sendMessageImpl(body, room_id)
    }
    fun sendReaction(msg: String, room_id: String, reacted_id: String): Outcome<String> {
        val body = ReactionRMEC(msg, reacted_id)
        return sendMessageImpl(body, room_id)
    }
    fun sendEdit(msg: String, room_id: String, edited_id: String): Outcome<String> {
        val fallback_msg = "* $msg"
        val body = TextRMEC(msg, fallback_msg, edited_id)
        return sendMessageImpl(body, room_id)
    }
    fun sendReadReceipt(eventId: String, room_id: String): Outcome<String> {
        try {
            val result = runBlocking {
                val receipt_confirmation =
                        client.post<String>("$server/_matrix/client/r0/rooms/$room_id/receipt/m.read/$eventId?access_token=$access_token") {
                            contentType(ContentType.Application.Json)
                        }
            }
            return Success("The receipt was sent")
        } catch (e: Exception) {
            return Error("Receipt Failed", e)
        }
    }
    fun sendPinnedStateEventImpl(eventIds: List<String>, room_id: String): Outcome<String> {
        try {
            val content = RoomPinnedEventContent(eventIds)
            val result = runBlocking {
                        client.put<String>("$server/_matrix/client/r0/rooms/$room_id/state/m.room.pinned_events/?access_token=$access_token") {
                            contentType(ContentType.Application.Json)
                            body = content
                        }
            }
            println("pinning put result: $result")
            return Success("The msg was pinned")
        } catch (e: Exception) {
            return Error("Pin Failed", e)
        }
    }
    fun sendPinnedEvent(eventId: String, room_id: String): Outcome<String> {
        val current = getPinnedEvents(room_id)
        if(!current.contains(eventId)) { return sendPinnedStateEventImpl(current.plus(eventId), room_id) }
        return Error("Pinning Already Pinned event")
    }
    fun sendUnpinnedEvent(eventId: String, room_id: String): Outcome<String> {
        val current = getPinnedEvents(room_id)
        if(current.contains(eventId)) { return sendPinnedStateEventImpl(current.minus(eventId), room_id) }
        return Error("Unpinning unpinned event")
    }

    fun sendImageMessage(url: String, room_id: String): Outcome<String> {
        try {
            val img_f = File(url)
            val image_data = img_f.readBytes()
            val f_size = image_data.size
            val (ct, mimetype) =
                if(url.endsWith(".png")) {
                    Pair(ContentType.Image.PNG, "image/png")
                } else if(url.endsWith(".gif")) {
                    Pair(ContentType.Image.GIF, "image/gif")
                } else {
                    Pair(ContentType.Image.JPEG, "image/jpeg")
                }
            val image_info = ImageInfo(0, mimetype, f_size, 0)

            val body = runBlocking {
                //Post Image to server
                val upload_img_response =
                    client.post<MediaUploadResponse>("$server/_matrix/media/r0/upload?access_token=$access_token") {
                        contentType(ct)
                        body = image_data
                    }
                //Construct Image Message Content with url returned by the server
                ImageRMEC(msgtype="m.image", body="image_alt_text", info=image_info, url=upload_img_response.content_uri)
            }
            //Send Image Event with link
            return sendMessageImpl(body, room_id)
        } catch (e: Exception) {
            return Error("Image Upload Failed", e)
        }
    }
    fun getRedactEvent(roomID: String, eventID: String) : RoomEvent {
        return runBlocking {
            client.get<Event>("$server/_matrix/client/r0/rooms/$roomID/event/$eventID?access_token=$access_token")
        } as RoomEvent
    }

    fun sendRedactEvent(roomID: String, eventID: String):  Outcome<String> {
        try {
            val result = runBlocking {
                val redaction_confirmation =
                    client.put<EventIdResponse>("$server/_matrix/client/r0/rooms/$roomID/redact/$eventID/$transactionId?access_token=$access_token") {
                        contentType(ContentType.Application.Json)
                        body = RedactionBody(reason = "User Sent")
                    }
                transactionId++
                Database.updateSessionTransactionId(session_id, transactionId)
                redaction_confirmation
            }
            return Success("Our redaction event id is: $result")
        } catch (e: Exception) {
            return Error("Redaction Failed", e)
        }
    }
    fun getDiplayNameAndAvatarFilePath(sender: String, roomId: String?): Pair<String?, String?> {
        val (displayname, avatar_url) =
                if(roomId != null) { //Get info at the Room-Member level
                     when (val room_member_info = getLocalRoomMemberDetails(sender, roomId)) {
                        is Success -> Pair(room_member_info.value.first, room_member_info.value.second)
                        is Error -> Pair(sender, null)
                    }
                } else { //Get info at the User-Profile level
                    when (val user_profile_info = getLocalUserProfileDetails(sender)) {
                        is Success -> Pair(user_profile_info.value.first, user_profile_info.value.second)
                        is Error -> Pair(sender, null)
                    }
                }

        return if (avatar_url != null) {
            when (val avatar_file_path = getLocalMediaPathFromUrl(avatar_url)) {
                is Success -> Pair(displayname, avatar_file_path.value)
                is Error -> Pair(displayname, null)
            }
        } else {
            Pair(displayname, null)
        }
    }

    fun getLocalUserProfileDetails(sender: String): Outcome<Pair<String?, String?>> {
        try {
            val cached_user = Database.getUserProfileFromCache(sender)
            if (cached_user != null) {
                return Success(cached_user)
            }

            synchronized(this) {
                if (in_flight_user_requests.contains(sender)) {
                    return Error("User request already in flight");
                }
                in_flight_user_requests.add(sender)
            }
            thread(start = true) {
                //No valid cache hit
                println("Background thread request for $sender")
                val (displayname, avatar_url) = getUserProfile(sender)
                val result = Database.addUserProfileToCache(sender, displayname, avatar_url, false)
                println("Finished downloading $sender in the background with $result")
                synchronized(this) {
                    in_flight_user_requests.remove(sender)
                }
                onUpdate()
            }

            println("Returning early, downloading $sender in the background")
            return Error("Downloading user profile in the background")
        } catch (e: Exception) {
            println("Error with user profile retrieval $e")
            return Error("User Profile Retrieval Failed", e)
        }
    }

    fun getLocalRoomMemberDetails(sender: String, roomId: String): Outcome<Pair<String?, String?>> {
        return try {
            val roomMemberEventContent = Database.getStateEvent(session_id, roomId, "m.room.member", sender)
                    ?.castToStateEventWithContentOfType<RoomMemberEventContent>()
            if(roomMemberEventContent == null) {
                Error("Room Member Event not found", null)
            } else {
                Success(Pair(roomMemberEventContent?.displayname, roomMemberEventContent?.avatar_url))
            }
        } catch (e: Exception) {
            println("Error with room member details retrieval $e")
            Error("Room Member details Retrieval Failed", e)
        }
    }

    fun getUserProfile(sender: String): Pair<String?,String?> {
        return runBlocking {
            val user_profile_response =
                    client.get<ProfileResponse>("$server/_matrix/client/r0/profile/$sender?access_token=$access_token")
            Pair(user_profile_response.displayname, user_profile_response.avatar_url)
        }
    }

    fun mediaQuery(media_url: String): ByteArray {
        val url = "$server/_matrix/media/r0/download/${media_url.replace("mxc://","")}"
        println("Retrieving media from $url")
        return runBlocking { client.get<ByteArray>(url) }
    }
    fun getLocalMediaPathFromUrl(media_url: String): Outcome<String> {
        try {
            val cached_media = Database.getMediaInCache(media_url)
            var existing_entry = false
            if (cached_media != null) {
                if(File(cached_media).exists()) {
                    //File is in cache and it exists
                    return Success(cached_media)
                } else {
                    existing_entry = true
                }
            }

            synchronized(this) {
                if (in_flight_media_requests.contains(media_url)) {
                    return Error("Media request already in flight");
                }
                in_flight_media_requests.add(media_url)
            }
            thread(start = true) {
                //No valid cache hit
                println("Background thread request for $media_url")
                val media = mediaQuery(media_url)
                val result = Database.addMediaToCache(media_url, media, existing_entry)
                println("Finished downloading $media_url in the backkground with $result")
                synchronized(this) {
                    in_flight_media_requests.remove(media_url)
                }
                onUpdate()
             }

            println("Returning early, downloading $media_url in the background")
            return Error("Downloading media in the background")
        } catch (e: Exception) {
            println("Error with media retrieval $e")
            return Error("Media Retrieval Failed", e)
        }
    }
    fun saveMediaAtPathFromUrl(path: String, media_url: String): Outcome<String> {
        try {
            val media = mediaQuery(media_url)
            val file = File(path)
            file.outputStream().write(media)
            val result = file.toPath().toString()

            println("Media file at $result")
            return Success(result)
        } catch (e: Exception) {
            println("Error with file retrieval $e")
            return Error("File Retrieval Failed", e)
        }
    }

    fun requestBackfill(room_id: String, event_id: String, from: String) {
        synchronized(this) {
            if (in_flight_backfill_requests.contains(Triple(room_id, event_id, from))) {
                return;
            }
            in_flight_backfill_requests.add(Triple(room_id, event_id, from))
        }
        thread(start = true) {
            try {
                println("Backfilling $room_id from |${from}| based on event $event_id")
                val url = "$server/_matrix/client/r0/rooms/$room_id/messages?access_token=$access_token&from=$from&dir=b"
                val response = runBlocking { client.get<BackfillResponse>(url) }
                println("|${from}| - done getting response")
                // A bit too heavy handed, but syncronized so that backfill requests
                // don't stomp on each other's minId
                synchronized(this) {
                    if (response.chunk != null) {
                        Database.transaction {
                            val events = response.chunk.map { it as? RoomEvent }.filterNotNull()
                            val maxId = Database.getRoomEventAndIdx(session_id, room_id, event_id)!!.second
                            val minId = Database.maxIdLessThan(maxId)
                            println("Inserting into backfill with min $minId and max $maxId")
                            // TODO: Should also consider how to insert the old state
                            // chunk.
                            var insertId = idBetween(minId, maxId, false)
                            events.forEachIndexed tobreak@{ index, event ->
                                try {
                                    // Similar to prev_batch later on, we record what is basically
                                    // prev_batch if we're the "first" (reversed, so last) event
                                    // so we can keep backfilling later by getting the non-null
                                    // prev_batch with lowest seqId for this room
                                    val new_end = if (index == events.size -1) { response.end } else { null }
                                    println("looking to add event from backfill at $insertId with $new_end")
                                    if (Database.getRoomEventAndIdx(session_id, room_id, event.event_id) != null) {
                                        println("Already exists! We must have caught up, breaking out")
                                        return@tobreak
                                    }
                                    Database.addRoomEvent(insertId, session_id, room_id, event, getRelatedEvent(event), new_end)
                                    insertId = idBetween(minId, insertId, false)
                                } catch (e: Exception) {
                                    println("while trying to insert (from backfill) at index $insertId ${event} got exception $e")
                                }
                            }
                        }
                    }
                    println("|${from}| - done parsing and adding to database")
                    in_flight_backfill_requests.remove(Triple(room_id, event_id, from))
                    Database.updatePrevBatch(session_id, room_id, event_id, null)
                }
                onUpdate()
                println("|${from}| - done onUpdate")
            } catch (e: Exception) {
                synchronized(this) {
                    println("This backfill for $room_id failed with an exception $e")
                    e.printStackTrace()
                    in_flight_backfill_requests.remove(Triple(room_id, event_id, from))
                }
            }
        }
    }

    fun closeSession() {
        // Update Database with latest transactionId
        Database.updateSessionTransactionId(session_id, transactionId)
        // TO ACT LIKE A LOGOUT, CLOSING THE CLIENT
        client.close()
        sync_should_run = false
    }
    fun getSpaceChildren(id: String): List<String> = Database.getStateEvents(session_id, id, "m.space.child").map { (id,event) ->
        if (event.castToStateEventWithContentOfType<SpaceChildContent>() != null) {
            id
        } else { null }
    }.filterNotNull()
    fun getRoomType(id: String) = Database.getStateEvent(session_id, id, "m.room.create", "")?.castToStateEventWithContentOfType<RoomCreationContent>()?.type
    fun getPinnedEvents(id: String): List<String> {
        return Database.getStateEvent(session_id, id, "m.room.pinned_events", "")?.castToStateEventWithContentOfType<RoomPinnedEventContent>()?.pinned
            ?: listOf()
    }
    fun determineRoomName(id: String): String {
        return Database.getStateEvent(session_id, id, "m.room.name", "")?.castToStateEventWithContentOfType<RoomNameContent>()?.name
            ?: Database.getStateEvent(session_id, id, "m.room.canonical_alias", "")?.castToStateEventWithContentOfType<RoomCanonicalAliasContent>()?.alias
            ?: "<no room name - $id>"
    }
    fun mergeInSync(new_sync_response: SyncResponse) {
        Database.transaction {
            for ((room_id, room) in new_sync_response.rooms?.join ?: mapOf()) {
                for (event in room.state.events + room.timeline.events) {
                    (event as? StateEvent<*>)?.let { Database.setStateEvent(session_id, room_id, it) }
                }
                val events = room.timeline.events.map { it as? RoomEvent }.filterNotNull()
                val redactedEvents : MutableList<String> = mutableListOf()
                synchronized(this) {
                    val max = Database.maxId()
                    var insertId = idBetween(max, "z", true)
                    events.forEachIndexed { index, event ->
                        try {
                            // Note that if this is the first event of this chunk, we record prev_batch for it
                            // so we can later backfill by picking the non-null prev_batch with smallest seqId
                            // for this room.
                            if(event is RoomMessageEvent && event.redacts != null){
                                redactedEvents.add(event.redacts)
                            } else {
                                println("adding event from sync at $insertId")
                                Database.addRoomEvent(insertId, session_id, room_id, event, getRelatedEvent(event),
                                    if (index == 0 && room.timeline.limited) {
                                        room.timeline.prev_batch
                                    } else {
                                        null
                                    }
                                )
                                insertId = idBetween(insertId, "z", true)
                            }
                        } catch (e: Exception) {
                            println("while trying to insert (from mergeInSync) ${event} got exception $e")
                        }
                    }
                }
                for (redactId in redactedEvents) {
                    val freshRedact = getRedactEvent(room_id,redactId)
                    Database.replaceRoomEvent(freshRedact,room_id,session_id)
                }
                Database.setRoomSummary(
                    session_id,
                    room_id,
                    determineRoomName(room_id),
                    room.unread_notifications?.notification_count,
                    room.unread_notifications?.highlight_count,
                    (room.timeline.events.findLast { it as? RoomMessageEvent != null } as? RoomMessageEvent)?.event_id
                )
            }
            Database.updateSessionNextBatch(session_id, new_sync_response.next_batch)
        }
    }
}


object JsonFormatHolder {
    public val jsonFormat = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
    }
}

class MatrixClient {
    fun login(username: String, password: String, onUpdate: () -> Unit): Outcome<MatrixSession> {
        val client = Platform.makeHttpClient()
        val server = "https://synapse.room409.xyz"
        try {
            val loginResponse = runBlocking {
                client.post<LoginResponse>("$server/_matrix/client/r0/login") {
                    contentType(ContentType.Application.Json)
                    body = LoginRequest(username, password)
                }
            }

            // Save to DB
            println("Saving session to db")
            val new_transactionId: Long = 0
            Database.saveSession(loginResponse.user_id, loginResponse.access_token, new_transactionId)
            val session_id = Database.getUserSession(loginResponse.user_id).second.first

            return Success(MatrixSession(client, server, loginResponse.user_id, session_id, loginResponse.access_token, new_transactionId, onUpdate))
        } catch (e: Exception) {
            return Error("Login failed", e)
        }
    }
    fun loginFromSavedSession(username: String, onUpdate: () -> Unit): Outcome<MatrixSession> {
        val client = Platform.makeHttpClient()
        val server = "https://synapse.room409.xyz"
        // Load from DB
        println("loading specific session from db")
        val session = Database.getUserSession(username)
        val session_id = session.second.first
        val tok = session.second.second
        val transactionId = session.third
        return Success(MatrixSession(client, server, username, session_id, tok, transactionId, onUpdate))
    }

    fun getStoredSessions(): List<String> {
        println("loading sessions from db")
        return Database.getStoredSessions().map({ it.first })
    }
}
