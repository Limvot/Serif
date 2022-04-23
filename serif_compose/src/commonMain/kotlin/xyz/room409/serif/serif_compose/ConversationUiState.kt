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

package xyz.room409.serif.serif_compose

import androidx.compose.runtime.mutableStateListOf
import xyz.room409.serif.serif_shared.SharedUiMessage

class ConversationUiState(
    val channelName: String,
    val encrypted: Boolean,
    val ourUserId: String,
    val channelMembers: Int,
    initialMessages: List<SharedUiMessage>,
    val pinned: List<String>,
    val members: List<String> = listOf(),
    val roomTopic: String = "",
    val roomAvatarUrl: String = ""
) {
    private val _messages: MutableList<SharedUiMessage> =
        mutableStateListOf(*initialMessages.toTypedArray())
    val messages: List<SharedUiMessage> = _messages
}

