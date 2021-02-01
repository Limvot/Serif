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

val client = HttpClient() {
    install(JsonFeature) {
        serializer = KotlinxSerializer(kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
        })
    }
}

class MatrixSession(val access_token: String) {
    fun test(): String {

        val result = runBlocking {

            val room_id = "!bwqkmRobBXpTSDiGIw:synapse.room409.xyz"
            val message_confirmation = client.put<EventIdResponse>("https://synapse.room409.xyz/_matrix/client/r0/rooms/$room_id/send/m.room.message/23?access_token=$access_token") {
                contentType(ContentType.Application.Json)
                body = RoomMessage("Final version - for now.....")
            }
            client.close()
            message_confirmation.event_id
        }

        return "Hello, ${Platform().platform}, ya cowpeople! - Our sent event id is: $result"
    }
}

class MatrixClient {
    fun version(): String {
        return "Serif Matrix client, pre-alpha on ${Platform().platform}"
    }

    fun login(username: String, password: String): MatrixSession {
        val loginResponse = runBlocking {
            client.post<LoginResponse>("https://synapse.room409.xyz/_matrix/client/r0/login") {
                contentType(ContentType.Application.Json)
                body = LoginRequest(username, password)
            }
        }
        return MatrixSession(loginResponse.access_token)
    }
}
