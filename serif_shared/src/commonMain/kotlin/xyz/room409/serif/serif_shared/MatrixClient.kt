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

class MatrixSession(val client: HttpClient, val access_token: String, var transactionId: Long, val onUpdate: () -> Unit) {
    private var sync_response: SyncResponse? = null
    private var sync_should_run = true
    private var sync_thread: Thread? = thread(start = true) {
        val timeout_ms: Long = 10000
        var fail_times = 0
        while (sync_should_run) {
            val next_batch = synchronized(this) { sync_response?.next_batch }
            val url = if (next_batch != null) {
                "https://synapse.room409.xyz/_matrix/client/r0/sync?since=$next_batch&timeout=$timeout_ms&access_token=$access_token"
            } else {
                val limit = 5
                "https://synapse.room409.xyz/_matrix/client/r0/sync?filter={\"room\":{\"timeline\":{\"limit\":$limit}}}&access_token=$access_token"
            }
            try {
                mergeInSync(runBlocking { client.get<SyncResponse>(url) })
                fail_times = 0
                onUpdate()
            } catch (e: Exception) {
                // Exponential backoff on failure
                val backoff_ms = timeout_ms shl fail_times
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

    fun sendMessage(msg: String, room_id: String): Outcome<String> {
        try {
            val result = runBlocking {
                val message_confirmation =
                    client.put<EventIdResponse>("https://synapse.room409.xyz/_matrix/client/r0/rooms/$room_id/send/m.room.message/$transactionId?access_token=$access_token") {
                        contentType(ContentType.Application.Json)
                        body = SendRoomMessage(msg)
                    }
                transactionId++
                Database.updateSession(access_token, transactionId)
                message_confirmation.event_id
            }

            return Success("Hello, ${Platform().platform}, ya cowpeople! - Our sent event id is: $result")
        } catch (e: Exception) {
            return Error("Message Send Failed", e)
        }
    }

    fun sendImageMessage(url: String, room_id: String): Outcome<String> {
        try {
            val result = runBlocking {
                val img_f = File(url)
                val image_data = img_f.readBytes()
                val f_size = image_data.size
                var ct = ContentType.Image.JPEG
                val mimetype =
                    if(url.endsWith(".png")) {
                        ct = ContentType.Image.PNG
                        "image/png"
                    } else if(url.endsWith(".gif")) {
                        ct = ContentType.Image.GIF
                        "image/gif"
                    } else {
                        "image/jpeg"
                    }
                val image_info = ImageInfo(0, mimetype, f_size, 0)

                //Post Image to server
                val upload_img_response =
                    client.post<MediaUploadResponse>("https://synapse.room409.xyz/_matrix/media/r0/upload?access_token=$access_token") {
                        contentType(ct)
                        body = image_data
                    }

                //Send link to image
                val message_confirmation =
                    client.put<EventIdResponse>("https://synapse.room409.xyz/_matrix/client/r0/rooms/$room_id/send/m.room.message/$transactionId?access_token=$access_token") {
                        contentType(ContentType.Application.Json)
                        body = SendRoomImageMessage("image_alt_text", image_info, upload_img_response.content_uri)
                    }

                transactionId++
                Database.updateSession(access_token, transactionId)
                message_confirmation.event_id
            }

            return Success("Sent event id is: $result")
        } catch (e: Exception) {
            return Error("Message Send Failed", e)
        }
    }

    fun getLocalImagePathFromUrl(image_url: String): Outcome<String> {
        try {
            val cached_img = Database.getImageInCache(image_url)
            if (cached_img != null) {
                return Success(cached_img)
            } else {
                val result = runBlocking {
                    val url = "https://synapse.room409.xyz/_matrix/media/r0/download/${image_url.replace("mxc://","")}"
                    println("Retrieving image from $url")
                    val media = client.get<ByteArray>(url)
                    Database.addImageToCache(image_url, media)
                }
                println("Img file at $result")
                return Success(result)
            }
        } catch (e: Exception) {
            println("Error with image retrieval $e")
            return Error("Image Retrieval Failed", e)
        }
    }

    fun requestBackfill(room_id: String) {
        thread(start = true) {
            try {
                val from = synchronized(this) { sync_response!!.rooms.join[room_id]!!.timeline.prev_batch }
                val url = "https://synapse.room409.xyz/_matrix/client/r0/rooms/$room_id/messages?access_token=$access_token&from=$from&dir=b"
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
                }
                onUpdate()
            } catch (e: Exception) {
                println("This backfill for $room_id failed with an exception $e")
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
        try {
            val loginResponse = runBlocking {
                client.post<LoginResponse>("https://synapse.room409.xyz/_matrix/client/r0/login") {
                    contentType(ContentType.Application.Json)
                    body = LoginRequest(username, password)
                }
            }

            // Save to DB
            println("Saving session to db")
            val new_transactionId: Long = 0
            Database.saveSession(username, loginResponse.access_token, new_transactionId)

            return Success(MatrixSession(client, loginResponse.access_token, new_transactionId, onUpdate))
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
        // Load from DB
        println("loading specific session from db")
        val sessions = Database.getUserSession(username)
        val tok = sessions.second
        val transactionId = sessions.third
        return Success(MatrixSession(client, tok, transactionId, onUpdate))
    }

    fun getStoredSessions(): List<String> {
        println("loading sessions from db")
        return Database.getStoredSessions().map({ it.first })
    }
}
