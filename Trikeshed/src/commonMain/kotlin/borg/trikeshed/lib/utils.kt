package borg.trikeshed.lib

// Platform-specific imports moved to platform modules

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
    return "${(size * 10).toInt() / 10.0} ${units[unitIndex]}"
}

fun humanReadableByteCountSI(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1000.0 && unitIndex < units.size - 1) {
        size /= 1000.0
        unitIndex++
    }
    return "${(size * 10).toInt() / 10.0} ${units[unitIndex]}"
}

// File I/O functions moved to platform-specific modules
// These will be implemented in nativeMain with proper CPointer types 