package xyz.room409.serif.serif_shared

import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class LoginRequest(
    val type: String,
    val identifier: LoginIdentifier,
    val password: String,
    val initial_device_display_name: String
) {
    constructor(
        username: String,
        password: String
    ) : this(
        type = "m.login.password",
        identifier = LoginIdentifier(type = "m.id.user", user = username),
        password = password,
        initial_device_display_name = "Serif"
    )
}

@Serializable data class LoginIdentifier(val type: String, val user: String)

@Serializable data class CreateRoom(
    val preset: String,
    val name: String,
    val room_alias_name: String,
    val topic: String,
) {
    constructor(
        name: String,
        room_alias_name: String,
        topic: String
    ) : this(
        preset = "public_chat", // this should be a choice in enum of private_chat, trusted_private_chat and public_chat. Will rework.
        name = name,
        room_alias_name = room_alias_name,
        topic = topic,

    )
}

@Serializable
data class LoginResponse(val access_token: String, val user_id: String, val device_id: String)

@Serializable
data class AudioInfo(val duration: Int? = null, val size: Int, val mimetype: String)
@Serializable
data class ImageInfo(val h: Int? = 0, val mimetype: String, val size: Int, val w: Int? = 0)
@Serializable
data class VideoInfo(val duration: Int? = 0, val mimetype: String, val size: Int, val h: Int? = 0, val w: Int? = 0)
@Serializable
data class FileInfo(val mimetype: String, val size: Int)
@Serializable
data class MediaUploadResponse(val content_uri: String)
@Serializable
data class ProfileResponse(val displayname: String? = null, val avatar_url: String? = null)
@Serializable
data class EventIdResponse(val event_id: String)
@Serializable
data class UnreadNotifications(val highlight_count: Int? = null, val notification_count: Int? = null)

@Serializable data class SyncResponse(var next_batch: String, val rooms: Rooms? = null, val presence: Presence? = null)

@Serializable data class Rooms(val join: MutableMap<String, Room>)

@Serializable
data class Room(
    var timeline: Timeline,
    var state: State,
    val summary: RoomSummary,
    val ephemeral: Ephemeral,
    var unread_notifications: UnreadNotifications? = null
)

@Serializable
data class Presence(var events: List<Event> = listOf())

@Serializable
data class PresenceEvent(
    override val raw_self: JsonObject,
    override val raw_content: JsonElement,
    override val type: String = "m.presence",
    val sender: String,
    val content: PresenceEventContent
) : Event()

@Serializable
data class PresenceEventContent(
    val avatar_url: String? = null,
    val displayname: String? = null,
    val last_active_ago: Int? = null,
    val presence: PresenceState,
    val currently_active: Boolean? = false,
    val status_msg: String? = null
)

enum class PresenceState { online, offline, unavailable }

@Serializable
data class PresenceEventUpdate(val presence: PresenceState, val status_msg: String)

@Serializable data class Timeline(var events: List<Event>, var limited: Boolean = false, var prev_batch: String)

@Serializable data class State(var events: List<Event>)

@Serializable
data class RoomSummary(
    @SerialName("m.heroes") val heroes: List<String>? = null,
    @SerialName("m.joined_member_count") val joined_member_count: Long? = null,
    @SerialName("m.invited_member_count") val invited_member_count: Long? = null
)

@Serializable
data class Ephemeral(var events: List<EphemeralEvent>)
@Serializable
data class EphemeralEvent(
    val type: String = "m.typing",
    val content: EphemeralEventContent
)
@Serializable
data class EphemeralEventContent(val user_ids: List<String>? = null)
@Serializable
data class TypingStatusNotify(val typing: Boolean, val timeout: Int? = null)

@Serializable
data class BackfillResponse(
    val start: String,
    val end: String,
    val chunk: List<Event>? = null,
    val state: List<Event>? = null
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
data class UnsignedData(
    val age: Long? = null,
    val redacted_because: Event? =null,
    val transaction_id: String? = null
)

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
    override fun toString() = "StateEvent(" + raw_self.toString() + " (content: $content))"
}

