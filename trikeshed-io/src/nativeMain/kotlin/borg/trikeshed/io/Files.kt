@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
@file:OptIn(ExperimentalForeignApi::class)

package borg.trikeshed.io

import borg.trikeshed.lib.*
import kotlinx.cinterop.*
import platform.posix.*
import borg.trikeshed.lib.toIdx


/**
 * Native implementation of Files
 */
actual object Files {
    
    actual fun readAllLines(path: String): List<String> {
        return readString(path).split("\n")
    }
    
    actual fun readAllBytes(path: String): ByteArray {
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
    
    actual fun readString(path: String): String {
        return readAllBytes(path).decodeToString()
    }
    
    actual fun write(path: String, content: ByteArray) {
        val file = fopen(path, "wb") ?: return
        content.usePinned { pinned ->
            fwrite(pinned.addressOf(0), 1u, content.size.toULong(), file)
        }
        fclose(file)
    }
    
    actual fun write(path: String, lines: List<String>) {
        write(path, lines.joinToString("\n"))
    }
    
    actual fun write(path: String, string: String) {
        write(path, string.encodeToByteArray())
    }
    
    actual fun exists(path: String): Boolean {
        return access(path, F_OK) == 0
    }
    
    actual fun cwd(): String {
        memScoped {
            val buffer = allocArray<ByteVar>(PATH_MAX)
            return getcwd(buffer, PATH_MAX.toULong())?.toKString() ?: "/"
        }
    }
    
    actual fun streamLines(fileName: String, bufsize: Int): Sequence<Join<Long, ByteArray>> {
        return sequence {
            val file = fopen(fileName, "r") ?: return@sequence
            try {
                memScoped {
                    val buffer = allocArray<ByteVar>(bufsize)
                    var lineNumber = 0L
                    while (fgets(buffer, bufsize, file) != null) {
                        val line = buffer.toKString().trimEnd('\n')
                        yield(lineNumber++ j line.encodeToByteArray())
                    }
                }
            } finally {
                fclose(file)
            }
        }
    }
    
    actual fun iterateLines(fileName: String, bufsize: Int): Iterable<Join<Long, Indexed<Byte>>> {
        return streamLines(fileName, bufsize).map { (lineNum, bytes) ->
            lineNum j bytes.toIdx()
        }.asIterable()
    }
    
    actual fun delete(path: String) {
        remove(path)
    }
    
    actual fun readLinesSeq(path: String): Sequence<String> {
        return streamLines(path, 4096).map { it.b.decodeToString() }
    }
    
    actual fun readLines(path: String): List<String> {
        return readLinesSeq(path).toList()
    }
}