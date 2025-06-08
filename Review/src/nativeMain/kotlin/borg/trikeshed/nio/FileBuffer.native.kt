package borg.trikeshed.nio

import borg.trikeshed.lib.LongSeries
import borg.trikeshed.lib.FilePath
import borg.trikeshed.lib.FileOffset
import borg.trikeshed.lib.FileSize

actual class FileBuffer actual constructor(
    actual val filePath: FilePath,
    actual val initialOffset: FileOffset,
    actual val blockSize: FileSize, // User's requested block/file size; -1L in bytes means use file's full size
    actual val readOnly: Boolean
) : LongSeries<Byte>, Usable {

    // internal state for the placeholder
    private var internalIsOpen: Boolean = false
    // Represents the actual size of the mapped region after open() is called.
    // For placeholder, this might be based on blockSize or a fixed value.
    private var internalActualFileSize: FileSize = if (blockSize.bytes >= 0) blockSize else FileSize(0L)

    // 'a' from LongSeries represents the size of the accessible region for the series.
    actual override val a: Long get() = internalActualFileSize.bytes

    // Accessor: (FileOffset.value) -> Byte
    actual override val b: (offsetValue: Long) -> Byte = { offsetValue ->
        if (!internalIsOpen) throw IllegalStateException("File ${filePath.path} is not open for series access.")
        if (offsetValue < 0 || offsetValue >= internalActualFileSize.bytes) {
            throw IndexOutOfBoundsException("Index $offsetValue out of bounds for file size ${internalActualFileSize.bytes}")
        }
        // Placeholder: always return 0
        0.toByte()
    }

    actual override fun close() {
        internalIsOpen = false
        println("FileBuffer.close() called for ${filePath.path} (Native placeholder)")
    }

    actual override fun open() {
        internalIsOpen = true
        // If blockSize was indicative of full file, a real implementation would stat the file here.
        // For this placeholder, if blockSize was negative, let's assign a default mock size.
        if (blockSize.bytes < 0) {
            internalActualFileSize = FileSize(1024L) // Default placeholder size
        } else {
            internalActualFileSize = blockSize // Use blockSize if it was positive
        }
        println("FileBuffer.open() called for ${filePath.path}. Effective size: ${internalActualFileSize.bytes} (Native placeholder)")
    }

    actual fun isOpen(): Boolean = internalIsOpen

    actual fun size(): FileSize {
        return internalActualFileSize
    }

    actual fun get(index: FileOffset): Byte {
        if (!internalIsOpen) throw IllegalStateException("File ${filePath.path} is not open")
        if (index.value < 0 || index.value >= internalActualFileSize.bytes) {
            throw IndexOutOfBoundsException("Index ${index.value} out of bounds for actual size ${internalActualFileSize.bytes}")
        }
        // Placeholder: always return 0
        println("FileBuffer.get(${index.value}) from ${filePath.path} (Native placeholder)")
        return 0.toByte()
    }

    actual fun put(index: FileOffset, value: Byte) {
        if (readOnly) throw IllegalStateException("File ${filePath.path} is read-only.")
        if (!internalIsOpen) throw IllegalStateException("File ${filePath.path} is not open")
        if (index.value < 0 || index.value >= internalActualFileSize.bytes) {
            throw IndexOutOfBoundsException("Index ${index.value} out of bounds for actual size ${internalActualFileSize.bytes}")
        }
        println("FileBuffer.put(${index.value}, $value) into ${filePath.path} (Native placeholder)")
    }
}
