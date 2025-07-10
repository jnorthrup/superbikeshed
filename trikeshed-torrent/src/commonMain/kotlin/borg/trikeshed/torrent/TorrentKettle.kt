@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.torrent

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.ByteIndexed

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
import borg.trikeshed.lib.InfoHash
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.CoroutineContext

// Type aliases for torrent domain
typealias PieceIndex = Int
typealias PeerAddress = String

typealias PieceHash = ByteArray
typealias ByteOffset = Long
typealias ChunkSize = Int
typealias IntIndexed = Indexed<Int>

/**
 * Torrent Kettle - Segregates peer connections into coroutine context boxes
 * based on chunk access patterns and IO capabilities
 * 
 * Different "kettles" for different use cases:
 * - StreamingKettle: Sequential piece access for video/audio streaming
 * - RandomAccessKettle: Sparse piece access for seeking in large files
 * - ArchiveKettle: Selective file extraction from multi-file torrents
 * - BulkKettle: Full torrent download with optimal piece selection
 */
sealed class TorrentKettle {
    
    /**
     * Chunk access strategy determines piece priority and prefetch behavior
     */
    sealed class ChunkStrategy {
        data class Sequential(
            val startPiece: PieceIndex,
            val bufferAhead: Int = 5,
            val bufferBehind: Int = 2
        ) : ChunkStrategy()
        
        data class RandomAccess(
            val hotspots: Indexed<Int>,  // Frequently accessed pieces
            val cacheSize: Int = 20
        ) : ChunkStrategy()
        
        data class Selective(
            val fileIndexes: Indexed<Int>,
            val priorityMap: Join<PieceIndex, Int>  // Piece -> Priority
        ) : ChunkStrategy()
        
        object Bulk : ChunkStrategy()
    }
    
    /**
     * IO strategy determines the underlying transport mechanism
     */
    sealed class IOStrategy {
        data class UringIO(
            val ringSize: Int = 256,
            val sqPollThread: Boolean = true
        ) : IOStrategy()
        
        data class NioIO(
            val selectorThreads: Int = 2,
            val directBuffers: Boolean = true
        ) : IOStrategy()
        
        data class KqueueIO(
            val maxEvents: Int = 1024
        ) : IOStrategy()
        
        object StandardIO : IOStrategy()
    }
    
    /**
     * Peer connection boxed with its coroutine context
     */
    data class PeerBox(
        val peer: PeerAddress,
        val context: CoroutineContext,
        val scope: CoroutineScope,
        val inputChannel: Channel<PeerMessage>,
        val outputChannel: Channel<PeerMessage>
    )
    
    /**
     * Piece data with verification
     */
    data class Piece(
        val index: PieceIndex,
        val data: ByteIndexed,
        val hash: PieceHash,
        val verified: Boolean
    )
    
    /**
     * Messages between peers
     */
    sealed class PeerMessage {
        data class Have(val piece: PieceIndex) : PeerMessage()
        data class Bitfield(val pieces: BooleanArray) : PeerMessage()
        data class Request(val piece: PieceIndex, val offset: Int, val length: Int) : PeerMessage()
        data class Block(val piece: PieceIndex, val offset: Int, val data: ByteIndexed) : PeerMessage()
        object Choke : PeerMessage()
        object Unchoke : PeerMessage()
        object Interested : PeerMessage()
        object NotInterested : PeerMessage()
    }
}

/**
 * Streaming kettle for sequential media playback
 */
