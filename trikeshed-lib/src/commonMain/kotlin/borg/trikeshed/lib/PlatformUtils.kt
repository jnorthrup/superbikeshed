package borg.trikeshed.lib

import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * Platform-agnostic utilities for system operations
 */
expect object PlatformUtils {
    /**
     * Get current system time in milliseconds since epoch
     */
    fun currentTimeMillis(): Long
    
    /**
     * Get current system time in nanoseconds
     */
    fun nanoTime(): Long
    
    /**
     * Generate a unique identifier
     */
    fun generateId(): String
    
    /**
     * Sleep for the specified duration
     */
    suspend fun sleep(duration: Duration)
    
    /**
     * Get system property (platform-specific)
     */
    fun getSystemProperty(key: String): String?
    
    /**
     * Get environment variable
     */
    fun getEnvironmentVariable(key: String): String?
} 