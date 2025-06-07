@file:OptIn(ExperimentalForeignApi::class)

package borg.trikeshed.io

import borg.trikeshed.lib.*
import kotlin.jvm.*

import borg.trikeshed.lib.CZero.z
import borg.trikeshed.lib.CZero.nz
import borg.trikeshed.native.HasPosixErr
import kotlinx.cinterop.* 
import platform.posix.* 

/**
opens file for syncronous read  /write

 NOTE: This is a clone of PosixFile, should be reconciled later with hierarchical kotlin native posix root, or not.

 the project is not intended to be run on anything except linux, however there's too many good reasons to keep the door
 open to general native targets.

 in the case of getDirFd and getFd, linux fcntl.h is platform specific and is only used in inbound uring samples.
 */

class LinuxPosixFile(
    val path: String?,
    O_FLAGS: UInt = PosixOpenOpts.withFlags(PosixOpenOpts.OpenReadOnly, PosixOpenOpts.OpenSync),
    override val fd: Int = run {
        platform.posix.open(path, O_FLAGS.toInt())
    },
) : IPlatformFile {
    override fun read64(buf: ByteArray): ULong {
        val addressOf = buf.pin().addressOf(0)
        val b: CArrayPointer<ByteVar> = addressOf.reinterpret()
        val read = read(fd, b, buf.size.toULong())
        HasPosixErr.posixRequires(read >= 0) { "read failed with result ${HasPosixErr.reportErr(read.toInt())}" }
        return read.toULong()
    }

    override fun close(): Int {
        val close = close(fd)
        HasPosixErr.posixRequires(close >= 0) { "close failed with result ${HasPosixErr.reportErr(close)}" }
        st_?.let { nativeHeap.free(it.__stat.rawPtr) }
        return close
    }

    override var st_: stat? = null
    override val st: stat by lazy {
        val st__ = st_ ?: (nativeHeap.alloc<__stat>().also { st_ = stat(it) })
        fstat(fd, st__.__stat.ptr)
        st__
    }

    override val size: Long get() = st.st_size

    override fun write64(buf: ByteArray): ULong {
        val addressOf = buf.pin().addressOf(0)
        val b: CArrayPointer<ByteVar> = addressOf.reinterpret()
        val write = write(fd, b, buf.size.toULong())
        HasPosixErr.posixRequires(write >= 0) { "write failed with result ${HasPosixErr.reportErr(write.toInt())}" }
        return write.toULong()
    }

    override fun seek(offset: Long, whence: Int): ULong {
        val offr: __off_t = lseek(fd, offset, whence)
        HasPosixErr.posixRequires(offr >= 0) { "seek failed with result ${HasPosixErr.reportErr(res = offr.toInt())}" }
        return offr.toULong()
    }

    companion object {
        override fun open(path: String?, O_FLAGS: Int): Int {
            val fd = platform.posix.open(path, O_FLAGS)
            HasPosixErr.posixRequires(fd > 0) { "File::open $path returned ${HasPosixErr.reportErr(fd)}" }
            return fd
        }

        override fun statk(path: String?, stat1: stat): stat {
            val __stat1 = stat1.st_ ?: nativeHeap.alloc<__stat>().also { stat1.st_ = stat(it) }
            stat(path, __stat1.__stat.ptr).also {
                HasPosixErr.posixRequires(it.z) { "statx $path" }
                return stat1
            }
        }

        override val page_size: Long by lazy { sysconf(_SC_PAGE_SIZE) }

        fun mmap_base(
            __addr: CValuesRef<*>? = 0L.toCPointer<ByteVar>(),
            __len: size_t = page_size.convert(),
            __prot: Int,
            __flags: Int,
            fd: Int,
            __offset: Long,
        ): COpaquePointer {
            HasPosixErr.warning(__offset % page_size == 0L) { "$__offset requires blocksize of $page_size" }
            val cPointer = mmap(__addr, __len, __prot, __flags, fd, __offset)
            HasPosixErr.posixRequires(cPointer.toLong() != -1L) {
                "mmap failed with result ${
                    HasPosixErr.reportErr(cPointer.toLong().toInt())
                }"
            }

            return cPointer!!
        }

        fun getDirFd(namedDirAndFile: List<String>): Int = if (namedDirAndFile.first().isEmpty()) {
            AT_FDCWD
        } else {
            platform.posix.open(namedDirAndFile.first(), O_DIRECTORY).also {
                HasPosixErr.posixRequires(it > 0) { "opendir ${namedDirAndFile.first()}" }
            }
        }

        override fun namedDirAndFile(file_path: String): List<String> = file_path.lastIndexOf('/').let { tail ->
            if (tail == -1) listOf("", file_path) else listOf(
                file_path.substring(0, tail),
                file_path.substring(tail.inc())
            )
        }
    }
}