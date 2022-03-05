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
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.File
import javax.swing.*
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

actual object AudioPlayer {
    var url = ""
    var audioPlayer_proc : Process? = null;
    actual fun loadAudio(audio_url: String) {
        if(audio_url != url) {
            // reload vlc process
            restart_vlc(audio_url);
            send_command("pause")

            //Update URL
            url = audio_url
        }
    }
    actual fun play() {
        send_command("pause")
    }
    actual fun stop() {
        send_command("stop")
    }
    actual fun isPlaying(cb: (Boolean) -> Unit) {
        thread(start = true) {
            var ret = false;
            if(audioPlayer_proc != null) {
                try {
                    val writer = BufferedWriter(OutputStreamWriter(audioPlayer_proc?.getOutputStream()))
                    val reader = BufferedReader(InputStreamReader(audioPlayer_proc?.getInputStream()))
                    /* NOTE: We can't just call readline on reader because vlc is not
                     * guaranteed to print out a new line in stdout while it is waiting
                     * for user input after the '>'. I wasn't able to get the input working
                     * using a cleaner method so for now we loop to clear out anything in
                     * the buffer from previous commands/startup, send the status command,
                     * and then read in the output.
                     *
                     * A better (future) solution would probably be to have vlc open up a local socket
                     * and talk to it over that.
                     */
                    //Toss out old stdout data
                    while(reader.ready()) {
                        if(reader.read().toChar() == '>')  break
                    }

                    //Send status command and wait for it to process
                    writer.write("status\n")
                    writer.flush()
                    Thread.sleep(5)

                    //read in status output
                    var status = ""
                    while(reader.ready()) {
                        val c = reader.read().toChar()
                        if(c == '>')  break
                        status = "$status$c"
                    }
                    val parts = status.trim().split('\n')
                    if(parts.size < 3) {
                        println("Didn't get back >= 3 status elements!")
                        println("STATUS: $status")
                        for(p in parts) {
                            println("Part: $p")
                        }
                        println("----")
                    } else {
                        var status_message = ""
                        //Get last status message
                        val rparts = parts.asReversed()
                        for(p in rparts) {
                            if(p.contains(" state ")) {
                                status_message = p.trim();
                                break
                            }
                        }
                        ret = (status_message == "( state playing )")
                    }
                } catch (e1: Exception) {
                    println("Excpetion in thread sending command to VLC, assuming playback is stopped.");
                }
                cb(ret);
            }
        }
    }
    actual fun getActiveUrl(): String {
        return url
    }
    fun restart_vlc(audio_url: String) {
        if(audioPlayer_proc != null) {
            send_command("stop");
            send_command("quit");
            audioPlayer_proc?.destroy()
            audioPlayer_proc = null
        }
        try {
                //println("Trying to open $audio_url with exec 'vlc -I rc $audio_url'")
                val pb = ProcessBuilder("vlc","-I","rc","--rc-fake-tty",audio_url)
                pb.redirectErrorStream(true)
                audioPlayer_proc = pb.start()
                //println("running vlc")
            } catch (e1: Exception) {
                println("Couldn't get ProcessBuilder('vlc -I rc --rc-fake-tty $audio_url') problem was $e1")
            }
    }
    fun send_command(cmd: String) {
        if(audioPlayer_proc != null) {
            // Send vlc command
            val writer = BufferedWriter(OutputStreamWriter(audioPlayer_proc?.getOutputStream()))
            //println("Sending command '$cmd' to vlc process")
            writer.write("$cmd\n")
            writer.flush()
        }
    }
}

actual fun ShowSaveDialog(filename: String, save_location_callback: (String)->Unit): Unit {
    val chooser = JFileChooser()
    chooser.setSelectedFile(File(filename))
    val ret = chooser.showSaveDialog(null)
    if(ret == JFileChooser.APPROVE_OPTION) {
        val user_file_path = chooser.getSelectedFile().getAbsolutePath()
        save_location_callback(user_file_path)
    }
}
