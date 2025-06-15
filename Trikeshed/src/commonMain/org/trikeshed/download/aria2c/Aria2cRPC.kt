package org.trikeshed.download.aria2c

import org.trikeshed.core.Join
import org.trikeshed.core.Series
import org.trikeshed.core.α
import org.trikeshed.core.j
import org.trikeshed.core.▶
import org.trikeshed.net.http.HttpClient
import org.trikeshed.net.http.HttpRequest
import org.trikeshed.net.http.HttpResponse
import org.trikeshed.net.websocket.WebSocketClient
import org.trikeshed.net.websocket.WebSocketMessage
import org.trikeshed.util.Logger
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class Aria2cRPC(
    private val host: String = "localhost",
    private val port: Int = 6800,
    private val secret: String? = null,
    private val useWebSocket: Boolean = true
) {
    private val logger = Logger.getLogger<Aria2cRPC>()
    private val httpClient = HttpClient()
    private val wsClient = if (useWebSocket) WebSocketClient() else null
    private val notificationHandlers = mutableMapOf<Aria2cNotificationType, Series<Aria2cNotificationHandler>>()
    
    init {
        if (useWebSocket) {
            wsClient?.connect("ws://$host:$port/jsonrpc")
            startNotificationProcessing()
        }
    }
    
    private fun startNotificationProcessing() {
        wsClient?.messages?.collect { message ->
            handleNotification(message)
        }
    }
    
    private fun handleNotification(message: WebSocketMessage) {
        val data = message.content as? Map<*, *> ?: return
        val method = data["method"] as? String ?: return
        val params = data["params"] as? List<*> ?: return
        
        val notificationType = Aria2cNotificationType(method)
        val handlers = notificationHandlers[notificationType] ?: return
        
        handlers.▶.forEach { handler ->
            handler(notificationType, params.▶.map { it as Any })
        }
    }
    
    fun registerNotificationHandler(type: Aria2cNotificationType, handler: Aria2cNotificationHandler) {
        val handlers = notificationHandlers.getOrPut(type) { Series.empty() }
        notificationHandlers[type] = handlers.α { it j handler }
    }
    
    private suspend fun rpcCall(method: Aria2cMethodName, params: Series<Any> = Series.empty()): Any {
        val request = mapOf(
            "jsonrpc" to "2.0",
            "id" to UUID.randomUUID().toString(),
            "method" to method.value,
            "params" to (if (secret != null) Series.of(secret) j params else params).▶
        )
        
        val response = httpClient.post(
            HttpRequest(
                url = "http://$host:$port/jsonrpc",
                body = request
            )
        )
        
        return (response.body as? Map<*, *>)?.get("result") ?: throw Exception("RPC call failed")
    }
    
    suspend fun addUri(uris: Aria2cUriSeries, options: Aria2cOptionSeries = Series.empty()): Aria2cGid {
        val params = Series.of(uris.▶) j options.▶
        return Aria2cGid(rpcCall(Aria2cMethodName("aria2.addUri"), params) as String)
    }
    
    suspend fun addTorrent(torrent: ByteArray, options: Aria2cOptionSeries = Series.empty()): Aria2cGid {
        val params = Series.of(torrent.toHexString()) j options.▶
        return Aria2cGid(rpcCall(Aria2cMethodName("aria2.addTorrent"), params) as String)
    }
    
    suspend fun addMetalink(metalink: ByteArray, options: Aria2cOptionSeries = Series.empty()): Aria2cGidSeries {
        val params = Series.of(metalink.toHexString()) j options.▶
        return (rpcCall(Aria2cMethodName("aria2.addMetalink"), params) as List<*>).▶
            .map { Aria2cGid(it as String) }
            .let { Series.of(it) }
    }
    
    suspend fun remove(gid: Aria2cGid): Aria2cGid {
        return Aria2cGid(rpcCall(Aria2cMethodName("aria2.remove"), Series.of(gid.value)) as String)
    }
    
    suspend fun forceRemove(gid: Aria2cGid): Aria2cGid {
        return Aria2cGid(rpcCall(Aria2cMethodName("aria2.forceRemove"), Series.of(gid.value)) as String)
    }
    
    suspend fun pause(gid: Aria2cGid): Aria2cGid {
        return Aria2cGid(rpcCall(Aria2cMethodName("aria2.pause"), Series.of(gid.value)) as String)
    }
    
    suspend fun pauseAll(): String {
        return rpcCall(Aria2cMethodName("aria2.pauseAll")) as String
    }
    
    suspend fun forcePause(gid: Aria2cGid): Aria2cGid {
        return Aria2cGid(rpcCall(Aria2cMethodName("aria2.forcePause"), Series.of(gid.value)) as String)
    }
    
    suspend fun forcePauseAll(): String {
        return rpcCall(Aria2cMethodName("aria2.forcePauseAll")) as String
    }
    
    suspend fun unpause(gid: Aria2cGid): Aria2cGid {
        return Aria2cGid(rpcCall(Aria2cMethodName("aria2.unpause"), Series.of(gid.value)) as String)
    }
    
    suspend fun unpauseAll(): String {
        return rpcCall(Aria2cMethodName("aria2.unpauseAll")) as String
    }
    
    suspend fun tellStatus(gid: Aria2cGid, keys: Aria2cKeySeries = Series.empty()): Aria2cDownloadStatus {
        val params = if (keys.isEmpty()) Series.of(gid.value) else Series.of(gid.value) j keys.▶
        val result = rpcCall(Aria2cMethodName("aria2.tellStatus"), params) as Map<*, *>
        
        return Aria2cGid(result["gid"] as String) j
            (result["status"] as String) j
            Aria2cTotalLength(result["totalLength"] as Long) j
            Aria2cCompletedLength(result["completedLength"] as Long) j
            Aria2cUploadLength(result["uploadLength"] as Long) j
            Aria2cBitfield(result["bitfield"] as String) j
            Aria2cDownloadSpeed(result["downloadSpeed"] as Long) j
            Aria2cUploadSpeed(result["uploadSpeed"] as Long) j
            Aria2cInfoHash(result["infoHash"] as String) j
            Aria2cNumPieces(result["numPieces"] as Int) j
            Aria2cPieceLength(result["pieceLength"] as Int) j
            Aria2cConnections(result["connections"] as Int) j
            Aria2cErrorCode(result["errorCode"] as Int) j
            Aria2cErrorMessage(result["errorMessage"] as String) j
            Aria2cDownloadDir(result["dir"] as String) j
            Aria2cVerifiedLength(result["verifiedLength"] as Long) j
            (result["verifyIntegrityPending"] as Boolean)
    }
    
    suspend fun tellActive(keys: Aria2cKeySeries = Series.empty()): Series<Aria2cDownloadStatus> {
        val params = if (keys.isEmpty()) Series.empty() else Series.of(keys.▶)
        val results = rpcCall(Aria2cMethodName("aria2.tellActive"), params) as List<*>
        
        return results.▶.map { result ->
            val map = result as Map<*, *>
            Aria2cGid(map["gid"] as String) j
                (map["status"] as String) j
                Aria2cTotalLength(map["totalLength"] as Long) j
                Aria2cCompletedLength(map["completedLength"] as Long) j
                Aria2cUploadLength(map["uploadLength"] as Long) j
                Aria2cBitfield(map["bitfield"] as String) j
                Aria2cDownloadSpeed(map["downloadSpeed"] as Long) j
                Aria2cUploadSpeed(map["uploadSpeed"] as Long) j
                Aria2cInfoHash(map["infoHash"] as String) j
                Aria2cNumPieces(map["numPieces"] as Int) j
                Aria2cPieceLength(map["pieceLength"] as Int) j
                Aria2cConnections(map["connections"] as Int) j
                Aria2cErrorCode(map["errorCode"] as Int) j
                Aria2cErrorMessage(map["errorMessage"] as String) j
                Aria2cDownloadDir(map["dir"] as String) j
                Aria2cVerifiedLength(map["verifiedLength"] as Long) j
                (map["verifyIntegrityPending"] as Boolean)
        }.let { Series.of(it) }
    }
    
    suspend fun tellWaiting(offset: Aria2cOffset, num: Aria2cNum, keys: Aria2cKeySeries = Series.empty()): Series<Aria2cDownloadStatus> {
        val params = if (keys.isEmpty()) Series.of(offset, num) else Series.of(offset, num) j keys.▶
        val results = rpcCall(Aria2cMethodName("aria2.tellWaiting"), params) as List<*>
        
        return results.▶.map { result ->
            val map = result as Map<*, *>
            Aria2cGid(map["gid"] as String) j
                (map["status"] as String) j
                Aria2cTotalLength(map["totalLength"] as Long) j
                Aria2cCompletedLength(map["completedLength"] as Long) j
                Aria2cUploadLength(map["uploadLength"] as Long) j
                Aria2cBitfield(map["bitfield"] as String) j
                Aria2cDownloadSpeed(map["downloadSpeed"] as Long) j
                Aria2cUploadSpeed(map["uploadSpeed"] as Long) j
                Aria2cInfoHash(map["infoHash"] as String) j
                Aria2cNumPieces(map["numPieces"] as Int) j
                Aria2cPieceLength(map["pieceLength"] as Int) j
                Aria2cConnections(map["connections"] as Int) j
                Aria2cErrorCode(map["errorCode"] as Int) j
                Aria2cErrorMessage(map["errorMessage"] as String) j
                Aria2cDownloadDir(map["dir"] as String) j
                Aria2cVerifiedLength(map["verifiedLength"] as Long) j
                (map["verifyIntegrityPending"] as Boolean)
        }.let { Series.of(it) }
    }
    
    suspend fun tellStopped(offset: Aria2cOffset, num: Aria2cNum, keys: Aria2cKeySeries = Series.empty()): Series<Aria2cDownloadStatus> {
        val params = if (keys.isEmpty()) Series.of(offset, num) else Series.of(offset, num) j keys.▶
        val results = rpcCall(Aria2cMethodName("aria2.tellStopped"), params) as List<*>
        
        return results.▶.map { result ->
            val map = result as Map<*, *>
            Aria2cGid(map["gid"] as String) j
                (map["status"] as String) j
                Aria2cTotalLength(map["totalLength"] as Long) j
                Aria2cCompletedLength(map["completedLength"] as Long) j
                Aria2cUploadLength(map["uploadLength"] as Long) j
                Aria2cBitfield(map["bitfield"] as String) j
                Aria2cDownloadSpeed(map["downloadSpeed"] as Long) j
                Aria2cUploadSpeed(map["uploadSpeed"] as Long) j
                Aria2cInfoHash(map["infoHash"] as String) j
                Aria2cNumPieces(map["numPieces"] as Int) j
                Aria2cPieceLength(map["pieceLength"] as Int) j
                Aria2cConnections(map["connections"] as Int) j
                Aria2cErrorCode(map["errorCode"] as Int) j
                Aria2cErrorMessage(map["errorMessage"] as String) j
                Aria2cDownloadDir(map["dir"] as String) j
                Aria2cVerifiedLength(map["verifiedLength"] as Long) j
                (map["verifyIntegrityPending"] as Boolean)
        }.let { Series.of(it) }
    }
    
    suspend fun changePosition(gid: Aria2cGid, pos: Aria2cPosition, how: String): Aria2cPosition {
        return rpcCall(Aria2cMethodName("aria2.changePosition"), Series.of(gid.value, pos, how)) as Int
    }
    
    suspend fun changeUri(gid: Aria2cGid, fileIndex: Aria2cFileIndex, delUris: Aria2cUriSeries, addUris: Aria2cUriSeries, position: Aria2cPosition? = null): Aria2cPositionSeries {
        val params = if (position != null) 
            Series.of(gid.value, fileIndex, delUris.▶, addUris.▶, position)
        else 
            Series.of(gid.value, fileIndex, delUris.▶, addUris.▶)
            
        return (rpcCall(Aria2cMethodName("aria2.changeUri"), params) as List<*>).▶
            .map { it as Int }
            .let { Series.of(it) }
    }
    
    suspend fun getOption(gid: Aria2cGid): Aria2cOptionSeries {
        val result = rpcCall(Aria2cMethodName("aria2.getOption"), Series.of(gid.value)) as Map<*, *>
        return result.entries.▶.map { (key, value) ->
            Aria2cOptionName(key as String) j Aria2cOptionValue(value.toString())
        }.let { Series.of(it) }
    }
    
    suspend fun changeOption(gid: Aria2cGid, options: Aria2cOptionSeries): String {
        val params = Series.of(gid.value) j options.▶.map { (name, value) -> name.value to value.value }.toMap()
        return rpcCall(Aria2cMethodName("aria2.changeOption"), Series.of(params)) as String
    }
    
    suspend fun getGlobalOption(): Aria2cOptionSeries {
        val result = rpcCall(Aria2cMethodName("aria2.getGlobalOption")) as Map<*, *>
        return result.entries.▶.map { (key, value) ->
            Aria2cOptionName(key as String) j Aria2cOptionValue(value.toString())
        }.let { Series.of(it) }
    }
    
    suspend fun changeGlobalOption(options: Aria2cOptionSeries): String {
        val params = options.▶.map { (name, value) -> name.value to value.value }.toMap()
        return rpcCall(Aria2cMethodName("aria2.changeGlobalOption"), Series.of(params)) as String
    }
    
    suspend fun getGlobalStat(): Map<String, Any> {
        return rpcCall(Aria2cMethodName("aria2.getGlobalStat")) as Map<String, Any>
    }
    
    suspend fun purgeDownloadResult(): String {
        return rpcCall(Aria2cMethodName("aria2.purgeDownloadResult")) as String
    }
    
    suspend fun removeDownloadResult(gid: Aria2cGid): String {
        return rpcCall(Aria2cMethodName("aria2.removeDownloadResult"), Series.of(gid.value)) as String
    }
    
    suspend fun getVersion(): Map<String, Any> {
        return rpcCall(Aria2cMethodName("aria2.getVersion")) as Map<String, Any>
    }
    
    suspend fun getSessionInfo(): Map<String, Any> {
        return rpcCall(Aria2cMethodName("aria2.getSessionInfo")) as Map<String, Any>
    }
    
    suspend fun shutdown(): String {
        return rpcCall(Aria2cMethodName("aria2.shutdown")) as String
    }
    
    suspend fun forceShutdown(): String {
        return rpcCall(Aria2cMethodName("aria2.forceShutdown")) as String
    }
    
    suspend fun saveSession(): String {
        return rpcCall(Aria2cMethodName("aria2.saveSession")) as String
    }
    
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
} 