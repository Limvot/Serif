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
    init {
        // small hack
        if (Database.db == null) {
            Database.initDb(DriverFactory(application))
        }
        m = MatrixLogin()
        var messageOne = "nothing"
        when (val _m = m) {
            is MatrixLogin -> {
                m = _m.login("<>>", "<>>") {
                    m = m.refresh()
                    when (val _m = m) {
                        is MatrixLogin -> {
                            _messages.value = listOf(
                                SharedUiMessagePlain("a",_m.login_message + " cont","c",1,mapOf(),null),
                            )
                        }
                        is MatrixChatRoom -> {
                            _messages.value = _m.messages;
                        }
                    }
                }
            }
            else -> {
                messageOne = "hmmm2"
            }
        }
        var messageTwo = "nothing"
        when (val _m = m) {
            is MatrixChatRoom -> {
                _messages.value = _m.messages;
            }
            is MatrixLogin -> {
                messageTwo = _m.login_message
            }
            else -> {
                messageTwo = "hmmmm2"
            }
        }
        _messages.value = listOf(
            SharedUiMessagePlain("a",messageOne,"c",1,mapOf(),null),
            SharedUiMessagePlain("a",messageTwo,"c",1,mapOf(),null),
        )
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
