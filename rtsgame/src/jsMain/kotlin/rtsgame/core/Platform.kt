package rtsgame.core

import kotlin.js.Date

actual object PlatformTime {
    actual fun nanoTime(): Long {
        return (Date.now() * 1_000_000).toLong()
    }
    
    actual fun currentTimeMillis(): Long {
        return Date.now().toLong()
    }
} 