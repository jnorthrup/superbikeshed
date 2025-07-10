@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.torrent.rpc

import borg.trikeshed.lib.*
import borg.trikeshed.services.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import borg.trikeshed.torrent.batch.*
import borg.trikeshed.torrent.batch.MediaPlayerAction

/**
 * aria2c-style RPC Server for TrikeDownloader
 * 
 * Provides JSON-RPC interface similar to aria2c with:
 * - Download management (add, pause, resume, remove)
 * - Status monitoring (tellStatus, tellActive, tellWaiting)
 * - Global statistics (getGlobalStat)
 * - Session management (saveSession, shutdown)
 * 
 * Uses RequestFactory pattern with distributed objects for scalability
 */
class TorrentRpcServer(
    internal val downloader: TrikeDownloader,
    internal val requestFactory: RequestFactoryService
) {
    
    internal val activeDownloads = mutableMapOf<String, DownloadTask>()
    internal val downloadStats = mutableMapOf<String, DownloadProgress>()
    internal val sessionData = mutableMapOf<String, Any>()
    internal val sessionManager = SessionManager()
    internal val downloadRegistry = DownloadRegistry()
    internal val progressTracker = ProgressTracker()
    internal val sessionPersistence = SessionPersistence()
    internal val nodeCoordinator = NodeCoordinator()
    internal val batchController = TorrentBatchController()
    
    init {
        registerRpcMethods()
    }
    
    /**
     * Register all RPC methods with RequestFactory
     */
    internal fun registerRpcMethods() {
        // Download management
        requestFactory.registerMethodValidator("aria2.addUri") { validateAddUri(it) }
        requestFactory.registerMethodValidator("aria2.addTorrent") { validateAddTorrent(it) }
        requestFactory.registerMethodValidator("aria2.remove") { validateRemove(it) }
        requestFactory.registerMethodValidator("aria2.pause") { validatePause(it) }
        requestFactory.registerMethodValidator("aria2.resume") { validateResume(it) }
        
        // Status queries
        requestFactory.registerMethodValidator("aria2.tellStatus") { validateTellStatus(it) }
        requestFactory.registerMethodValidator("aria2.tellActive") { validateTellActive(it) }
        requestFactory.registerMethodValidator("aria2.tellWaiting") { validateTellWaiting(it) }
        requestFactory.registerMethodValidator("aria2.tellStopped") { validateTellStopped(it) }
        
        // Global operations
        requestFactory.registerMethodValidator("aria2.getGlobalStat") { validateGetGlobalStat(it) }
        requestFactory.registerMethodValidator("aria2.pauseAll") { validatePauseAll(it) }
        requestFactory.registerMethodValidator("aria2.resumeAll") { validateResumeAll(it) }
        
        // Session management
        requestFactory.registerMethodValidator("aria2.saveSession") { validateSaveSession(it) }
        requestFactory.registerMethodValidator("aria2.shutdown") { validateShutdown(it) }
        requestFactory.registerMethodValidator("aria2.forceShutdown") { validateForceShutdown(it) }
    }
    
    /**
     * Handle RPC request and return response
     */
    suspend fun handleRpcRequest(request: RpcRequest): RpcResponse {
        return try {
            when (request.method) {
                "aria2.addUri" -> handleAddUri(request)
                "aria2.addTorrent" -> handleAddTorrent(request)
                "aria2.remove" -> handleRemove(request)
                "aria2.pause" -> handlePause(request)
                "aria2.resume" -> handleResume(request)
                "aria2.tellStatus" -> handleTellStatus(request)
                "aria2.tellActive" -> handleTellActive(request)
                "aria2.tellWaiting" -> handleTellWaiting(request)
                "aria2.tellStopped" -> handleTellStopped(request)
                "aria2.getGlobalStat" -> handleGetGlobalStat(request)
                "aria2.pauseAll" -> handlePauseAll(request)
                "aria2.resumeAll" -> handleResumeAll(request)
                "aria2.saveSession" -> handleSaveSession(request)
                "aria2.shutdown" -> handleShutdown(request)
                "aria2.forceShutdown" -> handleForceShutdown(request)
                else -> RpcResponse.error(request.id, -32601, "Method not found")
            }
        } catch (e: Exception) {
            RpcResponse.error(request.id, -32603, "Internal error: ${e.message}")
        }
    }
    
    // Download Management Methods
    
    internal suspend fun handleAddUri(request: RpcRequest): RpcResponse {
        val uris = request.params?.get("uris") as? List<String> ?: return RpcResponse.error(request.id, -32602, "Missing uris")
        val options = request.params?.get("options") as? Map<String, Any?> ?: emptyMap()
        
        val downloadId = generateDownloadId()
        val task = DownloadTask.HttpDownload(
            id = downloadId,
            url = uris.first(),
            method = "GET",
            headers = (options["header"] as? List<String>)?.associate { 
                val parts = it.split(":", limit = 2)
                parts[0] to parts.getOrNull(1) ?: ""
            } ?: emptyMap(),
            timeout = (options["timeout"] as? Number)?.toLong() ?: 30000L
        )
        
        activeDownloads[downloadId] = task
        downloader.addDownload(task)
        
        return RpcResponse.success(request.id, downloadId)
    }
    
    internal suspend fun handleAddTorrent(request: RpcRequest): RpcResponse {
        val torrent = request.params?.get("torrent") as? String ?: return RpcResponse.error(request.id, -32602, "Missing torrent")
        val uris = request.params?.get("uris") as? List<String> ?: emptyList()
        val options = request.params?.get("options") as? Map<String, Any?> ?: emptyMap()
        
        val downloadId = generateDownloadId()
        val task = DownloadTask.TorrentDownload(
            id = downloadId,
            url = torrent,
            trackers = uris,
            pieceSize = (options["piece-length"] as? Number)?.toInt() ?: 16384
        )
        
        activeDownloads[downloadId] = task
        downloader.addDownload(task)
        
        return RpcResponse.success(request.id, downloadId)
    }
    
    internal suspend fun handleRemove(request: RpcRequest): RpcResponse {
        val gid = request.params?.get("gid") as? String ?: return RpcResponse.error(request.id, -32602, "Missing gid")
        
        val removed = downloader.removeDownload(gid)
        if (removed) {
            activeDownloads.remove(gid)
            downloadStats.remove(gid)
        }
        
        return RpcResponse.success(request.id, if (removed) gid else null)
    }
    
    internal suspend fun handlePause(request: RpcRequest): RpcResponse {
        val gid = request.params?.get("gid") as? String ?: return RpcResponse.error(request.id, -32602, "Missing gid")
        
        val paused = downloader.pauseDownload(gid)
        return RpcResponse.success(request.id, if (paused) gid else null)
    }
    
    internal suspend fun handleResume(request: RpcRequest): RpcResponse {
        val gid = request.params?.get("gid") as? String ?: return RpcResponse.error(request.id, -32602, "Missing gid")
        
        val resumed = downloader.resumeDownload(gid)
        return RpcResponse.success(request.id, if (resumed) gid else null)
    }
    
    // Status Query Methods
    
    internal suspend fun handleTellStatus(request: RpcRequest): RpcResponse {
        val gid = request.params?.get("gid") as? String ?: return RpcResponse.error(request.id, -32602, "Missing gid")
        val keys = request.params?.get("keys") as? List<String> ?: emptyList()
        
        val status = downloadStats[gid] ?: return RpcResponse.error(request.id, -32001, "Download not found")
        
        val result = if (keys.isEmpty()) {
            createFullStatus(status)
        } else {
            createFilteredStatus(status, keys)
        }
        
        return RpcResponse.success(request.id, result)
    }
    
    internal suspend fun handleTellActive(request: RpcRequest): RpcResponse {
        val keys = request.params?.get("keys") as? List<String> ?: emptyList()
        
        val activeDownloads = downloadStats.values.filter { it.status == DownloadStatus.DOWNLOADING }
        val result = activeDownloads.map { status ->
            if (keys.isEmpty()) createFullStatus(status) else createFilteredStatus(status, keys)
        }
        
        return RpcResponse.success(request.id, result)
    }
    
    internal suspend fun handleTellWaiting(request: RpcRequest): RpcResponse {
        val offset = (request.params?.get("offset") as? Number)?.toInt() ?: 0
        val num = (request.params?.get("num") as? Number)?.toInt() ?: 100
        val keys = request.params?.get("keys") as? List<String> ?: emptyList()
        
        val waitingDownloads = downloadStats.values
            .filter { it.status == DownloadStatus.WAITING }
            .drop(offset)
            .take(num)
            .map { status ->
                if (keys.isEmpty()) createFullStatus(status) else createFilteredStatus(status, keys)
            }
        
        return RpcResponse.success(request.id, waitingDownloads)
    }
    
    internal suspend fun handleTellStopped(request: RpcRequest): RpcResponse {
        val offset = (request.params?.get("offset") as? Number)?.toInt() ?: 0
        val num = (request.params?.get("num") as? Number)?.toInt() ?: 100
        val keys = request.params?.get("keys") as? List<String> ?: emptyList()
        
        val stoppedDownloads = downloadStats.values
            .filter { it.status in listOf(DownloadStatus.COMPLETED, DownloadStatus.CANCELLED, DownloadStatus.ERROR) }
            .drop(offset)
            .take(num)
            .map { status ->
                if (keys.isEmpty()) createFullStatus(status) else createFilteredStatus(status, keys)
            }
        
        return RpcResponse.success(request.id, stoppedDownloads)
    }
    
    // Global Operations
    
    internal suspend fun handleGetGlobalStat(request: RpcRequest): RpcResponse {
        val allDownloads = downloadStats.values
        val active = allDownloads.count { it.status == DownloadStatus.DOWNLOADING }
        val waiting = allDownloads.count { it.status == DownloadStatus.WAITING }
        val stopped = allDownloads.count { it.status in listOf(DownloadStatus.COMPLETED, DownloadStatus.CANCELLED, DownloadStatus.ERROR) }
        
        val result = mapOf(
            "downloadSpeed" to allDownloads.filter { it.status == DownloadStatus.DOWNLOADING }.sumOf { it.downloadSpeed },
            "uploadSpeed" to allDownloads.filter { it.status == DownloadStatus.DOWNLOADING }.sumOf { it.uploadSpeed },
            "numActive" to active,
            "numWaiting" to waiting,
            "numStopped" to stopped,
            "numStoppedTotal" to stopped
        )
        
        return RpcResponse.success(request.id, result)
    }
    
    internal suspend fun handlePauseAll(request: RpcRequest): RpcResponse {
        val paused = downloader.pauseAll()
        return RpcResponse.success(request.id, "OK")
    }
    
    internal suspend fun handleResumeAll(request: RpcRequest): RpcResponse {
        val resumed = downloader.resumeAll()
        return RpcResponse.success(request.id, "OK")
    }
    
    // Session Management
    
    internal suspend fun handleSaveSession(request: RpcRequest): RpcResponse {
        val sessionFile = request.params?.get("session-file") as? String ?: "aria2.session"
        
        // Save current session state
        sessionData["downloads"] = activeDownloads
        sessionData["stats"] = downloadStats
        
        return RpcResponse.success(request.id, "OK")
    }
    
    internal suspend fun handleShutdown(request: RpcRequest): RpcResponse {
        // Graceful shutdown
        downloader.stop()
        return RpcResponse.success(request.id, "OK")
    }
    
    internal suspend fun handleForceShutdown(request: RpcRequest): RpcResponse {
        // Force shutdown
        downloader.stop()
        return RpcResponse.success(request.id, "OK")
    }
    
    // Helper Methods
    
    internal fun createFullStatus(progress: DownloadProgress): Map<String, Any?> {
        return mapOf(
            "gid" to progress.id,
            "status" to progress.status.name.lowercase(),
            "totalLength" to progress.totalBytes,
            "completedLength" to progress.downloadedBytes,
            "uploadLength" to progress.uploadedBytes,
            "bitfield" to progress.bitfield,
            "downloadSpeed" to progress.downloadSpeed,
            "uploadSpeed" to progress.uploadSpeed,
            "infoHash" to progress.infoHash,
            "numSeeders" to progress.numSeeders,
            "seeder" to progress.isSeeder,
            "pieceLength" to progress.pieceLength,
            "numPieces" to progress.numPieces,
            "connections" to progress.connections,
            "errorCode" to progress.errorCode,
            "errorMessage" to progress.errorMessage,
            "followedBy" to progress.followedBy,
            "following" to progress.following,
            "belongsTo" to progress.belongsTo,
            "dir" to progress.directory,
            "files" to progress.files,
            "bittorrent" to progress.bittorrent,
            "verifiedLength" to progress.verifiedLength,
            "verifyIntegrityPending" to progress.verifyIntegrityPending
        )
    }
    
    internal fun createFilteredStatus(progress: DownloadProgress, keys: List<String>): Map<String, Any?> {
        val fullStatus = createFullStatus(progress)
        return keys.associateWith { fullStatus[it] }
    }
    
    internal fun generateDownloadId(): String {
        return "trike_${System.currentTimeMillis()}_${(0..999).random()}"
    }
    
    // Validation Methods
    
    internal fun validateAddUri(params: Any): Boolean = true
    internal fun validateAddTorrent(params: Any): Boolean = true
    internal fun validateRemove(params: Any): Boolean = true
    internal fun validatePause(params: Any): Boolean = true
    internal fun validateResume(params: Any): Boolean = true
    internal fun validateTellStatus(params: Any): Boolean = true
    internal fun validateTellActive(params: Any): Boolean = true
    internal fun validateTellWaiting(params: Any): Boolean = true
    internal fun validateTellStopped(params: Any): Boolean = true
    internal fun validateGetGlobalStat(params: Any): Boolean = true
    internal fun validatePauseAll(params: Any): Boolean = true
    internal fun validateResumeAll(params: Any): Boolean = true
    internal fun validateSaveSession(params: Any): Boolean = true
    internal fun validateShutdown(params: Any): Boolean = true
    internal fun validateForceShutdown(params: Any): Boolean = true
    
    // Batch Management Methods
    suspend fun createBatch(name: String, maxConcurrent: Int = 3, priority: String = "NORMAL", 
                           mediaPlayerIntegration: Boolean = false): Map<String, Any> {
        val batchPriority = BatchPriority.valueOf(priority.uppercase())
        val config = BatchConfig(
            name = name,
            maxConcurrent = maxConcurrent,
            priority = batchPriority,
            mediaPlayerIntegration = mediaPlayerIntegration
        )
        
        val batchId = batchController.createBatch(config)
        return mapOf(
            "batchId" to batchId,
            "name" to name,
            "status" to "created"
        )
    }
    
    suspend fun addTorrentToBatch(batchId: String, torrentUrl: String): Map<String, Any> {
        val torrentInfo = TorrentInfo(
            infoHash = "hash_${torrentUrl.hashCode()}",
            name = "Torrent from $torrentUrl",
            totalSize = 1024 * 1024 * 100, // 100MB
            downloadedSize = 0,
            status = TorrentStatus.QUEUED,
            downloadSpeed = 0.0
        )
        
        val success = batchController.addTorrentToBatch(batchId, torrentInfo)
        return mapOf(
            "success" to success,
            "batchId" to batchId,
            "torrentId" to torrentInfo.infoHash
        )
    }
    
    suspend fun startBatch(batchId: String): Map<String, Any> {
        val success = batchController.startBatch(batchId)
        return mapOf(
            "success" to success,
            "batchId" to batchId,
            "status" to if (success) "started" else "failed"
        )
    }
    
    suspend fun pauseBatch(batchId: String): Map<String, Any> {
        val success = batchController.pauseBatch(batchId)
        return mapOf(
            "success" to success,
            "batchId" to batchId,
            "status" to if (success) "paused" else "failed"
        )
    }
    
    suspend fun resumeBatch(batchId: String): Map<String, Any> {
        val success = batchController.resumeBatch(batchId)
        return mapOf(
            "success" to success,
            "batchId" to batchId,
            "status" to if (success) "resumed" else "failed"
        )
    }
    
    suspend fun getBatchStatus(batchId: String): Map<String, Any>? {
        val status = batchController.getBatchStatus(batchId) ?: return null
        return mapOf(
            "batchId" to status.batchId,
            "name" to status.name,
            "status" to status.status.name,
            "torrentCount" to status.torrentCount,
            "activeCount" to status.activeCount,
            "completedCount" to status.completedCount,
            "totalProgress" to status.totalProgress,
            "totalSize" to status.totalSize,
            "downloadedSize" to status.downloadedSize,
            "downloadSpeed" to status.downloadSpeed,
            "eta" to status.eta?.inWholeSeconds,
            "mediaPlayerState" to status.mediaPlayerState?.let { state ->
                mapOf(
                    "isPlaying" to state.isPlaying,
                    "currentTorrentId" to state.currentTorrentId,
                    "playProgress" to state.playProgress,
                    "autoPlayEnabled" to state.autoPlayEnabled,
                    "playlist" to state.playlist
                )
            }
        )
    }
    
    suspend fun getAllBatchStatuses(): List<Map<String, Any>> {
        return batchController.getAllBatchStatuses().map { status ->
            mapOf(
                "batchId" to status.batchId,
                "name" to status.name,
                "status" to status.status.name,
                "torrentCount" to status.torrentCount,
                "activeCount" to status.activeCount,
                "completedCount" to status.completedCount,
                "totalProgress" to status.totalProgress,
                "totalSize" to status.totalSize,
                "downloadedSize" to status.downloadedSize,
                "downloadSpeed" to status.downloadSpeed,
                "eta" to status.eta?.inWholeSeconds
            )
        }
    }
    
    suspend fun reorderBatch(batchId: String, newPosition: Int): Map<String, Any> {
        val success = batchController.reorderBatch(batchId, newPosition)
        return mapOf(
            "success" to success,
            "batchId" to batchId,
            "newPosition" to newPosition
        )
    }
    
    suspend fun setBatchPriority(batchId: String, priority: String): Map<String, Any> {
        val batchPriority = BatchPriority.valueOf(priority.uppercase())
        val success = batchController.setBatchPriority(batchId, batchPriority)
        return mapOf(
            "success" to success,
            "batchId" to batchId,
            "priority" to priority
        )
    }
    
    // Media Player Control Methods
    suspend fun getMediaPlayerState(batchId: String): Map<String, Any>? {
        val state = batchController.getMediaPlayerState(batchId) ?: return null
        return mapOf(
            "batchId" to batchId,
            "isPlaying" to state.isPlaying,
            "currentTorrentId" to state.currentTorrentId,
            "playProgress" to state.playProgress,
            "autoPlayEnabled" to state.autoPlayEnabled,
            "playlist" to state.playlist
        )
    }
    
    suspend fun controlMediaPlayer(batchId: String, action: String, params: Map<String, Any> = emptyMap()): Map<String, Any> {
        val mediaAction = when (action.lowercase()) {
            "play" -> MediaPlayerAction.Play
            "pause" -> MediaPlayerAction.Pause
            "stop" -> MediaPlayerAction.Stop
            "next" -> MediaPlayerAction.Next
            "previous" -> MediaPlayerAction.Previous
            "seek" -> MediaPlayerAction.Seek(params["position"] as? Double ?: 0.0)
            "setvolume" -> MediaPlayerAction.SetVolume(params["volume"] as? Double ?: 1.0)
            "setplaylist" -> MediaPlayerAction.SetPlaylist(params["torrentIds"] as? List<String> ?: emptyList())
            else -> return mapOf("success" to false, "error" to "Unknown action: $action")
        }
        
        val success = batchController.controlMediaPlayer(batchId, mediaAction)
        return mapOf(
            "success" to success,
            "batchId" to batchId,
            "action" to action
        )
    }
    
    suspend fun removeBatch(batchId: String): Map<String, Any> {
        val success = batchController.removeBatch(batchId)
        return mapOf(
            "success" to success,
            "batchId" to batchId
        )
    }
    
    suspend fun processBatchQueue(): Map<String, Any> {
        batchController.processBatchQueue()
        return mapOf(
            "success" to true,
            "message" to "Batch queue processed"
        )
    }
}

/**
 * RPC Request/Response data classes
 */
@Serializable
data class RpcRequest(
    val jsonrpc: String = "2.0",
    val id: String,
    val method: String,
    val params: Map<String, Any?>? = null
)

@Serializable
data class RpcResponse(
    val jsonrpc: String = "2.0",
    val id: String,
    val result: Any? = null,
    val error: RpcError? = null
) {
    companion object {
        fun success(id: String, result: Any?): RpcResponse {
            return RpcResponse(id = id, result = result)
        }
        
        fun error(id: String, code: Int, message: String): RpcResponse {
            return RpcResponse(id = id, error = RpcError(code, message))
        }
    }
}

@Serializable
data class RpcError(
    val code: Int,
    val message: String,
    val data: Any? = null
) 