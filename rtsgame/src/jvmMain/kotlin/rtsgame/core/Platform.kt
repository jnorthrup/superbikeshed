package rtsgame.core

import kotlin.system.getTimeMillis
import kotlin.system.getTimeNanos

actual object PlatformTime {
    actual fun nanoTime(): Long = getTimeNanos()
    actual fun currentTimeMillis(): Long = getTimeMillis()
} 