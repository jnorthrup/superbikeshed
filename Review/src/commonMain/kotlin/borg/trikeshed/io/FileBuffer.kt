package borg.trikeshed.io

import borg.trikeshed.lib.LongSeries
import borg.trikeshed.lib.logDebug
import borg.trikeshed.lib.FilePath
import borg.trikeshed.lib.FileOffset
import borg.trikeshed.lib.FileSize

/**
 * open a filebuffer
 */
fun open(
    filePath: FilePath,
    initialOffset: FileOffset = FileOffset(0L),
    blockSize: FileSize = FileSize(-1L), // -1L might indicate to use file's full size from initialOffset
    readOnly: Boolean = true
): FileBuffer {
    logDebug { "pre-opening ${filePath.path}" }
    val buffer = FileBuffer(filePath, initialOffset, blockSize, readOnly)
    logDebug { "this isOpen()=${buffer.isOpen()}" }
    buffer.open()
    if(!buffer.isOpen()) throw  IllegalStateException("FileBuffer ${filePath.path} not open")

    logDebug { "call(ed) open()" }
    logDebug { "this isOpen()=${buffer.isOpen()}" }
    return buffer
}

/**
 * An openable and closeable mmap file.
 * LongSeries 'a' typically represents the size of the series.
 * LongSeries 'b' is the accessor (index: Long) -> Byte.
 *
 *  get has no side effects but put has undefined effects on size and sync
 */
expect class FileBuffer(
    filePath: FilePath,
    initialOffset: FileOffset = FileOffset(0L),
    /** blocksize or file-size if -1L in FileSize.bytes */
    blockSize: FileSize = FileSize(-1L),
    readOnly: Boolean = true,
) : LongSeries<Byte>, Usable {
    // 'a' from LongSeries represents the size of the accessible region.
    // This should correspond to 'blockSize.bytes' if positive, or actual mapped size.
    actual override val a: Long // Represents the effective size of the buffer for LongSeries
    actual override val b: (offset: Long) -> Byte // Accessor: (FileOffset.value) -> Byte

    val filePath: FilePath
    val initialOffset: FileOffset
    val blockSize: FileSize // User's requested block/file size
    val readOnly: Boolean

    actual override fun close()
    /**
     * open the filebuffer
     * throws IllegalStateException if open "fails?"
     */
    actual override fun open() //post-init open
    fun isOpen(): Boolean
    fun size(): FileSize // Returns the actual mapped size of the buffer
    fun get(index: FileOffset): Byte
    fun put(index: FileOffset, value: Byte)
}

fun openFileBuffer(
    filePath: FilePath,
    initialOffset: FileOffset = FileOffset(0L),
    blockSize: FileSize = FileSize(-1L),
    readOnly: Boolean = true,
): FileBuffer = open(filePath, initialOffset, blockSize, readOnly)
