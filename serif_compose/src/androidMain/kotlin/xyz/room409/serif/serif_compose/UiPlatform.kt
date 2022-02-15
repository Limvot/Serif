package xyz.room409.serif.serif_compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Button
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import xyz.room409.serif.serif_shared.SharedUiMessage

actual object UiPlatform {
    actual fun getOpenUrl(): ((url: String) -> Unit)? = null
}

@Composable actual fun DeletionDialog(message: SharedUiMessage, sendRedaction: (String)->Unit, close_deletion_dialog: () -> Unit): Unit {
    var reason by remember { mutableStateOf("") }
    Dialog(
        onDismissRequest = { close_deletion_dialog(); reason = "" },
    ) {
        Surface() {
            Column() {
                Text("Deleting: ${message.message}")
                Text("Reason:")
                TextField(
                    value = reason,
                    onValueChange = { reason = it })
                Row() {
                    Button(onClick = { sendRedaction(message.id); close_deletion_dialog(); reason = "" }) {
                        Text("Confirm")
                    }
                    Button(onClick = { close_deletion_dialog(); reason = "" }) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

actual fun ShowSaveDialog(filename: String, save_location_callback: (String)->Unit): Unit {
    //TODO: implement this with native android file chooser
    return
}
