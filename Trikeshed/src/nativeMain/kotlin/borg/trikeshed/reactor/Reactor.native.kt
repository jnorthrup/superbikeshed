package borg.trikeshed.reactor

import platform.posix.time

actual fun getCurrentTimeMillis(): Long = time(null) * 1000L