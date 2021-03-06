/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package xyz.room409.serif.serif_cli
import xyz.room409.serif.serif_shared.*
import xyz.room409.serif.serif_shared.db.DriverFactory
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

class App {
    val console = System.console()
    val queue = LinkedBlockingQueue<String>()
    var mstate: MatrixState = MatrixLogin()
    val version: String
        get() {
            return mstate.version + ", CLI UI"
        }
    fun run() {
        if (console == null) {
            println()
            println("Console was null! Probs running under gradle.")
            println("It doesn't work, and is really irritating")
            println("Try ./run_dist.sh if you're on a Unixy OS")
            println("Otherwise, if you're on Windows, you'll have to manually")
            println("do ./gradlew :serif_cli:assembleDist and then find and")
            println("extract the distribution zip, and then run the batch file")
            println("in the bin folder. Probs best to automate the process")
            println("like run_dist.sh, but I'm not on Windows and can't")
            println("a batch script without a reference. My apologies.")
            println("-Nathan")
            println()
            return
        }

        // Each UI will create it's specific DriverFactory
        // And call this function before the backend can get
        // information out of the database
        Database.initDb(DriverFactory())

        val getInputToQueue: () -> Unit = { thread(start = true) { queue.add(console.readLine()) }; }

        while (true) {
            // We do this little dance so that Kotlin smart cast works and
            // doesn't complain that it might change between the when
            // and the use in the branches.
            mstate = when (val m = mstate) {
                is MatrixLogin -> {
                    print(m.login_message)
                    val onSync: () -> Unit = { println("there was a new sync"); queue.add(":refresh"); }
                    val login_prompt = { ->
                        print("Username: ")
                        val username = console.readLine()
                        print("Password: ")
                        val password = String(console.readPassword())
                        clearScreen()
                        println("Logging in with username |$username| and a password I won't print...")
                        m.login(username, password, onSync)
                    }

                    val sessions = m.getSessions()
                    for ((idx, u) in sessions.withIndex()) {
                        println("$idx: $u")
                    }
                    if (sessions.size == 0) {
                        login_prompt()
                    } else {
                        print("Input a session number, or :n to login normally> ")
                        val resp = console.readLine()
                        if (resp != ":n") {
                            val i = resp.toIntOrNull()
                            if (i != null && i in (0..sessions.size - 1)) {
                                clearScreen()
                                println("Logging in with saved session for |${sessions[i]}| ...")
                                m.loginFromSession(sessions[i], onSync)
                            } else {
                                println("invalid choice $resp, try again")
                                m
                            }
                        } else {
                            login_prompt()
                        }
                    }
                }
                is MatrixRooms -> {
                    println(m.message)
                    m.rooms.forEachIndexed { i, room ->
                        println("$i - ${room.id} - ${room.name}- ${room.unreadCount}, ${room.highlightCount}")
                    }
                    print("Input a room number, :refresh, or :q> ")
                    getInputToQueue()
                    val msg = queue.take()
                    if (msg == ":refresh") {
                        m.refresh()
                    } else if (msg == ":q") {
                        m.fake_logout()
                    } else {
                        val selection = msg.toIntOrNull()
                        if (selection != null && selection >= 0 && selection < m.rooms.size) {
                            m.getRoom(m.rooms[selection].id)
                        } else {
                            println("Bad number $msg, try again")
                            m
                        }
                    }
                }
                is MatrixChatRoom -> {
                    printRoom(m.messages.takeLast(20), m.name)
                    print("Message (or :b for back, :refresh for refresh)> ")
                    getInputToQueue()
                    val msg = queue.take()
                    if (msg == ":b") {
                        m.exitRoom()
                    } else if (msg == ":refresh") {
                        m.refresh()
                    } else {
                        m.sendMessage(msg)
                    }
                }
            }
        }
    }

    fun clearScreen() {
        // Print out ANSI escape code for clearing the screen
        val esc = 27.toChar()
        print("$esc[H$esc[2J")
    }
    fun printRoom(messages: List<SharedUiMessage>, room_id: String) {
        // Start Fresh
        this.clearScreen()

        // Print Header
        println("==$room_id==")

        // print last 20 messages or so
        val maxSenderLen = messages.map { message -> message.sender.length }.max()
        messages.forEach { message ->
            println("${message.sender.padEnd(maxSenderLen!!, ' ')}: ${message.message}")
        }
    }
}

fun main(args: Array<String>) {
    var app = App()
    println("Welcome to " + app.version)
    app.run()
    println("Exiting!")
}
