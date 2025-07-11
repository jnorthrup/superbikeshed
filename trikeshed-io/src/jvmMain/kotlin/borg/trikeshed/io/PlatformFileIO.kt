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
    actual suspend fun deleteFile(path: String): Boolean
    actual suspend fun exists(path: String): Boolean
    actual suspend fun asyncReadFile(path: String): ByteArray?
    actual suspend fun asyncWriteFile(path: String, content: ByteArray): Boolean
}

actual val platformFileIO: PlatformFileIO = PlatformFileIOImpl()

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

    actual override suspend fun deleteFile(path: String): Boolean {
        return try {
            File(path).delete()
        } catch (e: Exception) {
            false
        }
    }

    actual override suspend fun exists(path: String): Boolean {
        return try {
            File(path).exists()
        } catch (e: Exception) {
            false
        }
    }

    actual override suspend fun asyncReadFile(path: String): ByteArray? {
        val file = File(path)
        if (!file.exists()) return null
        val size = file.length().toInt()
        val buffer = ByteArray(size)
        val engine = AsyncIOEngine.create()
        val handle = AsyncFileManager.instance.registerFile(path)
        val read = engine.read(handle, buffer, 0)
        return if (read > 0) buffer else null
    }

    actual override suspend fun asyncWriteFile(path: String, content: ByteArray): Boolean {
        val engine = AsyncIOEngine.create()
        val handle = AsyncFileManager.instance.registerFile(path)
        val written = engine.write(handle, content, 0)
        return written == content.size
    }
}