@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.quic

import borg.trikeshed.lib.*
import borg.trikeshed.io.IOContext
import borg.trikeshed.net.tls.TLS13Protocol
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * QUIC Client implementation with 0-RTT support and attention-based streaming
 * Implements RFC 9000 QUIC transport protocol
 */
class QuicClient(
    internal val config: QuicConfig,
    internal val ioContext: IOContext
) {
    internal val connections = mutableMapOf<String, QuicConnection>()
    internal val sessionCache = config.sessionCache ?: DefaultQuicSessionCache()
    
    // Configuration
    var connectTimeout: Duration = 30.seconds
    var idleTimeout: Duration = 900.seconds
    var maxStreamsPerConnection: Int = 100
    
    /**
     * Connect to a QUIC server with 0-RTT support if available
     */
    suspend fun connect(
        host: String,
        port: Int,
        alpn: List<String> = listOf("h3", "h3-29")
    ): QuicConnection {
        val key = "$host:$port"
        
        // Check existing connection
        connections[key]?.let { conn ->
            if (conn.isAlive()) return conn
            conn.close()
        }
        
        // Create new connection
        val connection = QuicConnection(
            isClient = true,
            config = config,
            ioContext = ioContext
        )
        
        // Try 0-RTT if we have cached session
        val cachedSession = sessionCache.get(host, port)
        if (cachedSession != null) {
            try {
                connection.connect0RTT(host, port, cachedSession, alpn)
                connections[key] = connection
                return connection
            } catch (e: Exception) {
                // Fall back to 1-RTT
            }
        }
        
        // Standard 1-RTT connection
        connection.connect(host, port, alpn)
        
        // Cache session for future 0-RTT
        connection.getSession()?.let { session ->
            sessionCache.put(host, port, session)
        }
        
        connections[key] = connection
        return connection
    }
    
    /**
     * Open a bidirectional stream
     */
    suspend fun openStream(connection: QuicConnection): QuicStream {
        return connection.openBidirectionalStream()
    }
    
    /**
     * Open a unidirectional stream
     */
    suspend fun openUniStream(connection: QuicConnection): QuicStream {
        return connection.openUnidirectionalStream()
    }
    
    /**
     * Send data with attention-based optimization
     */
    suspend fun send(
        stream: QuicStream,
        data: ByteArray,
        fin: Boolean = false
    ) {
        stream.send(data, fin)
    }
    
    /**
     * Send multiple ranges efficiently (for sparse attention)
     */
    suspend fun sendRanges(
        connection: QuicConnection,
        ranges: Indexed<Join<Twin<Long>, ByteArray>>
    ) = coroutineScope {
        val streams = Array(ranges.component1()) { i ->
            async {
                val stream = openUniStream(connection)
                val rangeData = ranges.component2()(i)
                val metadata = rangeData.component1()  // Twin<Long> = start j end
                val data = rangeData.component2()
                
                // Send range metadata first
                val metadataBytes = "${metadata.component1()}-${metadata.component2()}:".toByteArray()
                stream.send(metadataBytes, false)
                stream.send(data, true)
                
                stream
            }
        }
        
        streams.map { it.await() }.toTypedArray()
    }
    
    /**
     * Receive data from stream
     */
    suspend fun receive(stream: QuicStream): ByteArray {
        return stream.receive()
    }
    
    /**
     * Stream data with backpressure support
     */
    suspend fun stream(
        connection: QuicConnection,
        onData: suspend (QuicStream, ByteArray) -> Unit
    ) {
        coroutineScope {
            while (connection.isAlive()) {
                val stream = connection.acceptStream() ?: break
                
                launch {
                    try {
                        while (!stream.isFinished()) {
                            val data = stream.receive()
                            if (data.isNotEmpty()) {
                                onData(stream, data)
                            }
                        }
                    } catch (e: Exception) {
                        stream.close()
                    }
                }
            }
        }
    }
    
    /**
     * Migrate connection to new network path
     */
    suspend fun migrate(
        connection: QuicConnection,
        newLocalAddress: String? = null
    ): Boolean {
        return connection.migrateConnection(newLocalAddress)
    }
    
    /**
     * Get connection statistics
     */
    fun getStats(connection: QuicConnection): QuicStats {
        return QuicStats(
            rtt = connection.getRTT(),
            congestionWindow = connection.getCongestionWindow(),
            bytesInFlight = connection.getBytesInFlight(),
            packetsLost = connection.getPacketsLost(),
            streamsActive = connection.getActiveStreamCount()
        )
    }
    
    /**
     * Close all connections gracefully
     */
    suspend fun closeAll(errorCode: Long = 0, reason: String = "Client closing") {
        connections.values.forEach { conn ->
            conn.close(errorCode, reason)
        }
        connections.clear()
    }
    
    /**
     * HTTP/3 specific: Send HTTP request over QUIC
     */
    suspend fun sendH3Request(
        connection: QuicConnection,
        method: String,
        path: String,
        headers: Map<String, String>,
        body: ByteArray? = null
    ): QuicStream {
        val stream = openStream(connection)
        
        // Encode HTTP/3 request (QPACK)
        val requestData = encodeH3Request(method, path, headers, body)
        stream.send(requestData, body == null)
        
        body?.let { stream.send(it, true) }
        
        return stream
    }
    
    internal fun encodeH3Request(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: ByteArray?
    ): ByteArray {
        // Simplified HTTP/3 encoding (real implementation would use QPACK)
        val requestHeaders = mutableListOf(
            ":method" to method,
            ":path" to path,
            ":scheme" to "https"
        )
        
        headers.forEach { (name, value) ->
            requestHeaders.add(name.lowercase() to value)
        }
        
        if (body != null) {
            requestHeaders.add("content-length" to body.size.toString())
        }
        
        // Mock encoding - real implementation would use QPACK
        return requestHeaders.joinToString("\r\n") { (k, v) -> "$k: $v" }
            .toByteArray()
    }
}

