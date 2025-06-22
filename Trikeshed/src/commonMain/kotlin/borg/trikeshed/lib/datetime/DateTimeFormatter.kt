package borg.trikeshed.lib.datetime

import kotlinx.datetime.Clock

fun formatDateTime(timestamp: Long): String {
    return timestamp.toString()
}

fun parseDateTime(dateString: String): Long {
    return dateString.toLongOrNull() ?: 0L
}

fun getCurrentTimeMillis(): Long {
    return Clock.System.now().toEpochMilliseconds()
}

data class DateTimeComponents(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
    val millisecond: Int = 0
)

expect fun getCurrentDateTime(): DateTimeComponents

fun formatRfc1123(dateTime: DateTimeComponents): String {
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", 
                       "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    
    // Simple implementation - assumes current day of week
    val dayOfWeek = days[0] // Sunday
    val month = months[dateTime.month - 1]
    
    return "${dayOfWeek}, ${dateTime.day.toString().padStart(2, '0')} $month ${dateTime.year} " +
           "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}:${dateTime.second.toString().padStart(2, '0','0')} GMT"
} 