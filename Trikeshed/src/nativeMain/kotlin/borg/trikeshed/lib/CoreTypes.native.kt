package borg.trikeshed.lib

import kotlinx.cinterop.*
import platform.posix.*

actual fun getCurrentTimeMillis(): Long {
    val time = alloc<timespec>()
    clock_gettime(CLOCK_REALTIME, time.ptr)
    return time.tv_sec * 1000 + time.tv_nsec / 1_000_000
}

actual fun getSystemProperty(key: String): String? {
    // For native platforms, we'll return null for now
    // This can be enhanced with platform-specific property access if needed
    return null
} 