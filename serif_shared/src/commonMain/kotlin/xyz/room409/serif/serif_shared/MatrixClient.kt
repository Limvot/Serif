package xyz.room409.serif.serif_shared
import kotlinx.coroutines.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.http.*

sealed class Outcome<out T : Any> { }
data class Success<out T : Any>(val value: T) : Outcome<T>()
data class Error(val message: String, val cause: Exception? = null) : Outcome<Nothing>()

// Returns the first Event of class T, or null
inline fun <reified T> List<Event>.firstOfType(): T? = this.map { (it as? T?) }.firstOrNull { it != null }

class MatrixSession(val client: HttpClient, val access_token: String, var transactionId : Long) {
    var sync_response: SyncResponse? = null

    // rooms is a pair of room_id and room display name
    // Display name is calculated by first checking for a room name event in the state events,
    // then for a canonical alias in the state events, then a list of hero users in the room summary,
    // and then the error message. Technically we should also be looking at timeline events
    // for room name changes too. Also, it's not working when there's not a room name -
    // the way I'm reading the doc heros should be non-null... maybe it's not decoding right?
    val rooms: List<Pair<String,String>>
        get() = sync_response?.rooms?.join?.entries?.map { (id, room) ->
            Pair(id, (room.state.events.firstOfType<StateEvent<RoomNameContent>>()?.content?.name
                   ?: room.state.events.firstOfType<StateEvent<RoomCanonicalAliasContent>>()?.content?.alias
                   ?: room.summary.heroes?.joinToString(", ")
                   ?: "<no room name or heroes>"))
        }?.toList() ?: listOf()

    fun sendMessage(msg: String, room_id: String): Outcome<String> {
        try {
            val result = runBlocking {
                val message_confirmation =
                client.put<EventIdResponse>("https://synapse.room409.xyz/_matrix/client/r0/rooms/$room_id/send/m.room.message/$transactionId?access_token=$access_token") {
                    contentType(ContentType.Application.Json)
                    body = SendRoomMessage(msg)
                }
                transactionId++
                message_confirmation.event_id
            }

            return Success("Hello, ${Platform().platform}, ya cowpeople! - Our sent event id is: $result")
        } catch (e: Exception) {
            return Error("Message Send Failed", e)
        }
    }

    fun closeSession() {
        //Update Database with latest transactionId
        Database.updateSession(this.access_token, this.transactionId)
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
                        if (sync_response!!.rooms.join.containsKey(room_id)) {
                            // This should actually be updated with messages from the timeline too
                            sync_response!!.rooms.join[room_id]!!.state = room.state
                            // This also needs to be changed to something that supports discontinuous
                            // sections of timeline with different prev_patch, etc
                            sync_response!!.rooms.join[room_id]!!.timeline.events += room.timeline.events
                        } else {
                            println(" adding in new room_id and room")
                            sync_response!!.rooms.join[room_id] = room
                        }
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

            //Save to DB
            println("Saving session to db")
            val new_transactionId : Long = 0
            Database.saveSession(username, loginResponse.access_token, new_transactionId)

            return Success(MatrixSession(client, loginResponse.access_token, new_transactionId))
        } catch (e: Exception) {
            return Error("Login failed", e)
        }
    }
    fun loginFromSavedSession(username: String): Outcome<MatrixSession> {
        val client = HttpClient() {
            install(JsonFeature) {
                serializer = KotlinxSerializer(kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                })
            }
        }
        //Load from DB
        println("loading specific session from db")
        val sessions = Database.getStoredSessions()
        for((user, tok, transactionId) in sessions) {
            if(user == username) {
                return Success(MatrixSession(client, tok, transactionId))
            }
        }
        return Error("No Saved Session for $username")
    }

    fun getStoredSessions() : List<String> {
        println("loading sessions from db")
        return Database.getStoredSessions().map({ it.first })
    }
}
