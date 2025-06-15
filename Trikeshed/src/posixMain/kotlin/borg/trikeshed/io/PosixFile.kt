@file:OptIn(ExperimentalForeignApi::class)

package borg.trikeshed.io

import borg.trikeshed.lib.CZero.z
import borg.trikeshed.native.HasDescriptor
import borg.trikeshed.native.HasPosixErr
import kotlinx.cinterop.*
import platform.posix.*

actual class PosixFile actual constructor(
    actual val path: String?
) : Usable, HasDescriptor {
    actual fun open(): Unit {
        fd = platform.posix.open(path, O_RDONLY_FLAG)  // Use O_RDONLY_FLAG as defined
    }

    actual fun close(): Unit {
        platform.posix.close(fd)
    }

    actual override val size: Long
        get() = st.st_size.toLong()  // Ensure this matches the expect val size: Long

    // Correct existing functions
    actual fun read64(buf: ByteArray): ULong {
        val addressOf = buf.pin().addressOf(0)
        val b: CArrayPointer<ByteVar> = addressOf.reinterpret()
        val read = platform.posix.read(fd, b, buf.size.toULong())
        HasPosixErr.posixRequires(read >= 0) { "read failed" }
        return read.toULong()
    }

    actual fun write64(buf: ByteArray): ULong {
        val addressOf = buf.pin().addressOf(0)
        val b: CArrayPointer<ByteVar> = addressOf.reinterpret()
        val write = platform.posix.write(fd, b, buf.size.toULong())
        HasPosixErr.posixRequires(write >= 0) { "write failed" }
        return write.toULong()
    }

    actual fun seek(offset: PosixOffset, whence: Int = SEEK_SET): ULong {
        val offr = platform.posix.lseek(fd, offset, whence)
        HasPosixErr.posixRequires(offr >= 0) { "seek failed" }
        return offr.toULong()
    }

    actual fun mmap(len: ULong, prot: Int, flags: Int, offset: PosixOffset): MappedPointer {
        // Simplified; assume MappedPointer is defined elsewhere
        return MappedPointer(platform.posix.mmap(0, len, prot, flags, fd, offset))
    }

    actual companion object {
        actual fun open(path: String?, flags: Int, mode: Int? = null): PosixFile {
            val fd = platform.posix.open(path, flags, mode ?: 0)
            HasPosixErr.posixRequires(fd > 0) { "open failed" }
            return PosixFile(path).apply { this.fd = fd }  // Adjust constructor if needed
        }
    }
}
    actual override fun read64(buf: ByteArray): ULong {
        val addressOf = buf.pin().addressOf(0)
        val b: CArrayPointer<ByteVar> = addressOf.reinterpret()
        val read = read(fd, b, buf.size.toULong())
        HasPosixErr.posixRequires(read >= 0) { "read failed with result ${HasPosixErr.reportErr(read.toInt())}" }
        return read.toULong()
    }

    actual override fun close(): Int {
        val close = close(fd)
        HasPosixErr.posixRequires(close >= 0) { "close failed with result ${HasPosixErr.reportErr(close)}" }
        return close
    }

    actual override var st_: stat? = null
    actual override val st: stat by lazy {
        val st__ = st_ ?: (nativeHeap.alloc<stat>().also { st_ = it })
        fstat(fd, st__.ptr)
        st__
    }

    actual override fun write64(buf: ByteArray): ULong {
        val addressOf = buf.pin().addressOf(0)
        val b: CArrayPointer<ByteVar> = addressOf.reinterpret()
        val write = write(fd, b, buf.size.toULong())
        HasPosixErr.posixRequires(write >= 0) { "write failed with result ${HasPosixErr.reportErr(write.toInt())}" }
        return write.toULong()
    }

    actual override fun seek(offset: PosixOffset, whence: Int): ULong {
        val offr: __off_t = lseek(fd, offset, whence)
        HasPosixErr.posixRequires(offr >= 0) { "seek failed with result ${HasPosixErr.reportErr(offr.toInt())}" }
        return offr.toULong()
    }

    actual fun mmap(len: ULong, prot: Int, flags: Int, offset: PosixOffset): MappedPointer {
        require(offset % sysconf(_SC_PAGE_SIZE) == 0L) { "offset must be a multiple of the page size" }
        return MappedPointerImpl(mmap_base(fd, len, prot, flags, offset))
    }

    actual companion object {
        actual fun open(path: String?, flags: Int, mode: Int? = null): PosixFile {
            val fd = platform.posix.open(path, flags)
            HasPosixErr.posixRequires(fd > 0) { "File::open $path returned ${HasPosixErr.reportErr(fd)}" }
            return PosixFile(path, fd)
        }
    }
}

actual class MappedPointer actual constructor(pointer: COpaquePointer) {
    actual fun getByte(index: Long): Byte {
        return memScoped { (pointer + index)?.reinterpret<CArrayPointer<ByteVar>>()?.get(0) ?: 0 }
    }

    actual fun putByte(index: Long, value: Byte) {
        memScoped { (pointer + index)?.reinterpret<CArrayPointer<ByteVar>>()?.set(0, value) }
    }

    actual fun unmap() {
        platform.posix.munmap(pointer, 0)  // Ensure length is handled if known
    }
}
    actual fun getByte(index: Long): Byte {
        // Simplified implementation; adapt as needed
        return memScoped { (pointer + index)?.reinterpret<CArrayPointer<ByteVar>>()?.get(0) ?: 0 }
    }

    actual fun putByte(index: Long, value: Byte) {
        memScoped { (pointer + index)?.reinterpret<CArrayPointer<ByteVar>>()?.set(0, value) }
    }

    actual fun unmap() {
        munmap(pointer, /* length */ 0)  // Ensure proper length is passed if known
    }
}

actual val SEEK_SET_CONSTANT: Int = SEEK_SET
actual val O_RDONLY_FLAG: Int = O_RDONLY