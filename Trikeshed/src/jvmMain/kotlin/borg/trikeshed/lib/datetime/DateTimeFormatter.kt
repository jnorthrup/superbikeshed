package borg.trikeshed.lib.datetime

import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

actual fun getCurrentDateTime(): DateTimeComponents {
    val now = ZonedDateTime.now(ZoneOffset.UTC)
    return DateTimeComponents(
        year = now.year,
        month = now.monthValue,
        day = now.dayOfMonth,
        hour = now.hour,
        minute = now.minute,
        second = now.second,
        millisecond = now.nano / 1_000_000
    )
}
