package xyz.room409.serif.serif_shared
import io.ktor.client.*
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

fun is_edit_content(msg_content: TextRMEC): Boolean = ((msg_content.new_content != null) && (msg_content.relates_to?.event_id != null))
fun isStandaloneEvent(e: Event): Boolean {
    if (e as? RoomMessageEvent != null) {
        return !(e.content is ReactionRMEC || (e.content is TextRMEC && is_edit_content(e.content)))
    } else {
        return false
    }
}
fun getRelatedEvent(e: Event): String? {
    if (e as? RoomMessageEvent != null) {
        if (e.content is ReactionRMEC) {
            return e.content!!.relates_to!!.event_id!!
        } else if (e.content is TextRMEC && is_edit_content(e.content)) {
            return e.content!!.relates_to!!.event_id!!
        }
    }
    return null
}

// Returns the first Event of class T, or null
inline fun <reified T> List<Event>.firstOfType(): T? = this.map { (it as? T?) }.firstOrNull { it != null }
// Returns the content of type T from the first Event of type StateEvent<T>
// This can't just be a call to firstofType<StateEvent<T>> because the safe cast operator only works one level, and you'd
// still get ClassCastExceptions when it returns an Event that is indeed of type StateEvent<?> but not instantiated with
// the right T. Java generics are bad, yall, and Kotlin in trying to be nice sometimes makes things much worse.
inline fun <reified T> List<Event>.firstStateEventContentOfType(): T? = this.map { ((it as? StateEvent<T>?)?.content) as? T? }.firstOrNull { it != null }

class MatrixSession(val client: HttpClient, val server: String, val user: String, val access_token: String, var transactionId: Long, val onUpdate: () -> Unit) {
    private var in_flight_backfill_requests: MutableSet<String> = mutableSetOf()
    private var sync_response: SyncResponse? = null
    private var sync_should_run = true
    private var sync_thread: Thread? = thread(start = true) {
        val timeout_ms: Long = 20000
        var fail_times = 0
        while (sync_should_run) {
            val next_batch = Database.getSessionNextBatch(access_token)
            println("next_batch is $next_batch")
            val url = if (next_batch != null) {
                "$server/_matrix/client/r0/sync?since=$next_batch&timeout=$timeout_ms&access_token=$access_token"
            } else {
                val limit = 5
                "$server/_matrix/client/r0/sync?filter={\"room\":{\"timeline\":{\"limit\":$limit}}}&access_token=$access_token"
            }
            try {
                mergeInSync(runBlocking { client.get<SyncResponse>(url) })
                fail_times = 0
                onUpdate()
            } catch (e: Exception) {
                // Exponential backoff on failure
                val backoff_ms = timeout_ms shl fail_times
                e.printStackTrace()
                println("This sync failed with an exception $e, waiting ${backoff_ms / 1000} seconds before trying again")
                fail_times += 1
                Thread.sleep(backoff_ms)
            }
        }
    }

