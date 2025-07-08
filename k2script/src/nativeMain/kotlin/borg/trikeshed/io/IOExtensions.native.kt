@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package borg.trikeshed.io

import kotlinx.cinterop.*
import platform.posix.*

actual fun readBytes(path: String): ByteArray {
    val file = fopen(path, "rb") ?: throw IllegalArgumentException("Cannot open file: $path")
    
    try {
        // Get file size
        fseek(file, 0, SEEK_END)
        val size = ftell(file).toInt()
        fseek(file, 0, SEEK_SET)
        
        // Read file
        val buffer = ByteArray(size)
        buffer.usePinned { pinned ->
            fread(pinned.addressOf(0), 1u, size.toULong(), file)
        }
        
        return buffer
    } finally {
        fclose(file)
    }
}

actual fun writeBytes(path: String, data: ByteArray) {
    val file = fopen(path, "wb") ?: throw IllegalArgumentException("Cannot create file: $path")
    
    try {
        data.usePinned { pinned ->
            fwrite(pinned.addressOf(0), 1u, data.size.toULong(), file)
        }
    } finally {
        fclose(file)
    }
}

actual fun fileExists(path: String): Boolean {
    val file = fopen(path, "r")
    return if (file != null) {
        fclose(file)
        true
    } else {
        false
    }
}

actual fun createDirectory(path: String): Boolean {
    return mkdir(path, 0755u) == 0
}

actual fun deleteFile(path: String): Boolean {
    return remove(path) == 0
}