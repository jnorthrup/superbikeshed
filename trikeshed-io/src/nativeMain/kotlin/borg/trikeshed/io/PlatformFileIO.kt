@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
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
    actual suspend fun deleteFile(path: String): Boolean
    actual suspend fun exists(path: String): Boolean
    actual suspend fun asyncReadFile(path: String): ByteArray?
    actual suspend fun asyncWriteFile(path: String, content: ByteArray): Boolean
}

actual val platformFileIO: PlatformFileIO = PlatformFileIOImpl()

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

    actual override suspend fun asyncReadFile(path: String): ByteArray? {
        val file = fopen(path, "rb") ?: return null
        fseek(file, 0, SEEK_END)
        val size = ftell(file).toInt()
        rewind(file)
        fclose(file)
        
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
    
    actual override suspend fun deleteFile(path: String): Boolean {
        return remove(path) == 0
    }
    
    actual override suspend fun exists(path: String): Boolean {
        val file = fopen(path, "r")
        return if (file != null) {
            fclose(file)
            true
        } else {
            false
        }
    }
}