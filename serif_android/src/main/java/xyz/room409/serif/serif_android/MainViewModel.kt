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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

import xyz.room409.serif.serif_compose.*
import xyz.room409.serif.serif_shared.*
import xyz.room409.serif.serif_shared.db.DriverFactory
import java.util.*
import java.util.concurrent.locks.ReentrantLock

/**
 * Used to communicate between screens.
 */

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val inter: MatrixInterface

    private val _drawerShouldBeOpened = MutableStateFlow(false)
    val drawerShouldBeOpened: StateFlow<Boolean> = _drawerShouldBeOpened
    fun openDrawer() {
        _drawerShouldBeOpened.value = true
    }
    fun resetOpenDrawerAction() {
        _drawerShouldBeOpened.value = false
    }
    init {
        // small hack
        if (Database.db == null) {
            Database.initDb(DriverFactory(application))
        }
        Platform.context = application
        inter = MatrixInterface()
    }
    fun backgroundInvoke(f: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            f()
        }
    }
    fun login(server: String, username: String, password: String) = backgroundInvoke(inter.login(server, username, password))
    fun login(session: String) = backgroundInvoke(inter.login(session))
    fun sendMessage(message: String) = backgroundInvoke(inter.sendMessage(message))
    fun navigateToRoom(id: String) = backgroundInvoke(inter.navigateToRoom(id))
    fun exitRoom() = backgroundInvoke(inter.exitRoom())
    fun bumpWindow(id: String?) = backgroundInvoke(inter.bumpWindow(id))
    fun runInViewModel(f: (MatrixInterface) -> (() -> Unit)) = backgroundInvoke(f(inter))

    val ourUserId: MutableState<String>
        get() = inter.ourUserId
    val messages: MutableState<List<SharedUiMessage>>
        get() = inter.messages
    val roomPath: MutableState<List<String>>
        get() = inter.roomPath
    val roomName: MutableState<String>
        get() = inter.roomName
    val sessions: MutableState<List<String>>
        get() = inter.sessions
    val uistate: MutableState<UiScreenState>
        get() = inter.uistate
    val pinned: MutableState<List<String>>
        get() = inter.pinned
}
