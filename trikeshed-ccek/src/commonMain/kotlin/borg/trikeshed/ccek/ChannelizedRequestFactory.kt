@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.ccek

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.datetime.*
import kotlin.coroutines.CoroutineContext

/**
 * Channelized RequestFactory with roundtrip transaction support.
 * Integrates HTTP/0.9 through HTTP/3 with channel architecture and CCEK composition.
 */
class ChannelizedRequestFactory(
    internal val context: CoroutineContext
) : CoroutineContext.Element {
    
    companion object Key : CoroutineContext.Key<ChannelizedRequestFactory>
    override val key = Key
    
    internal val _transactions = MutableSharedFlow<RequestTransaction>()
    internal val activeHandles = mutableMapOf<String, ChannelizedRequestHandle>()
    
    /**
     * Flow of all request transactions for monitoring and logging.
     */
    val transactions: Flow<RequestTransaction> = _transactions.asSharedFlow()
    
    /**
     * Spawn a request handle for the specified HTTP protocol.
     */
    suspend fun spawn(protocol: HTTPProtocol): Result<ChannelizedRequestHandle> {
        return try {
            val handleId = "handle-${Clock.System.now().toEpochMilliseconds()}"
            val handle = ChannelizedRequestHandle(
                id = handleId,
                protocol = protocol,
                factory = this,
                context = context
            )
            
            activeHandles[handleId] = handle
            
            _transactions.emit(RequestTransaction(
                id = "tx-${handleId}",
                type = TransactionType.HANDLE_SPAWNED,
                protocol = protocol,
                timestamp = Clock.System.now(),
                status = TransactionStatus.COMPLETED
            ))
            
            Result.success(handle)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Clone this factory with evolved capabilities.
     */
    suspend fun clone(): ChannelizedRequestFactory {
        return ChannelizedRequestFactory(context)
    }
    
    /**
     * Grow legs - enable mobility and migration capabilities.
     */
    suspend fun growLegs(): ChannelizedRequestFactory {
        val mobilityContext = context + MobilityCapability(enabled = true)
        return ChannelizedRequestFactory(mobilityContext)
    }
    
    /**
     * Shed old protocols and birth new ones.
     */
    suspend fun shed(): ChannelizedRequestFactory {
        // Close old handles
        activeHandles.values.forEach { it.close() }
        activeHandles.clear()
        
        // Create fresh factory
        return ChannelizedRequestFactory(context)
    }
    
    /**
     * Execute a roundtrip transaction with full lifecycle tracking.
     */
    suspend fun executeRoundtrip(
        protocol: HTTPProtocol,
        request: ChannelizedHTTPRequest
    ): Result<RoundtripResult> {
        val transactionId = "roundtrip-${Clock.System.now().toEpochMilliseconds()}"
        
        return try {
            // Start transaction
            _transactions.emit(RequestTransaction(
                id = transactionId,
                type = TransactionType.ROUNDTRIP_STARTED,
                protocol = protocol,
                timestamp = Clock.System.now(),
                status = TransactionStatus.PENDING,
                metadata = mapOf("request_path" to request.path)
            ))
            
            // Spawn handle
            val handle = spawn(protocol).getOrThrow()
            
            // Execute request
            val response = handle.execute(request).getOrThrow()
            
            // Complete transaction
            val result = RoundtripResult(
                transactionId = transactionId,
                request = request,
                response = response,
                protocol = protocol,
                timing = RoundtripTiming(
                    startTime = Clock.System.now(),
                    endTime = Clock.System.now(),
                    duration = 0 // Calculate properly
                )
            )
            
            _transactions.emit(RequestTransaction(
                id = transactionId,
                type = TransactionType.ROUNDTRIP_COMPLETED,
                protocol = protocol,
                timestamp = Clock.System.now(),
                status = TransactionStatus.COMPLETED,
                metadata = mapOf(
                    "response_status" to response.status,
                    "response_size" to (response.body?.size ?: 0)
                )
            ))
            
            Result.success(result)
        } catch (e: Exception) {
            _transactions.emit(RequestTransaction(
                id = transactionId,
                type = TransactionType.ROUNDTRIP_FAILED,
                protocol = protocol,
                timestamp = Clock.System.now(),
                status = TransactionStatus.FAILED,
                metadata = mapOf("error" to e.message.orEmpty())
            ))
            
            Result.failure(e)
        }
    }
    
    internal suspend fun recordTransaction(transaction: RequestTransaction) {
        _transactions.emit(transaction)
    }
}

/**
 * Channelized request handle with enhanced capabilities.
 */
class ChannelizedRequestHandle(
    val id: String,
    override val protocol: HTTPProtocol,
    internal val factory: ChannelizedRequestFactory,
    internal val context: CoroutineContext
) : RequestHandle {
    
    internal var closed = false
    
    override suspend fun execute(request: HTTPRequest): Result<HTTPResponse> {
        if (closed) return Result.failure(IllegalStateException("Handle is closed"))
        
        val channelizedRequest = when (request) {
            is ChannelizedHTTPRequest -> request
            else -> ChannelizedHTTPRequest.from(request)
        }
        
        return try {
            factory.recordTransaction(RequestTransaction(
                id = "exec-${id}-${Clock.System.now().toEpochMilliseconds()}",
                type = TransactionType.REQUEST_EXECUTED,
                protocol = protocol,
                timestamp = Clock.System.now(),
                status = TransactionStatus.PENDING
            ))
            
            val response = executeChannelized(channelizedRequest)
            
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun stream(request: HTTPRequest): Flow<HTTPChunk> = flow {
        if (closed) throw IllegalStateException("Handle is closed")
        
        val channelizedRequest = when (request) {
            is ChannelizedHTTPRequest -> request
            else -> ChannelizedHTTPRequest.from(request)
        }
        
        // Simulate streaming for different protocols
        when (protocol) {
            HTTPProtocol.HTTP_0_9 -> {
                // HTTP/0.9 doesn't support chunking
                val response = executeChannelized(channelizedRequest)
                emit(HTTPChunk(response.body ?: byteArrayOf(), final = true))
            }
            HTTPProtocol.HTTP_1_1 -> {
                // HTTP/1.1 chunked encoding
                val response = executeChannelized(channelizedRequest)
                val body = response.body ?: byteArrayOf()
                val chunkSize = 1024
                
                for (i in body.indices step chunkSize) {
                    val chunk = body.sliceArray(i until minOf(i + chunkSize, body.size))
                    emit(HTTPChunk(chunk, final = i + chunkSize >= body.size))
                }
            }
            HTTPProtocol.HTTP_2, HTTPProtocol.HTTP_3 -> {
                // HTTP/2 and HTTP/3 with multiplexing
                val response = executeChannelized(channelizedRequest)
                val body = response.body ?: byteArrayOf()
                // Simulate frames
                emit(HTTPChunk(body, final = true))
            }
            else -> {
                val response = executeChannelized(channelizedRequest)
                emit(HTTPChunk(response.body ?: byteArrayOf(), final = true))
            }
        }
    }
    
    override suspend fun upgrade(to: UpgradeProtocol): Result<UpgradedConnection> {
        return try {
            val connection = ChannelizedUpgradedConnection(
                protocol = to,
                originalHandle = this
            )
            Result.success(connection)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun close() {
        closed = true
    }
    
    internal suspend fun executeChannelized(request: ChannelizedHTTPRequest): ChannelizedHTTPResponse {
        // Protocol-specific execution logic
        return when (protocol) {
            HTTPProtocol.HTTP_0_9 -> executeHttp09(request)
            HTTPProtocol.HTTP_1_0 -> executeHttp10(request)
            HTTPProtocol.HTTP_1_1 -> executeHttp11(request)
            HTTPProtocol.HTTP_2 -> executeHttp2(request)
            HTTPProtocol.HTTP_3 -> executeHttp3(request)
        }
    }
    
    internal suspend fun executeHttp09(request: ChannelizedHTTPRequest): ChannelizedHTTPResponse {
        // HTTP/0.9: Only GET, no headers, no status codes
        if (request.method != "GET") {
            throw IllegalArgumentException("HTTP/0.9 only supports GET")
        }
        
        return ChannelizedHTTPResponse(
            status = 200, // Implicit success
            headers = emptyMap(),
            body = "HTTP/0.9 response for ${request.path}".encodeToByteArray(),
            protocol = HTTPProtocol.HTTP_0_9,
            channelMetadata = ChannelMetadata(
                executionTime = Clock.System.now(),
                channelType = "http09",
                compressed = false
            )
        )
    }
    
    internal suspend fun executeHttp10(request: ChannelizedHTTPRequest): ChannelizedHTTPResponse {
        return ChannelizedHTTPResponse(
            status = 200,
            headers = mapOf(
                "Connection" to listOf("close"),
                "Content-Type" to listOf("text/plain")
            ),
            body = "HTTP/1.0 response for ${request.path}".encodeToByteArray(),
            protocol = HTTPProtocol.HTTP_1_0,
            channelMetadata = ChannelMetadata(
                executionTime = Clock.System.now(),
                channelType = "http10",
                compressed = false
            )
        )
    }
    
    internal suspend fun executeHttp11(request: ChannelizedHTTPRequest): ChannelizedHTTPResponse {
        return ChannelizedHTTPResponse(
            status = 200,
            headers = mapOf(
                "Connection" to listOf("keep-alive"),
                "Transfer-Encoding" to listOf("chunked"),
                "Content-Type" to listOf("text/plain")
            ),
            body = "HTTP/1.1 response for ${request.path}".encodeToByteArray(),
            protocol = HTTPProtocol.HTTP_1_1,
            channelMetadata = ChannelMetadata(
                executionTime = Clock.System.now(),
                channelType = "http11",
                compressed = false
            )
        )
    }
    
    internal suspend fun executeHttp2(request: ChannelizedHTTPRequest): ChannelizedHTTPResponse {
        return ChannelizedHTTPResponse(
            status = 200,
            headers = mapOf(
                ":status" to listOf("200"),
                "content-type" to listOf("text/plain")
            ),
            body = "HTTP/2 response for ${request.path}".encodeToByteArray(),
            protocol = HTTPProtocol.HTTP_2,
            channelMetadata = ChannelMetadata(
                executionTime = Clock.System.now(),
                channelType = "http2",
                compressed = true,
                multiplexed = true
            )
        )
    }
    
    internal suspend fun executeHttp3(request: ChannelizedHTTPRequest): ChannelizedHTTPResponse {
        return ChannelizedHTTPResponse(
            status = 200,
            headers = mapOf(
                ":status" to listOf("200"),
                "content-type" to listOf("text/plain")
            ),
            body = "HTTP/3 QUIC response for ${request.path}".encodeToByteArray(),
            protocol = HTTPProtocol.HTTP_3,
            channelMetadata = ChannelMetadata(
                executionTime = Clock.System.now(),
                channelType = "http3-quic",
                compressed = true,
                multiplexed = true,
                quicStream = true
            )
        )
    }
}

/**
 * Enhanced HTTP request with channel metadata.
 */
@Serializable
data class ChannelizedHTTPRequest(
    override val method: String,
    override val path: String,
    override val headers: Map<String, List<String>> = emptyMap(),
    override val body: ByteArray? = null,
    override val protocolHint: HTTPProtocol? = null,
    val channelMetadata: ChannelMetadata? = null,
    val transactionId: String? = null
) : HTTPRequest {
    
    companion object {
        fun from(request: HTTPRequest): ChannelizedHTTPRequest {
            return when (request) {
                is ChannelizedHTTPRequest -> request
                else -> ChannelizedHTTPRequest(
                    method = request.method,
                    path = request.path,
                    headers = request.headers,
                    body = request.body,
                    protocolHint = request.protocolHint
                )
            }
        }
    }
}

/**
 * Enhanced HTTP response with channel metadata.
 */
@Serializable
data class ChannelizedHTTPResponse(
    override val status: Int,
    override val headers: Map<String, List<String>>,
    override val body: ByteArray? = null,
    override val protocol: HTTPProtocol,
    val channelMetadata: ChannelMetadata
) : HTTPResponse

/**
 * Channel metadata for requests and responses.
 */
@Serializable
data class ChannelMetadata(
    val executionTime: Instant,
    val channelType: String,
    val compressed: Boolean = false,
    val multiplexed: Boolean = false,
    val quicStream: Boolean = false,
    val retryCount: Int = 0
)

/**
 * Request transaction for lifecycle tracking.
 */
@Serializable
data class RequestTransaction(
    val id: String,
    val type: TransactionType,
    val protocol: HTTPProtocol,
    val timestamp: Instant,
    val status: TransactionStatus,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Transaction types.
 */
@Serializable
enum class TransactionType {
    HANDLE_SPAWNED,
    REQUEST_EXECUTED,
    ROUNDTRIP_STARTED,
    ROUNDTRIP_COMPLETED,
    ROUNDTRIP_FAILED,
    STREAM_STARTED,
    STREAM_COMPLETED,
    UPGRADE_REQUESTED
}

/**
 * Transaction status.
 */
@Serializable
enum class TransactionStatus {
    PENDING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Roundtrip result with full lifecycle data.
 */
@Serializable
data class RoundtripResult(
    val transactionId: String,
    val request: ChannelizedHTTPRequest,
    val response: ChannelizedHTTPResponse,
    val protocol: HTTPProtocol,
    val timing: RoundtripTiming
)

/**
 * Timing information for roundtrips.
 */
@Serializable
data class RoundtripTiming(
    val startTime: Instant,
    val endTime: Instant,
    val duration: Long // milliseconds
)

/**
 * Mobility capability for factory migration.
 */
class MobilityCapability(val enabled: Boolean) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<MobilityCapability>
    override val key = Key
}

/**
 * Upgraded connection for protocol transitions.
 */
class ChannelizedUpgradedConnection(
    override val protocol: UpgradeProtocol,
    internal val originalHandle: ChannelizedRequestHandle
) : UpgradedConnection {
    
    override suspend fun send(data: ByteArray): Result<Unit> {
        return Result.success(Unit)
    }
    
    override suspend fun receive(): Result<ByteArray> {
        return Result.success(byteArrayOf())
    }
    
    override suspend fun close() {
        originalHandle.close()
    }
}

/**
 * Extension function to create ChannelizedRequestFactory in CCEK context.
 */
suspend fun CoroutineContext.channelizedRequestFactory(): ChannelizedRequestFactory {
    return this[ChannelizedRequestFactory] ?: ChannelizedRequestFactory(this)
}