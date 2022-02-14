package xyz.room409.serif.serif_compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread
import xyz.room409.serif.serif_shared.SharedUiMessage

actual object UiPlatform {
    actual fun getOpenUrl(): ((url: String) -> Unit)? = { url: String ->
        thread(start = true) {
            // We have to try using xdg-open first,
            // since PinePhone somehow implements the
            // Desktop API but has the same problem with the
            // GTK_BACKEND var
            try {
                println("Trying to open $url with exec 'xdg-open $url'")
                val pb = ProcessBuilder("xdg-open", url)
                // Somehow this environment variable gets set for pb
                // when it's NOT in System.getenv(). And of course, this
                // is the one that makes xdg-open try to launch an X version
                // of Firefox, giving the dreaded Firefox is already running
                // message if you've got a Wayland version running already.
                pb.environment().clear()
                pb.environment().putAll(System.getenv())
                pb.redirectErrorStream(true)
                val process = pb.start()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                while (reader.readLine() != null) {}
                process.waitFor()
                println("done trying to open url")
            } catch (e1: Exception) {
                try {
                    println("Trying to open $url with Desktop")
                    java.awt.Desktop.getDesktop().browse(java.net.URI(url))
                } catch (e2: Exception) {
                    println("Couldn't get ProcessBuilder('xdg-open $url') or Desktop, problem was $e1 then $e2")
                }
            }
        }
    }
}

@Composable actual fun DeletionDialog(message: SharedUiMessage, sendRedaction: (String)->Unit, close_deletion_dialog: () -> Unit): Unit {
        var reason by remember { mutableStateOf("") }
        Dialog(
            onCloseRequest = { close_deletion_dialog(); reason = "" },
            state = rememberDialogState(position = WindowPosition(Alignment.Center))
        ) {
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
/*
    */
