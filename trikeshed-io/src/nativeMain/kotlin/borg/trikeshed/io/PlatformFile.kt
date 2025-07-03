package borg.trikeshed.io

import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)

/**
 * Native implementation of PlatformFile
 */
actual class PlatformFile actual constructor(private val path: String) {
    
    actual fun exists(): Boolean {
        return access(path, F_OK) == 0
    }
    
    actual fun isDirectory(): Boolean {
        memScoped {
            val stat = alloc<stat>()
            if (stat(path, stat.ptr) != 0) return false
            return (stat.st_mode.toInt() and S_IFDIR) != 0
        }
    }
    
    actual fun readAllBytes(): ByteArray {
        val file = fopen(path, "rb") ?: return ByteArray(0)
        
        fseek(file, 0, SEEK_END)
        val size = ftell(file).toInt()
        rewind(file)
        
        val buffer = ByteArray(size)
        buffer.usePinned { pinned ->
            fread(pinned.addressOf(0), 1u, size.toULong(), file)
        }
        fclose(file)
        
        return buffer
    }
}