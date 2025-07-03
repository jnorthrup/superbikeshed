@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package borg.trikeshed.io

import kotlinx.cinterop.*
import platform.posix.*

// Native platform file implementation using POSIX APIs
actual class PlatformFile actual constructor(private val path: String) {
    
    actual fun exists(): Boolean {
        return access(path, F_OK) == 0
    }
    
    actual fun isDirectory(): Boolean {
        return memScoped {
            val stat = alloc<stat>()
            if (stat(path, stat.ptr) == 0) {
                (stat.st_mode.toInt() and S_IFMT) == S_IFDIR
            } else {
                false
            }
        }
    }
    
    actual fun readAllBytes(): ByteArray {
        val file = fopen(path, "rb") ?: throw RuntimeException("Cannot open file: $path")
        
        try {
            // Get file size
            fseek(file, 0, SEEK_END)
            val size = ftell(file).toInt()
            fseek(file, 0, SEEK_SET)
            
            if (size <= 0) return ByteArray(0)
            
            // Read file content
            return memScoped {
                val buffer = allocArray<ByteVar>(size)
                val bytesRead = fread(buffer, 1u, size.toULong(), file).toInt()
                
                ByteArray(bytesRead) { buffer[it] }
            }
        } finally {
            fclose(file)
        }
    }
}