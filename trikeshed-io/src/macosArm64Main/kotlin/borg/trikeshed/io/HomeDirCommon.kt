package borg.trikeshed.io

actual val homedirGet: String = "/tmp"
actual fun mktemp(): String = "/tmp/temp_file"
actual fun rm(path: String): Boolean = true
actual fun mkdir(path: String): Boolean = true