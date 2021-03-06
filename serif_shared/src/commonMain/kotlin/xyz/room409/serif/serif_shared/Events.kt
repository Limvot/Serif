package xyz.room409.serif.serif_shared

import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class LoginRequest(val type: String, val identifier: LoginIdentifier, val password: String, val initial_device_display_name: String) {
    constructor(username: String, password: String) : this(
        type = "m.login.password",
        identifier = LoginIdentifier(type = "m.id.user", user = username),
        password = password,
        initial_device_display_name = "Serif"
    )
}
@Serializable
data class LoginIdentifier(val type: String, val user: String)

@Serializable
data class LoginResponse(val access_token: String)

@Serializable
data class SendRoomMessage(val msgtype: String, val body: String) {
    constructor(body: String) : this(msgtype = "m.text", body = body)
}
@Serializable
data class EventIdResponse(val event_id: String)
@Serializable
data class UnreadNotifications(val highlight_count: Int? = null, val unread_count: Int? = null)
@Serializable
data class SyncResponse(var next_batch: String, val rooms: Rooms)
@Serializable
data class Rooms(val join: MutableMap<String, Room>)
@Serializable
data class Room(var timeline: Timeline, var state: State, val summary: RoomSummary, var unread_notifications: UnreadNotifications? = null)

@Serializable
data class Timeline(var events: List<Event>, val prev_batch: String)
@Serializable
data class State(var events: List<Event>)
@Serializable
data class RoomSummary(
    @SerialName("m.heroes") val heroes: List<String>? = null,
    @SerialName("m.joined_member_count") val joined_member_count: Long? = null,
    @SerialName("m.invited_member_count") val invited_member_count: Long? = null
)

@Serializable(with = EventSerializer::class)
abstract class Event {
    abstract val raw_self: JsonObject
    abstract val raw_content: JsonElement
    abstract val type: String
}
abstract class RoomEvent : Event() {
    abstract val event_id: String
    abstract val sender: String
    abstract val origin_server_ts: Long
    abstract val unsigned: UnsignedData?
}
@Serializable
data class UnsignedData(val age: Long? = null, /*redacted_because: Event?,*/ val transaction_id: String? = null)
@Serializable
class StateEvent<T>(
    override val raw_self: JsonObject,
    override val raw_content: JsonElement,
    override val type: String,
    override val event_id: String,
    override val sender: String,
    override val origin_server_ts: Long,
    override val unsigned: UnsignedData? = null,
    val state_key: String,
    /*val prev_content: EventContent?*/
    val content: T,
) : RoomEvent() {
    override fun toString() = "RoomNameEvent(" + raw_self.toString() + ")"
}
@Serializable
class RoomNameContent(val name: String)
@Serializable
class RoomCanonicalAliasContent(val alias: String? = null, val alt_aliases: List<String>? = null)

// RoomMessageEvent and RoomMessageEventContent should eventually be generic on message type
@Serializable
class RoomMessageEvent(
    override val raw_self: JsonObject,
    override val raw_content: JsonElement,
    override val type: String,
    override val event_id: String,
    override val sender: String,
    override val origin_server_ts: Long,
    override val unsigned: UnsignedData? = null,
    val content: RoomMessageEventContent
) : RoomEvent() {
    override fun toString() = "RoomMessageEvent(" + raw_self.toString() + ")"
}
@Serializable
class RoomMessageEventContent(val body: String, val msgtype: String)

@Serializable
class EventFallback(
    override val raw_self: JsonObject,
    override val raw_content: JsonElement,
    override val type: String
) : Event() {
    override fun toString() = "EventFallback(" + raw_self.toString() + ")"
}
@Serializable
class RoomEventFallback(
    override val raw_self: JsonObject,
    override val raw_content: JsonElement,
    override val type: String,
    override val event_id: String,
    override val sender: String,
    override val origin_server_ts: Long,
    override val unsigned: UnsignedData? = null
) : RoomEvent() {
    override fun toString() = "RoomEventFallback(" + raw_self.toString() + ")"
}

object EventSerializer : JsonContentPolymorphicSerializer<Event>(Event::class) {
    override fun selectDeserializer(element: JsonElement) = element.jsonObject["type"]!!.jsonPrimitive.content.let { type ->
        when {
            type == "m.room.message" -> RoomMessageEventSerializer
            type == "m.room.name" -> RoomNameStateEventSerializer
            type == "m.room.canonical_alias" -> RoomCanonicalAliasStateEventSerializer
            type.startsWith("m.room") -> RoomEventFallbackSerializer
            else -> EventFallbackSerializer
        }
    }
}

object EventFallbackSerializer : GenericJsonEventSerializer<EventFallback>(EventFallback.serializer())
object RoomEventFallbackSerializer : GenericJsonEventSerializer<RoomEventFallback>(RoomEventFallback.serializer())
object RoomMessageEventSerializer : GenericJsonEventSerializer<RoomMessageEvent>(RoomMessageEvent.serializer())
object RoomNameStateEventSerializer : GenericJsonEventSerializer<StateEvent<RoomNameContent>>(StateEvent.serializer(RoomNameContent.serializer()))
object RoomCanonicalAliasStateEventSerializer : GenericJsonEventSerializer<StateEvent<RoomCanonicalAliasContent>>(StateEvent.serializer(RoomCanonicalAliasContent.serializer()))

open class GenericJsonEventSerializer<T : Any>(clazz: KSerializer<T>) : JsonTransformingSerializer<T>(clazz) {
    override fun transformDeserialize(element: JsonElement): JsonElement = buildJsonObject {
        put("raw_self", element)
        put("raw_content", element.jsonObject["content"]!!)
        for ((key, value) in element.jsonObject) {
            put(key, value)
        }
    }
    override fun transformSerialize(element: JsonElement): JsonElement =
        element.jsonObject["raw_self"]!!
}
