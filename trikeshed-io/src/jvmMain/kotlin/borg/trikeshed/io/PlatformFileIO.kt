@file:OptIn(RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

import borg.trikeshed.lib.*
import java.io.File

/**
 * JVM implementation of PlatformFileIO
 */
actual interface PlatformFileIO {
    actual suspend fun readFile(path: String): Join<Int, (Int) -> Byte>?
    actual suspend fun writeFile(path: String, content: Join<Int, (Int) -> Byte>): Boolean
}

actual class PlatformFileIOImpl : PlatformFileIO {
    
    actual override suspend fun readFile(path: String): Join<Int, (Int) -> Byte>? {
        return try {
            val bytes = File(path).readBytes()
            bytes.size j { i: Int -> bytes[i] }
        } catch (e: Exception) {
            null
        }
    }
    
    actual override suspend fun writeFile(path: String, content: Join<Int, (Int) -> Byte>): Boolean {
        return try {
            val size = content.a
            val bytes = ByteArray(size) { i -> content.b(i) }
            File(path).writeBytes(bytes)
            true
        } catch (e: Exception) {
            false
        }
    }
}