    fun <T> mapRooms(f: (String,String,Int,Int,RoomMessageEvent?) -> T): List<T> = Database.mapRooms(f)
    fun getReleventRoomEventsForWindow(room_id: String, window_back_length: Int, message_window_base: String?, window_forward_length: Int): Pair<List<Event>, Boolean> {
        val overrideCurrent = message_window_base == null
        val base_and_seqId = message_window_base?.let { Database.getRoomEventAndIdx(room_id, it) } ?: Database.getMostRecentRoomEventAndIdx(room_id)
        // completely empty room!!
        if (base_and_seqId == null) {
            return Pair(listOf(), true)
        }
        println("Got base_and_seqId $base_and_seqId from asking for $message_window_base in $room_id")
        var base_seqId = base_and_seqId.second.toLong()

        val window_back_length = window_back_length + if (isStandaloneEvent(base_and_seqId.first)) { 0 } else { 1 }
        var back_base_seqId = base_and_seqId.second.toLong()
        var back_events: List<Pair<Event,Long>> = listOf()
        while (back_events.size < window_back_length) {
            println("back_events.size (filtered) (${back_events.size}) < window_back_length($window_back_length), so getting ${window_back_length.toLong() - back_events.size} new events")
            val new_events = Database.getRoomEventsBackwardsFromPoint(room_id, back_base_seqId, window_back_length.toLong() - back_events.size)
            back_events = new_events.filter { isStandaloneEvent(it.first) } + back_events
            println("\tgot ${new_events.size} events")
            if (new_events.size == 0) {
                println("\tnew_events.size == 0, breaking")
                break;
            }
            back_base_seqId = new_events.first()!!.second
        }
        println("Done getting back, have ${back_events.size} (filtered) back events (from request for $window_back_length back events)")

        var fore_base_seqId = base_and_seqId.second.toLong()
        var fore_events: List<Pair<Event,Long>> = listOf()
        while (fore_events.size < window_forward_length) {
            println("fore_standalone_events(${fore_events.size}) < window_fore_length($window_forward_length), so getting ${window_forward_length.toLong() - fore_events.size} new events")
            val new_events = Database.getRoomEventsForwardsFromPoint(room_id, fore_base_seqId, window_forward_length.toLong() - fore_events.size)
            fore_events = fore_events.filter { isStandaloneEvent(it.first) } + new_events
            println("\tgot ${new_events.size} (filtered) events")
            if (new_events.size == 0) {
                println("\tnew_events.size == 0, breaking")
                break
            }
            fore_base_seqId = new_events.last()!!.second
        }
        println("Done getting fore, have ${fore_events.size} (filtered) forewards events (from request for $window_forward_length foreard events)")

        if (back_events.size < window_back_length) {
            println("back_events.size (${back_events.size}) < window_back_length ($window_back_length), requesting backfill")
            requestBackfill(room_id)
        }
        val prelim_total_events = back_events + listOf(base_and_seqId) + fore_events
        val relatedEvents = Database.getRelatedEvents(room_id, prelim_total_events.map { (it.first as RoomEvent).event_id })
        val total_events = (back_events + listOf(base_and_seqId) + fore_events + relatedEvents).sortedBy { it.second } .map { it.first }
        println("to double check, we have ${total_events.size} total events, with ${total_events.count { isStandaloneEvent(it) }} standalone, and ${relatedEvents.size} pulled using getRelatedEvents")
        return Pair(total_events, overrideCurrent || fore_events.size < window_forward_length)
    }
    fun getRoomEvent(room_id: String, event_id: String): Event? = Database.getRoomEventAndIdx(room_id, event_id)?.first
    fun createRoom(name:String, room_alias_name: String, topic: String): Outcome<String> {
        try {
            val result = runBlocking {
                val creation_confirmation =
                client.post<String>("$server/_matrix/client/r0/createRoom?access_token=$access_token") {
                    contentType(ContentType.Application.Json)
                    body = CreateRoom(name, room_alias_name, topic)
                }
                Database.updateSessionTransactionId(access_token, transactionId)
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
                Database.updateSessionTransactionId(access_token, transactionId)
                message_confirmation.event_id
            }

            return Success("Our sent event id is: $result")
        } catch (e: Exception) {
            return Error("Message Send Failed", e)
        }
    }
    fun sendMessage(msg: String, room_id: String, reply_id: String = ""): Outcome<String> {
        val (msg, relation) = if(reply_id != "") {
            Pair("> in reply to $reply_id\n\n$msg",
                 RelationBlock(ReplyToRelation(reply_id)))
        } else {
            Pair(msg, null)
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

            //No valid cache hit
            val media = mediaQuery(media_url)
            val result = Database.addMediaToCache(media_url, media, existing_entry)

            println("Media file at $result")
            return Success(result)
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

    fun requestBackfill(room_id: String) {
        synchronized(this) {
            if (in_flight_backfill_requests.contains(room_id)) {
                return;
            }
            in_flight_backfill_requests.add(room_id)
        }
        thread(start = true) {
            try {
                val from = Database.getPrevBatch(room_id)!!
                println("Backfilling from |${from}|")
                val url = "$server/_matrix/client/r0/rooms/$room_id/messages?access_token=$access_token&from=$from&dir=b"
                val response = runBlocking { client.get<BackfillResponse>(url) }
                println("|${from}| - done getting response")
                // A bit too heavy handed, but syncronized so that backfill requests
                // don't stomp on each other's minId
                synchronized(this) {
                    if (response.chunk != null) {
                        val events = response.chunk.map { it as? RoomEvent }.filterNotNull()
                        val min = Database.minId()
                        println("Inserting into backfill with min $min")
                        // TODO: Should also consider how to insert the old state
                        // chunk.
                        events.forEachIndexed { index, event ->
                            try {
                                val new_end = if (index == events.size -1) { response.end } else { null }
                                println("adding event at ${min-(index+1)} with $new_end")
                                Database.addRoomEvent(min-(index+1), room_id, event, getRelatedEvent(event), new_end)
                            } catch (e: Exception) {
                                println("while trying to insert (from backfill) at index ${min-(index+1)} ${event} got exception $e")
                            }
                        }
                    }
                    println("|${from}| - done parsing and adding to database")
                    in_flight_backfill_requests.remove(room_id)
                }
                onUpdate()
                println("|${from}| - done onUpdate")
            } catch (e: Exception) {
                synchronized(this) {
                    println("This backfill for $room_id failed with an exception $e")
                    e.printStackTrace()
                    in_flight_backfill_requests.remove(room_id)
                }
            }
        }
    }

    fun closeSession() {
        // Update Database with latest transactionId
        Database.updateSessionTransactionId(this.access_token, this.transactionId)
        // TO ACT LIKE A LOGOUT, CLOSING THE CLIENT
        client.close()
        sync_should_run = false
    }
    fun determineRoomName(room: Room, id: String): String {
        return room.state.events.firstStateEventContentOfType<RoomNameContent>()?.name
            ?: room.state.events.firstStateEventContentOfType<RoomCanonicalAliasContent>()?.alias
            ?: room.summary.heroes?.joinToString(", ")
            ?: "<no room name - $id>"
    }
    fun mergeInSync(new_sync_response: SyncResponse) {
        for ((room_id, room) in new_sync_response.rooms.join) {
            for (event in room.state.events + room.timeline.events) {
                (event as? StateEvent<*>)?.let { println("got state event $it"); Database.setStateEvent(room_id, it) }
            }
            val events = room.timeline.events.map { it as? RoomEvent }.filterNotNull()
            events.forEachIndexed { index, event ->
                try {
                    Database.addRoomEvent(null, room_id, event, getRelatedEvent(event), if (index == 0) { room.timeline.prev_batch } else { null })
                } catch (e: Exception) {
                    println("while trying to insert (from mergeInSync) ${event} got exception $e")

                }
            }
            Database.setRoomSummary(
                room_id,
                null,
                determineRoomName(room, room_id),
                room.unread_notifications?.notification_count,
                room.unread_notifications?.highlight_count,
                room.timeline.events.findLast { it as? RoomMessageEvent != null } as? RoomMessageEvent
            )
        }
        Database.updateSessionNextBatch(access_token, new_sync_response.next_batch)
    }
}


object JsonFormatHolder {
    public val jsonFormat = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
    }
}

class MatrixClient {
    fun login(username: String, password: String, onUpdate: () -> Unit): Outcome<MatrixSession> {
        val client = HttpClient() {
            install(JsonFeature) {
                serializer = KotlinxSerializer(JsonFormatHolder.jsonFormat)
            }
        }
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

            return Success(MatrixSession(client, server, loginResponse.user_id, loginResponse.access_token, new_transactionId, onUpdate))
        } catch (e: Exception) {
            return Error("Login failed", e)
        }
    }
    fun loginFromSavedSession(username: String, onUpdate: () -> Unit): Outcome<MatrixSession> {
        val client = HttpClient() {
            install(JsonFeature) {
                serializer = KotlinxSerializer(
                    kotlinx.serialization.json.Json {
                        ignoreUnknownKeys = true
                    }
                )
            }
        }
        val server = "https://synapse.room409.xyz"
        // Load from DB
        println("loading specific session from db")
        val sessions = Database.getUserSession(username)
        val tok = sessions.second
        val transactionId = sessions.third
        return Success(MatrixSession(client, server, username, tok, transactionId, onUpdate))
    }

    fun getStoredSessions(): List<String> {
        println("loading sessions from db")
        return Database.getStoredSessions().map({ it.first })
    }
}
