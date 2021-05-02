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
        val timeout_ms: Long = 10000
        var fail_times = 0
        while (sync_should_run) {
            val next_batch = synchronized(this) { sync_response?.next_batch }
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

    // rooms is a pair of room_id and room display name
    // Display name is calculated by first checking for a room name event in the state events,
    // then for a canonical alias in the state events, then a list of hero users in the room summary,
    // and then the error message. Technically we should also be looking at timeline events
    // for room name changes too. Also, it's not working when there's not a room name -
    // the way I'm reading the doc heroes should be non-null... maybe it's not decoding right?

    fun <T> mapRooms(f: (String, Room) -> T): List<T> = synchronized(this) {
        sync_response?.rooms?.join?.entries?.map { (id, room,) -> f(id, room) }?.toList() ?: listOf()
    }
    fun <T> mapRoom(id: String, f: (Room) -> T): T? = synchronized(this) {
        sync_response?.rooms?.join?.get(id)?.let { f(it) }
    }

    fun getRoomEvents(id: String) = synchronized(this) { sync_response!!.rooms.join[id]!!.timeline.events }

    fun createRoom(name:String, room_alias_name: String, topic: String): Outcome<String> {
        try {
            val result = runBlocking {
                val creation_confirmation =
                client.post<String>("$server/_matrix/client/r0/createRoom?access_token=$access_token") {
                    contentType(ContentType.Application.Json)
                    body = CreateRoom(name, room_alias_name, topic)
                }
                Database.updateSession(access_token, transactionId)
                creation_confirmation
            }
            return Success("Our create create room event id is: $result")
        } catch (e: Exception){
            return Error("Create Room Failed", e)
        }
    }


    fun sendMessageImpl(message_content: RoomMessageEventContent, room_id: String): Outcome<String> {

        try {
            val result = runBlocking {
                val message_confirmation =
                    client.put<EventIdResponse>("$server/_matrix/client/r0/rooms/$room_id/send/m.room.message/$transactionId?access_token=$access_token") {
                        contentType(ContentType.Application.Json)
                        body = message_content
                    }
                transactionId++
                Database.updateSession(access_token, transactionId)
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
                val from = synchronized(this) { sync_response!!.rooms.join[room_id]!!.timeline.prev_batch }
                val url = "$server/_matrix/client/r0/rooms/$room_id/messages?access_token=$access_token&from=$from&dir=b"
                val response = runBlocking { client.get<BackfillResponse>(url) }
                synchronized(this) {
                    // This also needs to be changed to something that supports discontinuous
                    // sections of timeline with different prev_patch, etc
                    if (response.chunk != null) {
                        sync_response!!.rooms.join[room_id]!!.state.events += response.chunk.filter { it is StateEvent<*> }
                        sync_response!!.rooms.join[room_id]!!.timeline.events = response.chunk.asReversed() + sync_response!!.rooms.join[room_id]!!.timeline.events
                    }
                    if (response.state != null) {
                        sync_response!!.rooms.join[room_id]!!.state.events += response.state
                    }
                    sync_response!!.rooms.join[room_id]!!.timeline.prev_batch = response.end
                    in_flight_backfill_requests.remove(room_id)
                }
                onUpdate()
            } catch (e: Exception) {
                println("This backfill for $room_id failed with an exception $e")
                synchronized(this) {
                    in_flight_backfill_requests.remove(room_id)
                }
            }
        }
    }

    fun closeSession() {
        // Update Database with latest transactionId
        Database.updateSession(this.access_token, this.transactionId)
        // TO ACT LIKE A LOGOUT, CLOSING THE CLIENT
        client.close()
        sync_should_run = false
    }
    fun mergeInSync(new_sync_response: SyncResponse) {
        synchronized(this) {
            if (sync_response == null) {
                sync_response = new_sync_response
            } else {
                for ((room_id, room) in new_sync_response.rooms.join) {
                    if (sync_response!!.rooms.join.containsKey(room_id)) {
                        // A little bit hacky, we just add all our new state events here, prepending
                        // the new ones
                        sync_response!!.rooms.join[room_id]!!.state.events = room.state.events + sync_response!!.rooms.join[room_id]!!.state.events
                        sync_response!!.rooms.join[room_id]!!.state.events = room.timeline.events.filter { it is StateEvent<*> }.asReversed() + sync_response!!.rooms.join[room_id]!!.state.events
                        // This also needs to be changed to something that supports discontinuous
                        // sections of timeline with different prev_patch, etc
                        sync_response!!.rooms.join[room_id]!!.timeline.events += room.timeline.events
                        sync_response!!.rooms.join[room_id]!!.unread_notifications = room.unread_notifications
                    } else {
                        sync_response!!.rooms.join[room_id] = room
                    }
                }
                sync_response!!.next_batch = new_sync_response.next_batch
            }
        }
    }
}

class MatrixClient {
    fun login(username: String, password: String, onUpdate: () -> Unit): Outcome<MatrixSession> {
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
