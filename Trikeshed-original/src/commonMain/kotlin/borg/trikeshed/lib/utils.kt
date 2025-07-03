package borg.trikeshed.lib

import kotlinx.cinterop.*

fun logDebug(block: () -> String) {
    println("[DEBUG] ${block()}")
}

fun debug(block: () -> String) {
    println("[DEBUG] ${block()}")
}

fun fromOctal(value: String): Int {
    return value.toIntOrNull(8) ?: 0
}

fun readableUnitsToNumber(value: String): Long {
    return value.toLongOrNull() ?: 0L
}

fun humanReadableByteCountIEC(bytes: Long): String {
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024.0 && unitIndex < units.size - 1) {
        size /= 1024.0
        unitIndex++
    }
    return "%.1f %s".format(size, units[unitIndex])
}

fun humanReadableByteCountSI(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1000.0 && unitIndex < units.size - 1) {
        size /= 1000.0
        unitIndex++
    }
    return "%.1f %s".format(size, units[unitIndex])
}

expect fun writeULong(value: ULong, file: CPointer<out CPointed>?)
expect fun writeUShort(value: UShort, file: CPointer<out CPointed>?)
expect fun readULong(file: CPointer<out CPointed>?): ULong
expect fun readUShort(file: CPointer<out CPointed>?): UShort 