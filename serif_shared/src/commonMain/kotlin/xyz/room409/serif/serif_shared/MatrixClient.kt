package xyz.room409.serif.serif_shared
import kotlinx.coroutines.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.http.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

sealed class Outcome<out T : Any> { }
data class Success<out T : Any>(val value: T) : Outcome<T>()
data class Error(val message: String, val cause: Exception? = null) : Outcome<Nothing>()

@Serializable
data class LoginRequest(val type: String, val identifier: LoginIdentifier, val password: String, val initial_device_display_name: String) {
    constructor(username: String, password: String): this(type="m.login.password",
                                                          identifier=LoginIdentifier(type="m.id.user",user=username),
                                                          password=password,
                                                          initial_device_display_name="Serif")
}
@Serializable
data class LoginIdentifier(val type: String, val user: String)

@Serializable
data class LoginResponse(val access_token: String)

@Serializable
data class SendRoomMessage(val msgtype: String, val body: String) {
    constructor(body: String): this(msgtype="m.text",body=body)
}
@Serializable
data class EventIdResponse(val event_id: String)

@Serializable
data class SyncResponse(var next_batch: String, val rooms: Rooms)
@Serializable
data class Rooms(val join: MutableMap<String, Room>)
@Serializable
data class Room(val timeline: Timeline)
@Serializable
data class Timeline(val events: List<Event>, val prev_batch: String)

@Serializable(with = EventSerializer::class)
abstract class Event {
    abstract val self: JsonObject
    abstract val content: JsonElement
    abstract val type: String
}

object EventSerializer : JsonContentPolymorphicSerializer<Event>(Event::class) {
    override fun selectDeserializer(element: JsonElement) = when {
        else -> EventFallbackSerializer
    }
}

abstract class RoomEvent: Event() {
    abstract val event_id: String
    abstract val sender: String
    abstract val origin_server_ts: Long
    abstract val unsigned: UnsignedData?
}

@Serializable
data class UnsignedData(val age: Long?, /*redacted_because: Event?,*/ val transaction_id: String?)

abstract class StateEvent: RoomEvent() {
    abstract val state_key: String
    /*abstract val prev_content: EventContent?*/
}

abstract class RoomMessage: RoomEvent() {
    abstract val body: String
    abstract val msgtype: String
}

//@Serializable
//class RoomMessageFallback(val self: JsonObject): RoomMessage()
//@Serializable
//class RoomEventFallback(val self: JsonObject): RoomEvent()
@Serializable
class EventFallback(override val self: JsonObject, override val content: JsonElement, override val type: String): Event() {
    override fun toString() = self.toString()
}

object EventFallbackSerializer : JsonTransformingSerializer<EventFallback>(EventFallback.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement = buildJsonObject {
        put("self", element)
        put("content", element.jsonObject["content"]!!)
        put("type", element.jsonObject["type"]!!)
    }
    override fun transformSerialize(element: JsonElement): JsonElement =
        element.jsonObject["self"]!!
}



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
}
class MatrixRooms(val msession: MatrixSession, val rooms: List<String>, val message: String): MatrixState() {
    fun sync(): MatrixState {
        when (val syncResult = msession.sync()) {
            is Success -> { return MatrixRooms(msession=msession, rooms=msession.sync_response?.rooms?.join?.keys?.toList() ?: listOf(),
                                               message="Sync success\n") }
            is Error   -> { return MatrixRooms(msession=msession, rooms=rooms,
                                               message="${syncResult.message} - exception was ${syncResult.cause}, maybe try again?\n") }
        }
    }
    fun getRoom(): MatrixState {
        return MatrixChatRoom(msession)
    }
}
class MatrixChatRoom(val msession: MatrixSession): MatrixState() {
    fun sendMessage(msg : String): MatrixState {
        when (val sendMessageResult = msession.sendMessage(msg)) {
            is Success -> { println("${sendMessageResult.value}") }
            is Error -> { println("${sendMessageResult.message} - exception was ${sendMessageResult.cause}") }
        }
        return this
    }
    fun exitRoom(): MatrixState {
        msession.closeSession()
        return MatrixLogin("Closing session, returning to the login prompt for now\n", MatrixClient())
    }
}
class MatrixSession(val client: HttpClient, val access_token: String) {
    var sync_response: SyncResponse? = null
    fun sendMessage(msg : String): Outcome<String> {
        try {
            val result = runBlocking {
                val room_id = "!bwqkmRobBXpTSDiGIw:synapse.room409.xyz"
                val message_confirmation = client.put<EventIdResponse>("https://synapse.room409.xyz/_matrix/client/r0/rooms/$room_id/send/m.room.message/23?access_token=$access_token") {
                    contentType(ContentType.Application.Json)
                    body = SendRoomMessage(msg)
                }
                message_confirmation.event_id
            }

            return Success("Hello, ${Platform().platform}, ya cowpeople! - Our sent event id is: $result")
        } catch (e: Exception) {
            return Error("Message Send Failed", e)
        }
    }

    fun closeSession() {
        // TO ACT LIKE A LOGOUT, CLOSING THE CLIENT
        client.close()
    }
    fun sync(): Outcome<Unit> {
        val timeout_ms = 10000
        val url = if (sync_response != null) {
            "https://synapse.room409.xyz/_matrix/client/r0/sync?since=${sync_response!!.next_batch}&timeout=$timeout_ms&access_token=$access_token"
        } else {
            val limit = 5
            "https://synapse.room409.xyz/_matrix/client/r0/sync?filter={\"room\":{\"timeline\":{\"limit\":$limit}}}&access_token=$access_token"
        }
        try {
            runBlocking {
                val new_sync_response = client.get<SyncResponse>(url)
                if (sync_response != null) {
                    println("Sync response wasn't null")
                    for ((room_id, room) in new_sync_response.rooms.join) {
                        println(" adding in new room_id and room")
                        // Do a better combine
                        sync_response!!.rooms.join[room_id] = room
                    }
                    sync_response!!.next_batch = new_sync_response.next_batch
                } else {
                    println("was null, adding whole response")
                    sync_response = new_sync_response
                }
                println("Current response was $sync_response")
            }
            return Success(Unit)
        } catch (e: Exception) {
            return Error("Sync failed", e)
        }
    }
}

class MatrixClient {
    fun login(username: String, password: String): Outcome<MatrixSession> {
        val client = HttpClient() {
            install(JsonFeature) {
                serializer = KotlinxSerializer(kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                })
            }
        }
        try {
            val loginResponse = runBlocking {
                client.post<LoginResponse>("https://synapse.room409.xyz/_matrix/client/r0/login") {
                    contentType(ContentType.Application.Json)
                    body = LoginRequest(username, password)
                }
            }
            return Success(MatrixSession(client, loginResponse.access_token))
        } catch (e: Exception) {
            return Error("Login failed", e)
        }
    }
}
