@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.compression

import borg.trikeshed.lib.*

/**
 * Universal tail-index extraction for archives that store their index at the end
 */

enum class ArchiveFormat {
    ZIP_JAR,
    ZSTD_SEEKABLE
}

sealed class UniversalIndex {
    abstract fun extractRange(file: ByteArray, start: Long, end: Long): ByteArray
}

data class ZipIndex(val centralDirectory: ByteArray) : UniversalIndex() {
    override fun extractRange(file: ByteArray, start: Long, end: Long): ByteArray {
        // Parse central directory to find file entry
        // Extract compressed data and decompress
        return file.sliceArray(start.toInt() until end.toInt()) // Simplified
    }
}

data class ZstdIndex(val seekTable: ByteArray, val numFrames: Int) : UniversalIndex() {
    override fun extractRange(file: ByteArray, start: Long, end: Long): ByteArray {
        // Use seek table to find frame containing start position
        // Decompress only required frames
        return file.sliceArray(start.toInt() until end.toInt()) // Simplified
    }
}

/**
 * Extract tail index from archive formats
 */
fun extractTailIndex(file: ByteArray, format: ArchiveFormat): UniversalIndex {
    return when (format) {
        ArchiveFormat.ZIP_JAR -> {
            // ZIP: End of Central Directory Record is last 22 bytes minimum
            val eocd = file.sliceArray(file.size - 22 until file.size)
            val centralDirOffset = eocd.readUInt32LE(16)
            val centralDir = file.sliceArray(centralDirOffset until file.size - 22)
            ZipIndex(centralDir)
        }
        
        ArchiveFormat.ZSTD_SEEKABLE -> {
            // ZSTD: Seek table footer is last 9 bytes
            val footer = file.sliceArray(file.size - 9 until file.size)
            val seekTableSize = footer.readUInt32LE(0)
            val numFrames = footer.readUInt32LE(4)
            val seekTable = file.sliceArray(file.size - 9 - seekTableSize until file.size - 9)
            ZstdIndex(seekTable, numFrames)
        }
    }
}

/**
 * Random access bytes using attention bands
 */
fun randomAccessBytes(
    file: ByteArray,
    index: UniversalIndex,
    attentionBands: Indexed<Twin<Long>>
): Indexed<Byte> {
    
    val totalBytes = (0 until attentionBands.a).sumOf { i ->
        val band = attentionBands.b(i)
        (band.b - band.a).toInt()
    }
    
    return totalBytes j { byteIndex: Int ->
        var currentByte = byteIndex
        var bandIndex = 0
        
        // Find which band this byte belongs to
        while (bandIndex < attentionBands.a) {
            val band = attentionBands.b(bandIndex)
            val bandSize = (band.b - band.a).toInt()
            
            if (currentByte < bandSize) {
                val rangeData = index.extractRange(file, band.a, band.b)
                return@j rangeData[currentByte]
            }
            
            currentByte -= bandSize
            bandIndex++
        }
        
        0.toByte() // fallback
    }
}

/**
 * ByteArray extension for reading little-endian uint32
 */
internal fun ByteArray.readUInt32LE(offset: Int): Int {
    return (this[offset].toInt() and 0xFF) or
           ((this[offset + 1].toInt() and 0xFF) shl 8) or
           ((this[offset + 2].toInt() and 0xFF) shl 16) or
           ((this[offset + 3].toInt() and 0xFF) shl 24)
}