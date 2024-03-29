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

package xyz.room409.serif.serif_android.conversation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import com.google.accompanist.insets.*
import xyz.room409.serif.serif_android.MainViewModel
import xyz.room409.serif.serif_android.R

import xyz.room409.serif.serif_compose.*
import xyz.room409.serif.serif_compose.theme.JetchatTheme

import kotlin.math.min

class ConversationFragment : Fragment() {

    private val activityViewModel: MainViewModel by activityViewModels()

    @OptIn(ExperimentalAnimatedInsets::class) // Opt-in to experiment animated insets support
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(inflater.context).apply {
        layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)

        // Create a ViewWindowInsetObserver using this view, and call start() to
        // start listening now. The WindowInsets instance is returned, allowing us to
        // provide it to AmbientWindowInsets in our content below.
        val windowInsets = ViewWindowInsetObserver(this)
            // We use the `windowInsetsAnimationsEnabled` parameter to enable animated
            // insets support. This allows our `ConversationContent` to animate with the
            // on-screen keyboard (IME) as it enters/exits the screen.
            .start(windowInsetsAnimationsEnabled = true)

        setContent {
            CompositionLocalProvider(
                //LocalBackPressedDispatcher provides requireActivity().onBackPressedDispatcher,
                LocalWindowInsets provides windowInsets,
            ) {
                JetchatTheme(false) {
                    val roomName by activityViewModel.roomName
                    val ourUserId by activityViewModel.ourUserId
                    val messages by activityViewModel.messages
                    val pinned by activityViewModel.pinned
                    ConversationContent(
                        bumpWindowBase = { idx -> activityViewModel.bumpWindow(idx?.let { idx -> activityViewModel.messages.value.reversed().let { messages -> messages[min(idx, messages.size-1)].id } }); },
                        uiState = ConversationUiState(roomName, ourUserId, 0, messages.reversed(), pinned),
                        runInViewModel = { activityViewModel.runInViewModel(it) },
                        navigateToProfile = { user ->
                            // Click callback
                            val bundle = bundleOf("userId" to user)
                            findNavController().navigate(
                                R.id.nav_profile,
                                bundle
                            )
                        },
                        onNavIconPressed = {
                            activityViewModel.openDrawer()
                        },
                        // Add padding so that we are inset from any left/right navigation bars
                        // (usually shown when in landscape orientation)
                        modifier = Modifier.navigationBarsPadding(bottom = false),
                        // Use navigationBarsWithImePadding(), to move the input panel above both the
                        // navigation bar, and on-screen keyboard (IME)
                        uiInputModifier = Modifier.navigationBarsWithImePadding(),
                    )
                }
            }
        }
    }
}
