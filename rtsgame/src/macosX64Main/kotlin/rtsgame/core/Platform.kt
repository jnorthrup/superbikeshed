package rtsgame.core

import platform.posix.clock_gettime
import platform.posix.CLOCK_MONOTONIC
import platform.posix.CLOCK_REALTIME
import platform.posix.timespec

actual object PlatformTime {
    actual fun nanoTime(): Long {
        val timespec = timespec()
        clock_gettime(CLOCK_MONOTONIC, timespec.ptr)
        return timespec.tv_sec * 1_000_000_000L + timespec.tv_nsec
    }
    
    actual fun currentTimeMillis(): Long {
        val timespec = timespec()
        clock_gettime(CLOCK_REALTIME, timespec.ptr)
        return timespec.tv_sec * 1000L + timespec.tv_nsec / 1_000_000L
    }
} 