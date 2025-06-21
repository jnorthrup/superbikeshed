@file:OptIn(ExperimentalForeignApi::class)

package borg.trikeshed.lib.datetime

import kotlinx.cinterop.*
import platform.posix.*

actual fun getCurrentDateTime(): DateTimeComponents {
    val time = time(null)
    val tm = localtime(time)?.pointed
    return if (tm != null) {
        DateTimeComponents(
            year = tm.tm_year + 1900,
            month = tm.tm_mon + 1,
            day = tm.tm_mday,
            hour = tm.tm_hour,
            minute = tm.tm_min,
            second = tm.tm_sec,
            millisecond = 0
        )
    } else {
        DateTimeComponents(1970, 1, 1, 0, 0, 0, 0)
    }
}
