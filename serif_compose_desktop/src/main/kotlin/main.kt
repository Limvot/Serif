

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState

import kotlin.concurrent.thread
import kotlin.math.min

import xyz.room409.serif.serif_shared.*
import xyz.room409.serif.serif_shared.db.DriverFactory
import xyz.room409.serif.serif_compose.*
import xyz.room409.serif.serif_compose.theme.JetchatTheme

class FakeViewModel {
    val inter: MatrixInterface

    init {
        Database.initDb(DriverFactory())
        inter = MatrixInterface()
    }
    fun backgroundInvoke(f: () -> Unit) {
        //viewModelScope.launch(Dispatchers.IO) {
        thread {
            f()
        }
    }
    fun sendMessage(message: String) = backgroundInvoke(inter.sendMessage(message))
    fun navigateToRoom(id: String) = backgroundInvoke(inter.navigateToRoom(id))
    fun exitRoom() = backgroundInvoke(inter.exitRoom())
    fun bumpWindow(id: String?) = backgroundInvoke(inter.bumpWindow(id))
    val ourUserId: MutableState<String>
        get() = inter.ourUserId
    val messages: MutableState<List<SharedUiMessage>>
        get() = inter.messages
    val roomPath: MutableState<List<String>>
        get() = inter.roomPath
    val roomName: MutableState<String>
        get() = inter.roomName
}


fun main() = application {
    Window(
            onCloseRequest = ::exitApplication,
            title = "Compose for Desktop",
            state = rememberWindowState(width = 300.dp, height = 300.dp)
    ) {
        val count = remember { mutableStateOf(0) }
        val fakeViewModel = remember { FakeViewModel() }
        val scrollState = rememberLazyListState()
        JetchatTheme(false) {
            ConversationContent(
                bumpWindowBase = { idx -> fakeViewModel.bumpWindow(idx?.let { idx -> fakeViewModel.messages.value.reversed().let { messages -> messages[min(idx, messages.size-1)].id } }); },
                uiState = ConversationUiState(fakeViewModel.roomName.value, fakeViewModel.ourUserId.value, 0, fakeViewModel.messages.value.reversed()),
                sendMessage = { message -> fakeViewModel.sendMessage(message); },
                navigateToRoom = { room -> fakeViewModel.navigateToRoom(room); },
                exitRoom = { fakeViewModel.exitRoom(); },
                navigateToProfile = { user -> println("clicked on user $user"); },
                onNavIconPressed = { println("Pressed nav icon..."); },
            )
        }
    }
}


