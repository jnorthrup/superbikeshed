package borg.trikeshed.ljson

import borg.trikeshed.lib.Indexed
import kotlinx.coroutines.flow.Flow

/**
 * Provides specialized random access capabilities for remote gzipped files by
 * leveraging the KzranGzipReader.
 */
class GzipFileAttention(private val httpClient: HttpRangeClient) {

    private val kzranReader = KzranGzipReader(httpClient)

    /**
     * Reads a specific range of bytes from the uncompressed data of a remote gzipped file.
     *
     * @param url The URL of the remote gzipped file.
     * @param startOffset The starting byte offset in the uncompressed data.
     * @param length The number of bytes to read.
     * @return A Flow of Indexed<Byte> containing the decompressed data.
     */
    suspend fun readRange(url: String, startOffset: Long, length: Long): Flow<Indexed<Byte>> {
        val index = kzranReader.buildIndex(url)
        val frame = AttentionFrame(startOffset, startOffset + length)
        return kzranReader.readAttentionFrames(url, index, listOf(frame))
    }
}
