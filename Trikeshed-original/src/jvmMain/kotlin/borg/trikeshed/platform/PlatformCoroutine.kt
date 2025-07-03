package borg.trikeshed.platform

actual fun <T> runBlocking(block: suspend () -> T): T = kotlinx.coroutines.runBlocking(block)