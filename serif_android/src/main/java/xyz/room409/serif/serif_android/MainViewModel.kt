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
import java.util.*

/**
 * Used to communicate between screens.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private var m: MatrixState
    private var username: String? = null
    private var password: String? = null
    private val _ourUserId: MutableStateFlow<String> = MutableStateFlow("")
    val ourUserId: StateFlow<String> = _ourUserId
    private val _messages: MutableStateFlow<List<SharedUiMessage>> = MutableStateFlow(listOf())
    val messages: StateFlow<List<SharedUiMessage>> = _messages
    private val _roomPath: MutableStateFlow<List<String>> = MutableStateFlow(listOf())
    val roomPath: StateFlow<List<String>> = _roomPath
    private val _roomName: MutableStateFlow<String> = MutableStateFlow("<>")
    val roomName: StateFlow<String> = _roomName
    init {
        // small hack
        if (Database.db == null) {
            Database.initDb(DriverFactory(application))
        }
        Platform.context = application
        m = MatrixLogin()
        refresh()
    }
    fun refresh() {
        when (val _m = m) {
            is MatrixLogin -> {
                val milli = Date().getTime()
                var fake_messages: List<SharedUiMessage> = listOf(SharedUiMessagePlain("System Status",_m.login_message,"a",milli,mapOf(),null))
                fake_messages += _m.getSessions().map { SharedUiRoom("System Status", "Session: $it",it,milli,mapOf(),null, 0, 0, null) }
                if (username != null) {
                    fake_messages += listOf(SharedUiMessagePlain("You", username!!,"b",milli,mapOf(),null))
                }
                if (password != null) {
                    fake_messages += listOf(SharedUiMessagePlain("You", password!!,"c",milli,mapOf(),null))
                }
                _messages.value = fake_messages
                _roomPath.value = listOf()
                _roomName.value = "Login"
            }
            is MatrixChatRoom -> {
                _messages.value = _m.messages
                _roomPath.value = _m.room_ids
                _roomName.value = _m.name
                _ourUserId.value = _m.username
            }
        }
    }
    fun status_message(message: String) {
        _messages.value = listOf(SharedUiMessagePlain("System Status",message,"c",Date().getTime(),mapOf(),null))
    }
    fun sendMessage(message: String) {
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
    fun navigateToRoom(id: String) {
        when (val _m = m) {
            is MatrixChatRoom -> {
                m = _m.getRoom(id)
                refresh()
            }
            is MatrixLogin -> {
                if (_m.getSessions().contains(id)) {
                    m = _m.loginFromSession(id) {
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
