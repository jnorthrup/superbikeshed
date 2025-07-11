package rtsgame.core

import rtsgame.compat.*

/**
 * Time utilities for cross-platform compatibility
 */
object TimeUtils {
    /**
     * Get current time in nanoseconds
     */
    fun nanoTime(): Long {
        return currentTimeMillis() * 1_000_000
    }
    
    /**
     * Get current time in milliseconds
     */
    fun currentTimeMillis(): Long {
        return rtsgame.compat.currentTimeMillis()
    }
}