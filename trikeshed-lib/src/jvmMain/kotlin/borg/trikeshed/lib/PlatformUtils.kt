package borg.trikeshed.lib

import kotlin.time.Duration
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * JVM implementation of PlatformUtils
 */
actual object PlatformUtils {
    
    actual fun currentTimeMillis(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    
    actual fun nanoTime(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    
    actual fun generateId(): String = UUID.randomUUID().toString()
    
    actual suspend fun sleep(duration: Duration) = delay(duration)
    
    actual fun getSystemProperty(key: String): String? = System.getProperty(key)
    
    actual fun getEnvironmentVariable(key: String): String? = System.getenv(key)
} 