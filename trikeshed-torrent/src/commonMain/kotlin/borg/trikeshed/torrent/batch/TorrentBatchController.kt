@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.torrent.batch

import borg.trikeshed.torrent.TorrentInfo
import borg.trikeshed.torrent.TorrentStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
data class BatchConfig(
    val name: String,
    val maxConcurrent: Int = 3,
    val priority: BatchPriority = BatchPriority.NORMAL,
    val mediaPlayerIntegration: Boolean = false,
    val autoPlayThreshold: Double = 0.1, // 10% downloaded
    val bandwidthLimit: Long? = null, // bytes per second
    val scheduleConfig: ScheduleConfig? = null
)

@Serializable
enum class BatchPriority {
    LOW, NORMAL, HIGH, CRITICAL
}

@Serializable
data class ScheduleConfig(
    val startTime: String? = null, // HH:mm format
    val endTime: String? = null,
    val daysOfWeek: List<Int> = emptyList(), // 0=Sunday, 1=Monday, etc.
    val timezone: String = "UTC"
)

@Serializable
data class BatchStatus(
    val batchId: String,
    val name: String,
    val status: BatchState,
    val torrentCount: Int,
    val activeCount: Int,
    val completedCount: Int,
    val totalProgress: Double,
    val totalSize: Long,
    val downloadedSize: Long,
    val downloadSpeed: Double,
    val eta: Duration?,
    val mediaPlayerState: MediaPlayerState? = null,
    val createdAt: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
    val updatedAt: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
)

@Serializable
enum class BatchState {
    QUEUED, ACTIVE, PAUSED, COMPLETED, ERROR, SCHEDULED
}

@Serializable
data class MediaPlayerState(
    val isPlaying: Boolean = false,
    val currentTorrentId: String? = null,
    val playProgress: Double = 0.0,
    val autoPlayEnabled: Boolean = false,
    val playlist: List<String> = emptyList() // torrent IDs in play order
)

class TorrentBatchController {
    internal val mutex = Mutex()
    internal val batches = mutableMapOf<String, TorrentBatch>()
    internal val batchQueue = mutableListOf<String>()
    internal val mediaPlayer = TorrentMediaPlayer()
    
    suspend fun createBatch(config: BatchConfig): String {
        return mutex.withLock {
            val batchId = "batch_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}_${config.name.hashCode()}"
            val batch = TorrentBatch(batchId, config)
            batches[batchId] = batch
            batchQueue.add(batchId)
            batchId
        }
    }
    
    suspend fun addTorrentToBatch(batchId: String, torrentInfo: TorrentInfo): Boolean {
        return mutex.withLock {
            val batch = batches[batchId] ?: return@withLock false
            batch.addTorrent(torrentInfo)
            true
        }
    }
    
    suspend fun startBatch(batchId: String): Boolean {
        return mutex.withLock {
            val batch = batches[batchId] ?: return@withLock false
            batch.start()
            if (batch.config.mediaPlayerIntegration) {
                mediaPlayer.enableForBatch(batchId)
            }
            true
        }
    }
    
    suspend fun pauseBatch(batchId: String): Boolean {
        return mutex.withLock {
            val batch = batches[batchId] ?: return@withLock false
            batch.pause()
            mediaPlayer.pauseForBatch(batchId)
            true
        }
    }
    
    suspend fun resumeBatch(batchId: String): Boolean {
        return mutex.withLock {
            val batch = batches[batchId] ?: return@withLock false
            batch.resume()
            if (batch.config.mediaPlayerIntegration) {
                mediaPlayer.resumeForBatch(batchId)
            }
            true
        }
    }
    
    suspend fun removeBatch(batchId: String): Boolean {
        return mutex.withLock {
            val batch = batches.remove(batchId) ?: return@withLock false
            batchQueue.remove(batchId)
            mediaPlayer.removeBatch(batchId)
            true
        }
    }
    
    suspend fun getBatchStatus(batchId: String): BatchStatus? {
        return mutex.withLock {
            val batch = batches[batchId] ?: return@withLock null
            batch.getStatus()
        }
    }
    
    suspend fun getAllBatchStatuses(): List<BatchStatus> {
        return mutex.withLock {
            batches.values.map { it.getStatus() }
        }
    }
    
    suspend fun reorderBatch(batchId: String, newPosition: Int): Boolean {
        return mutex.withLock {
            val currentIndex = batchQueue.indexOf(batchId)
            if (currentIndex == -1) return@withLock false
            
            batchQueue.removeAt(currentIndex)
            val insertIndex = newPosition.coerceIn(0, batchQueue.size)
            batchQueue.add(insertIndex, batchId)
            true
        }
    }
    
    suspend fun setBatchPriority(batchId: String, priority: BatchPriority): Boolean {
        return mutex.withLock {
            val batch = batches[batchId] ?: return@withLock false
            batch.setPriority(priority)
            // Reorder queue based on priority
            reorderQueueByPriority()
            true
        }
    }
    
    suspend fun getMediaPlayerState(batchId: String): MediaPlayerState? {
        return mutex.withLock {
            mediaPlayer.getState(batchId)
        }
    }
    
    suspend fun controlMediaPlayer(batchId: String, action: MediaPlayerAction): Boolean {
        return mutex.withLock {
            mediaPlayer.control(batchId, action)
        }
    }
    
