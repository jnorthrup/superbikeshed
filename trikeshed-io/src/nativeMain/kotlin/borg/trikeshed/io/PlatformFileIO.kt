@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@file:OptIn(RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

@file:OptIn(ExperimentalForeignApi::class)

package borg.trikeshed.io

import borg.trikeshed.lib.*
import kotlinx.cinterop.*
import platform.posix.*

/**
 * Native implementation of PlatformFileIO
 */
actual interface PlatformFileIO {
    actual suspend fun readFile(path: String): Join<Int, (Int) -> Byte>?
    actual suspend fun writeFile(path: String, content: Join<Int, (Int) -> Byte>): Boolean
}

actual class PlatformFileIOImpl : PlatformFileIO {
    
    actual override suspend fun readFile(path: String): Join<Int, (Int) -> Byte>? {
        val file = fopen(path, "rb") ?: return null
        
        fseek(file, 0, SEEK_END)
        val size = ftell(file).toInt()
        rewind(file)
        
        val buffer = ByteArray(size)
        buffer.usePinned { pinned ->
            fread(pinned.addressOf(0), 1u, size.toULong(), file)
        }
        fclose(file)
        
        return size j { i: Int -> buffer[i] }
    }
    
    actual override suspend fun writeFile(path: String, content: Join<Int, (Int) -> Byte>): Boolean {
        val file = fopen(path, "wb") ?: return false
        
        val size = content.a
        val bytes = ByteArray(size) { i -> content.b(i) }
        
        val written = bytes.usePinned { pinned ->
            fwrite(pinned.addressOf(0), 1u, size.toULong(), file)
        }
        fclose(file)
        
        return written.toInt() == size
    }
}