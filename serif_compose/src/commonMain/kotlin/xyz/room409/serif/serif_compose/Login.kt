package xyz.room409.serif.serif_compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.ExperimentalMaterialApi
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Card
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.fillMaxWidth

@Composable
fun LoginContent(
    loginMethod: (String, String, String) -> Unit, 
    sessionLogin: (String) -> Unit, 
    sessions: List<String>,
    modifier: Modifier = Modifier
)
{
    var user_text by remember { mutableStateOf(TextFieldValue("")) }
    var password_text by remember { mutableStateOf(TextFieldValue("")) }
    val scrollState = rememberLazyListState()
    Surface(modifier = Modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.height(74.dp))

                // Scrollable list of sessions
                Text(text = "Current Sessions")
                SessionTiles(
                    sessions = sessions,
                    scrollState = scrollState,
                    sessionLogin = sessionLogin, 
                )

                Divider(modifier = Modifier.padding(32.dp))

                // Login fields 
                Text(text = "Login")
                OutlinedTextField(
                    value = user_text,
                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                    label = { Text(text = "Username") },
                    placeholder = { Text(text = "AcidBurn") },
                    onValueChange = { user_text = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
                OutlinedTextField(
                    value = password_text,
                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                    label = { Text(text = "Password") },
                    placeholder = { Text(text = "super_secret_password") },
                    visualTransformation = PasswordVisualTransformation(),
                    onValueChange = { password_text = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Button(
                    onClick = { loginMethod("https://synapse.room409.xyz",user_text.text,password_text.text)}, 
                    modifier = Modifier.padding(8.dp).fillMaxWidth()
                ) {
                    Text(text = "Login")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SessionTiles(
    sessions: List<String>,
    scrollState: LazyListState,
    sessionLogin: (String) -> Unit, 
    modifier: Modifier = Modifier
)
{
    Box(modifier = modifier) {
        LazyColumn(
            reverseLayout = true,
            state = scrollState,
            modifier = Modifier.fillMaxWidth()
        ) {
            for (s in sessions) {
                item(s) {
                    Card(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        backgroundColor = MaterialTheme.colors.primary,
                        shape = RoundedCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
                        onClick = { sessionLogin(s) }
                    ) {
                        Column {
                            Text(
                                text = s,
                                modifier = Modifier.padding(8.dp),
                                color = MaterialTheme.colors.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
