/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.room409.serif.serif_android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

import xyz.room409.serif.serif_shared.*
import xyz.room409.serif.serif_shared.db.DriverFactory

/**
 * Used to communicate between screens.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private var m: MatrixState
    private val _messages: MutableStateFlow<List<SharedUiMessage>> = MutableStateFlow(listOf())
    val messages: StateFlow<List<SharedUiMessage>> = _messages
    private val _roomPath: MutableStateFlow<List<String>> = MutableStateFlow(listOf())
    val roomPath: StateFlow<List<String>> = _roomPath
    init {
        // small hack
        if (Database.db == null) {
            Database.initDb(DriverFactory(application))
        }
        Platform.context = application
        m = MatrixLogin()
        val on_refresh: () -> Unit = {
            m = m.refresh()
            refresh()
        }
        when (val _m = m) {
            is MatrixLogin -> {
                val sessions = _m.getSessions()
                if (sessions.size > 0) {
                    m = _m.loginFromSession(sessions[0], on_refresh)
                } else {
                    m = _m.login("testuser", "keyboardcowpeople", on_refresh)
                }
                refresh()
            }
            else -> {
                status_message("Tried to login from not Matrixlogin, impossible")
            }
        }
    }
    fun refresh() {
        when (val _m = m) {
            is MatrixLogin -> {
                status_message(_m.login_message)
            }
            is MatrixChatRoom -> {
                _messages.value = _m.messages
                _roomPath.value = _m.room_ids
            }
        }
    }
    fun status_message(message: String) {
        _messages.value = listOf(SharedUiMessagePlain("System Status",message,"c",1,mapOf(),null))
    }
    fun sendMessage(message: String) {
        when (val _m = m) {
            is MatrixChatRoom -> {
                _m.sendMessage(message)
            }
            else -> {
                status_message("Tried to send message not on a room")
            }
        }
    }
    fun navigateToRoom(id: String) {
        when (val _m = m) {
            is MatrixChatRoom -> {
                m = _m.getRoom(id)
                refresh()
            }
            else -> {
                status_message("Tried to navigate on not a chat room")
            }
        }
    }
    fun exitRoom() {
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

    private val _drawerShouldBeOpened = MutableStateFlow(false)
    val drawerShouldBeOpened: StateFlow<Boolean> = _drawerShouldBeOpened
    fun openDrawer() {
        _drawerShouldBeOpened.value = true
    }
    fun resetOpenDrawerAction() {
        _drawerShouldBeOpened.value = false
    }
}
