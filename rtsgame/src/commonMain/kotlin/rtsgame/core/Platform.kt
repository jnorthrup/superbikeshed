package rtsgame.core

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Platform-specific time functions
 */
expect object PlatformTime {
    fun nanoTime(): Long
    fun currentTimeMillis(): Long
}

/**
 * Common time utilities
 */
object TimeUtils {
    fun getCurrentTime(): Instant = Clock.System.now()
    
    fun nanoTime(): Long = PlatformTime.nanoTime()
    
    fun currentTimeMillis(): Long = PlatformTime.currentTimeMillis()
} 