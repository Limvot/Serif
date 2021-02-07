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
data class RoomMessage(val msgtype: String, val body: String) {
    constructor(body: String): this(msgtype="m.text",body=body)
}
@Serializable
data class EventIdResponse(val event_id: String)


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
            is Success -> { return MatrixRooms(msession=loginResult.value) }
            is Error -> { return MatrixLogin(login_message="${loginResult.message} - exception was ${loginResult.cause}, please login again...\n",
                                             mclient=mclient) }
        }
    }
}
class MatrixRooms(val msession: MatrixSession): MatrixState() {
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
    fun sendMessage(msg : String): Outcome<String> {
        try {

            val result = runBlocking {

                val room_id = "!bwqkmRobBXpTSDiGIw:synapse.room409.xyz"
                val message_confirmation = client.put<EventIdResponse>("https://synapse.room409.xyz/_matrix/client/r0/rooms/$room_id/send/m.room.message/23?access_token=$access_token") {
                    contentType(ContentType.Application.Json)
                    body = RoomMessage(msg)
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
