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
                        "user": "testuser"
                    },
                    "password": "keyboardcowpeople",
                    "initial_device_display_name": "UsSerif"
                }
                """
            }
            val room_id = "!bwqkmRobBXpTSDiGIw:synapse.room409.xyz"
            val access_token = "MDAyMWxvY2F0aW9uIHN5bmFwc2Uucm9vbTQwOS54eXoKMDAxM2lkZW50aWZpZXIga2V5CjAwMTBjaWQgZ2VuID0gMQowMDMwY2lkIHVzZXJfaWQgPSBAdGVzdHVzZXI6c3luYXBzZS5yb29tNDA5Lnh5egowMDE2Y2lkIHR5cGUgPSBhY2Nlc3MKMDAyMWNpZCBub25jZSA9IGk6SzlNWk9mZlhqMUxtZWQKMDAyZnNpZ25hdHVyZSCc_KG-tenzmHYeQ07YBdsJiwyFYkUHx979z4A7fN7r3Ao"
            client.put<String>("https://synapse.room409.xyz/_matrix/client/r0/rooms/$room_id/send/m.room.message/2?access_token=$access_token") {
                body = """
                   {
                    "msgtype": "m.text",
                    "body": "Konnichiwa!asdfasdfasdfasdf"
                   }
                """
            }
            client.close()
            content
        }


        return "Hello, ${Platform().platform}, ya cowpeople! - $result"
    }
}
