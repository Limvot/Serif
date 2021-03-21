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
data class LoginResponse(val access_token: String, val identifier: LoginIdentifier)

@Serializable
data class SendRoomMessage(
    val msgtype: String,
    val body: String,
    @SerialName("m.new_content") val new_content: TextRMEC? = null,
    @SerialName("m.relates_to") val relates_to: RelationBlock? = null
) {
    constructor(body: String) : this(msgtype = "m.text", body = body)
    constructor(body: String, rel_to: RelationBlock?) : this(msgtype = "m.text", body = body, relates_to = rel_to)
    constructor(msg: String, fallback: String, original_event: String) : this(
        msgtype="m.text",
        body=fallback,
        new_content=TextRMEC(msg,"m.text"),
        relates_to=RelationBlock(null,"m.replace",original_event)
    )
}

@Serializable
data class AudioInfo(val duration: Int? = null, val size: Int, val mimetype: String)
@Serializable
data class ImageInfo(val h: Int? = 0, val mimetype: String, val size: Int, val w: Int? = 0)
@Serializable
data class SendRoomImageMessage(val msgtype: String, val body: String, val info: ImageInfo, val url: String) {
    constructor(body: String, info: ImageInfo, url: String) : this(
        msgtype = "m.image",
        body = body,
        info = info,
        url = url
    )
}
@Serializable
data class MediaUploadResponse(val content_uri: String)
@Serializable
data class EventIdResponse(val event_id: String)
@Serializable
data class UnreadNotifications(val highlight_count: Int? = null, val notification_count: Int? = null)
@Serializable
data class SyncResponse(var next_batch: String, val rooms: Rooms)
@Serializable
data class Rooms(val join: MutableMap<String, Room>)
@Serializable
data class Room(var timeline: Timeline, var state: State, val summary: RoomSummary, var unread_notifications: UnreadNotifications? = null)

@Serializable
data class Timeline(var events: List<Event>, var prev_batch: String)
@Serializable
data class State(var events: List<Event>)
@Serializable
data class RoomSummary(
    @SerialName("m.heroes") val heroes: List<String>? = null,
    @SerialName("m.joined_member_count") val joined_member_count: Long? = null,
    @SerialName("m.invited_member_count") val invited_member_count: Long? = null
)

@Serializable
data class BackfillResponse(val start: String, val end: String, val chunk: List<Event>? = null, val state: List<Event>? = null)

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
@Serializable(with = RoomMessageEventContentSerializer::class)
abstract class RoomMessageEventContent {
    abstract val body: String
    abstract val msgtype: String
}
@Serializable
class TextRMEC(
    override val body: String = "<missing message body, likely redacted>",
    override val msgtype: String = "<missing type, likely redacted>",
    @SerialName("m.new_content") val new_content: TextRMEC? = null,
    @SerialName("m.relates_to") val relates_to: RelationBlock? = null
) : RoomMessageEventContent()
@Serializable
class ImageRMEC(
    override val body: String = "<missing message body, likely redacted>",
    override val msgtype: String = "<missing type, likely redacted>",
    val info: ImageInfo,
    val url: String
) : RoomMessageEventContent()
@Serializable
class AudioRMEC(
    override val body: String = "<missing message body, likely redacted>",
    override val msgtype: String = "<missing type, likely redacted>",
    val info: AudioInfo,
    val url: String
) : RoomMessageEventContent()
@Serializable
class FallbackRMEC(
    override val body: String = "<missing message body, likely redacted>",
    override val msgtype: String = "<missing type, likely redacted>",
) : RoomMessageEventContent()

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

@Serializable
class ReplyToRelation(val event_id: String)
@Serializable
class RelationBlock(
@SerialName("m.in_reply_to") val in_reply_to: ReplyToRelation? = null,
val rel_type: String? = null,
val event_id: String? = null,
)

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

object RoomMessageEventContentSerializer : JsonContentPolymorphicSerializer<RoomMessageEventContent>(RoomMessageEventContent::class) {
    override fun selectDeserializer(element: JsonElement) = element.jsonObject["msgtype"]?.jsonPrimitive?.content.let { type ->
        when {
            type == "m.text" -> TextRMEC.serializer()
            type == "m.image" -> ImageRMEC.serializer()
            type == "m.audio" -> AudioRMEC.serializer()
            else -> FallbackRMEC.serializer()
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
