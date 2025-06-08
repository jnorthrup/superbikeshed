package simple

import borg.trikeshed.lib.*
import borg.trikeshed.lib.Series // Ensure Series is available
import borg.trikeshed.nio.*
import borg.trikeshed.native.HasPosixErr
import kotlinx.cinterop.*
import platform.posix.*
import platform.posix.open
import platform.posix.close
import platform.posix.read
import platform.posix.write
import platform.posix.lseek
import platform.posix.fstat
import platform.posix.stat
import platform.posix.mmap
import platform.posix.munmap
import platform.posix.perror
import platform.posix.fgets
import platform.posix.strerror
import platform.posix.errno
import platform.posix.EOF

// A concrete implementation of file operations for Posix.
// This class wraps native POSIX file descriptors and provides higher-level operations.
@OptIn(ExperimentalForeignApi::class)
class PosixFile(
    val path: String,
    O_FLAGS: UInt = PosixOpenOpts.withFlags(PosixOpenOpts.OpenReadOnly, PosixOpenOpts.OpenSync),
    val fd: Int = run {
        val openedFd = platform.posix.open(path, O_FLAGS.toInt())
        HasPosixErr.posixRequires(openedFd > 0) { "PosixFile::open $path returned ${HasPosixErr.reportErr(openedFd)}" }
        openedFd
    },
) : IPlatformFile { // Implement IPlatformFile

    val st: borg.trikeshed.nio.stat by lazy {
        memScoped {
            val statBuf = alloc<platform.posix.stat>()
            fstat(fd, statBuf.ptr)
            borg.trikeshed.nio.stat(statBuf.ptr) // Wrap native stat struct in actual class
        }
    }

    override val size: Long get() = st.st_size // From HasSize (via IPlatformFile)

    override fun read64(buf: ByteArray): ULong { // Renamed from read to match IPlatformFile
        val addressOf = buf.pin().addressOf(0)
        val b: CArrayPointer<ByteVar> = addressOf.reinterpret()
        val bytesRead = read(fd, b, buf.size.toULong())
        HasPosixErr.posixRequires(bytesRead >= 0) { "read64 failed with result ${HasPosixErr.reportErr(bytesRead.toInt())}" }
        return bytesRead.toULong()
    }

    override fun write64(buf: ByteArray): ULong { // Renamed from write to match IPlatformFile
        val addressOf = buf.pin().addressOf(0)
        val b: CArrayPointer<ByteVar> = addressOf.reinterpret()
        val bytesWritten = write(fd, b, buf.size.toULong())
        HasPosixErr.posixRequires(bytesWritten >= 0) { "write64 failed with result ${HasPosixErr.reportErr(bytesWritten.toInt())}" }
        return bytesWritten.toULong()
    }

    override fun seek(offset: Long, whence: Int /* = SEEK_SET */): ULong { // Default removed to match IPlatformFile expect
        val offr = lseek(fd, offset, whence)
        HasPosixErr.posixRequires(offr >= 0) { "seek failed with result ${HasPosixErr.reportErr(res = offr.toInt())}" }
        return offr.toULong()
    }

    override fun close(): Int {
        val closed = platform.posix.close(fd)
        HasPosixErr.posixRequires(closed >= 0) { "close failed with result ${HasPosixErr.reportErr(closed)}" }
        return closed
    }

    fun mmap(length: ULong, prot: Int = PROT_READ or PROT_WRITE, flags: Int = MAP_SHARED, offset: Long = 0): COpaquePointer {
        val mappedPtr = platform.posix.mmap(null, length, prot, flags, fd, offset)
        HasPosixErr.posixRequires(mappedPtr != MAP_FAILED) { "mmap failed for $path, length $length, offset $offset: ${HasPosixErr.reportErr(mappedPtr.toLong().toInt())}" }
        return mappedPtr!!
    }

    // IPlatformFile requires st_ and st if they are not implemented by delegation
    override var st_: borg.trikeshed.nio.stat? = null // Actual stat from borg.trikeshed.nio
        get() = field ?: st.also { field = it } // Lazy init if needed

    companion object {
        fun readAllBytes(filename: String): ByteArray = memScoped {
            val file = PosixFile(filename, PosixOpenOpts.withFlags(PosixOpenOpts.OpenReadOnly))
            try {
                val buffer = ByteArray(file.size.toInt())
                val bytesRead = file.read64(buffer)
                HasPosixErr.posixRequires(bytesRead.toLong() == file.size) { "Failed to read all bytes from $filename" }
                buffer
            } finally {
                file.close()
            }
        }

        fun readString(filename: String): String = readAllBytes(filename).decodeToString()

        fun writeBytes(filename: String, bytes: ByteArray) = memScoped {
            val file = PosixFile(filename, PosixOpenOpts.withFlags(PosixOpenOpts.O_Creat, PosixOpenOpts.O_Trunc, PosixOpenOpts.O_Rdwr))
            try {
                val bytesWritten = file.write64(bytes)
                HasPosixErr.posixRequires(bytesWritten.toLong() == bytes.size.toLong()) { "Failed to write all bytes to $filename" }
            } finally {
                file.close()
            }
        }

        fun writeString(filename: String, content: String) = writeBytes(filename, content.encodeToByteArray())

        fun writeLines(filename: String, lines: Series<String>) = writeString(filename, lines.`▶`.joinToString("\n"))

        fun exists(filename: String): Boolean = memScoped {
            val statBuf = alloc<platform.posix.stat>()
            platform.posix.stat(filename, statBuf.ptr) == 0
        }

        // --- ReadLines utilities ---
        fun readLinesSeq(path: String): Sequence<String> = sequence {
            val file = PosixFile(path, PosixOpenOpts.withFlags(PosixOpenOpts.OpenReadOnly))
            val fp = fdopen(file.fd, "r")
            try {
                if (fp == null) {
                    throw IllegalStateException("fdopen failed for $path: ${strerror(errno)?.toKString()}")
                }

                memScoped {
                    val linePtr: CPointerVarOf<CPointer<ByteVarOf<Byte>>> = alloc()
                    val len: ULongVarOf<size_t> = alloc()
                    len.value = 0uL

                    while (true) {
                        val read = getline(linePtr.ptr, len.ptr, fp)
                        if (read == -1L) break
                        yield(linePtr.value!!.toKString())
                    }
                    free(linePtr.value) // Free buffer allocated by getline
                }
            } finally {
                fclose(fp)
                file.close() // Close the underlying PosixFile
            }
        }

        fun readLines(path: String): Series<String> = readLinesSeq(path).toSeries() // Use .toSeries()
    }
}