    internal suspend fun reorderQueueByPriority() {
        batchQueue.sortByDescending { batches[it]?.config?.priority?.ordinal ?: 0 }
    }
    
    suspend fun processBatchQueue() {
        mutex.withLock {
            val activeBatches = batches.values.filter { it.getStatus().status == BatchState.ACTIVE }
            val maxConcurrent = batches.values.sumOf { it.config.maxConcurrent }
            
            if (activeBatches.size < maxConcurrent) {
                val nextBatchId = batchQueue.find { batchId ->
                    val batch = batches[batchId]
                    batch?.getStatus()?.status == BatchState.QUEUED
                }
                
                nextBatchId?.let { startBatch(it) }
            }
        }
    }
}

sealed class MediaPlayerAction {
    object Play : MediaPlayerAction()
    object Pause : MediaPlayerAction()
    object Stop : MediaPlayerAction()
    object Next : MediaPlayerAction()
    object Previous : MediaPlayerAction()
    data class Seek(val position: Double) : MediaPlayerAction() // 0.0 to 1.0
    data class SetVolume(val volume: Double) : MediaPlayerAction() // 0.0 to 1.0
    data class SetPlaylist(val torrentIds: List<String>) : MediaPlayerAction()
}

class TorrentBatch(
    val batchId: String,
    val config: BatchConfig
) {
    internal val torrents = mutableMapOf<String, TorrentInfo>()
    internal var status = BatchState.QUEUED
    internal var priority = config.priority
    
    fun addTorrent(torrentInfo: TorrentInfo) {
        torrents[torrentInfo.infoHash] = torrentInfo
    }
    
    fun start() {
        status = BatchState.ACTIVE
    }
    
    fun pause() {
        status = BatchState.PAUSED
    }
    
    fun resume() {
        status = BatchState.ACTIVE
    }
    
    fun setPriority(newPriority: BatchPriority) {
        priority = newPriority
    }
    
    fun getStatus(): BatchStatus {
        val totalSize = torrents.values.sumOf { it.totalSize }
        val downloadedSize = torrents.values.sumOf { it.downloadedSize }
        val progress = if (totalSize > 0) downloadedSize.toDouble() / totalSize else 0.0
        
        return BatchStatus(
            batchId = batchId,
            name = config.name,
            status = status,
            torrentCount = torrents.size,
            activeCount = torrents.values.count { it.status == TorrentStatus.DOWNLOADING },
            completedCount = torrents.values.count { it.status == TorrentStatus.COMPLETED },
            totalProgress = progress,
            totalSize = totalSize,
            downloadedSize = downloadedSize,
            downloadSpeed = torrents.values.sumOf { it.downloadSpeed },
            eta = calculateEta(),
            createdAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            updatedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )
    }
    
    internal fun calculateEta(): Duration? {
        val remainingSize = torrents.values.sumOf { it.totalSize - it.downloadedSize }
        val speed = torrents.values.sumOf { it.downloadSpeed }
        return if (speed > 0) (remainingSize / speed).seconds else null
    }
}

class TorrentMediaPlayer {
    internal val batchStates = mutableMapOf<String, MediaPlayerState>()
    
    fun enableForBatch(batchId: String) {
        batchStates[batchId] = MediaPlayerState(autoPlayEnabled = true)
    }
    
    fun pauseForBatch(batchId: String) {
        batchStates[batchId]?.let { state ->
            batchStates[batchId] = state.copy(isPlaying = false)
        }
    }
    
    fun resumeForBatch(batchId: String) {
        batchStates[batchId]?.let { state ->
            batchStates[batchId] = state.copy(isPlaying = true)
        }
    }
    
    fun removeBatch(batchId: String) {
        batchStates.remove(batchId)
    }
    
    fun getState(batchId: String): MediaPlayerState? {
        return batchStates[batchId]
    }
    
    fun control(batchId: String, action: MediaPlayerAction): Boolean {
        val state = batchStates[batchId] ?: return false
        
        batchStates[batchId] = when (action) {
            is MediaPlayerAction.Play -> state.copy(isPlaying = true)
            is MediaPlayerAction.Pause -> state.copy(isPlaying = false)
            is MediaPlayerAction.Stop -> state.copy(isPlaying = false, playProgress = 0.0)
            is MediaPlayerAction.Next -> {
                val currentIndex = state.playlist.indexOf(state.currentTorrentId)
                val nextIndex = (currentIndex + 1) % state.playlist.size
                state.copy(currentTorrentId = state.playlist.getOrNull(nextIndex))
            }
            is MediaPlayerAction.Previous -> {
                val currentIndex = state.playlist.indexOf(state.currentTorrentId)
                val prevIndex = if (currentIndex > 0) currentIndex - 1 else state.playlist.size - 1
                state.copy(currentTorrentId = state.playlist.getOrNull(prevIndex))
            }
            is MediaPlayerAction.Seek -> state.copy(playProgress = action.position.coerceIn(0.0, 1.0))
            is MediaPlayerAction.SetVolume -> state // Volume would be handled by actual media player
            is MediaPlayerAction.SetPlaylist -> state.copy(playlist = action.torrentIds)
        }
        
        return true
    }
} 