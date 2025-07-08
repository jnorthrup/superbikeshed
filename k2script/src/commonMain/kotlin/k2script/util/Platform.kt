@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package k2script.util

/**
 * Platform utilities for k2script
 */
expect object Platform {
    fun currentTimeMillis(): Long
    fun getProperty(key: String): String?
    fun getenv(key: String): String?
}