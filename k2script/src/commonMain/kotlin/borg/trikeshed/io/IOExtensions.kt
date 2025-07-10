@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package borg.trikeshed.io

import borg.trikeshed.lib.*

/**
 * IO Extensions for TrikeShed
 * Platform-specific implementations will override these
 */
expect fun readBytes(path: String): ByteArray

expect fun writeBytes(path: String, data: ByteArray)

expect fun fileExists(path: String): Boolean

expect fun createDirectory(path: String): Boolean

expect fun deleteFile(path: String): Boolean

/**
 * Common IO utilities
 */
object IO {
    fun readText(path: String): String {
        return readBytes(path).decodeToString()
    }
    
    fun writeText(path: String, text: String) {
        writeBytes(path, text.encodeToByteArray())
    }
}

/**
 * ByteArray extensions for TrikeShed compatibility
 */
fun ByteArray.toIndexed(): Indexed<Byte> {
    return size j { i -> this[i] }
}

/**
 * ByteArray to ByteIndexed conversion
 */
fun ByteArray.toByteIndexed(): ByteIndexed {
    return ByteIndexed(size j { i -> this[i] })
}