package borg.trikeshed.zip

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.a
import borg.trikeshed.lib.b
import borg.trikeshed.lib.j
import borg.trikeshed.net.quic.QuicConnection
import borg.trikeshed.net.quic.QuicStream
import borg.trikeshed.net.quic.QuicConfig
import borg.trikeshed.net.quic.DefaultQuicSessionCache
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

/**
 * ZipfileAttention is a component designed to intelligently handle large remote zip files.
 * It uses QUIC range requests to read the zip file's central directory without downloading the entire file.
 */
interface RemoteFileAccessor {
    suspend fun getFileSize(): Long
    suspend fun readRange(startByte: Long, endByte: Long): Indexed<Byte>
}

class RemoteFileAttention(
    private val remoteFileUrl: String,
    private val fileAccessor: RemoteFileAccessor
) {

    /**
     * Fetches the total size of the remote file using a HEAD request.
     * This is a placeholder and assumes the QUIC connection can handle HTTP HEAD requests.
     * In a real scenario, this would involve sending an HTTP HEAD request over a QUIC stream
     * and parsing the Content-Length header.
     */
    private suspend fun getRemoteFileSize(): Long {
        return fileAccessor.getFileSize()
    }

    /**
     * Reads a specific byte range from the remote file using QUIC range requests.
     */
    private suspend fun readRemoteFileRange(startByte: Long, endByte: Long): Indexed<Byte> {
        return fileAccessor.readRange(startByte, endByte)
    }

    /**
     * Locates and reads the Central Directory End Record (EOCD) of the zip file.
     * This record is typically at the end of the zip file.
     */
    private suspend fun findEOCDRecord(fileSize: Long): Pair<Long, Indexed<Byte>>? {
        val eocdSignature = Indexed(byteArrayOf(0x50, 0x4B, 0x05, 0x06)) // PK

        // Search the last 64KB for the EOCD signature
        val searchBufferSize = (64 * 1024).toLong() // 64 KB
        val searchStart = (fileSize - searchBufferSize).coerceAtLeast(0L)
        val buffer = readRemoteFileRange(searchStart, fileSize - 1)

        // Search for the EOCD signature in the buffer
        for (i in (buffer.a - eocdSignature.a) downTo 0) {
            var match = true
            for (j in 0 until eocdSignature.a) {
                if (buffer.b(i + j) != eocdSignature.b(j)) {
                    match = false
                    break
                }
            }
            if (match) {
                val eocdOffsetInFile = searchStart + i
                return Pair(eocdOffsetInFile, buffer.j(i, buffer.a - i))
            }
        }
        return null
    }

    /**
     * Parses the EOCD record to find the Central Directory offset and size.
     * This is a simplified parser and assumes a standard EOCD format.
     */
    private fun parseEOCDRecord(eocdRecord: Indexed<Byte>): Pair<Long, Long>? {
        // EOCD record structure (simplified for relevant fields):
        // Offset 12: Central Directory size (4 bytes)
        // Offset 16: Central Directory offset (4 bytes)

        if (eocdRecord.a < 22) { // Minimum size of EOCD record
            println("Error: EOCD record too short.")
            return null
        }

        val centralDirectorySize = readLittleEndianLong(eocdRecord, 12, 4)
        val centralDirectoryOffset = readLittleEndianLong(eocdRecord, 16, 4)

        return Pair(centralDirectoryOffset, centralDirectorySize)
    }

    /**
     * Reads a little-endian long from the Indexed<Byte> at a given offset and length.
     */
    private fun readLittleEndianLong(data: Indexed<Byte>, offset: Int, length: Int): Long {
        var value = 0L
        for (i in 0 until length) {
            value = value or ((data.b(offset + i).toLong() and 0xFF) shl (8 * i))
        }
        return value
    }

    /**
     * Reads the Central Directory and extracts file information.
     * This is a simplified parser and only extracts file names and uncompressed sizes.
     */
    private suspend fun readCentralDirectory(centralDirectoryOffset: Long, centralDirectorySize: Long): List<ZipEntry> {
        val centralDirectoryBytes = readRemoteFileRange(centralDirectoryOffset, centralDirectoryOffset + centralDirectorySize - 1)
        val entries = mutableListOf<ZipEntry>()
        var currentOffset = 0

        while (currentOffset < centralDirectoryBytes.a) {
            // Central File Header signature: 0x02014B50
            val signature = readLittleEndianLong(centralDirectoryBytes, currentOffset, 4)
            if (signature != 0x02014B50L) {
                println("Warning: Invalid Central File Header signature at offset $currentOffset. Stopping parsing.")
                break
            }

            // Offset 28: File Name Length (2 bytes)
            val fileNameLength = readLittleEndianLong(centralDirectoryBytes, currentOffset + 28, 2).toInt()
            // Offset 30: Extra Field Length (2 bytes)
            val extraFieldLength = readLittleEndianLong(centralDirectoryBytes, currentOffset + 30, 2).toInt()
            // Offset 32: File Comment Length (2 bytes)
            val fileCommentLength = readLittleEndianLong(centralDirectoryBytes, currentOffset + 32, 2).toInt()
            // Offset 24: Uncompressed Size (4 bytes)
            val uncompressedSize = readLittleEndianLong(centralDirectoryBytes, currentOffset + 24, 4)
            // Offset 46: Relative Offset of Local File Header (4 bytes)
            val localHeaderOffset = readLittleEndianLong(centralDirectoryBytes, currentOffset + 42, 4)


            val fileNameBytes = centralDirectoryBytes.j(currentOffset + 46, fileNameLength)
            val fileName = fileNameBytes.toByteArray().decodeToString()

            entries.add(ZipEntry(fileName, uncompressedSize, localHeaderOffset))

            currentOffset += 46 + fileNameLength + extraFieldLength + fileCommentLength
        }
        return entries
    }

    /**
     * Initiates the process of reading the zip file's index and extracting file information.
     */
    suspend fun getZipFileIndex(): List<ZipEntry> {
        val fileSize = getRemoteFileSize()
        println("Remote file size: $fileSize bytes")

        val eocdResult = findEOCDRecord(fileSize)
        if (eocdResult == null) {
            println("Error: Central Directory End Record not found.")
            return emptyList()
        }

        val (eocdOffset, eocdRecord) = eocdResult
        println("EOCD record found at offset: $eocdOffset")

        val centralDirectoryInfo = parseEOCDRecord(eocdRecord)
        if (centralDirectoryInfo == null) {
            println("Error: Could not parse Central Directory information from EOCD record.")
            return emptyList()
        }

        val (centralDirectoryOffset, centralDirectorySize) = centralDirectoryInfo
        println("Central Directory offset: $centralDirectoryOffset, size: $centralDirectorySize")

        return readCentralDirectory(centralDirectoryOffset, centralDirectorySize)
    }

    /**
     * Represents a single entry in the zip file's Central Directory.
     */
    data class ZipEntry(
        val fileName: String,
        val uncompressedSize: Long,
        val localHeaderOffset: Long
    )
}

// Extension to convert Indexed<Byte> to ByteArray
public fun Indexed<Byte>.toByteArray(): ByteArray = ByteArray(a) { i -> b(i) }
