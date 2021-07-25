package xyz.room409.serif.serif_shared

import xyz.room409.serif.serif_shared.db.*
import xyz.room409.serif.serif_shared.db.DriverFactory
import xyz.room409.serif.serif_shared.db.SessionDb

import java.io.File

import kotlinx.serialization.*
import kotlinx.serialization.json.*

object Database {
    // See platform specific code in serif_shared for the
    // different implementations of DriverFactory and the
    // specific database drivers
    var db: SessionDb? = null
    fun initDb(driverFactory: DriverFactory) {
        this.db = SessionDb(driverFactory.createDriver())
    }
    fun transaction(f: () -> Unit) = this.db?.sessionDbQueries?.transaction{ f() }
    fun <T> transactionWithResult(f: () -> T) = this.db?.sessionDbQueries?.transaction{ f() }

    fun saveSession(username: String, access_token: String, transactionId: Long) {
        this.db?.sessionDbQueries?.insertSession(username, access_token, transactionId)
    }

    fun updateSessionTransactionId(session_id: Long, transactionId: Long) {
        this.db?.sessionDbQueries?.updateSessionTransactionId(transactionId, session_id)
    }

    fun updateSessionNextBatch(session_id: Long, nextBatch: String) {
        this.db?.sessionDbQueries?.updateSessionNextBatch(nextBatch, session_id)
    }
    fun getSessionNextBatch(session_id: Long): String? =
        this.db?.sessionDbQueries?.getSessionNextBatch(session_id)?.executeAsOneOrNull()?.nextBatch

    fun getStoredSessions(): List<Triple<String, String, Long>> {
        val saved_sessions = this.db?.sessionDbQueries?.selectAllSessions(
            { id: Long, user: String, auth_tok: String, nextBatch: String?, transactionId: Long ->
                Triple(user, auth_tok, transactionId)
            })?.executeAsList() ?: listOf()
        return saved_sessions
    }

    fun getUserSession(user: String): Triple<String, Pair<Long, String>, Long> {
        val saved_session = this.db?.sessionDbQueries?.selectUserSession(user) { id: Long, user: String, auth_tok: String, nextBatch: String?, transactionId: Long ->
            Triple(user, Pair(id, auth_tok), transactionId)
        }?.executeAsOne() ?: Triple("", Pair(0L,""), 0L)
        return saved_session
    }

    fun deleteAllSessions() {
        this.db?.sessionDbQueries?.deleteAllSessions()
    }

    fun getMediaInCache(url: String): String? {
        return this.db?.sessionDbQueries?.selectCachedMedia(url) { _: String, localPath: String ->
            localPath
        }?.executeAsOneOrNull()
    }

    fun addMediaToCache(url: String, file_data: ByteArray, update: Boolean): String {
        val cache_path = File(System.getProperty("user.dir") + "/cache/")
        cache_path.mkdirs()
        val file = File.createTempFile("serif_media_", "", cache_path)
        file.outputStream().write(file_data)
        val local = file.toPath().toString()
        if (update) {
            this.db?.sessionDbQueries?.updateMedia(local, url)
        } else {
            this.db?.sessionDbQueries?.insertMedia(url, local)
        }
        return local
    }

    fun <T> mapRooms(session_id: Long, f: (String,String,Int,Int,String?) -> T): List<T> = (this.db?.sessionDbQueries?.getRooms(session_id)?.executeAsList() ?: listOf()).map { r ->
        f(r.id, r.name, r.unread_notif_count.toInt(), r.unread_highlight_count.toInt(), r.last_event)
    }

    fun getUserProfileFromCache(sender: String): Pair<String?,String?>? {
        return this.db?.sessionDbQueries?.selectCachedContact(sender) { _: String, displayName: String?, avatarUrl: String?  ->
            Pair(displayName, avatarUrl)
        }?.executeAsOneOrNull()
    }

    fun addUserProfileToCache(sender: String, displayname: String?, avatar_url: String?, update: Boolean) {
        if (update) {
            this.db?.sessionDbQueries?.updateContact(displayname, avatar_url, sender)
        } else {
            this.db?.sessionDbQueries?.insertContact(sender, displayname, avatar_url)
        }
    }

    // setter that doesn't overwrite if passed null, if doesn't exist using default
    fun setRoomSummary(session_id: Long, id: String, name: String?, unread_notif_count: Int?, unread_highlight_count: Int?, last_event: String?) {
        val old = this.db?.sessionDbQueries?.getRoom(session_id, id)?.executeAsOneOrNull()
        if (old != null) {
            this.db?.sessionDbQueries?.updateRoomSummary(
                name ?: old.name,
                unread_notif_count?.toLong() ?: old?.unread_notif_count ?: 0,
                unread_highlight_count?.toLong() ?: old?.unread_highlight_count ?: 0,
                last_event ?: old?.last_event,
                session_id,
                id
            )
        } else {
            this.db?.sessionDbQueries?.insertRoomSummary(
                session_id,
                id,
                name ?: id,
                unread_notif_count?.toLong() ?: 0,
                unread_highlight_count?.toLong() ?: 0,
                last_event
            )
        }
    }
    fun getRoomSummary(session_id: Long, id: String) = this.db?.sessionDbQueries?.getRoom(session_id, id)?.executeAsOneOrNull()?.let {
        Triple(it.name,Pair(it.unread_notif_count.toInt(), it.unread_highlight_count.toInt()), it.last_event)
    }