class StreamingKettle(
    internal val infoHash: InfoHash,
    internal val totalPieces: Int,
    internal val pieceSize: ChunkSize,
    internal val ioStrategy: TorrentKettle.IOStrategy = TorrentKettle.IOStrategy.NioIO()
) : TorrentKettle() {
    
    internal val peerBoxes: MutableMap<PeerAddress, PeerBox> = mutableMapOf()
    internal val pieceBuffer: MutableMap<PieceIndex, Piece> = mutableMapOf()
    internal var playheadPiece: PieceIndex = 0
    
    /**
     * Stream pieces starting from a specific position
     */
    fun streamFrom(startPiece: PieceIndex): Flow<ByteIndexed> = flow {
        playheadPiece = startPiece
        val strategy = ChunkStrategy.Sequential(startPiece)
        
        coroutineScope {
            // Prefetch ahead of playhead
            launch {
                while (isActive) {
                    val prefetchRange: Indexed<Int> = (playheadPiece until minOf(
                        playheadPiece + strategy.bufferAhead,
                        totalPieces
                    )) j { it }
                    
                    requestPieces(prefetchRange)
                    delay(100) // Adjust prefetch rate
                }
            }
            
            // Emit pieces as they become available
            while (playheadPiece < totalPieces) {
                val piece = waitForPiece(playheadPiece)
                emit(piece.data)
                playheadPiece++
                
                // Clean up old pieces
                val cutoff = playheadPiece - strategy.bufferBehind
                pieceBuffer.keys.filter { it < cutoff }.forEach {
                    pieceBuffer.remove(it)
                }
            }
        }
    }
    
    /**
     * Add a peer with appropriate coroutine context
     */
    suspend fun addPeer(peer: PeerAddress) {
        val context: CoroutineContext = when (ioStrategy) {
            is IOStrategy.UringIO -> Dispatchers.IO + CoroutineName("uring-$peer")
            is IOStrategy.NioIO -> Dispatchers.IO + CoroutineName("nio-$peer")
            is IOStrategy.KqueueIO -> Dispatchers.IO + CoroutineName("kqueue-$peer")
            is IOStrategy.StandardIO -> Dispatchers.IO + CoroutineName("std-$peer")
        }
        
        val scope = CoroutineScope(context)
        val peerBox = PeerBox(
            peer = peer,
            context = context,
            scope = scope,
            inputChannel = Channel(capacity = 100),
            outputChannel = Channel(capacity = 100)
        )
        
        peerBoxes[peer] = peerBox
        
        // Launch peer handler in its context box
        scope.launch {
            handlePeer(peerBox)
        }
    }
    
    internal suspend fun handlePeer(peerBox: PeerBox) {
        // Peer protocol handling in isolated context
        for (message in peerBox.inputChannel) {
            when (message) {
                is PeerMessage.Block -> {
                    // Store received piece
                    pieceBuffer[message.piece] = Piece(
                        index = message.piece,
                        data = message.data,
                        hash = ByteArray(20), // TODO: Calculate hash
                        verified = false // TODO: Verify
                    )
                }
                is PeerMessage.Have -> {
                    // Update peer's piece availability
                }
                // Handle other messages...
                else -> {}
            }
        }
    }
    
    internal suspend fun requestPieces(pieces: Indexed<Int>) {
        // Distribute piece requests among peers
        val availablePeers: Indexed<PeerBox> = peerBoxes.values.filter { 
            it.outputChannel.trySend(PeerMessage.Interested).isSuccess 
        }.toIdx()
        
        if (availablePeers.a == 0) return
        
        for (i in 0 until pieces.a) {
            val piece: Int = pieces.b.invoke(i)
            if (!pieceBuffer.containsKey(piece)) {
                val peer: PeerBox = availablePeers.b.invoke(i % availablePeers.a)
                peer.outputChannel.send(
                    PeerMessage.Request(piece, 0, pieceSize)
                )
            }
        }
    }
    
    internal suspend fun waitForPiece(index: PieceIndex): Piece {
        while (!pieceBuffer.containsKey(index)) {
            delay(50)
        }
        return pieceBuffer[index]!!
    }
}

/**
 * Random access kettle for seeking within large files
 */
