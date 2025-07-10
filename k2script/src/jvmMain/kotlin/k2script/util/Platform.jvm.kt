@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.util

actual object Platform {
    actual fun currentTimeMillis(): Long = System.currentTimeMillis()
    actual fun getProperty(key: String): String? = System.getProperty(key)
    actual fun getenv(key: String): String? = System.getenv(key)
}