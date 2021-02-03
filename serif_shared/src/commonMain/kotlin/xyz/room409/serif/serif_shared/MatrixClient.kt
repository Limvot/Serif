package xyz.room409.serif.serif_shared
import kotlinx.coroutines.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.http.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

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
        return MatrixRooms(msession=mclient.login(username, password))
    }
}
class MatrixRooms(val msession: MatrixSession): MatrixState() {
    fun test(): MatrixState {
        return MatrixLogin("${msession.test()}, now going back to login for now\n", MatrixClient())
    }
}
class MatrixSession(val client: HttpClient, val access_token: String) {
    fun test(): String {

        val result = runBlocking {

            val room_id = "!bwqkmRobBXpTSDiGIw:synapse.room409.xyz"
            val message_confirmation = client.put<EventIdResponse>("https://synapse.room409.xyz/_matrix/client/r0/rooms/$room_id/send/m.room.message/23?access_token=$access_token") {
                contentType(ContentType.Application.Json)
                body = RoomMessage("Final version - for now.....")
            }
            message_confirmation.event_id
        }

        // TO ACT LIKE A LOGOUT, CLOSING THE CLIENT
        client.close()
        return "Hello, ${Platform().platform}, ya cowpeople! - Our sent event id is: $result"
    }
}

class MatrixClient {
    fun login(username: String, password: String): MatrixSession {
        val client = HttpClient() {
            install(JsonFeature) {
                serializer = KotlinxSerializer(kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                })
            }
        }
        val loginResponse = runBlocking {
            client.post<LoginResponse>("https://synapse.room409.xyz/_matrix/client/r0/login") {
                contentType(ContentType.Application.Json)
                body = LoginRequest(username, password)
            }
        }
        return MatrixSession(client, loginResponse.access_token)
    }
}