class RandomAccessKettle(
    internal val infoHash: InfoHash,
    internal val totalPieces: Int,
    internal val pieceSize: ChunkSize,
    internal val ioStrategy: TorrentKettle.IOStrategy = TorrentKettle.IOStrategy.UringIO()
) : TorrentKettle() {
    
    internal val peerBoxes: MutableMap<PeerAddress, PeerBox> = mutableMapOf()
    internal val pieceCache: MutableMap<PieceIndex, Piece> = mutableMapOf()
    internal val accessHistory: MutableList<PieceIndex> = mutableListOf()
    
    /**
     * Fetch a specific byte range, potentially spanning multiple pieces
     */
    suspend fun fetchRange(startByte: ByteOffset, endByte: ByteOffset): ByteIndexed {
        val startPiece: PieceIndex = (startByte / pieceSize).toInt()
        val endPiece: PieceIndex = (endByte / pieceSize).toInt()
        
        val pieces: Indexed<Piece> = (startPiece..endPiece) j { pieceIdx ->
            fetchPiece(pieceIdx)
        }
        
        // Combine pieces and extract requested byte range
        val totalSize: Int = pieces.a * pieceSize
        val result: ByteIndexed = totalSize j { byteIdx ->
            val pieceIdx = byteIdx / pieceSize
            val offsetInPiece = byteIdx % pieceSize
            pieces.b.invoke(pieceIdx).data[offsetInPiece]
        }
        
        val startOffset = (startByte % pieceSize).toInt()
        val length = (endByte - startByte).toInt()
        
        return length j { i -> result[startOffset + i] }
    }
    
    internal suspend fun fetchPiece(index: PieceIndex): Piece {
        // Check cache first
        pieceCache[index]?.let { return it }
        
        // Request from peers using io_uring for low latency
        val piece = requestPieceWithUring(index)
        
        // Update cache with LRU eviction
        updateCache(index, piece)
        
        return piece
    }
    
    internal suspend fun requestPieceWithUring(index: PieceIndex): Piece {
        // TODO: Implement io_uring-based piece fetching
        return Piece(index, ByteArray(pieceSize) { it.toByte() }, ByteArray(20), true)
    }
    
    internal fun updateCache(index: PieceIndex, piece: Piece) {
        pieceCache[index] = piece
        accessHistory.add(index)
        
        // LRU eviction
        val strategy = ChunkStrategy.RandomAccess(
            hotspots = (accessHistory.takeLast(10).size) j { i -> accessHistory.takeLast(10)[i] },
            cacheSize = 20
        )
        
        if (pieceCache.size > strategy.cacheSize) {
            val lru: PieceIndex = accessHistory.first()
            pieceCache.remove(lru)
            accessHistory.removeAt(0)
        }
    }
}

/**
 * Factory for creating appropriate kettle based on use case
 */
object TorrentKettleFactory {
    
    fun createStreamingKettle(
        infoHash: InfoHash,
        totalPieces: Int,
        pieceSize: ChunkSize,
        ioStrategy: TorrentKettle.IOStrategy = detectOptimalIOStrategy()
    ): StreamingKettle {
        return StreamingKettle(infoHash, totalPieces, pieceSize, ioStrategy)
    }
    
    fun createRandomAccessKettle(
        infoHash: InfoHash,
        totalPieces: Int,
        pieceSize: ChunkSize,
        ioStrategy: TorrentKettle.IOStrategy = detectOptimalIOStrategy()
    ): RandomAccessKettle {
        return RandomAccessKettle(infoHash, totalPieces, pieceSize, ioStrategy)
    }
    
    internal fun detectOptimalIOStrategy(): TorrentKettle.IOStrategy {
        return when {
            isLinuxWithUring() -> TorrentKettle.IOStrategy.UringIO()
            isMacOS() -> TorrentKettle.IOStrategy.KqueueIO()
            else -> TorrentKettle.IOStrategy.NioIO()
        }
    }
    
    internal fun isLinuxWithUring(): Boolean {
        return borg.trikeshed.lib.platform.PlatformDetection.hasIoUringSupport()
    }
    
    internal fun isMacOS(): Boolean {
        return borg.trikeshed.lib.platform.PlatformDetection.getPlatformInfo().os == borg.trikeshed.lib.platform.OperatingSystem.MACOS
    }
}