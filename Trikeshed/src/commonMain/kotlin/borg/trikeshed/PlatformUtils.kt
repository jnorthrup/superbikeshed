package borg.trikeshed

/**
 * Platform-specific utilities
 */
expect object PlatformUtils {
    fun currentTimeMillis(): Long
    fun getProperty(key: String): String?
    fun getProperty(key: String, defaultValue: String): String
} 