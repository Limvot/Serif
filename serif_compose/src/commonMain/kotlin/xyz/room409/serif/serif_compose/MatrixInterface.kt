
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
    private var username: String? = null
    private var password: String? = null
    val ourUserId: MutableState<String> = mutableStateOf("")
    val messages: MutableState<List<SharedUiMessage>> = mutableStateOf(listOf())
    val roomPath: MutableState<List<String>> = mutableStateOf(listOf())
    val roomName: MutableState<String> = mutableStateOf("<>")
    val lock = ReentrantLock()
    val actions: MutableList<Action> = mutableListOf()
    var already_a_background_thread_running = false
    var m: MatrixState = MatrixLogin()
    init {
        refresh()
    }
    fun refresh() {
        when (val _m = m) {
            is MatrixLogin -> {
                val milli = Date().getTime()
                var fake_messages: List<SharedUiMessage> = listOf(SharedUiMessagePlain("System Status", "System Status", null, _m.login_message, _m.login_message,"a", milli, mapOf(),null))
                fake_messages += _m.getSessions().map { SharedUiRoom("System Status", "System Status", null, "Session: $it", "Session: $it",it,milli,mapOf(),null, 0, 0, null) }
                if (username != null) {
                    fake_messages += listOf(SharedUiMessagePlain("You", "You", null, username!!, username!!,"b",milli,mapOf(),null))
                }
                if (password != null) {
                    fake_messages += listOf(SharedUiMessagePlain("You", "You", null, password!!, password!!,"c",milli,mapOf(),null))
                }
                messages.value = fake_messages
                roomPath.value = listOf()
                roomName.value = "Login"
            }
            is MatrixChatRoom -> {
                messages.value = _m.messages
                roomPath.value = _m.room_ids
                roomName.value = _m.name
                ourUserId.value = _m.username
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
                is MatrixLogin -> {
                    if (username == null) {
                        username = message
                    } else if (password == null) {
                        password = message
                        m = _m.login(username!!, password!!) {
                            m = m.refresh()
                            refresh()
                        }
                        username = null
                        password = null
                    }
                    refresh()
                }
            }
        }
    }
    fun navigateToRoom(id: String) = pushDo(Action.NavigateToRoom(id))
    fun exitRoom() = pushDo(Action.ExitRoom())
    fun bumpWindow(id: String?) = pushDo(Action.Refresh(50, id, 50))

    private fun pushDo(_a: Action): () -> Unit {
        println("Pushing $_a")
        lock.lock()
        try {
            when (_a) {
                is Action.NavigateToRoom -> { println("clearing actiosn b/c NavigateToRoom"); actions.clear(); }
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
                        m = _m.exitRoom()
                        refresh()
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
                    is MatrixLogin -> {
                        if (_m.getSessions().contains(a.id)) {
                            m = _m.loginFromSession(a.id) {
                                m = m.refresh()
                                refresh()
                            }
                            refresh()
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
    }
}
