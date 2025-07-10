@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.torrent.client

import borg.trikeshed.torrent.rpc.*
import borg.trikeshed.torrent.batch.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.net.http.*
import java.net.URI
import java.time.Duration

/**
 * TrikeAria Client - aria2c-compatible RPC client
 * 
 * Provides programmatic access to TrikeDownloader RPC server with:
 * - Full aria2c API compatibility
 * - Async/await support
 * - Real-time progress monitoring
 * - Batch operations
 * - Error handling and retries
 */
class TrikeAriaClient(
    internal val serverUrl: String = "http://localhost:6800",
    internal val timeout: Duration = Duration.ofSeconds(30),
    internal val maxRetries: Int = 3
) {
    
    internal val httpClient = HttpClient.newBuilder()
        .connectTimeout(timeout)
        .build()
    
    internal val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = false
    }
    
    internal var requestId = 0L
    
    /**
     * Add HTTP/FTP download
     */
    suspend fun addUri(
        uris: List<String>,
        options: Map<String, Any?> = emptyMap()
    ): String {
        val request = RpcRequest(
            id = generateRequestId(),
            method = "aria2.addUri",
            params = mapOf(
                "uris" to uris,
                "options" to options
            )
        )
        
        val response = sendRequest(request)
        return response.result as String
    }
    
    /**
     * Add torrent download
     */
    suspend fun addTorrent(
        torrent: String,
        uris: List<String> = emptyList(),
        options: Map<String, Any?> = emptyMap()
    ): String {
        val request = RpcRequest(
            id = generateRequestId(),
            method = "aria2.addTorrent",
            params = mapOf(
                "torrent" to torrent,
                "uris" to uris,
                "options" to options
            )
        )
        
        val response = sendRequest(request)
        return response.result as String
    }
    
    /**
     * Remove download
     */
    suspend fun remove(gid: String): String? {
        val request = RpcRequest(
            id = generateRequestId(),
            method = "aria2.remove",
            params = mapOf("gid" to gid)
        )
        
        val response = sendRequest(request)
        return response.result as? String
    }
    
    /**
     * Pause download
     */
    suspend fun pause(gid: String): String? {
        val request = RpcRequest(
            id = generateRequestId(),
            method = "aria2.pause",
            params = mapOf("gid" to gid)
        )
        
        val response = sendRequest(request)
        return response.result as? String
    }
    
    /**
     * Resume download
     */
    suspend fun resume(gid: String): String? {
        val request = RpcRequest(
            id = generateRequestId(),
            method = "aria2.resume",
            params = mapOf("gid" to gid)
        )
        
        val response = sendRequest(request)
        return response.result as? String
    }
    
    /**
     * Get download status
     */
    suspend fun tellStatus(
        gid: String,
        keys: List<String> = emptyList()
    ): Map<String, Any?> {
        val request = RpcRequest(
            id = generateRequestId(),
            method = "aria2.tellStatus",
            params = mapOf(
                "gid" to gid,
                "keys" to keys
            )
        )
        
        val response = sendRequest(request)
        return response.result as Map<String, Any?>
    }
    
    /**
     * List active downloads
     */
    suspend fun tellActive(keys: List<String> = emptyList()): List<Map<String, Any?>> {
        val request = RpcRequest(
            id = generateRequestId(),
            method = "aria2.tellActive",
            params = mapOf("keys" to keys)
        )
        
        val response = sendRequest(request)
        return response.result as List<Map<String, Any?>>
    }
    
    /**
     * List waiting downloads
     */
    suspend fun tellWaiting(
        offset: Int = 0,
        num: Int = 100,
        keys: List<String> = emptyList()
    ): List<Map<String, Any?>> {
        val request = RpcRequest(
            id = generateRequestId(),
            method = "aria2.tellWaiting",
            params = mapOf(
                "offset" to offset,
                "num" to num,
                "keys" to keys
            )
        )
        
        val response = sendRequest(request)
        return response.result as List<Map<String, Any?>>
    }
    
    /**
     * List stopped downloads
     */
    suspend fun tellStopped(
        offset: Int = 0,
        num: Int = 100,
        keys: List<String> = emptyList()
    ): List<Map<String, Any?>> {
        val request = RpcRequest(
            id = generateRequestId(),
            method = "aria2.tellStopped",
            params = mapOf(
                "offset" to offset,
                "num" to num,
                "keys" to keys
            )
        )
        
        val response = sendRequest(request)
        return response.result as List<Map<String, Any?>>
    }
    
    /**
     * Get global statistics
     */
    suspend fun getGlobalStat(): Map<String, Any?> {
        val request = RpcRequest(
            id = generateRequestId(),
            method = "aria2.getGlobalStat"
        )
        
        val response = sendRequest(request)
        return response.result as Map<String, Any?>
    }
    
    /**
     * Pause all downloads
     */
    suspend fun pauseAll(): String {
        val request = RpcRequest(
            id = generateRequestId(),
            method = "aria2.pauseAll"
        )
        
        val response = sendRequest(request)
        return response.result as String
    }
    
    /**
     * Resume all downloads
     */
    suspend fun resumeAll(): String {
        val request = RpcRequest(
            id = generateRequestId(),
            method = "aria2.resumeAll"
        )
        
        val response = sendRequest(request)
        return response.result as String
    }
    
    /**
     * Save session
     */
    suspend fun saveSession(sessionFile: String = "aria2.session"): String {
        val request = RpcRequest(
            id = generateRequestId(),
            method = "aria2.saveSession",
            params = mapOf("session-file" to sessionFile)
        )
        
        val response = sendRequest(request)
        return response.result as String
    }
    
    /**
     * Shutdown gracefully
     */
    suspend fun shutdown(): String {
        val request = RpcRequest(
            id = generateRequestId(),
            method = "aria2.shutdown"
        )
        
        val response = sendRequest(request)
        return response.result as String
    }
    
    /**
     * Force shutdown
     */
    suspend fun forceShutdown(): String {
        val request = RpcRequest(
            id = generateRequestId(),
            method = "aria2.forceShutdown"
        )
        
        val response = sendRequest(request)
        return response.result as String
    }
    
    /**
     * Monitor download progress in real-time
     */
    fun monitorProgress(
        gid: String,
        interval: Duration = Duration.ofSeconds(1)
    ): Flow<Map<String, Any?>> = flow {
        while (true) {
            try {
                val status = tellStatus(gid, listOf("status", "completedLength", "totalLength", "downloadSpeed"))
                emit(status)
                
                // Stop monitoring if download is complete
                val downloadStatus = status["status"] as? String
                if (downloadStatus in listOf("complete", "error", "removed")) {
                    break
                }
                
                delay(interval.toMillis())
            } catch (e: Exception) {
                emit(mapOf("error" to e.message))
                break
            }
        }
    }
    
    /**
     * Batch operations
     */
    suspend fun batch(operations: List<suspend TrikeAriaClient.() -> Any?>): List<Any?> {
        return operations.map { operation ->
            try {
                operation()
            } catch (e: Exception) {
                mapOf("error" to e.message)
            }
        }
    }
    
    /**
     * Download with progress monitoring
     */
    suspend fun downloadWithProgress(
        uris: List<String>,
        options: Map<String, Any?> = emptyMap(),
        onProgress: (Map<String, Any?>) -> Unit = {}
    ): String {
        val gid = addUri(uris, options)
        
        monitorProgress(gid).collect { status ->
            onProgress(status)
        }
        
        return gid
    }
    
    /**
     * Send RPC request with retry logic
     */
    internal suspend fun sendRequest(request: RpcRequest): RpcResponse {
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                val requestJson = json.encodeToString(RpcRequest.serializer(), request)
                
                val httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("$serverUrl/jsonrpc"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .timeout(timeout)
                    .build()
                
                val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
                
                if (response.statusCode() == 200) {
                    val rpcResponse = json.decodeFromString(RpcResponse.serializer(), response.body())
                    
                    if (rpcResponse.error != null) {
                        throw RpcException(rpcResponse.error.code, rpcResponse.error.message)
                    }
                    
                    return rpcResponse
                } else {
                    throw HttpException("HTTP ${response.statusCode()}: ${response.body()}")
                }
                
            } catch (e: Exception) {
                lastException = e
                
                if (attempt < maxRetries - 1) {
                    delay(1000L * (attempt + 1)) // Exponential backoff
                }
            }
        }
        
        throw lastException ?: RuntimeException("Unknown error")
    }
    
    internal fun generateRequestId(): String {
        return "client_${++requestId}"
    }
    
    // Batch Management Methods
    suspend fun createBatch(name: String, maxConcurrent: Int = 3, priority: String = "NORMAL", 
                           mediaPlayerIntegration: Boolean = false): String {
        val result = rpcClient.call("createBatch", mapOf(
            "name" to name,
            "maxConcurrent" to maxConcurrent,
            "priority" to priority,
            "mediaPlayerIntegration" to mediaPlayerIntegration
        ))
        
        return result["batchId"] as? String ?: throw RuntimeException("Failed to create batch")
    }
    
    suspend fun addTorrentToBatch(batchId: String, torrentUrl: String): String {
        val result = rpcClient.call("addTorrentToBatch", mapOf(
            "batchId" to batchId,
            "torrentUrl" to torrentUrl
        ))
        
        if (result["success"] != true) {
            throw RuntimeException("Failed to add torrent to batch")
        }
        
        return result["torrentId"] as? String ?: throw RuntimeException("No torrent ID returned")
    }
    
    suspend fun startBatch(batchId: String): Boolean {
        val result = rpcClient.call("startBatch", mapOf("batchId" to batchId))
        return result["success"] == true
    }
    
    suspend fun pauseBatch(batchId: String): Boolean {
        val result = rpcClient.call("pauseBatch", mapOf("batchId" to batchId))
        return result["success"] == true
    }
    
    suspend fun resumeBatch(batchId: String): Boolean {
        val result = rpcClient.call("resumeBatch", mapOf("batchId" to batchId))
        return result["success"] == true
    }
    
    suspend fun getBatchStatus(batchId: String): Map<String, Any>? {
        return rpcClient.call("getBatchStatus", mapOf("batchId" to batchId))
    }
    
    suspend fun getAllBatchStatuses(): List<Map<String, Any>> {
        val result = rpcClient.call("getAllBatchStatuses", emptyMap())
        return result["batches"] as? List<Map<String, Any>> ?: emptyList()
    }
    
    suspend fun reorderBatch(batchId: String, newPosition: Int): Boolean {
        val result = rpcClient.call("reorderBatch", mapOf(
            "batchId" to batchId,
            "newPosition" to newPosition
        ))
        return result["success"] == true
    }
    
    suspend fun setBatchPriority(batchId: String, priority: String): Boolean {
        val result = rpcClient.call("setBatchPriority", mapOf(
            "batchId" to batchId,
            "priority" to priority
        ))
        return result["success"] == true
    }
    
    // Media Player Control Methods
    suspend fun getMediaPlayerState(batchId: String): Map<String, Any>? {
        return rpcClient.call("getMediaPlayerState", mapOf("batchId" to batchId))
    }
    
    suspend fun playMedia(batchId: String): Boolean {
        val result = rpcClient.call("controlMediaPlayer", mapOf(
            "batchId" to batchId,
            "action" to "play"
        ))
        return result["success"] == true
    }
    
    suspend fun pauseMedia(batchId: String): Boolean {
        val result = rpcClient.call("controlMediaPlayer", mapOf(
            "batchId" to batchId,
            "action" to "pause"
        ))
        return result["success"] == true
    }
    
    suspend fun stopMedia(batchId: String): Boolean {
        val result = rpcClient.call("controlMediaPlayer", mapOf(
            "batchId" to batchId,
            "action" to "stop"
        ))
        return result["success"] == true
    }
    
    suspend fun nextMedia(batchId: String): Boolean {
        val result = rpcClient.call("controlMediaPlayer", mapOf(
            "batchId" to batchId,
            "action" to "next"
        ))
        return result["success"] == true
    }
    
    suspend fun previousMedia(batchId: String): Boolean {
        val result = rpcClient.call("controlMediaPlayer", mapOf(
            "batchId" to batchId,
            "action" to "previous"
        ))
        return result["success"] == true
    }
    
    suspend fun seekMedia(batchId: String, position: Double): Boolean {
        val result = rpcClient.call("controlMediaPlayer", mapOf(
            "batchId" to batchId,
            "action" to "seek",
            "params" to mapOf("position" to position)
        ))
        return result["success"] == true
    }
    
    suspend fun setMediaVolume(batchId: String, volume: Double): Boolean {
        val result = rpcClient.call("controlMediaPlayer", mapOf(
            "batchId" to batchId,
            "action" to "setvolume",
            "params" to mapOf("volume" to volume)
        ))
        return result["success"] == true
    }
    
    suspend fun setMediaPlaylist(batchId: String, torrentIds: List<String>): Boolean {
        val result = rpcClient.call("controlMediaPlayer", mapOf(
            "batchId" to batchId,
            "action" to "setplaylist",
            "params" to mapOf("torrentIds" to torrentIds)
        ))
        return result["success"] == true
    }
    
    suspend fun removeBatch(batchId: String): Boolean {
        val result = rpcClient.call("removeBatch", mapOf("batchId" to batchId))
        return result["success"] == true
    }
    
    suspend fun processBatchQueue(): Boolean {
        val result = rpcClient.call("processBatchQueue", emptyMap())
        return result["success"] == true
    }
    
    // Batch Management Convenience Methods
    suspend fun createAndStartBatch(name: String, torrentUrls: List<String>, 
                                   maxConcurrent: Int = 3, priority: String = "NORMAL",
                                   mediaPlayerIntegration: Boolean = false): String {
        val batchId = createBatch(name, maxConcurrent, priority, mediaPlayerIntegration)
        
        torrentUrls.forEach { url ->
            addTorrentToBatch(batchId, url)
        }
        
        startBatch(batchId)
        return batchId
    }
    
    suspend fun batchDownload(torrentUrls: List<String>, batchName: String = "auto_batch"): String {
        return createAndStartBatch(batchName, torrentUrls)
    }
    
    suspend fun monitorBatch(batchId: String, intervalMs: Long = 1000, maxDuration: Long = 300000): List<Map<String, Any>> {
        val startTime = System.currentTimeMillis()
        val statuses = mutableListOf<Map<String, Any>>()
        
        while (System.currentTimeMillis() - startTime < maxDuration) {
            val status = getBatchStatus(batchId)
            if (status != null) {
                statuses.add(status)
                
                val batchStatus = status["status"] as? String
                if (batchStatus == "COMPLETED" || batchStatus == "ERROR") {
                    break
                }
            }
            
            kotlinx.coroutines.delay(intervalMs)
        }
        
        return statuses
    }
}

/**
 * RPC Exception
 */
class RpcException(
    val code: Int,
    override val message: String
) : Exception("RPC Error $code: $message")

/**
 * HTTP Exception
 */
class HttpException(override val message: String) : Exception(message) 