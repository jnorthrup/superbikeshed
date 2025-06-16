package borg.trikeshed.io

import kotlinx.cinterop.*
import platform.posix.*
import borg.trikeshed.native.HasPosixErr

actual object Files {
    actual fun readAllLines(path: String): List<String> = memScoped {
        val lines = mutableListOf<String>()
        val file = fopen(path, "r")
        if (file != null) {
            var line: CPointer<ByteVar>? = null
            while (fgets(line?.ptr, 0, file) != null) {
                lines.add(line!!.toKString())
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

    actual fun write(path: String, content: ByteArray) {
        val fd = open(path, O_WRONLY or O_CREAT or O_TRUNC, 0x0666)
        HasPosixErr.posixRequires(fd >= 0) { "Failed to open file for writing" }
        write(fd, content.refTo(0), content.size.toULong())
        close(fd)
    }

    actual fun exists(path: String): Boolean = access(path, F_OK) == 0

    actual fun cwd(): String = memScoped {
        val buffer = allocArray<ByteVar>(1024)
        getcwd(buffer, 1024u)
        buffer.toKString()
    }
}