    fun setStateEvent(session_id: Long, roomId: String, event: StateEvent<*>) {
        if (getStateEvent(session_id, roomId, event.type, event.state_key) != null) {
            this.db?.sessionDbQueries?.updateStateEvent(event.raw_self.toString(), session_id, roomId, event.type, event.state_key)
        } else {
            this.db?.sessionDbQueries?.insertStateEvent(session_id, roomId, event.type, event.state_key, event.raw_self.toString())
        }
    }
    fun getStateEvent(session_id: Long, roomId: String, type: String, stateKey: String): Event? =
        this.db?.sessionDbQueries?.getStateEvent(session_id, roomId, type, stateKey)?.executeAsOneOrNull()?.let { JsonFormatHolder.jsonFormat.decodeFromString<Event>(it) }
    fun getStateEvents(session_id: Long, roomId: String, type: String): List<Pair<String,Event>> =
        this.db?.sessionDbQueries?.getStateEvents(session_id, roomId, type)?.executeAsList()?.map { Pair(it.stateKey, JsonFormatHolder.jsonFormat.decodeFromString<Event>(it.data)) } ?: listOf()

    fun updatePrevBatch(session_id: Long, roomId: String, eventId: String, prevBatch: String?) {
        this.db!!.sessionDbQueries!!.updatePrevBatch(prevBatch, session_id, roomId, eventId)
    }

    fun minId(): String = this.db?.sessionDbQueries?.minId()?.executeAsOneOrNull()?.MIN ?: "z"
    fun maxId(): String = this.db?.sessionDbQueries?.maxId()?.executeAsOneOrNull()?.MAX ?: "a"
    fun maxIdLessThan(seqId: String): String = this.db?.sessionDbQueries?.maxIdLessThan(seqId)?.executeAsOneOrNull()?.MAX ?: "a"

    fun addRoomEvent(seqId: String, sessionId: Long, roomId: String, event: RoomEvent, related_event: String?, prevBatch: String?) {
        this.db?.sessionDbQueries?.addRoomEvent(seqId, sessionId, roomId, event.event_id, event.raw_self.toString(), related_event, prevBatch)
    }
    fun replaceRoomEvent(newEvent:RoomEvent, roomId: String,sessionId:Long) {
        this.db?.sessionDbQueries?.replaceRoomEvent(newEvent.raw_self.toString(),roomId,newEvent.event_id,sessionId)
    }
    fun getRoomEventAndIdx(session_id: Long, roomId: String, eventId: String): Triple<RoomEvent, String, String?>? =
        this.db?.sessionDbQueries?.getRoomEventAndIdx(session_id, roomId, eventId)?.executeAsOneOrNull()?.let {
            Triple(JsonFormatHolder.jsonFormat.decodeFromString<Event>(it.data) as RoomEvent, it.seqId, it.prevBatch)
        }
    fun getRoomEventsBackwardsFromPoint(session_id: Long, roomId: String, point: String, number: Long): List<Triple<RoomEvent,String,String?>> =
        this.db?.sessionDbQueries?.getRoomEventsBackwardsFromPointReversed(session_id, roomId, point, number)?.executeAsList()?.map { Triple(JsonFormatHolder.jsonFormat.decodeFromString<Event>(it.data) as RoomEvent, it.seqId, it.prevBatch) }?.reversed() ?: listOf()
    fun getRoomEventsForwardsFromPoint(session_id: Long, roomId: String, point: String, number: Long): List<Triple<RoomEvent,String,String?>> =
        this.db?.sessionDbQueries?.getRoomEventsForwardsFromPoint(session_id, roomId, point, number)?.executeAsList()?.map { Triple(JsonFormatHolder.jsonFormat.decodeFromString<Event>(it.data) as RoomEvent, it.seqId, it.prevBatch) } ?: listOf()
    fun getMostRecentRoomEventAndIdx(session_id: Long, roomId: String): Triple<RoomEvent,String,String?>? =
        this.db?.sessionDbQueries?.getMostRecentRoomEvent(session_id, roomId)?.executeAsOneOrNull()?.let { Triple(JsonFormatHolder.jsonFormat.decodeFromString<Event>(it.data) as RoomEvent, it.seqId, it.prevBatch) }
    fun getRelatedEvents(session_id: Long, roomId: String, eventIds: List<String>): List<Pair<RoomEvent,String>> =
        this.db?.sessionDbQueries?.getRelatedEvents(session_id, roomId, eventIds)?.executeAsList()?.map { Pair(JsonFormatHolder.jsonFormat.decodeFromString<Event>(it.data) as RoomEvent, it.seqId) } ?: listOf()
}
