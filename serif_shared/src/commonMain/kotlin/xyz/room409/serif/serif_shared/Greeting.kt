package xyz.room409.serif.serif_shared
import kotlinx.coroutines.*
import io.ktor.client.*
import io.ktor.client.request.*


class Greeting {
    fun greeting(): String {

        val result = runBlocking {
            val client = HttpClient()
            val content: String = client.post<String>("https://synapse.room409.xyz/_matrix/client/r0/login") {
                body = """
                {
                    "type": "m.login.password",
                    "identifier": {
                        "type": "m.id.user",
                        "user": "<redacted>"
                    },
                    "password": "<redacted>",
                    "initial_device_display_name": "UsSerif"
                }
                """
            }
            client.close()
            content
        }


        return "Hello, ${Platform().platform}, ya cowpeople! - $result"
    }
}
