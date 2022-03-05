
package xyz.room409.serif.serif_compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import xyz.room409.serif.serif_shared.*
import java.util.*
import java.util.concurrent.locks.ReentrantLock

/**
 * Used to communicate between screens.
 */

class MatrixInterface {
    val ourUserId: MutableState<String> = mutableStateOf("")
    val messages: MutableState<List<SharedUiMessage>> = mutableStateOf(listOf())
    val roomPath: MutableState<List<String>> = mutableStateOf(listOf())
    val roomName: MutableState<String> = mutableStateOf("<>")
    val roomTopic: MutableState<String> = mutableStateOf("")
    val sessions: MutableState<List<String>> = mutableStateOf(listOf())
    val pinned: MutableState<List<String>> = mutableStateOf(listOf())
    val members: MutableState<List<String>> = mutableStateOf(listOf())
    val avatar: MutableState<String> = mutableStateOf("")
    val lock = ReentrantLock()
    val actions: MutableList<Action> = mutableListOf()
    var already_a_background_thread_running = false
    var m: MatrixState = MatrixLogin()
    var uistate: MutableState<UiScreenState> = mutableStateOf(UiLogin())
    init {
        refresh()
    }
    fun refresh() {
        when (val _m = m) {
            is MatrixLogin -> {
                messages.value = listOf()
                roomPath.value = listOf()
                roomName.value = "Login"
                roomTopic.value = ""
                sessions.value = _m.getSessions()
                pinned.value = listOf()
                members.value = listOf()
                avatar.value = ""
            }
            is MatrixChatRoom -> {
                messages.value = _m.messages
                roomPath.value = _m.room_ids
                roomName.value = _m.name
                roomTopic.value = _m.roomTopic
                ourUserId.value = _m.username
                sessions.value = listOf()
                pinned.value = _m.pinned
                members.value = _m.members
                avatar.value = _m.avatar
            }
        }
    }
    fun status_message(message: String) {
        messages.value = listOf(SharedUiMessagePlain("System Status", "System Status", null,message, message,"c",Date().getTime(),mapOf(),null))
    }
    fun sendMessage(message: String): () -> Unit {
        return { ->
            when (val _m = m) {
                is MatrixChatRoom -> {
                    _m.sendMessage(message)
                }
            }
        }
    }
    fun login(session: String): () -> Unit {
        return { ->
            when(val _m = m) {
                is MatrixLogin -> {
                    m = _m.loginFromSession(session) {
                        m = m.refresh()
                        refresh()
                    }
                    if(m !is MatrixLogin) { uistate.value = UiChatRoom() }
                    else if(m is MatrixLogin) { uistate.value = UiLogin((m as MatrixLogin).login_message) }
                    refresh()
                }
            }
        }
    }
    fun login(server: String, username: String, password: String): () -> Unit {
        return { ->
            when(val _m = m) {
                is MatrixLogin -> {
                    m = _m.login(username, password) {
                        m = m.refresh()
                        refresh()
                    }
                    if(m !is MatrixLogin) { uistate.value = UiChatRoom() }
                    else if(m is MatrixLogin) { uistate.value = UiLogin((m as MatrixLogin).login_message) }
                    refresh()
                }
            }
        }
    }
    fun sendReply(message: String, eventid: String): () -> Unit {
        return { ->
            val _m = m
            if (_m is MatrixChatRoom) { _m.sendReply(message, eventid) }
        }
    }
    fun sendEdit(message: String, eventid: String): () -> Unit {
        return { ->
            val _m = m
            if (_m is MatrixChatRoom) { _m.sendEdit(message, eventid) }
        }
    }
    fun sendReaction(message: String, eventid: String): () -> Unit {
        return { ->
            val _m = m
            if (_m is MatrixChatRoom) { _m.sendReaction(message, eventid) }
        }
    }
    fun togglePinnedEvent(event_id: String): () -> Unit {
        return { ->
            val _m = m
            if (_m is MatrixChatRoom) { _m.togglePinnedEvent(event_id) }
        }
    }
    fun sendRedaction(msgid: String): () -> Unit {
        return { ->
            val _m = m
            if (_m is MatrixChatRoom) { _m.sendRedaction(msgid) }
        }
    }
    fun setRoomTopic(topic: String): () -> Unit {
        return { ->
            val _m = m
            if (_m is MatrixChatRoom) { _m.setRoomTopic(topic) }
        }
    }
    fun setRoomName(name: String): () -> Unit {
        return { ->
            val _m = m
            if (_m is MatrixChatRoom) { _m.setRoomName(name) }
        }
    }
    fun navigateToRoom(id: String) = pushDo(Action.NavigateToRoom(id))
    fun navigateToRoomInfo() = pushDo(Action.NavigateToRoomInfo())
    fun exitRoom() = pushDo(Action.ExitRoom())
    fun bumpWindow(id: String?) = pushDo(Action.Refresh(50, id, 50))

