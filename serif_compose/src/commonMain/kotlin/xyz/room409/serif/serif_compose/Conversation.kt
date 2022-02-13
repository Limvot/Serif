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

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFrom
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Button
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
//import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
//import xyz.room409.serif.serif_android.FunctionalityNotAvailablePopup
//import xyz.room409.serif.serif_android.R
//import xyz.room409.serif.serif_android.components.JetchatAppBar
//import xyz.room409.serif.serif_android.theme.elevatedSurface
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import xyz.room409.serif.serif_shared.SharedUiLocationMessage
import xyz.room409.serif.serif_shared.SharedUiImgMessage
import xyz.room409.serif.serif_shared.SharedUiAudioMessage
import xyz.room409.serif.serif_shared.SharedUiMessage
import xyz.room409.serif.serif_shared.SharedUiRoom
import java.io.File
import java.text.DateFormat
import java.util.*

abstract sealed class MessageSendType
class MstMessage() : MessageSendType()
data class MstReply(val msg: SharedUiMessage): MessageSendType()
data class MstEdit(val msg: SharedUiMessage): MessageSendType()
data class MstReaction(val msg: SharedUiMessage): MessageSendType()

/**
 * Entry point for a conversation screen.
 *
 * @param uiState [ConversationUiState] that contains messages to display
 * @param navigateToProfile User action when navigation to a profile is requested
 * @param modifier [Modifier] to apply to this layout node
 * @param onNavIconPressed Sends an event up when the user clicks on the menu
 */
@Composable
fun ConversationContent(
    uiState: ConversationUiState,
    bumpWindowBase: (Int?) -> Unit,
    runInViewModel: ((MatrixInterface) -> (() -> Unit)) -> Unit,
    navigateToProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    uiInputModifier: Modifier = Modifier,
    onNavIconPressed: () -> Unit = { },
) {

    val sendMessage = { message: String -> runInViewModel { inter -> inter.sendMessage(message) } }
    val sendReply = { message: String, eventid: String -> runInViewModel { inter -> inter.sendReply(message, eventid) } }
    val sendEdit = { message: String, eventid: String -> runInViewModel { inter -> inter.sendEdit(message, eventid) } }
    val sendReaction = { reaction: String, eventid: String -> runInViewModel { inter -> inter.sendReaction(reaction, eventid) } }
    val togglePinnedEvent = { event_id: String -> runInViewModel { inter -> inter.togglePinnedEvent(event_id) } }
    val sendRedaction = { eventid: String -> runInViewModel { inter -> inter.sendRedaction(eventid) } }
    val navigateToRoom = { id: String -> runInViewModel { inter -> inter.navigateToRoom(id) } }
    val exitRoom = { -> runInViewModel { inter -> inter.exitRoom() } }

    val scrollState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var msg_type : MutableState<MessageSendType> = remember { mutableStateOf(MstMessage()) }

    // Helper lambda to decide which message type we are trying to send
    val determine_message_send_callback = { message : String ->
        when(val _mt : MessageSendType = msg_type.value) {
            is MstMessage -> { sendMessage(message) }
            is MstReply -> { sendReply(message, _mt.msg.id) }
            is MstEdit -> { sendEdit(message, _mt.msg.id) }
            is MstReaction -> { sendReaction(message, _mt.msg.id) }
        }
        msg_type.value = MstMessage()
    }

    // Helper lambda to let popup change msg_type
    val change_message_type = { new_type : MessageSendType -> msg_type.value = new_type }

    Surface(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                // instead of using statusBarsPadding below
                // from accompinest, we just stick some in here
                Spacer(modifier = Modifier.height(74.dp))
                Messages(
                    messages = uiState.messages,
                    ourUserId = uiState.ourUserId,
                    bumpWindowBase = bumpWindowBase,
                    navigateToRoom = navigateToRoom,
                    navigateToProfile = navigateToProfile,
                    updateMsgType = change_message_type,
                    sendRedaction = sendRedaction,
                    sendReaction = sendReaction,
                    sendMessage = sendMessage,
                    togglePinnedEvent = togglePinnedEvent,
                    pinned = uiState.pinned,
                    modifier = Modifier.weight(1f),
                    scrollState = scrollState
                )
                when(val _mt : MessageSendType = msg_type.value) {
                    is MstMessage -> {  }
                    is MstReply -> {
                        MessageTypeContextBar(text = "Replying to: ${_mt.msg.message}", updateMsgType = change_message_type)
                    }
                    is MstEdit -> {
                        MessageTypeContextBar(text = "Editing: ${_mt.msg.message}", updateMsgType = change_message_type)
                    }
                    is MstReaction -> {
                        MessageTypeContextBar(text = "Reacting to: ${_mt.msg.message}", updateMsgType = change_message_type)
                    }
                }
                UserInput(
                    uiState.channelName,
                    determine_message_send_callback,
                    {
                        scope.launch {
                            scrollState.scrollToItem(0)
                        }
                    },
                    modifier = uiInputModifier
                )
            }
            // Channel name bar floats above the messages
            ChannelNameBar(
                channelName = uiState.channelName,
                channelMembers = uiState.channelMembers,
                onNavIconPressed = onNavIconPressed,
                onBackPressed = exitRoom,
                // Use statusBarsPadding() to move the app bar content below the status bar
                //modifier = Modifier.statusBarsPadding(),
            )
        }
    }

    // Shift our window
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.firstVisibleItemIndex }
            .map { index -> if (index > ((scrollState.layoutInfo.totalItemsCount * 3) / 4)) { 1 } else if (index < ((scrollState.layoutInfo.totalItemsCount * 1) / 4)) { -1 } else { 0 } }
            //.distinctUntilChanged()
            .filter { it != 0 }
            .collect {
                println("LOOKING TO BUMP $it - ${scrollState.firstVisibleItemIndex} / ${scrollState.layoutInfo.totalItemsCount}")
                bumpWindowBase(scrollState.firstVisibleItemIndex)
            }
    }
}

