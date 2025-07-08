@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package k2script.platform

actual object Environment {
    actual fun getProperty(key: String): String? = System.getProperty(key)
    actual fun getenv(key: String): String? = System.getenv(key)
    actual val userHome: String = System.getProperty("user.home") ?: "/"
}