    private fun pushDo(_a: Action): () -> Unit {
        println("Pushing $_a")
        lock.lock()
        try {
            when (_a) {
                is Action.NavigateToRoom -> { println("clearing actiosn b/c NavigateToRoom"); actions.clear(); }
                is Action.NavigateToRoomInfo -> { println("clearing actions b/c NavigateToRoomInfo"); actions.clear(); }
                is Action.ExitRoom -> { println("clearing actiosn b/c ExitRoom"); actions.clear(); }
                is Action.Refresh -> {
                    if (actions.size != 0) {
                        println("There are actions in the backlog, not doing Refresh")
                        return { -> }
                    }
                }
            }
            actions.add(_a)
            if (already_a_background_thread_running) {
                return { -> }
            }
        } finally { lock.unlock() }
        return { ->
            lock.lock()
            already_a_background_thread_running = true
            while (actions.size > 0) {
                val this_action = actions.removeFirst()
                lock.unlock()
                println("EXECUTING $this_action")
                execute(this_action)
                lock.lock()
            }
            already_a_background_thread_running = false
            lock.unlock()
        }
    }
    private fun execute(_a: Action) {
        when (val a = _a) {
            is Action.Refresh -> {
                when (val _m = m) {
                    is MatrixChatRoom -> {
                        //m = _m.refresh(_m.window_back_length, id, _m.window_forward_length)
                        println("doing a refresh to $a")
                        m = _m.refresh(a.window_back, a.base_id, a.window_forward)
                        refresh()
                    }
                    else -> {
                        status_message("Tried to exit on not a chat room")
                    }
                }
            }
            is Action.ExitRoom -> {
                when (val _m = m) {
                    is MatrixChatRoom -> {
                        if(uistate.value is UiRoomInfo) {
                            uistate.value = UiChatRoom()
                        } else {
                            m = _m.exitRoom()
                            refresh()
                            if(m is MatrixLogin) { uistate.value = UiLogin() }
                        }
                    }
                    else -> {
                        status_message("Tried to exit on not a chat room")
                    }
                }
            }
            is Action.NavigateToRoom -> {
                when (val _m = m) {
                    is MatrixChatRoom -> {
                        m = _m.getRoom(a.id)
                        refresh()
                    }
                    else -> {
                        status_message("Tried to navigate on not a chat room")
                    }
                }
            }
            is Action.NavigateToRoomInfo -> {
                when (val _m = m) {
                    is MatrixChatRoom -> {
                        if(_m.room_type == "m.space") {
                            status_message("Tried to navigate to room info when not in a room")
                        } else {
                            uistate.value = UiRoomInfo()
                        }
                    }
                    else -> {
                        status_message("Tried to navigate on not a chat room")
                    }
                }
            }
        }
    }

    sealed class Action() {
        data class Refresh(val window_back: Int, val base_id: String?, val window_forward: Int): Action()
        data class ExitRoom(val v:Int = 1): Action()
        data class NavigateToRoom(val id: String): Action()
        data class NavigateToRoomInfo(val unused:Int = 1): Action()
    }
}

sealed class UiScreenState()
class UiLogin(val message: String = ""): UiScreenState()
class UiChatRoom(): UiScreenState()
class UiRoomList(): UiScreenState()
class UiConfig(): UiScreenState()
class UiRoomInfo(): UiScreenState()
class UiUserInfo(): UiScreenState()
class UiRoomCreate(): UiScreenState()
class UiInvitation(): UiScreenState()
