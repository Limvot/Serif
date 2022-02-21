package xyz.room409.serif.serif_compose

//import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFrom
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.ui.ExperimentalComposeUiApi

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RoomInfoContent(
    uiState: ConversationUiState,
    runInViewModel: ((MatrixInterface) -> (() -> Unit)) -> Unit,
    modifier: Modifier = Modifier,
    uiInputModifier: Modifier = Modifier
)
{
    val onBackPressed = { -> runInViewModel { inter -> inter.exitRoom() } }
    var current_room_name by remember { mutableStateOf(uiState.channelName) }
    var current_room_topic by remember { mutableStateOf(uiState.roomTopic) }
    var room_name by remember { mutableStateOf(TextFieldValue(uiState.channelName)) }
    var room_topic by remember { mutableStateOf(TextFieldValue(uiState.roomTopic)) }
    Surface(modifier = Modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.height(74.dp))

                // List of Room Configuration Items
                Button( onClick = onBackPressed) { Text("Back") }
                Text(text = "Room Settings")

                Divider(modifier = Modifier.padding(32.dp))
                Text("[Placeholder for Room Icon]")
                Divider(modifier = Modifier.padding(32.dp))

                Text("Room Name")
                OutlinedTextField(
                    value = room_name,
                    singleLine = true,
                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                    label = { Text(text = current_room_name) },
                    placeholder = { Text(text = "Super Secret Club") },
                    onValueChange = { room_name = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
                Button(onClick = {
                    runInViewModel { inter -> 
                            //Don't allow empty room name
                            //Also only send out requests if the data has changed
                            if((room_name.text != "") && (room_name.text != current_room_name)) { inter.setRoomName(room_name.text) }
                            else { { -> } }
                    }
                }) { Text("Save") }
                Text("Room Topic")
                OutlinedTextField(
                    value = room_topic,
                    singleLine = true,
                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                    label = { Text(text = current_room_topic) },
                    placeholder = { Text(text = "Room Topic or Description") },
                    onValueChange = { room_topic = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
                Button(onClick = {
                    runInViewModel { inter -> 
                            //Only send out requests if the data has changed
                            if(room_topic.text != current_room_topic) { inter.setRoomTopic(room_topic.text) }
                            else { { -> } }
                    }
                }) { Text("Save") }

                Divider(modifier = Modifier.padding(16.dp))
                Text(
                    text = "Member list",
                    style = MaterialTheme.typography.caption
                )
                for(member in uiState.members) {
                    Text("[User Avatar] $member")
                }
            }
        }
    }
}

