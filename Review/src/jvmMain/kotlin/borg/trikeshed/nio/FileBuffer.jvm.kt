package borg.trikeshed.nio

import borg.trikeshed.lib.LongSeries
import borg.trikeshed.lib.FilePath
import borg.trikeshed.lib.FileOffset
import borg.trikeshed.lib.FileSize
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicLong

/**
 * An openable and closeable mmap file.
 *
 *  get has no side effects but put has undefined effects on size and sync
 */
actual class FileBuffer actual constructor(
    actual val filePath: FilePath,
    actual val initialOffset: FileOffset,
    actual val blockSize: FileSize, // User's requested block/file size; -1L in bytes means use file's full size
    actual val readOnly: Boolean
) : LongSeries<Byte>, Usable {

    private var internalIsOpen: Boolean = false
    private lateinit var buffer: MappedByteBuffer
    private var internalActualFileSize: FileSize = FileSize(0L) // Actual size of the mapped region

    // 'a' from LongSeries represents the size of the accessible region for the series.
    actual override val a: Long get() = internalActualFileSize.bytes

    actual override val b: (offsetValue: Long) -> Byte = { offsetValue ->
        // This lambda is the accessor for LongSeries.
        // It receives a Long, which corresponds to FileOffset.value.
        // Ensure the file is open and the offset is valid.
        if (!internalIsOpen) {
            throw IllegalStateException("File ${filePath.path} is not open for series access.")
        }
        if (offsetValue < 0 || offsetValue >= internalActualFileSize.bytes) {
            throw IndexOutOfBoundsException("Index $offsetValue out of bounds for file size ${internalActualFileSize.bytes}")
        }
        buffer.get(offsetValue.toInt()) // MappedByteBuffer uses Int indexes
    }

    init {
        // Initialize and map the buffer if not readOnly and blkSize is positive,
        // or defer to open() if it's meant to be opened lazily.
        // For simplicity with current structure, full mapping happens in open().
        // If blkSize is positive, it implies a fixed size. If -1, it's dynamic up to file end.
        // The initial mapping in the original code was eager. We'll replicate that behavior in open().
    }

    actual override fun open() {
        if (internalIsOpen) return

        val file = java.io.File(filePath.path)
        if (!file.exists()) throw IllegalStateException("File ${filePath.path} does not exist.")

        val mode = if (readOnly) "r" else "rw"
        RandomAccessFile(file, mode).use { raf ->
            val channel = raf.channel
            val sizeToMap = if (blockSize.bytes < 0) { // If blkSize is -1, map from initialOffset to end of file
                maxOf(0, file.length() - initialOffset.value)
            } else {
                blockSize.bytes
            }

            // Ensure sizeToMap does not exceed what the file can offer from initialOffset
            val effectiveSize = minOf(sizeToMap, file.length() - initialOffset.value)
            if (effectiveSize <= 0 && sizeToMap > 0) throw IllegalStateException("Calculated effective size is 0 or negative. File size: ${file.length()}, initialOffset: ${initialOffset.value}, requested blockSize: ${blockSize.bytes}")


            this.buffer = channel.map(
                if (readOnly) FileChannel.MapMode.READ_ONLY else FileChannel.MapMode.READ_WRITE,
                initialOffset.value,
                effectiveSize // Map only the effective size
            )
            this.internalActualFileSize = FileSize(effectiveSize)
        }
        internalIsOpen = true
    }

    actual override fun close() {
        // MappedByteBuffer doesn't have a direct close.
        // Cleanup is usually handled by GC when the buffer is no longer referenced.
        // For explicit cleanup, force unmapping is platform-specific and tricky.
        // ((sun.nio.ch.DirectBuffer)buffer).cleaner().clean(); // Example of sun-specific cleanup
        internalIsOpen = false
        // Setting buffer to null or a dummy might help GC, but not strictly 'closing' the MMap.
    }

    actual fun isOpen(): Boolean = internalIsOpen

    actual fun size(): FileSize = internalActualFileSize

    actual fun get(index: FileOffset): Byte {
        if (!internalIsOpen) {
            throw IllegalStateException("File ${filePath.path} is not open.")
        }
        // We must use value from FileOffset and ensure it's within the mapped region's size.
        // The MappedByteBuffer itself is indexed from 0 relative to its mapped region.
        // If initialOffset is part of FileBuffer's contract for indexing (0 to mappedSize-1),
        // then index.value should be used directly if it's already relative to the start of mapping.
        // If LongSeries expects indexing from 0 for this buffer, then index.value is correct.
        if (index.value < 0 || index.value >= internalActualFileSize.bytes) {
            throw IndexOutOfBoundsException("Index ${index.value} out of bounds for mapped size ${internalActualFileSize.bytes}")
        }
        return buffer.get(index.value.toInt())
    }

    actual fun put(index: FileOffset, value: Byte) {
        if (readOnly) {
            throw IllegalStateException("File ${filePath.path} is open read-only.")
        }
        if (!internalIsOpen) {
            throw IllegalStateException("File ${filePath.path} is not open.")
        }
        if (index.value < 0 || index.value >= internalActualFileSize.bytes) {
            throw IndexOutOfBoundsException("Index ${index.value} out of bounds for mapped size ${internalActualFileSize.bytes}")
        }
        buffer.put(index.value.toInt(), value)
    }
}