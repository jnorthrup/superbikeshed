package borg.trikeshed.reactor

actual fun currentTimeMillis(): Long = Date().getTime().toLong()