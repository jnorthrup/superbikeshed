@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.torrent.simulation

import borg.trikeshed.lib.*
import borg.trikeshed.torrent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext

// Torrent type aliases - imported from TorrentKettle

/**
 * File System Simulation
 * 
 * Simulates file system operations for torrent downloads:
 * - Piece storage and retrieval
 * - File assembly
 * - Directory management
 * - All operations use coroutine context wiring
 */
class FileSystemSimulation(
    internal val context: CoroutineContext = Dispatchers.IO
) {
    
    // File system state
    internal val pieceStorage = mutableMapOf<InfoHash, MutableMap<Int, ByteIndexed>>()
    internal val fileStorage = mutableMapOf<String, SimulatedFile>()
    internal val directoryStructure = mutableMapOf<String, MutableSet<String>>()
    
    // Communication channels
    internal val fileSystemChannel = Channel<FileSystemEvent>(capacity = 1000)
    internal val fileSystemScope = CoroutineScope(context + CoroutineName("filesystem-sim"))
    
    /**
     * Start file system simulation
     */
    suspend fun start() = withContext(context) {
        println("💾 Starting File System Simulation")
        
        // Start file system event processor
        fileSystemScope.launch {
            processFileSystemEvents()
        }
        
        // Create initial directory structure
        createInitialDirectories()
        
        println("✅ File System Simulation started")
    }
    
    /**
     * Create initial directory structure
     */
    internal suspend fun createInitialDirectories() = withContext(context) {
        val directories = listOf(
            "downloads",
            "downloads/torrents",
            "downloads/http",
            "temp",
            "temp/pieces"
        )
        
        directories.forEach { dir ->
            directoryStructure[dir] = mutableSetOf()
        }
        
        println("📁 Created ${directories.size} initial directories")
    }
    
    /**
     * Store a piece for a torrent
     */
    suspend fun storePiece(
        infoHash: InfoHash,
        pieceIndex: Int,
        data: ByteIndexed
    ) = withContext(context) {
        // Simulate file I/O delay
        delay((5..50).random().toLong())
        
        pieceStorage.getOrPut(infoHash) { mutableMapOf() }[pieceIndex] = data
        
        fileSystemChannel.send(FileSystemEvent.PieceStored(infoHash, pieceIndex, data.component1()))
        
        println("💾 Stored piece $pieceIndex (${data.component1()} bytes) for torrent")
    }
    
    /**
     * Retrieve a piece for a torrent
     */
    suspend fun retrievePiece(
        infoHash: InfoHash,
        pieceIndex: Int
    ): ByteIndexed? = withContext(context) {
        // Simulate file I/O delay
        delay((2..20).random().toLong())
        
        val piece = pieceStorage[infoHash]?.get(pieceIndex)
        
        if (piece != null) {
            fileSystemChannel.send(FileSystemEvent.PieceRetrieved(infoHash, pieceIndex, piece.component1()))
            println("📖 Retrieved piece $pieceIndex (${piece.component1()} bytes)")
        } else {
            fileSystemChannel.send(FileSystemEvent.PieceNotFound(infoHash, pieceIndex))
            println("❌ Piece $pieceIndex not found")
        }
        
        return@withContext piece
    }
    
    /**
     * Assemble complete file from pieces
     */
    suspend fun assembleFile(
        infoHash: InfoHash,
        torrent: SimulatedTorrent,
        outputPath: String
    ): SimulatedFile = withContext(context) {
        println("🔧 Assembling file: $outputPath")
        
        // Simulate file assembly delay
        delay((100..500).random().toLong())
        
        val pieces = pieceStorage[infoHash] ?: emptyMap()
        val totalSize = pieces.values.sumOf { it.component1() }
        
        // Create assembled file data (simulated)
        val assembledData = \1 j { \2: Int ->
            val pieceIndex = i / torrent.pieceSize
            val offsetInPiece = i % torrent.pieceSize
            pieces[pieceIndex]?.getOrNull(offsetInPiece) ?: 0.toByte()
        }
        
        val file = SimulatedFile(
            path = outputPath,
            size = totalSize.toLong(),
            data = assembledData,
            createdAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            isComplete = pieces.size == torrent.totalPieces
        )
        
        fileStorage[outputPath] = file
        
        // Add to directory structure
        val directory = outputPath.substringBeforeLast("/", "downloads")
        directoryStructure.getOrPut(directory) { mutableSetOf() }.add(outputPath)
        
        fileSystemChannel.send(FileSystemEvent.FileAssembled(file))
        
        println("✅ File assembled: $outputPath (${file.size} bytes, ${pieces.size}/${torrent.totalPieces} pieces)")
        return@withContext file
    }
    
    /**
     * Check if torrent is complete
     */
    suspend fun isTorrentComplete(infoHash: InfoHash, totalPieces: Int): Boolean = withContext(context) {
        val pieces = pieceStorage[infoHash] ?: emptyMap()
        return pieces.size >= totalPieces
    }
    
    /**
     * Get torrent progress
     */
    suspend fun getTorrentProgress(infoHash: InfoHash, totalPieces: Int): TorrentProgress = withContext(context) {
        val pieces = pieceStorage[infoHash] ?: emptyMap()
        val downloadedPieces = pieces.size
        val downloadedBytes = pieces.values.sumOf { it.component1() }
        
        return TorrentProgress(
            downloadedPieces = downloadedPieces,
            totalPieces = totalPieces,
            downloadedBytes = downloadedBytes.toLong(),
            completionPercentage = (downloadedPieces.toDouble() / totalPieces) * 100.0
        )
    }
    
    /**
     * Create directory
     */
    suspend fun createDirectory(path: String) = withContext(context) {
        directoryStructure[path] = mutableSetOf()
        fileSystemChannel.send(FileSystemEvent.DirectoryCreated(path))
        println("📁 Created directory: $path")
    }
    
    /**
     * List directory contents
     */
    suspend fun listDirectory(path: String): List<String> = withContext(context) {
        return@withContext directoryStructure[path]?.toList() ?: emptyList()
    }
    
    /**
     * Delete file
     */
    suspend fun deleteFile(path: String): Boolean = withContext(context) {
        val file = fileStorage.remove(path)
        if (file != null) {
            // Remove from directory structure
            val directory = path.substringBeforeLast("/", "downloads")
            directoryStructure[directory]?.remove(path)
            
            fileSystemChannel.send(FileSystemEvent.FileDeleted(path))
            println("🗑️ Deleted file: $path")
            return@withContext true
        }
        return@withContext false
    }
    
    /**
     * Process file system events
     */
    internal suspend fun processFileSystemEvents() = withContext(context) {
        try {
            for (event in fileSystemChannel) {
                when (event) {
                    is FileSystemEvent.PieceStored -> {
                        handlePieceStored(event)
                    }
                    is FileSystemEvent.PieceRetrieved -> {
                        handlePieceRetrieved(event)
                    }
                    is FileSystemEvent.PieceNotFound -> {
                        handlePieceNotFound(event)
                    }
                    is FileSystemEvent.FileAssembled -> {
                        handleFileAssembled(event)
                    }
                    is FileSystemEvent.DirectoryCreated -> {
                        handleDirectoryCreated(event)
                    }
                    is FileSystemEvent.FileDeleted -> {
                        handleFileDeleted(event)
                    }
                }
            }
        } catch (e: Exception) {
            println("❌ File system event processing error: ${e.message}")
        }
    }
    
    // Event handlers
    internal suspend fun handlePieceStored(event: FileSystemEvent.PieceStored) = withContext(context) {
        // Piece stored successfully
    }
    
    internal suspend fun handlePieceRetrieved(event: FileSystemEvent.PieceRetrieved) = withContext(context) {
        // Piece retrieved successfully
    }
    
    internal suspend fun handlePieceNotFound(event: FileSystemEvent.PieceNotFound) = withContext(context) {
        println("❌ File system: Piece ${event.pieceIndex} not found")
    }
    
    internal suspend fun handleFileAssembled(event: FileSystemEvent.FileAssembled) = withContext(context) {
        println("✅ File system: File assembled - ${event.file.path}")
    }
    
    internal suspend fun handleDirectoryCreated(event: FileSystemEvent.DirectoryCreated) = withContext(context) {
        println("📁 File system: Directory created - ${event.path}")
    }
    
    internal suspend fun handleFileDeleted(event: FileSystemEvent.FileDeleted) = withContext(context) {
        println("🗑️ File system: File deleted - ${event.path}")
    }
    
    /**
     * Get file system statistics
     */
    suspend fun getFileSystemStats(): FileSystemStats = withContext(context) {
        val totalFiles = fileStorage.size
        val totalDirectories = directoryStructure.size
        val totalPieces = pieceStorage.values.sumOf { it.size }
        val totalData = fileStorage.values.sumOf { it.size }
        
        FileSystemStats(
            totalFiles = totalFiles,
            totalDirectories = totalDirectories,
            totalPieces = totalPieces,
            totalData = totalData,
            activeTorrents = pieceStorage.size
        )
    }
    
    /**
     * Stop file system simulation
     */
    suspend fun stop() = withContext(context) {
        println("🛑 Stopping File System Simulation")
        fileSystemScope.cancel()
        fileSystemChannel.close()
    }
}

// File system data classes
data class SimulatedFile(
    val path: String,
    val size: Long,
    val data: ByteIndexed,
    val createdAt: Long,
    val isComplete: Boolean
)

data class TorrentProgress(
    val downloadedPieces: Int,
    val totalPieces: Int,
    val downloadedBytes: Long,
    val completionPercentage: Double
)

data class FileSystemStats(
    val totalFiles: Int,
    val totalDirectories: Int,
    val totalPieces: Int,
    val totalData: Long,
    val activeTorrents: Int
)

// File system events
sealed class FileSystemEvent {
    data class PieceStored(val infoHash: InfoHash, val pieceIndex: Int, val size: Int) : FileSystemEvent()
    data class PieceRetrieved(val infoHash: InfoHash, val pieceIndex: Int, val size: Int) : FileSystemEvent()
    data class PieceNotFound(val infoHash: InfoHash, val pieceIndex: Int) : FileSystemEvent()
    data class FileAssembled(val file: SimulatedFile) : FileSystemEvent()
    data class DirectoryCreated(val path: String) : FileSystemEvent()
    data class FileDeleted(val path: String) : FileSystemEvent()
} 