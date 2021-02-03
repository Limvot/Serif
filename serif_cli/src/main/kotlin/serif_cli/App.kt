/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package xyz.room409.serif.serif_cli
import xyz.room409.serif.serif_shared.*

import java.io.Console

class App {
    val console = System.console()
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
        while (true) {
            val m = mstate
            mstate = when (m) {
                is MatrixLogin -> {
                    print(m.login_message)
                    print("Username: ")
                    val username = console.readLine()
                    print("Password: ")
                    val password = String(console.readPassword())
                    println("Logging in with username |$username| and a password I won't print...")
                    m.login(username, password)
                }
                is MatrixRooms -> {
                    println("Logged in! Sending test message")
                    m.test()
                }
            }
        }
    }
}

fun main(args: Array<String>) {
    var app = App()
    println("Welcome to " + app.version)
    app.run()
    println("Exiting!")
}
