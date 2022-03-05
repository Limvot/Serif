package xyz.room409.serif.serif_compose

import androidx.compose.runtime.*
import xyz.room409.serif.serif_shared.SharedUiMessage

expect object UiPlatform {
    fun getOpenUrl(): ((url: String) -> Unit)?
}

@Composable expect fun DeletionDialog(message: SharedUiMessage, sendRedaction: (String)->Unit, close_deletion_dialog: () -> Unit): Unit

expect fun ShowSaveDialog(filename: String, save_location_callback: (String)->Unit): Unit
