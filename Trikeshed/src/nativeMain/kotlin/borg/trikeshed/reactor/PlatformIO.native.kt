package borg.trikeshed.reactor

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.posix.clock_gettime
import platform.posix.CLOCK_MONOTONIC
import platform.posix.timespec

actual val IO: CoroutineDispatcher = Dispatchers.Default

actual object System {
    actual fun currentTimeMillis(): Long = kotlin.system.getTimeMillis()
    actual fun nanoTime(): Long {
        val timespec = timespec()
        clock_gettime(CLOCK_MONOTONIC, timespec.ptr)
        return timespec.tv_sec * 1_000_000_000 + timespec.tv_nsec
    }
} 