@Serializable class PreviousRoom(
    val room_id: String,
    val event_id: String,
)
@Serializable class RoomCreationContent(
    val creator: String,
    @SerialName("m.federate") val federate: Boolean? = null,
    val room_version: String? = null,
    val predecessor: PreviousRoom? = null,
    val type: String? = null,
)
@Serializable class SpaceChildContent(
    val via: List<String>? = null,
    val order: String? = null
)
@Serializable class RoomNameContent(val name: String)
@Serializable class RoomTopicContent(val topic: String)
@Serializable class RoomAvatarContent(val url: String? = null)
@Serializable class FallbackContent()

@Serializable
class RoomCanonicalAliasContent(val alias: String? = null, val alt_aliases: List<String>? = null)
@Serializable
class RoomPinnedEventContent(val pinned: List<String>? = null)
@Serializable
class RoomMemberEventContent(val displayname: String? = null, val avatar_url: String? = null)

// RoomMessageEvent and RoomMessageEventContent should eventually be generic on message type
@Serializable
class RoomMessageEvent(
    override val raw_self: JsonObject,
    override val raw_content: JsonElement,
    val redacts: String?=null,
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
class ReactionRMEC(
    @SerialName("m.relates_to") val relates_to: RelationBlock
) : RoomMessageEventContent() {
    constructor(reaction: String, react_to: String) : this (
        relates_to = RelationBlock(in_reply_to=null, rel_type="m.annotation", event_id=react_to, key=reaction)
    )
    override val body: String
        get() = "<reaction: ${relates_to.key}>"
    override val msgtype: String
        get() = "m.reaction"
}
@Serializable
class RedactionRMEC(
        override val body: String = "<missing message body, likely redacted>",
        override val msgtype: String = "<missing type, likely redacted>",
        val reason: String = "no reason given"
) : RoomMessageEventContent()
@Serializable
class RedactionBody(
    val reason : String
)
@Serializable
class TextRMEC(
    override val body: String = "<missing message body, likely redacted>",
    override val msgtype: String = "<missing type, likely redacted>",
    @SerialName("m.new_content") val new_content: TextRMEC? = null,
    @SerialName("m.relates_to") val relates_to: RelationBlock? = null,
    val format: String? = null,
    val formatted_body: String? = null
) : RoomMessageEventContent() {
    constructor(body: String, rel_to: RelationBlock?) : this(msgtype = "m.text", body = body, relates_to = rel_to)
    constructor(body: String, rel_to: RelationBlock?, formatted_body: String?) : this(
        msgtype = "m.text",
        body = body,
        relates_to = rel_to,
        format = if(formatted_body != null) { "org.matrix.custom.html" } else { null },
        formatted_body = formatted_body
    )
    constructor(msg: String, fallback: String, original_event: String) : this(
        msgtype = "m.text",
        body = fallback,
        new_content = TextRMEC(msg, "m.text"),
        relates_to = RelationBlock(null, "m.replace", original_event)
    )
}
@Serializable
class ImageRMEC(
    override val body: String = "<missing message body, likely redacted>",
    override val msgtype: String = "<missing type, likely redacted>",
    val info: ImageInfo,
    val url: String,
    @SerialName("m.relates_to") val relates_to: RelationBlock? = null
) : RoomMessageEventContent()
@Serializable
class AudioRMEC(
    override val body: String = "<missing message body, likely redacted>",
    override val msgtype: String = "<missing type, likely redacted>",
    val info: AudioInfo,
    val url: String,
    @SerialName("m.relates_to") val relates_to: RelationBlock? = null
) : RoomMessageEventContent()
@Serializable
class VideoRMEC(
    override val body: String = "<missing message body, likely redacted>",
    override val msgtype: String = "<missing type, likely redacted>",
    val info: VideoInfo,
    val url: String,
    @SerialName("m.relates_to") val relates_to: RelationBlock? = null
) : RoomMessageEventContent()
@Serializable
class FileRMEC(
    override val body: String = "<missing message body, likely redacted>",
    override val msgtype: String = "<missing type, likely redacted>",
    val info: FileInfo,
    val filename: String = "",
    val url: String,
    @SerialName("m.relates_to") val relates_to: RelationBlock? = null
) : RoomMessageEventContent()
@Serializable
class LocationRMEC(
    override val body: String = "<missing message body, likely redacted>",
    override val msgtype: String = "<missing type, likely redacted>",
    val geo_uri: String,
    @SerialName("m.relates_to") val relates_to: RelationBlock? = null
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
    val key: String? = null,
)

object EventSerializer : JsonContentPolymorphicSerializer<Event>(Event::class) {
    override fun selectDeserializer(element: JsonElement) =
        element.jsonObject["type"]!!.jsonPrimitive.content.let { type ->
            when {
                type == "m.room.create" -> RoomCreationEventSerializer
                type == "m.room.message" || type == "m.reaction" || type =="m.room.redaction"-> RoomMessageEventSerializer
                type == "m.room.name" -> RoomNameStateEventSerializer
                type == "m.room.topic" -> RoomTopicStateEventSerializer
                type == "m.room.avatar" -> RoomAvatarStateEventSerializer
                type == "m.room.canonical_alias" -> RoomCanonicalAliasStateEventSerializer
                type == "m.space.child" -> SpaceChildStateEventSerializer
                type == "m.room.pinned_events" -> RoomPinnedEventSerializer
                type == "m.room.member" -> RoomMemberEventSerializer //TODO: Make a member serializer
                type == "m.presence" -> PresenceEventSerializer
                element.jsonObject["state_key"] != null -> StateEventFallbackSerializer
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
            type == "m.video" -> VideoRMEC.serializer()
            type == "m.file" -> FileRMEC.serializer()
            type == "m.location" -> LocationRMEC.serializer()
            type == null && element.jsonObject["m.relates_to"]?.jsonObject?.get("rel_type")?.jsonPrimitive?.content == "m.annotation" -> ReactionRMEC.serializer()
            type == null && element.jsonObject["reason"] !=null -> RedactionRMEC.serializer()
            else -> FallbackRMEC.serializer()
        }
    }
}

object EventFallbackSerializer : GenericJsonEventSerializer<EventFallback>(EventFallback.serializer())
object RoomEventFallbackSerializer : GenericJsonEventSerializer<RoomEventFallback>(RoomEventFallback.serializer())
object RoomMessageEventSerializer : GenericJsonEventSerializer<RoomMessageEvent>(RoomMessageEvent.serializer())
object RoomCreationEventSerializer : GenericJsonEventSerializer<StateEvent<RoomCreationContent>>(StateEvent.serializer(RoomCreationContent.serializer()))
object RoomPinnedEventSerializer : GenericJsonEventSerializer<StateEvent<RoomPinnedEventContent>>(StateEvent.serializer(RoomPinnedEventContent.serializer()))
object PresenceEventSerializer : GenericJsonEventSerializer<PresenceEvent>(PresenceEvent.serializer())
object RoomMemberEventSerializer : GenericJsonEventSerializer<StateEvent<RoomMemberEventContent>>(StateEvent.serializer(RoomMemberEventContent.serializer()))
object RoomNameStateEventSerializer : GenericJsonEventSerializer<StateEvent<RoomNameContent>>(StateEvent.serializer(RoomNameContent.serializer()))
object RoomTopicStateEventSerializer : GenericJsonEventSerializer<StateEvent<RoomTopicContent>>(StateEvent.serializer(RoomTopicContent.serializer()))
object RoomAvatarStateEventSerializer : GenericJsonEventSerializer<StateEvent<RoomAvatarContent>>(StateEvent.serializer(RoomAvatarContent.serializer()))
object RoomCanonicalAliasStateEventSerializer : GenericJsonEventSerializer<StateEvent<RoomCanonicalAliasContent>>(StateEvent.serializer(RoomCanonicalAliasContent.serializer()))
object SpaceChildStateEventSerializer : GenericJsonEventSerializer<StateEvent<SpaceChildContent>>(StateEvent.serializer(SpaceChildContent.serializer()))
object StateEventFallbackSerializer : GenericJsonEventSerializer<StateEvent<FallbackContent>>(StateEvent.serializer(FallbackContent.serializer()))

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