/**
 * QUIC connection statistics
 */
data class QuicStats(
    val rtt: Duration,
    val congestionWindow: Long,
    val bytesInFlight: Long,
    val packetsLost: Long,
    val streamsActive: Int
)

/**
 * QUIC client builder with fluent API
 */
class QuicClientBuilder {
    internal var config: QuicConfig? = null
    internal var ioContext: IOContext? = null
    internal var connectTimeout: Duration = 30.seconds
    internal var idleTimeout: Duration = 900.seconds
    internal var sessionCache: QuicSessionCache? = null
    
    fun config(config: QuicConfig) = apply { this.config = config }
    fun ioContext(context: IOContext) = apply { this.ioContext = context }
    fun connectTimeout(timeout: Duration) = apply { this.connectTimeout = timeout }
    fun idleTimeout(timeout: Duration) = apply { this.idleTimeout = timeout }
    fun sessionCache(cache: QuicSessionCache) = apply { this.sessionCache = cache }
    
    fun build(): QuicClient {
        val finalConfig = config ?: QuicConfig.default().copy(
            sessionCache = sessionCache
        )
        val context = ioContext ?: IOContext.NioContext("quic-client")
        
        return QuicClient(finalConfig, context).apply {
            this.connectTimeout = this@QuicClientBuilder.connectTimeout
            this.idleTimeout = this@QuicClientBuilder.idleTimeout
        }
    }
}

/**
 * Extension functions for attention-based QUIC operations
 */

// Execute range request over QUIC
suspend fun QuicClient.executeRange(
    connection: QuicConnection,
    path: String,
    start: Long,
    end: Long
): ByteArray {
    val headers = mapOf(
        "range" to "bytes=$start-$end"
    )
    
    val stream = sendH3Request(
        connection,
        "GET",
        path,
        headers
    )
    
    // Receive response
    val responseData = mutableListOf<ByteArray>()
    while (!stream.isFinished()) {
        val chunk = receive(stream)
        if (chunk.isNotEmpty()) {
            responseData.add(chunk)
        }
    }
    
    return responseData.reduce { acc, bytes -> acc + bytes }
}

// Execute multiple ranges in parallel
suspend fun QuicClient.executeMultiRange(
    connection: QuicConnection,
    path: String,
    ranges: Indexed<Twin<Long>>
): Indexed<ByteArray> = coroutineScope {
    val results = Array(ranges.component1()) { i ->
        async {
            val range = ranges.component2()(i)
            executeRange(connection, path, range.component1(), range.component2())
        }
    }
    
    val data = results.map { it.await() }.toTypedArray()
    data.size j data::get
}

// Stream large file with progress
suspend fun QuicClient.streamFile(
    connection: QuicConnection,
    path: String,
    onProgress: suspend (Long, Long) -> Unit = { _, _ -> }
): ByteArray {
    val stream = sendH3Request(
        connection,
        "GET",
        path,
        emptyMap()
    )
    
    val chunks = mutableListOf<ByteArray>()
    var totalReceived = 0L
    var contentLength = -1L
    
    while (!stream.isFinished()) {
        val chunk = receive(stream)
        if (chunk.isNotEmpty()) {
            chunks.add(chunk)
            totalReceived += chunk.size
            
            if (contentLength > 0) {
                onProgress(totalReceived, contentLength)
            }
        }
    }
    
    return chunks.reduce { acc, bytes -> acc + bytes }
}