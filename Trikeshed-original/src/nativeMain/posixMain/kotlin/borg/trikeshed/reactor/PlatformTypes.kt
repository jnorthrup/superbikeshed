package borg.trikeshed.reactor

import kotlinx.cinterop.*
import platform.posix.*

actual fun currentTimeMillis(): Long {
    return getTimeMillis()
} 