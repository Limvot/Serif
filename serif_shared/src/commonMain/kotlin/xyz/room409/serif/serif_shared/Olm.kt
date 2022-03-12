package xyz.room409.serif.serif_shared

import io.github.brevilo.jolm.*

fun olm_test() {
    println("hi, making account")
    val a = Account()
    println("hi, made account")
    val keys = a.identityKeys()
    println("keys $keys")
    val sign = a.sign("hasdfhasdf")
    println("signed $sign")
    val pickle_key = "UNSAFEKEY"
    val pickle = a.pickle(pickle_key)
    println("pickle $pickle")
    val reup = Account.unpickle(pickle_key, pickle)
    println("reup $reup")
}