@Composable
fun MessageTypeContextBar(
    text: String,
    modifier: Modifier = Modifier,
    updateMsgType: (MessageSendType) -> Unit,
) {
    Divider()
    Row(modifier = Modifier.fillMaxWidth()) {
        Button( onClick = { updateMsgType(MstMessage()) }) {
            Text("Cancel")
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(text)
        }
    }
}

@Composable
fun ChannelNameBar(
    channelName: String,
    channelMembers: Int,
    modifier: Modifier = Modifier,
    onNavIconPressed: () -> Unit = { },
    onBackPressed: () -> Unit = { }
) {
    var functionalityNotAvailablePopupShown by remember { mutableStateOf(false) }
    if (functionalityNotAvailablePopupShown) {
        //FunctionalityNotAvailablePopup { functionalityNotAvailablePopupShown = false }
    }
    JetchatAppBar(
        modifier = modifier,
        onNavIconPressed = onNavIconPressed,
        title = {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Channel name
                Text(
                    text = channelName,
                    style = MaterialTheme.typography.subtitle1
                )
                // Number of members
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                    Text(
                        text = "members",
                        style = MaterialTheme.typography.caption
                    )
                }
            }
        },
        actions = {
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                Button( onClick = onBackPressed) {
                    Text("Back")
                }
                /*
                // Search icon
                Icon(
                    imageVector = Icons.Outlined.Search,
                    modifier = Modifier
                        .clickable(onClick = { functionalityNotAvailablePopupShown = true })
                        .padding(horizontal = 12.dp, vertical = 16.dp)
                        .height(24.dp),
                    contentDescription = stringResource(id = R.string.search)
                )
                // Info icon
                Icon(
                    imageVector = Icons.Outlined.Info,
                    modifier = Modifier
                        .clickable(onClick = { functionalityNotAvailablePopupShown = true })
                        .padding(horizontal = 12.dp, vertical = 16.dp)
                        .height(24.dp),
                    contentDescription = stringResource(id = R.string.info)
                )
                */
            }
        }
    )
}

