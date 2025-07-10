@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.ljson

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.toIndexed
import borg.trikeshed.zlib.Zstd
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Provides random access capabilities for remote Zstd-compressed files.
 */
class ZstdFileAttention(internal val httpClient: HttpRangeClient) {

    /**
     * Represents an entry in the Zstd frame index.
     */
    data class ZstdFrameIndexEntry(
        val compressedOffset: Long,
        val uncompressedOffset: Long,
        val compressedSize: Long,
        val uncompressedSize: Long
    )

    /**
     * Builds an index of Zstd frames in a remote file.
     *
     * @param url The URL of the remote Zstd-compressed file.
     * @return A Indexed of ZstdFrameIndexEntry objects.
     */
    suspend fun buildIndex(url: String): Indexed<ZstdFrameIndexEntry> {
        val entries = mutableListOf<ZstdFrameIndexEntry>()
        var compressedOffset = 0L
        var uncompressedOffset = 0L

        // Read the file in chunks to find Zstd frames
        // This is a simplified implementation and may not be robust for all Zstd archives.
        // A more robust implementation would need to handle skippable frames and other edge cases.
        httpClient.streamBytes(url, 0, null).collect { chunk ->
            var i = 0
            while (i < chunk.size - 4) {
                if (chunk[i] == 0x28.toByte() && chunk[i + 1] == 0xB5.toByte() && chunk[i + 2] == 0x2F.toByte() && chunk[i + 3] == 0xFD.toByte()) {
                    // Found a Zstd frame
                    val frameHeader = chunk.toIndexed().drop(i)
                    val frameSize = Zstd.getDecompressedSize(frameHeader.toByteArray()).toLong()
                    val compressedSize = Zstd.findFrameCompressedSize(frameHeader.toByteArray()).toLong()

                    entries.add(
                        ZstdFrameIndexEntry(
                            compressedOffset = compressedOffset + i,
                            uncompressedOffset = uncompressedOffset,
                            compressedSize = compressedSize,
                            uncompressedSize = frameSize
                        )
                    )

                    uncompressedOffset += frameSize
                    i += compressedSize.toInt()
                } else {
                    i++
                }
            }
            compressedOffset += chunk.size
        }

        return entries.toIndexed()
    }

    /**
     * Reads a specific range of bytes from the uncompressed data of a remote Zstd-compressed file.
     *
     * @param url The URL of the remote Zstd-compressed file.
     * @param index The Zstd frame index.
     * @param startOffset The starting byte offset in the uncompressed data.
     * @param length The number of bytes to read.
     * @return A Flow of Indexed<Byte> containing the decompressed data.
     */
    suspend fun readRange(url: String, index: Indexed<ZstdFrameIndexEntry>, startOffset: Long, length: Long): Flow<Indexed<Byte>> = flow {
        val endOffset = startOffset + length

        // Find the frames that cover the requested range
        val relevantFrames = index.play.filter { entry ->
            (entry.uncompressedOffset < endOffset) && (entry.uncompressedOffset + entry.uncompressedSize > startOffset)
        }

        for (frame in relevantFrames) {
            val compressedData = httpClient.fetchRange(url, frame.compressedOffset, frame.compressedOffset + frame.compressedSize)
            val decompressedData = Zstd.decompress(compressedData.toIndexed())

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
