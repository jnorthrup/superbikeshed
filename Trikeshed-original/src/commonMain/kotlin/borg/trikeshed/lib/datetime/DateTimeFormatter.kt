package borg.trikeshed.lib.datetime

fun formatDateTime(timestamp: Long): String {
    return timestamp.toString()
}

fun parseDateTime(dateString: String): Long {
    return dateString.toLongOrNull() ?: 0L
}

fun getCurrentTimeMillis(): Long {
    return System.currentTimeMillis()
}

data class DateTimeComponents(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int
)

fun getCurrentDateTime(): DateTimeComponents {
    return DateTimeComponents(2024, 1, 1, 0, 0, 0)
} 