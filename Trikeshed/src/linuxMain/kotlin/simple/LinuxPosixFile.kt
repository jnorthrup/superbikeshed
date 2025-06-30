@file:OptIn(ExperimentalForeignApi::class)
package simple

import borg.trikeshed.lib.CZero.z
import borg.trikeshed.native.HasDescriptor
import borg.trikeshed.native.HasPosixErr
import kotlinx.cinterop.*
import platform.posix.*
import zlinux_uring.AT_FDCWD

class LinuxPosixFile(
 val path: String?,
 O_FLAGS: __u32 = PosixOpenOpts.withFlags(PosixOpenOpts.OpenReadOnly, PosixOpenOpts.OpenSync),
 override val fd: Int = run {
 platform.posix.open(path, O_FLAGS.toInt())
 },
) : HasDescriptor, HasSize {
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
 st_?.let { nativeHeap.free(it.rawPtr) }
 return close
 }

 override var st_: stat? = null
 override val st: stat by lazy {
 val st__ = st_ ?: (nativeHeap.alloc<stat>().also { st_ = it })
 fstat(fd, st__.ptr)
 st__
 }

 override fun write64(buf: ByteArray): ULong {
     val addressOf = buf.pin().addressOf(0)
     val b: CArrayPointer<ByteVar> = addressOf.reinterpret()
     val write = write(fd, b, buf.size.toULong())
     HasPosixErr.posixRequires(write >= 0) { "write failed with result ${HasPosixErr.reportErr(write.toInt())}" }
     return write.toULong()
 }

 override fun seek(offset: __off_t, whence: Int): ULong {
 val offr: __off_t = lseek(fd, offset, whence)
 HasPosixErr.posixRequires(offr >= 0) { "seek failed with result ${HasPosixErr.reportErr(res = offr.toInt())}" }
 return offr.toULong()
 }
 // ... rest of file
}
