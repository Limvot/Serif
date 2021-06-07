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

    fun saveSession(username: String, access_token: String, transactionId: Long) {
        this.db?.sessionDbQueries?.insertSession(username, access_token, transactionId)
    }

    fun updateSessionTransactionId(access_token: String, transactionId: Long) {
        this.db?.sessionDbQueries?.updateSessionTransactionId(transactionId, access_token)
    }

    fun updateSessionNextBatch(access_token: String, nextBatch: String) {
        this.db?.sessionDbQueries?.updateSessionNextBatch(nextBatch, access_token)
    }
    fun getSessionNextBatch(access_token: String): String? =
        this.db?.sessionDbQueries?.getSessionNextBatch(access_token)?.executeAsOneOrNull()?.nextBatch

    fun getStoredSessions(): List<Triple<String, String, Long>> {
        val saved_sessions = this.db?.sessionDbQueries?.selectAllSessions(
            { user: String, auth_tok: String, nextBatch: String?, transactionId: Long ->
                Triple(user, auth_tok, transactionId)
            })?.executeAsList() ?: listOf()
        return saved_sessions
    }

    fun getUserSession(user: String): Triple<String, String, Long> {
        val saved_session = this.db?.sessionDbQueries?.selectUserSession(user) { user: String, auth_tok: String, nextBatch: String?, transactionId: Long ->
            Triple(user, auth_tok, transactionId)
        }?.executeAsOne() ?: Triple("", "", 0L)
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

    fun <T> mapRooms(f: (String,String,Int,Int,RoomMessageEvent?) -> T): List<T> = (this.db?.sessionDbQueries?.getRooms()?.executeAsList() ?: listOf()).map { r ->
        f(r.id, r.name, r.unread_notif_count.toInt(), r.unread_highlight_count.toInt(), r.last_event?.let { JsonFormatHolder.jsonFormat.decodeFromString<Event>(it) as? RoomMessageEvent })
    }

    // setter that doesn't overwrite if passed null, if doesn't exist using default
    fun setRoomSummary(id: String, name: String?, unread_notif_count: Int?, unread_highlight_count: Int?, last_event: RoomMessageEvent?) {
        val old = this.db?.sessionDbQueries?.getRoom(id)?.executeAsOneOrNull()
        if (old != null) {
            this.db?.sessionDbQueries?.updateRoomSummary(
                name ?: old.name,
                unread_notif_count?.toLong() ?: old?.unread_notif_count ?: 0,
                unread_highlight_count?.toLong() ?: old?.unread_highlight_count ?: 0,
                last_event?.raw_self?.toString() ?: old?.last_event,
                id
            )
        } else {
            this.db?.sessionDbQueries?.insertRoomSummary(
                id,
                name ?: id,
                unread_notif_count?.toLong() ?: 0,
                unread_highlight_count?.toLong() ?: 0,
                last_event?.raw_self?.toString()
            )
        }
    }
    fun getRoomName(id: String) = this.db?.sessionDbQueries?.getRoom(id)?.executeAsOneOrNull()?.name

    fun setStateEvent(roomId: String, event: StateEvent<*>) {
        if (getStateEvent(roomId, event.type, event.state_key) != null) {
            this.db?.sessionDbQueries?.updateStateEvent(event.raw_self.toString(), roomId, event.type, event.state_key)
        } else {
            this.db?.sessionDbQueries?.insertStateEvent(roomId, event.type, event.state_key, event.raw_self.toString())
        }
    }
    fun getStateEvent(roomId: String, type: String, stateKey: String): Event? =
        this.db?.sessionDbQueries?.getStateEvent(roomId, type, stateKey)?.executeAsOneOrNull()?.let { JsonFormatHolder.jsonFormat.decodeFromString<Event>(it) }
    fun getStateEvents(roomId: String): List<Event> =
        this.db?.sessionDbQueries?.getStateEvents(roomId)?.executeAsList()?.map { JsonFormatHolder.jsonFormat.decodeFromString<Event>(it) } ?: listOf()

    fun getPrevBatch(roomId: String): String =
        this.db!!.sessionDbQueries!!.getPrevBatch(roomId).executeAsOne().prevBatch!!

    fun minId(): Long = this.db?.sessionDbQueries?.minId()?.executeAsOneOrNull()?.MIN ?: 0

    fun addRoomEvent(seqId: Long?, roomId: String, event: RoomEvent, related_event: String?, prevBatch: String?) {
        this.db?.sessionDbQueries?.addRoomEvent(seqId, roomId, event.event_id, event.raw_self.toString(), related_event, prevBatch)
    }
    fun getRoomEventAndIdx(roomId: String, eventId: String): Pair<Event, Long>? =
        this.db?.sessionDbQueries?.getRoomEventAndIdx(roomId, eventId)?.executeAsOneOrNull()?.let {
            Pair(JsonFormatHolder.jsonFormat.decodeFromString<Event>(it.data), it.seqId)
        }
    fun getRoomEventsBackwardsFromPoint(roomId: String, point: Long, number: Long): List<Pair<Event,Long>> =
        this.db?.sessionDbQueries?.getRoomEventsBackwardsFromPointReversed(roomId, point, number)?.executeAsList()?.map { Pair(JsonFormatHolder.jsonFormat.decodeFromString<Event>(it.data), it.seqId) }?.reversed() ?: listOf()
    fun getRoomEventsForwardsFromPoint(roomId: String, point: Long, number: Long): List<Pair<Event,Long>> =
        this.db?.sessionDbQueries?.getRoomEventsForwardsFromPoint(roomId, point, number)?.executeAsList()?.map { Pair(JsonFormatHolder.jsonFormat.decodeFromString<Event>(it.data), it.seqId) } ?: listOf()
    fun getMostRecentRoomEventAndIdx(roomId: String): Pair<Event,Long>? =
        this.db?.sessionDbQueries?.getMostRecentRoomEvent(roomId)?.executeAsOneOrNull()?.let { Pair(JsonFormatHolder.jsonFormat.decodeFromString<Event>(it.data), it.seqId) }
    fun getRelatedEvents(roomId: String, eventIds: List<String>): List<Pair<Event,Long>> =
        this.db?.sessionDbQueries?.getRelatedEvents(roomId, eventIds)?.executeAsList()?.map { Pair(JsonFormatHolder.jsonFormat.decodeFromString<Event>(it.data), it.seqId) } ?: listOf()
}
