@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package k2script.ccek

import kotlinx.datetime.*

/**
 * CCEK System Context - provides platform-aware system access
 * Centralizes all system calls for proper context management
 */
interface SystemContext {
    fun currentTimeMillis(): Long
    fun getProperty(key: String): String?
    fun getenv(key: String): String?
    val userHome: String
}

/**
 * Default implementation using common implementation
 */
object DefaultSystemContext : SystemContext by CommonSystemContext()

/**
 * Common implementation that can be used across platforms
 */
open class CommonSystemContext : SystemContext {
    override fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
    
    override fun getProperty(key: String): String? = when (key) {
        "user.home" -> userHome
        else -> null
    }
    
    override fun getenv(key: String): String? = null
    
    override val userHome: String = "/"
}

/**
 * CCEK-aware context holder
 */
object CCEK {
    var systemContext: SystemContext = CommonSystemContext()
    
    inline val System: SystemContext get() = systemContext
    
    fun configure(context: SystemContext) {
        systemContext = context
    }
}