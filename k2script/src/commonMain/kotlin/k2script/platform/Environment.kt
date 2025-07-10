@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.platform

import kotlinx.datetime.*

/**
 * Common platform environment access
 */
expect object Environment {
    fun getProperty(key: String): String?
    fun getenv(key: String): String?
    val userHome: String
}

/**
 * CCEK context-aware system access
 */
object System {
    fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
    
    fun getProperty(key: String): String? = Environment.getProperty(key)
    
    fun getenv(key: String): String? = Environment.getenv(key)
}