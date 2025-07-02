package borg.trikeshed.ljson

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.toIndexed
import kotlinx.coroutines.flow.Flow

/**
 * Enables efficient interaction with large remote zip files by selectively
 * downloading and parsing the central directory of the zip file.
 */
class RemoteFileAttention(private val httpClient: HttpRangeClient) {

    /**
     * Represents an entry in the zip file's central directory.
     */
    data class ZipEntry(
        val fileName: String,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val localHeaderOffset: Long
    )

    /**
     * Fetches and parses the central directory of a remote zip file.
     *
     * @param url The URL of the remote zip file.
     * @return A Indexed of ZipEntry objects, representing the files in the archive.
     */
    suspend fun streamCentralDirectory(url: String): Flow<ZipEntry> {
        val fileSize = httpClient.fetchRange(url, 0, 0).size.toLong()
        val eocdBuffer = httpClient.fetchRange(url, fileSize - EOCD_RECORD_SIZE, fileSize)

        val eocdOffset = findEocdRecord(eocdBuffer.toIndexed())
        if (eocdOffset == -1L) {
            throw IllegalStateException("EOCD record not found in the last 64KB of the file.")
        }

        val eocd = parseEocd(eocdBuffer.toIndexed().drop(eocdOffset.toInt()))

        return flow {
            var offset = 0L
            val buffer = ByteArray(4096)
            var remainingBytes = ByteArray(0)

            while (offset < eocd.centralDirectorySize) {
                val bytesToFetch = min(buffer.size.toLong(), eocd.centralDirectorySize - offset).toInt()
                val chunk = httpClient.fetchRange(url, eocd.centralDirectoryOffset + offset, eocd.centralDirectoryOffset + offset + bytesToFetch)
                var chunkOffset = 0

                val currentBytes = remainingBytes + chunk

                while (chunkOffset < currentBytes.size) {
                    if (currentBytes.size - chunkOffset < 46) { // Not enough bytes for a full record
                        remainingBytes = currentBytes.drop(chunkOffset).toByteArray()
                        break
                    }

                    val entry = parseCentralDirectoryEntry(currentBytes.toIndexed().drop(chunkOffset))
                    val entrySize = 46 + entry.fileName.length

                    if (currentBytes.size - chunkOffset < entrySize) { // Not enough bytes for the full entry
                        remainingBytes = currentBytes.drop(chunkOffset).toByteArray()
                        break
                    }

                    emit(entry)
                    chunkOffset += entrySize
                    remainingBytes = ByteArray(0)
                }
                offset += bytesToFetch
            }
        }
    }

    suspend fun getCentralDirectory(url: String): Indexed<ZipEntry> {
        val entries = mutableListOf<ZipEntry>()
        streamCentralDirectory(url).collect { entries.add(it) }
        return entries.toIndexed()
    }

    suspend fun extractFile(url: String, entry: ZipEntry): Indexed<Byte> {
        val localHeaderBuffer = httpClient.fetchRange(url, entry.localHeaderOffset, entry.localHeaderOffset + LOCAL_FILE_HEADER_SIZE)
        val localHeader = parseLocalFileHeader(localHeaderBuffer.toIndexed())

        val compressedDataOffset = entry.localHeaderOffset + LOCAL_FILE_HEADER_SIZE + localHeader.fileNameLength + localHeader.extraFieldLength
        val compressedData = httpClient.fetchRange(url, compressedDataOffset, compressedDataOffset + entry.compressedSize)

        return Zlib.decompress(compressedData.toIndexed())
    }

    private fun parseLocalFileHeader(headerData: Indexed<Byte>): LocalFileHeader {
        return LocalFileHeader(
            fileNameLength = headerData.getShort(26),
            extraFieldLength = headerData.getShort(28)
        )
    }

    private data class LocalFileHeader(
        val fileNameLength: Short,
        val extraFieldLength: Short
    )

    private fun findEocdRecord(buffer: Indexed<Byte>): Long {
        for (i in buffer.size - EOCD_RECORD_SIZE downTo 0) {
            if (buffer[i] == 0x50.toByte() && buffer[i + 1] == 0x4b.toByte() && buffer[i + 2] == 0x05.toByte() && buffer[i + 3] == 0x06.toByte()) {
                return i.toLong()
            }
        }
        return -1L
    }

    private fun parseEocd(eocdData: Indexed<Byte>): EocdRecord {
        return EocdRecord(
            diskNumber = eocdData.getShort(4),
            startDiskNumber = eocdData.getShort(6),
            entriesOnDisk = eocdData.getShort(8),
            totalEntries = eocdData.getShort(10),
            centralDirectorySize = eocdData.getInt(12),
            centralDirectoryOffset = eocdData.getInt(16),
            commentLength = eocdData.getShort(20)
        )
    }

    private fun parseCentralDirectory(directoryData: Indexed<Byte>): Indexed<ZipEntry> {
        val entries = mutableListOf<ZipEntry>()
        var offset = 0
        while (offset < directoryData.size) {
            val entry = parseCentralDirectoryEntry(directoryData.drop(offset))
            entries.add(entry)
            offset += 46 + entry.fileName.length // 46 is the fixed size of a central directory entry
        }
        return entries.toIndexed()
    }

    private fun parseCentralDirectoryEntry(entryData: Indexed<Byte>): ZipEntry {
        val fileNameLength = entryData.getShort(28)
        val extraFieldLength = entryData.getShort(30)
        val fileCommentLength = entryData.getShort(32)

        val fileNameBytes = ByteArray(fileNameLength.toInt())
        for (i in 0 until fileNameLength) {
            fileNameBytes[i] = entryData[46 + i]
        }

        return ZipEntry(
            fileName = fileNameBytes.decodeToString(),
            compressedSize = entryData.getInt(20).toLong(),
            uncompressedSize = entryData.getInt(24).toLong(),
            localHeaderOffset = entryData.getInt(42).toLong()
        )
    }

    private data class EocdRecord(
        val diskNumber: Short,
        val startDiskNumber: Short,
        val entriesOnDisk: Short,
        val totalEntries: Short,
        val centralDirectorySize: Int,
        val centralDirectoryOffset: Int,
        val commentLength: Short
    )

    companion object {
        private const val EOCD_RECORD_SIZE = 22
        private const val LOCAL_FILE_HEADER_SIZE = 30
    }

    // Helper functions to read little-endian values from a Indexed<Byte>
    private fun Indexed<Byte>.getShort(offset: Int): Short {
        return ((this[offset + 1].toInt() and 0xFF) shl 8 or (this[offset].toInt() and 0xFF)).toShort()
    }

    private fun Indexed<Byte>.getInt(offset: Int): Int {
        return ((this[offset + 3].toInt() and 0xFF) shl 24 or
                ((this[offset + 2].toInt() and 0xFF) shl 16) or
                ((this[offset + 1].toInt() and 0xFF) shl 8) or
                (this[offset].toInt() and 0xFF))
    }
}
