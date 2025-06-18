@file:OptIn(ExperimentalForeignApi::class)

package borg.trikeshed.io
import kotlinx.cinterop.*
import platform.posix.*
import simple.PosixFile as SimplePosixFile // Use an alias to avoid name clash
import borg.trikeshed.native.HasPosixErr
import borg.trikeshed.lib.Usable

typealias PosixOffset = Long
typealias PosixStat = stat

// This file should contain the ACTUAL implementation for the posix target,
// matching the EXPECT declaration in commonMain.
actual class PosixFile actual constructor(
    actual val path: String?
) : Usable {
    // A simple wrapper around the more detailed simple.PosixFile
    private var internalFile: SimplePosixFile? = null

    actual override fun open() {
        if (internalFile == null) {
            // Open with default flags (read-only)
            internalFile = SimplePosixFile(path)
        }
    }

    actual override fun close() {
        internalFile?.close()
        internalFile = null
    }

    private fun file(): SimplePosixFile = internalFile ?: throw IllegalStateException("File not open. Call open() first.")

    actual fun read64(buf: ByteArray): ULong = file().read64(buf)
    actual fun write64(buf: ByteArray): ULong = file().write64(buf)

    actual fun seek(offset: PosixOffset, whence: Int): ULong = file().seek(offset, whence)

    actual val size: PosixOffset
        get() = file().size

    actual fun mmap(len: ULong, prot: Int, flags: Int, offset: PosixOffset): MappedPointer {
        val ptr = file().mmap(len, prot, flags, offset)
        return MappedPointer(ptr)
    }

    actual companion object {
        actual fun open(path: String?, flags: Int, mode: Int?): PosixFile {
            val file = PosixFile(path)
            // simple.PosixFile takes UInt flags, so convert.
            file.internalFile = SimplePosixFile(path, flags.toUInt())
            return file
        }
    }
}

actual val SEEK_SET_CONSTANT: Int = SEEK_SET
actual val O_RDONLY_FLAG: Int = O_RDONLY

actual class MappedPointer actual constructor(private val pointer: COpaquePointer?) {
    actual fun getByte(index: Long): Byte = (pointer?.toLong()?.plus(index))?.toCPointer<ByteVar>()!!.pointed.value
    actual fun putByte(index: Long, value: Byte) {
        (pointer?.toLong()?.plus(index))?.toCPointer<ByteVar>()!!.pointed.value = value
    }
    actual fun unmap() {
        // unmap would need size; this is a simplification. SimplePosixFile's munmap would be better.
        // munmap(pointer, 0) // Cannot call without size.
    }
}