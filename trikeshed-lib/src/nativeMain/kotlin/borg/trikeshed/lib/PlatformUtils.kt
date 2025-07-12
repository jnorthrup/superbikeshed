package borg.trikeshed.lib

import kotlinx.coroutines.delay
import kotlin.time.Duration

actual object PlatformUtils {
    actual fun currentTimeMillis(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    
    actual fun nanoTime(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() * 1_000_000
    
    actual fun generateId(): String = "id_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}_${kotlin.random.Random.nextInt()}"
    
    actual suspend fun sleep(duration: Duration) = delay(duration)
    
    actual fun getSystemProperty(key: String): String? = null // Not available on native
    
    actual fun getEnvironmentVariable(key: String): String? = null // Not available on native
} 