const val ConversationTestTag = "ConversationTestTag"

@Composable
fun Messages(
    messages: List<SharedUiMessage>,
    ourUserId: String,
    bumpWindowBase: (Int?) -> Unit,
    navigateToRoom: (String) -> Unit,
    navigateToProfile: (String) -> Unit,
    updateMsgType: (MessageSendType) -> Unit,
    sendRedaction: (String) -> Unit,
    sendReaction: (String,String) -> Unit,
    sendMessage: (String) -> Unit,
    togglePinnedEvent: (String) -> Unit,
    pinned: List<String>,
    scrollState: LazyListState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    Box(modifier = modifier) {

        LazyColumn(
            reverseLayout = true,
            state = scrollState,
            // Add content padding so that the content can be scrolled (y-axis)
            // below the status bar + app bar
            // TODO: Get height from somewhere
            /*
            contentPadding = rememberInsetsPaddingValues(
                insets = LocalWindowInsets.current.statusBars,
                additionalTop = 90.dp
            ),
            */
            modifier = Modifier
                .testTag(ConversationTestTag)
                .fillMaxSize()
        ) {
            for (index in messages.indices) {
            //itemsIndexed(
            //    items = messages,
            //    key = {i,x -> x.id}
            //) { index, content ->
                val prevAuthor = messages.getOrNull(index - 1)?.sender
                val nextAuthor = messages.getOrNull(index + 1)?.sender
                val content = messages[index]
                val isFirstMessageByAuthor = prevAuthor != content.sender
                val isLastMessageByAuthor = nextAuthor != content.sender

                val df = DateFormat.getDateInstance()
                val prevTimestamp = messages.getOrNull(index-1)?.timestamp?.let {
                    df.format(Date(it))
                }

                if (prevTimestamp != null) {
                    val newTimestamp = df.format(Date(content.timestamp))
                    if (prevTimestamp != newTimestamp) {
                        item(content.id + prevTimestamp) {
                            DayHeader(prevTimestamp)
                        }
                    }
                }


                item(content.id) {
                    Message(
                        onRoomClick = navigateToRoom,
                        onAuthorClick = { name -> navigateToProfile(name) },
                        updateMsgType = updateMsgType,
                        sendRedaction = sendRedaction,
                        sendReaction = sendReaction,
                        sendMessage = sendMessage,
                        togglePinnedEvent = togglePinnedEvent,
                        pinned = pinned,
                        msg = content,
                        ourUserId = ourUserId,
                        isUserMe = content.sender == ourUserId,
                        isFirstMessageByAuthor = isFirstMessageByAuthor,
                        isLastMessageByAuthor = isLastMessageByAuthor
                    )
                }
            }
        }
        // Jump to bottom button shows up when user scrolls past a threshold.
        // Convert to pixels:
        val jumpThreshold = with(LocalDensity.current) {
            JumpToBottomThreshold.toPx()
        }

        // Show the button if the first visible item is not the first one or if the offset is
        // greater than the threshold.
        val jumpToBottomButtonEnabled by remember {
            derivedStateOf {
                scrollState.firstVisibleItemIndex != 0 ||
                    scrollState.firstVisibleItemScrollOffset > jumpThreshold
            }
        }

        JumpToBottom(
            // Only show if the scroller is not at the bottom
            enabled = jumpToBottomButtonEnabled,
            onClicked = {
                scope.launch {
                    bumpWindowBase(null)
                    scrollState.animateScrollToItem(0)
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun Message(
    onRoomClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
    updateMsgType: (MessageSendType) -> Unit,
    sendRedaction: (String) -> Unit,
    sendReaction: (String,String) -> Unit,
    sendMessage: (String) -> Unit,
    togglePinnedEvent: (String) -> Unit,
    pinned: List<String>,
    msg: SharedUiMessage,
    ourUserId: String,
    isUserMe: Boolean,
    isFirstMessageByAuthor: Boolean,
    isLastMessageByAuthor: Boolean
) {
    val borderColor = if (isUserMe) {
        MaterialTheme.colors.primary
    } else {
        MaterialTheme.colors.secondary
    }

    val spaceBetweenAuthors = if (isLastMessageByAuthor) Modifier.padding(top = 8.dp) else Modifier
    Row(modifier = spaceBetweenAuthors) {
        if (isLastMessageByAuthor) {
            // Avatar
            // Spacer instead of avatar :/
            Surface(color = Color(msg.sender.hashCode()), shape = CircleShape) {
                Spacer(modifier = Modifier.width(74.dp).height(74.dp))
            }
            /*
            Image(
                modifier = Modifier
                    .clickable(onClick = { onAuthorClick(msg.sender) })
                    .padding(horizontal = 16.dp)
                    .size(42.dp)
                    .border(1.5.dp, borderColor, CircleShape)
                    .border(3.dp, MaterialTheme.colors.surface, CircleShape)
                    .clip(CircleShape)
                    .align(Alignment.Top),
                //painter = painterResource(id = R.drawable.someone_else),
                contentScale = ContentScale.Crop,
                contentDescription = null,
            )
            */
        } else {
            // Space under avatar
            Spacer(modifier = Modifier.width(74.dp))
        }
        AuthorAndTextMessage(
            msg = msg,
            isFirstMessageByAuthor = isFirstMessageByAuthor,
            isLastMessageByAuthor = isLastMessageByAuthor,
            roomClicked = onRoomClick,
            authorClicked = onAuthorClick,
            isUserMe = isUserMe,
            updateMsgType = updateMsgType,
            sendRedaction = sendRedaction,
            ourUserId = ourUserId,
            sendReaction = sendReaction,
            sendMessage = sendMessage,
            togglePinnedEvent = togglePinnedEvent,
            pinned = pinned,
            modifier = Modifier
                .padding(end = 16.dp)
                .weight(1f)
        )
    }
}

@Composable
fun AuthorAndTextMessage(
    msg: SharedUiMessage,
    isFirstMessageByAuthor: Boolean,
    isLastMessageByAuthor: Boolean,
    roomClicked: (String) -> Unit,
    authorClicked: (String) -> Unit,
    isUserMe: Boolean,
    updateMsgType: (MessageSendType) -> Unit,
    sendRedaction: (String) -> Unit,
    ourUserId: String,
    sendReaction: (String,String) -> Unit,
    sendMessage: (String) -> Unit,
    togglePinnedEvent: (String) -> Unit,
    pinned: List<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (isLastMessageByAuthor) {
            AuthorNameTimestamp(msg)
        }
        ChatItemBubble(msg,
                isFirstMessageByAuthor,
                roomClicked = roomClicked,
                authorClicked = authorClicked,
                updateMsgType = updateMsgType,
                sendRedaction = sendRedaction,
                togglePinnedEvent = togglePinnedEvent,
                pinned = pinned,
                isUserMe = isUserMe,
                sendMessage = sendMessage)
        if (isFirstMessageByAuthor) {
            // Last bubble before next author
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            // Between bubbles
            Spacer(modifier = Modifier.height(4.dp))
        }
        MessageReactions(msg, modifier, ourUserId, sendReaction, sendRedaction)
    }
}

@Composable
private fun AuthorNameTimestamp(msg: SharedUiMessage) {
    // Combine author and timestamp for a11y.
    Row(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = msg.displayname ?: msg.sender,
            style = MaterialTheme.typography.subtitle1,
            modifier = Modifier
                .alignBy(LastBaseline)
                .paddingFrom(LastBaseline, after = 8.dp) // Space to 1st bubble
        )
        Spacer(modifier = Modifier.width(8.dp))
        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
            Text(
                text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(msg.timestamp)),
                style = MaterialTheme.typography.caption,
                modifier = Modifier.alignBy(LastBaseline)
            )
        }
    }
}

@Composable
private fun MessageReactions(msg: SharedUiMessage, modifier: Modifier, ourUserId: String, sendReaction: (String,String) -> Unit, sendRedaction: (String) -> Unit) {
    val backgroundBubbleColor =
        if (MaterialTheme.colors.isLight) {
            Color(0xFFF5F5F5)
        } else {
            Color(0x22222222)
        }
    val bubbleShape = ChatBubbleShape
    if (msg.reactions.size > 0 ) {
        Spacer(modifier = Modifier.height(2.dp))
        //This message has reaction, render them all
        Row(modifier = Modifier) {
            for((reaction,senders) in msg.reactions.entries) {
                Surface(color = backgroundBubbleColor, shape = bubbleShape) {
                    val reaction_text =
                    if(senders.size > 1) {
                        "${reaction} ${senders.size}"
                    } else {
                        "${reaction}"
                    }
                    ClickableText(
                        text = AnnotatedString(reaction_text),
                        style = MaterialTheme.typography.body1.copy(color = LocalContentColor.current),
                        //modifier = Modifier.padding(8.dp),
                        onClick = {
                            val sender_ids = senders.map { it.sender }.toSet()
                            if(!sender_ids.contains(ourUserId)) {
                                //Send reaction message
                                sendReaction(reaction, msg.id)
                            } else {
                                //Redact the reaction event we previously sent
                                senders.forEach {
                                    if(it.sender == ourUserId) {
                                        sendRedaction(it.event_id)
                                    }
                                }
                            }
                        }
                    )
                }
                //Space between reaction bubbles
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
        //Space after row of reactions
        Spacer(modifier = Modifier.height(4.dp))
    }
}

private val ChatBubbleShape = RoundedCornerShape(0.dp, 8.dp, 8.dp, 0.dp)
private val LastChatBubbleShape = RoundedCornerShape(0.dp, 8.dp, 8.dp, 8.dp)

@Composable
fun DayHeader(dayString: String) {
    Row(
        modifier = Modifier
            .padding(vertical = 8.dp, horizontal = 16.dp)
            .height(16.dp)
    ) {
        DayHeaderLine()
        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
            Text(
                text = dayString,
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.overline
            )
        }
        DayHeaderLine()
    }
}

@Composable
private fun RowScope.DayHeaderLine() {
    Divider(
        modifier = Modifier
            .weight(1f)
            .align(Alignment.CenterVertically),
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
    )
}

@Composable
fun ChatItemBubble(
    message: SharedUiMessage,
    lastMessageByAuthor: Boolean,
    roomClicked: (String) -> Unit,
    authorClicked: (String) -> Unit,
    togglePinnedEvent: (String) -> Unit,
    updateMsgType: (MessageSendType) -> Unit,
    sendMessage: (String) -> Unit,
    pinned: List<String>,
    sendRedaction: (String) -> Unit,
    isUserMe: Boolean
) {

    val uriHandler = LocalUriHandler.current
    val backgroundBubbleColor =
        if (MaterialTheme.colors.isLight) {
            Color(0xFFF5F5F5)
        } else {
            Color(0x22222222)
            //MaterialTheme.colors.elevatedSurface(2.dp)
        }

    var show_deletion_dialog by remember { mutableStateOf(false) }
    if(show_deletion_dialog) {
        DeletionDialog(message, sendRedaction, { show_deletion_dialog = false })
    }

    var show_menu by remember { mutableStateOf(false) }
    DropdownMenu(
        expanded = show_menu,
        onDismissRequest = {show_menu = false}
    ) {
        DropdownMenuItem(
            onClick = { updateMsgType(MstReply(message)); show_menu = false }
        ) {
            Text("Reply")
        }
        if(isUserMe) {
            DropdownMenuItem(
                onClick = { updateMsgType(MstEdit(message)); show_menu = false }
            ) {
                Text("Edit")
            }
        }
        DropdownMenuItem(
            onClick = { updateMsgType(MstReaction(message)); show_menu = false }
        ) {
            Text("Reaction")
        }
        DropdownMenuItem(
            onClick = { togglePinnedEvent(message.id); show_menu = false }
        ) {
            if(pinned.contains(message.id)) {
                Text("Unpin Message")
            } else {
                Text("Pin Message")
            }
        }
        //TODO(marcus): Implement deletion logic
        DropdownMenuItem(
            onClick = {
                show_deletion_dialog = true
                show_menu = false
            }
        ) {
            Text("Delete")
        }
        Divider()
        //TODO(marcus): Implement show source logic
        DropdownMenuItem(
            onClick = {
                println("Viewing Message Source")
                show_menu = false
            }
        ) {
            Text("View Source")
        }
    }

    /* NOTE(marcus): The API is still being worked on for clickable callbacks,
     * in particular right click handling which only really makes sense on desktop.
     * For now we can just use some of the click events provided for combinedClickable,
     * and we can add right click support later.
     */
    val bubbleShape = if (lastMessageByAuthor) LastChatBubbleShape else ChatBubbleShape
    @OptIn(ExperimentalFoundationApi::class)
    Box(
        modifier = Modifier.combinedClickable(
            onClick = {
                println("NORMAL Press for message ${message.id}: ${message.message}")
            },
            onDoubleClick = {
                println("DOUBLE Press for message ${message.id}: ${message.message}")
                show_menu = true
            },
            onLongClick = {
                println("LONG Press for message ${message.id}: ${message.message}")
                show_menu = true
            }
        ).background(Color(0x22222222))
    )
    {
        Column {
            if (message is SharedUiImgMessage) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(color = backgroundBubbleColor, shape = bubbleShape) {
                    Spacer(modifier = Modifier.width(74.dp).height(74.dp))
                    /*
                    Image(
                        painter = rememberImagePainter(File(message.url)),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(160.dp),
                        contentDescription = message.message
                    )
                    */
                }
            } else if (message is SharedUiLocationMessage) {
                val styledMessage = messageFormatter(text = message.message)
                val parts = message.location.split(",")
                val lat = parts[0].replace("geo:","")
                val lon = parts[1]
                val href = "https://maps.google.com/?q=$lat,$lon"
                Spacer(modifier = Modifier.height(4.dp))
                Surface(color = backgroundBubbleColor, shape = bubbleShape) {
                    ClickableText(
                        text = styledMessage,
                        style = MaterialTheme.typography.body1.copy(color = LocalContentColor.current),
                        modifier = Modifier.padding(8.dp),
                        onClick = {
                            val other_opener = UiPlatform.getOpenUrl()
                            if (other_opener != null) {
                                other_opener(href)
                            } else {
                                uriHandler.openUri(href)
                            }
                        }
                    )
                }
            } else if (message is SharedUiAudioMessage) {
                val audio_title = message.message
                val audio_url = message.url
                var isPlaying by remember { mutableStateOf((AudioPlayer.isPlaying() && (audio_url == AudioPlayer.getActiveUrl()))) }
                var msg_button_text = mutableStateOf(if(isPlaying) { "Pause" } else { "Play" })
                Spacer(modifier = Modifier.height(4.dp))
                Surface(color = backgroundBubbleColor, shape = bubbleShape) {
                    Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                        Text("${audio_title}\n${audio_url}")
                        Button(onClick = {
                            AudioPlayer.loadAudio(audio_url)
                            AudioPlayer.play()
                            isPlaying = !isPlaying
                            msg_button_text.value = if(isPlaying) { "Pause" } else { "Play" }
                        }) {
                            Text(msg_button_text.value)
                        }
                    }
                }
            } else {
                Surface(color = backgroundBubbleColor, shape = bubbleShape) {
                    if(isTelegramPollMessage(message)) {
                        //Telegram Poll message
                        TelegramPollMessage(
                            message = message,
                            sendMessage = sendMessage
                        )
                    } else {
                        //Normal Text message
                        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                            if(message.replied_event != null) {
                                val parent = message.replied_event!!
                                val text = if(parent.message.length > 80) {
                                    "${parent.message.take(80)}..."
                                } else {
                                    parent.message
                                }
                                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Divider(modifier = Modifier.fillMaxHeight().width(8.dp).background(Color(0x44444444)))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    ClickableText(text = AnnotatedString(text),
                                             style = MaterialTheme.typography.body1.copy(color = LocalContentColor.current),
                                             modifier = Modifier.padding(8.dp),
                                             onClick = {}
                                         )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Divider(modifier = Modifier.height(2.dp))
                            }
                        }
                        ClickableMessage(
                            message = message,
                            roomClicked = roomClicked,
                            authorClicked = authorClicked
                        )
                    }
                }
            }
        }
    }
}

