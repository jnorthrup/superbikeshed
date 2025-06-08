package borg.trikeshed.nio

actual val homedirGet: String
    get() = System.getProperty("user.home")!!
