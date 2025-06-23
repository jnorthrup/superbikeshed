@file:OptIn(ExperimentalForeignApi::class)
package borg.trikeshed.io

import kotlinx.cinterop.*
import platform.posix.*
import borg.trikeshed.native.HasPosixErr
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.bridge.*

actual object Files {
    actual fun readAllLines(path: String): List<String> = memScoped {
        val lines = mutableListOf<String>()
        val file = fopen(path, "r")
        if (file != null) {
            val buffer = ByteArray(4096)
            buffer.usePinned { pinned ->
                while (fgets(pinned.addressOf(0), buffer.size, file) != null) {
                    lines.add(pinned.get().toKString().trimEnd('\n', '\r'))
                }
            }
            fclose(file)
        }
        lines
    }

    actual fun readAllBytes(path: String): ByteArray {
        val fd = open(path, O_RDONLY)
        HasPosixErr.posixRequires(fd >= 0) { "Failed to open file" }
        val size = lseek(fd, 0, SEEK_END)
        lseek(fd, 0, SEEK_SET)
        val buffer = ByteArray(size.toInt())
        read(fd, buffer.refTo(0), size.toULong())
        close(fd)
        return buffer
    }

    actual fun readString(path: String): String {
        return readAllBytes(path).decodeToString()
    }

    actual fun write(path: String, content: ByteArray) {
        val fd = open(path, O_WRONLY or O_CREAT or O_TRUNC, 0x0666)
        HasPosixErr.posixRequires(fd >= 0) { "Failed to open file for writing" }
        write(fd, content.refTo(0), content.size.toULong())
        close(fd)
    }

    actual fun write(path: String, lines: List<String>) {
        write(path, lines.joinToString("\n").encodeToByteArray())
    }

    actual fun write(path: String, string: String) {
        write(path, string.encodeToByteArray())
    }

    actual fun exists(path: String): Boolean = access(path, F_OK) == 0

    actual fun cwd(): String = memScoped {
        val buffer = allocArray<ByteVar>(1024)
        getcwd(buffer, 1024u)
        buffer.toKString()
    }

    actual fun streamLines(fileName: String, bufsize: Int): Sequence<Join<Long, ByteArray>> = sequence {
        val lines = readAllLines(fileName)
        lines.forEachIndexed { index, line ->
            yield(Join(index.toLong(), line.encodeToByteArray()))
        }
    }

    actual fun iterateLines(fileName: String, bufsize: Int): Iterable<Join<Long, Indexed<Byte>>> {
        val lines = readAllLines(fileName)
        return lines.mapIndexed { index, line ->
            val bytes = line.encodeToByteArray()
            index.toLong() j bytes.toList().toIdx()
        }
    }

    actual fun delete(path: String) {
        val result = unlink(path)
        HasPosixErr.posixRequires(result == 0) { "Failed to delete file" }
    }

    actual fun readLinesSeq(path: String): Sequence<String> {
        return readAllLines(path).asSequence()
    }

    actual fun readLines(path: String): List<String> {
        return readAllLines(path)
    }
}