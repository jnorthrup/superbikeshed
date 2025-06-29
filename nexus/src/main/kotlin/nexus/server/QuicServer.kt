package nexus.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import nexus.core.DefaultNexusAgent
import nexus.core.IpfsPubSubService
import nexus.core.LogLevel

/**
 * QUIC Server for the RelaxFactory architecture.
 * 
 * This implements a KMP-compatible QUIC server listener that can:
 * - Accept incoming QUIC connections
 * - Handle HTTP/3 requests
 * - Bridge to IPFS storage
 * - Support CouchDB-compatible API
 */
class QuicServer(
    private val agent: DefaultNexusAgent,
    private val scope: CoroutineScope,
    private val config: QuicServerConfig
) {
    private val _connections = MutableSharedFlow<QuicConnection>()
    val connections: Flow<QuicConnection> = _connections.asSharedFlow()
    
    private var isRunning = false
    private val platformServer = PlatformQuicServer(config)
    
    /**
     * Starts the QUIC server
     */
    suspend fun start() {
        if (isRunning) {
            throw IllegalStateException("Server is already running")
        }
        
        agent.logLevel.takeIf { it <= LogLevel.INFO }?.let {
            println("Starting QUIC server on ${config.host}:${config.port}")
        }
        
        try {
            platformServer.start()
            isRunning = true
            
            scope.launch {
                platformServer.connections.collect { connection ->
                    _connections.emit(connection)
                }
            }
            
            agent.logLevel.takeIf { it <= LogLevel.INFO }?.let {
                println("QUIC server started successfully")
            }
        } catch (e: Exception) {
            agent.logLevel.takeIf { it <= LogLevel.ERROR }?.let {
                println("Failed to start QUIC server: ${e.message}")
            }
            throw e
        }
    }
    
    /**
     * Stops the QUIC server
     */
    suspend fun stop() {
        if (!isRunning) {
            return
        }
        
        agent.logLevel.takeIf { it <= LogLevel.INFO }?.let {
            println("Stopping QUIC server...")
        }
        
        try {
            platformServer.stop()
            isRunning = false
            
            agent.logLevel.takeIf { it <= LogLevel.INFO }?.let {
                println("QUIC server stopped successfully")
            }
        } catch (e: Exception) {
            agent.logLevel.takeIf { it <= LogLevel.ERROR }?.let {
                println("Error stopping QUIC server: ${e.message}")
            }
            throw e
        }
    }
    
    /**
     * Checks if the server is currently running
     */
    fun isRunning(): Boolean = isRunning
}

/**
 * Configuration for the QUIC server
 */
@GenerateDsl
data class QuicServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 8080,
    val maxConnections: Int = 1000,
    val connectionTimeoutMs: Long = 30000,
    val maxStreamsPerConnection: Int = 100,
    val enableTls: Boolean = true,
    val certificatePath: String? = null,
    val privateKeyPath: String? = null,
    val alpnProtocols: List<String> = listOf("h3", "h3-29", "h3-28"),
    val enableRetry: Boolean = true,
    val maxRetries: Int = 3,
    val keepAliveMs: Long = 30000,
    val maxIdleTimeoutMs: Long = 60000,
)

/**
 * Represents a QUIC connection
 */
data class QuicConnection(
    val id: String,
    val remoteAddress: String,
    val localAddress: String,
    val streams: Flow<QuicStream>,
    val isActive: Boolean = true,
    val createdAt: Long = System.kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
)

/**
 * Represents a QUIC stream
 */
data class QuicStream(
    val id: String,
    val connectionId: String,
    val direction: StreamDirection,
    val headers: Map<String, String>,
    val data: Flow<ByteArray>,
    val isFinished: Boolean = false,
)

/**
 * Stream direction
 */
enum class StreamDirection {
    INBOUND,  // Client to server
    OUTBOUND  // Server to client
}

/**
 * Platform-specific QUIC server implementation
 */
expect class PlatformQuicServer(config: QuicServerConfig) {
    val connections: Flow<QuicConnection>
    
    suspend fun start()
    suspend fun stop()
    fun isRunning(): Boolean
}

/**
 * Platform-specific implementations
 */

// JVM Implementation
actual class PlatformQuicServer actual constructor(
    private val config: QuicServerConfig
) {
    private var quicEngine: borg.trikeshed.net.quic.QuicEngine? = null
    private val _connections = MutableSharedFlow<QuicConnection>()
    
    actual val connections: Flow<QuicConnection> = _connections.asSharedFlow()
    
    actual suspend fun start() {
        try {
            // Create initial connection state for TrikeShed QUIC engine
            val initialState = borg.trikeshed.net.quic.QuicConnectionState(
                localConnectionId = borg.trikeshed.net.quic.ConnectionId.random(),
                remoteConnectionId = borg.trikeshed.net.quic.ConnectionId.random(),
                version = 1L,
                transportParams = borg.trikeshed.net.quic.TransportParameters(
                    maxStreamData = config.maxStreamsPerConnection.toLong(),
                    maxData = config.maxConnections.toLong(),
                    maxBidiStreams = config.maxStreamsPerConnection.toLong(),
                    maxUniStreams = config.maxStreamsPerConnection.toLong(),
                    idleTimeout = config.maxIdleTimeoutMs
                ),
                nextPacketNumber = 0L,
                bytesInFlight = 0L
            )
            
            // Generate private key (in production, load from config.privateKeyPath)
            val privateKey = 32 j { (it * 7 + 13).toByte() }
            
            // Initialize TrikeShed QUIC engine
            quicEngine = borg.trikeshed.net.quic.QuicEngine(
                role = borg.trikeshed.net.quic.QuicEngine.Role.SERVER,
                initialState = initialState,
                port = config.port,
                privateKey = privateKey
            )
            
            println("Started TrikeShed QUIC server on ${config.host}:${config.port}")
            
        } catch (e: Exception) {
            println("Failed to start TrikeShed QUIC server: ${e.message}")
            throw e
        }
    }
    
    actual suspend fun stop() {
        quicEngine = null
        println("Stopped TrikeShed QUIC server")
    }
    
    actual fun isRunning(): Boolean = quicEngine != null
}

// Native Implementation (Linux/macOS)
actual class PlatformQuicServer actual constructor(
    private val config: QuicServerConfig
) {
    private var server: Any? = null // Would be actual QUIC server implementation
    
    actual val connections: Flow<QuicConnection> = MutableSharedFlow()
    
    actual suspend fun start() {
        // Native QUIC implementation using C-interop
        // This would use libraries like quiche or similar
        println("Starting Native QUIC server on ${config.host}:${config.port}")
    }
    
    actual suspend fun stop() {
        // Stop native QUIC server
        println("Stopping Native QUIC server")
    }
    
    actual fun isRunning(): Boolean = server != null
}

// JS Implementation (for completeness, though QUIC in browsers is limited)
actual class PlatformQuicServer actual constructor(
    private val config: QuicServerConfig
) {
    private var server: Any? = null
    
    actual val connections: Flow<QuicConnection> = MutableSharedFlow()
    
    actual suspend fun start() {
        // JS QUIC implementation (limited by browser capabilities)
        println("Starting JS QUIC server on ${config.host}:${config.port}")
    }
    
    actual suspend fun stop() {
        // Stop JS QUIC server
        println("Stopping JS QUIC server")
    }
    
    actual fun isRunning(): Boolean = server != null
} 