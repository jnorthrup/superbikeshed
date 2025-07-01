package borg.trikeshed.ljson

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.ByteSeries
import borg.trikeshed.lib.IntSeries
import borg.trikeshed.lib.toSeries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

typealias ByteOffset = Long
typealias PieceIndex = Int
typealias FileIndex = Int
typealias TorrentHash = ByteArray
typealias PieceHash = ByteArray

/**
 * Attention mechanism for selective piece downloading from torrents using aria2c
 * Enables reading specific byte ranges from torrent files without downloading the entire torrent
 * 
 * Uses immaculate type information for all operations
 */
class Aria2cAttention {
    
    data class TorrentPiece(
        val index: PieceIndex,
        val offset: ByteOffset,
        val length: Int,
        val hash: PieceHash
    )
    
    data class FileRange(
        val filePath: String,
        val startOffset: ByteOffset,
        val endOffset: ByteOffset,
        val pieces: Series<TorrentPiece>
    )
    
    /**
     * Aria2c RPC method parameters with complete type information
     */
    sealed class Aria2cMethod {
        data class AddTorrent(
            val torrent: TorrentHash,
            val options: Map<String, String> = emptyMap()
        ) : Aria2cMethod()
        
        data class GetFiles(
            val gid: String
        ) : Aria2cMethod()
        
        data class GetPeers(
            val gid: String
        ) : Aria2cMethod()
        
        data class SelectFile(
            val gid: String,
            val fileIndexes: IntSeries
        ) : Aria2cMethod()
    }
    
    /**
     * Download specific pieces from a torrent that contain the requested byte range
     * Returns ByteSeries with complete type information
     */
    suspend fun fetchRange(
        torrentUrl: String,
        filePath: String,
        startOffset: ByteOffset,
        endOffset: ByteOffset,
        outputDir: String = "/tmp/aria2c-attention"
    ): ByteSeries = suspendCoroutine { continuation ->
        
        val dir: File = File(outputDir)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        
        // Build aria2c command with explicit types
        val args: Series<String> = listOf(
            "aria2c",
            "--select-file=$filePath",
            "--piece-length=1M",
            "--seed-time=0",
            "--bt-stop-timeout=60",
            "--enable-dht=true",
            "--bt-enable-lpd=true",
            "--follow-torrent=mem",
            "--file-allocation=none",
            "--dir=$outputDir",
            "--console-log-level=warn",
            torrentUrl
        ).toSeries()
        
        val processBuilder: ProcessBuilder = ProcessBuilder(args.toList())
        val process: Process = processBuilder.start()
        
        val output: String = process.inputStream.bufferedReader().readText()
        val errors: String = process.errorStream.bufferedReader().readText()
        val exitCode: Int = process.waitFor()
        
        if (exitCode == 0) {
            val downloadedFile: File = File(outputDir, filePath)
            if (downloadedFile.exists()) {
                val bytes: ByteArray = downloadedFile.inputStream().use { stream ->
                    stream.skip(startOffset)
                    val length: Int = (endOffset - startOffset).toInt()
                    val buffer: ByteArray = ByteArray(length)
                    stream.read(buffer)
                    buffer
                }
                
                downloadedFile.delete()
                
                // Return as ByteSeries with explicit type in join
                val result: ByteSeries = bytes.size j { i: Int -> bytes[i] }
                continuation.resume(result)
            } else {
                throw RuntimeException("Downloaded file not found: $filePath")
            }
        } else {
            throw RuntimeException("aria2c failed with exit code $exitCode\nOutput: $output\nErrors: $errors")
        }
    }
    
    /**
     * Stream specific byte ranges from a torrent file
     */
    fun streamBytes(
        torrentUrl: String,
        filePath: String,
        startOffset: Long,
        endOffset: Long,
        chunkSize: Int = 1024 * 1024  // 1MB chunks
    ): Flow<Indexed<Byte>> = flow {
        var currentOffset = startOffset
        
        while (currentOffset < endOffset) {
            val chunkEnd = minOf(currentOffset + chunkSize, endOffset)
            val chunk = fetchRange(torrentUrl, filePath, currentOffset, chunkEnd)
            emit(chunk)
            currentOffset = chunkEnd
        }
    }
    
    /**
     * Download only the torrent metadata (no actual file content)
     */
    suspend fun fetchTorrentMetadata(torrentUrl: String): ByteArray {
        val tempFile = File.createTempFile("torrent", ".torrent")
        
        try {
            val processBuilder = ProcessBuilder(
                "aria2c",
                "--bt-metadata-only=true",   // Only download metadata
                "--bt-save-metadata=true",   // Save .torrent file
                "--dir=${tempFile.parent}",
                "--out=${tempFile.name}",
                torrentUrl
            )
            
            val process = processBuilder.start()
            val exitCode = process.waitFor()
            
            if (exitCode == 0 && tempFile.exists()) {
                return tempFile.readBytes()
            } else {
                throw RuntimeException("Failed to fetch torrent metadata")
            }
        } finally {
            tempFile.delete()
        }
    }
}