package borg.trikeshed.io

import borg.trikeshed.lib.LongSeries
import borg.trikeshed.lib.FilePath
import borg.trikeshed.lib.FileOffset
import borg.trikeshed.lib.FileSize

actual class FileBuffer actual constructor(
    actual val filePath: FilePath,
    actual val initialOffset: FileOffset,
    actual val blockSize: FileSize,
    actual val readOnly: Boolean
) : LongSeries<Byte>, Usable {

    // 'a' from LongSeries represents the size of the accessible region for the series.
    actual override val a: Long
        get() = TODO("Not yet implemented for JS. Should return effective mapped size as Long (e.g., internalActualFileSize.bytes).")

    // Accessor: (FileOffset.value) -> Byte
    actual override val b: (offsetValue: Long) -> Byte
        get() = { offsetValue -> TODO("Not yet implemented for JS. Received offset: $offsetValue. Should access underlying buffer using this offset if it were FileOffset.value.") }

    // Properties from expect class are now directly in constructor with 'actual val'

    actual override fun close() {
        // TODO("Not yet implemented for JS. FilePath: ${filePath.path}")
    }

    actual override fun open() {
        // TODO("Not yet implemented for JS. FilePath: ${filePath.path}, Offset: ${initialOffset.value}, BlockSize: ${blockSize.bytes}")
    }

    actual fun isOpen(): Boolean {
        TODO("Not yet implemented for JS. FilePath: ${filePath.path}")
        // return false // Example placeholder
    }

    actual fun size(): FileSize {
        TODO("Not yet implemented for JS. Should return FileSize. FilePath: ${filePath.path}")
        // return FileSize(0L) // Example placeholder
    }

    actual fun get(index: FileOffset): Byte {
        TODO("Not yet implemented for JS. Index: ${index.value}, FilePath: ${filePath.path}")
        // return 0.toByte() // Example placeholder
    }

    actual fun put(index: FileOffset, value: Byte) {
        if (readOnly) throw IllegalStateException("File ${filePath.path} is read-only.")
        TODO("Not yet implemented for JS. Index: ${index.value}, Value: $value, FilePath: ${filePath.path}")
    }
}