fun isTelegramPollMessage(message: SharedUiMessage): Boolean {
    var isPoll = true
    if(message.formatted_message != null) {
        //Message needs to have a !tg voting code and it has to not be in a reply
        isPoll = message.formatted_message!!.contains("Vote with <code>!tg vote") && !(message.formatted_message!!.contains("<mx-reply>"))
    } else {
        isPoll = false
    }
    return isPoll
}

val tg_code_rgx = Regex("<code>(.*?) &.*</code>")
val tg_option_rgx = Regex("<li>(.*?)</li>")
val title_opt_rgx = Regex("""<br/>""")
val opt_code_rgx = Regex("""</ol>""")
@Composable
fun TelegramPollMessage(message: SharedUiMessage, sendMessage: (String) -> Unit) {
    val msg = message.formatted_message!!
    val (title, rest) = msg.split(title_opt_rgx)
    val (opts, code) = rest.split(opt_code_rgx)
    val options = tg_option_rgx.findAll(opts)!!
    val code_link = tg_code_rgx.find(code)!!.destructured.toList()[0]
    Column {
        Text(title)
        options.forEachIndexed {
        i, opt ->
            val idx = i+1
            val str = opt.destructured.toList()[0]
            Button(onClick = { sendMessage("$code_link $idx"); println("Clicked $idx ${str}") } ) {
                Text("$idx. ${str}")
            }
        }
        Text("\nOr\nVote with $code_link <choice number>")
    }
}

@Composable
fun ClickableMessage(message: SharedUiMessage, roomClicked: (String) -> Unit, authorClicked: (String) -> Unit) {
    val uriHandler = LocalUriHandler.current

    val styledMessage = messageFormatter(text = message.message)

    ClickableText(
        text = styledMessage,
        style = MaterialTheme.typography.body1.copy(color = LocalContentColor.current),
        modifier = Modifier.padding(8.dp),
        onClick = {
            if (message is SharedUiRoom) {
                roomClicked(message.id)
            } else {
                styledMessage
                    .getStringAnnotations(start = it, end = it)
                    .firstOrNull()
                    ?.let { annotation ->
                        when (annotation.tag) {
                            SymbolAnnotationType.LINK.name -> {
                                val other_opener = UiPlatform.getOpenUrl()
                                if (other_opener != null) {
                                    other_opener(annotation.item)
                                } else {
                                    uriHandler.openUri(annotation.item)
                                }
                            }
                            SymbolAnnotationType.PERSON.name -> authorClicked(annotation.item)
                            else -> Unit
                        }
                    }
            }
        }
    )
}

private val JumpToBottomThreshold = 56.dp

private fun ScrollState.atBottom(): Boolean = value == 0
