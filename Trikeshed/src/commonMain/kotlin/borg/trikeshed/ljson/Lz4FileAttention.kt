package borg.trikeshed.ljson

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.toIndexed
import borg.trikeshed.zlib.Lz4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Provides random access capabilities for remote LZ4-compressed files.
 */
class Lz4FileAttention(private val httpClient: HttpRangeClient) {

    /**
     * Represents an entry in the LZ4 frame index.
     */
    data class Lz4FrameIndexEntry(
        val compressedOffset: Long,
        val uncompressedOffset: Long,
        val compressedSize: Long,
        val uncompressedSize: Long
    )

    /**
     * Builds an index of LZ4 frames in a remote file.
     *
     * @param url The URL of the remote LZ4-compressed file.
     * @return A Indexed of Lz4FrameIndexEntry objects.
     */
    suspend fun buildIndex(url: String): Indexed<Lz4FrameIndexEntry> {
        val entries = mutableListOf<Lz4FrameIndexEntry>()
        var compressedOffset = 0L
        var uncompressedOffset = 0L

        // Read the file in chunks to find LZ4 frames
        httpClient.streamBytes(url, 0, null).collect { chunk ->
            var i = 0
            while (i < chunk.size - 4) { // LZ4 frame magic number is 4 bytes
                // LZ4 Frame Format Magic Number: 0x184D2204
                if (chunk[i] == 0x04.toByte() && chunk[i + 1] == 0x22.toByte() && chunk[i + 2] == 0x4D.toByte() && chunk[i + 3] == 0x18.toByte()) {
                    // Found an LZ4 frame
                    val (actualCompressedSize, uncompressedSize) = Lz4.parseFrameHeader(chunk.toIndexed().drop(i))

                    entries.add(
                        Lz4FrameIndexEntry(
                            compressedOffset = compressedOffset + i,
                            uncompressedOffset = uncompressedOffset,
                            compressedSize = actualCompressedSize, // Now accurate
                            uncompressedSize = uncompressedSize
                        )
                    )

                    uncompressedOffset += uncompressedSize
                    i += actualCompressedSize.toInt() // Move past the actual frame size
                } else {
                    i++
                }
            }
            compressedOffset += chunk.size
        }
        }

        return entries.toIndexed()
    }

    /**
     * Reads a specific range of bytes from the uncompressed data of a remote LZ4-compressed file.
     *
     * @param url The URL of the remote LZ4-compressed file.
     * @param index The LZ4 frame index.
     * @param startOffset The starting byte offset in the uncompressed data.
     * @param length The number of bytes to read.
     * @return A Flow of Indexed<Byte> containing the decompressed data.
     */
    suspend fun readRange(url: String, index: Indexed<Lz4FrameIndexEntry>, startOffset: Long, length: Long): Flow<Indexed<Byte>> = flow {
        val endOffset = startOffset + length

        // Find the frames that cover the requested range
        val relevantFrames = index.play.filter { entry ->
            (entry.uncompressedOffset < endOffset) && (entry.uncompressedOffset + entry.uncompressedSize > startOffset)
        }

        for (frame in relevantFrames) {
            // Fetch the entire LZ4 frame
            val compressedData = httpClient.fetchRange(url, frame.compressedOffset, frame.compressedOffset + frame.compressedSize)
            val decompressedData = Lz4.decompressFrame(compressedData.toIndexed())

            // Calculate the portion of the decompressed data that falls within the requested range
            val relativeStart = maxOf(0L, startOffset - frame.uncompressedOffset)
            val relativeEnd = minOf(frame.uncompressedSize, endOffset - frame.uncompressedOffset)
            val bytesToEmit = (relativeEnd - relativeStart).toInt()

            if (bytesToEmit > 0) {
                emit(decompressedData.drop(relativeStart.toInt()).take(bytesToEmit))
            }
        }
    }
}
