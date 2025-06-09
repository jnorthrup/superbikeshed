package borg.trikeshed.io

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Series
import kotlinx.cinterop.*
import platform.posix.*
import simple.PosixFile

actual object Files {
    actual fun readAllLines(filename: String): List<String> = readLines(filename)
    actual fun readAllBytes(filename: String): ByteArray = simple.PosixFile.readAllBytes(filename)
    actual fun readString(filename: String): String = simple.PosixFile.readString(filename)
    actual fun write(filename: String, bytes: ByteArray): Unit = simple.PosixFile.writeBytes(filename, bytes).let { }
    actual fun write(filename: String, lines: List<String>): Unit = simple.PosixFile.writeLines(filename, lines)
    actual fun write(filename: String, string: String): Unit = simple.PosixFile.writeString(filename, string).let { }

    /**cinterop to get cwd from posix */
    actual fun cwd(): String = memScoped {
        val pathmax = pathconf(".", _PC_PATH_MAX).let { if (it > 0) it else 256L } // Fallback if _PC_PATH_MAX is not defined
        val buf = allocArray<ByteVar>(pathmax.toInt() + 1) // +1 for null terminator
        val cwd = getcwd(buf, pathmax.toULong())
        cwd?.toKString() ?: throw IllegalStateException("getcwd failed: ${strerror(errno)?.toKString()}")
    }

    actual fun exists(filename: String): Boolean = simple.PosixFile.exists(filename)

    /** read offsets and lines accompanying*/
    actual fun streamLines(
        fileName: String,
        bufsize: Int,
    ): Sequence<Join<Long, ByteArray>> = sequence {
        val file = simple.PosixFile(fileName)
        val fp = fdopen(file.fd, "r")
        val line: CPointerVarOf<CPointer<ByteVarOf<Byte>>> = alloc()
        val len: ULongVarOf<size_t> = alloc()
        len.value = 0u
        var read: ssize_t
        var offset = 0L

        while (true) {
            read = getline(line.ptr, len.ptr, fp)
            if (read == -1L) break
            val lineBytes = ByteArray(read.toInt())
            for (i in 0 until read.toInt()) {
                lineBytes[i] = line.value!![i]
            }
            yield(Join(offset, lineBytes))
            offset += read
        }

        free(line.value)
        fclose(fp)
        file.close()
    }
}
