package borg.trikeshed.platform

expect fun <T> runBlocking(block: suspend () -> T): T
