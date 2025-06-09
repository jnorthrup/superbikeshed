package borg.trikeshed.nio

import kotlinx.cinterop.*
import platform.posix.*

import borg.trikeshed.lib.LongSeries
import borg.trikeshed.lib.logDebug
import simple.PosixFile
import simple.PosixOpenOpts

/**
 * FileBuffer is a memory-mapped file buffer that provides access to file contents
 * through a native pointer interface.
 */
actual class FileBuffer actual constructor(
    val filename: String,
    val blkSize: Long,
    val initialOffset: Long,
) : LongSeries {
    
    @OptIn(ExperimentalForeignApi::class)
    var buffer: COpaquePointer? = null
    var file: simple.PosixFile? = null

    actual override val a: Long get() {
        ensureOpen()
        return initialOffset
    }

    actual override val b: Long get() {
        ensureOpen()
        val len: ULong = if (blkSize == (-1L)) file!!.size.toULong() else blkSize.toULong()
        return initialOffset + len.toLong()
    }

    private fun ensureOpen() {
        if (!isOpen()) {
            this.open()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun open() {
        if (isOpen()) return

        file = simple.PosixFile(
            filename,
            PosixOpenOpts.withFlags(PosixOpenOpts.OpenReadOnly, PosixOpenOpts.OpenSync)
        )

        val len: ULong = if (blkSize == (-1L)) file!!.size.toULong() else blkSize.toULong()
        logDebug { "len: $len" }
        buffer = file!!.mmap(len, offset = initialOffset)
    }

    actual fun isOpen(): Boolean = buffer != null

    @OptIn(ExperimentalForeignApi::class)
    actual fun close() {
        buffer?.let {
            val len: ULong = if (blkSize == (-1L)) file!!.size.toULong() else blkSize.toULong()
            munmap(it, len)
            buffer = null
        }
        file?.close()
        file = null
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun get(index: Long): Byte {
        ensureOpen()
        val ptr = buffer!!.reinterpret<ByteVar>()
        return ptr[index - initialOffset]
    }
}
