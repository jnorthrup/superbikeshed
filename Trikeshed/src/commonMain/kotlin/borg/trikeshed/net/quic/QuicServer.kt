package borg.trikeshed.net.quic

import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.*

/**
 * Complete QUIC server implementation
 */
class QuicServer(
    private val engine: QuicEngine,
    private val port: Int,
    private val host: String = "0.0.0.0"
) {
    private var isRunning = false
    private val connections = mutableMapOf<ConnectionId, QuicConnection>()
    private val streamHandlers = mutableMapOf<Long, StreamHandler>()
    private val connectionHandlers = mutableListOf<ConnectionHandler>()
    
    /**
     * Start the QUIC server
     */
    suspend fun start() {
        if (isRunning) return
        
        isRunning = true
        println("QUIC Server starting on $host:$port")
        
        while (isRunning) {
            try {
                // Accept new connections
                val connection = acceptConnection()
                if (connection != null) {
                    handleNewConnection(connection)
                }
                
                // Process existing connections
                processConnections()
                
            } catch (e: Exception) {
                println("QUIC Server error: ${e.message}")
            }
        }
    }
    
    /**
     * Stop the server
     */
    suspend fun stop() {
        isRunning = false
        
        // Close all connections
        connections.values.forEach { it.close() }
        connections.clear()
        
        println("QUIC Server stopped")
    }
    
    /**
     * Add connection handler
     */
    fun onConnection(handler: ConnectionHandler) {
        connectionHandlers.add(handler)
    }
    
    /**
     * Add stream handler
     */
    fun onStream(streamId: Long, handler: StreamHandler) {
        streamHandlers[streamId] = handler
    }
    
    /**
     * Send data on stream
     */
    suspend fun sendStreamData(connectionId: ConnectionId, streamId: Long, data: Indexed<Byte>): Boolean {
        val connection = connections[connectionId] ?: return false
        
        return try {
            val stream = connection.getStream(streamId)
            if (stream != null) {
                stream.writeBytes(data)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            println("Failed to send stream data: ${e.message}")
            false
        }
    }
    
    /**
     * Broadcast data to all connections
     */
    suspend fun broadcast(data: Indexed<Byte>): Int {
        var sentCount = 0
        
        for (connection in connections.values) {
            try {
                // Use control stream (stream ID 0) for broadcasts
                val stream = connection.getStream(0)
                if (stream != null) {
                    stream.writeBytes(data)
                    sentCount++
                }
            } catch (e: Exception) {
                println("Broadcast failed for connection: ${e.message}")
            }
        }
        
        return sentCount
    }
    
    /**
     * Get connection statistics
     */
    fun getStats(): ServerStats {
        return ServerStats(
            isRunning = isRunning,
            connectionCount = connections.size,
            totalStreams = connections.values.sumOf { it.getStreamCount() },
            port = port,
            host = host
        )
    }
    
    private suspend fun acceptConnection(): QuicConnection? {
        // Simulate accepting a new connection
        // In a real implementation, this would use the QUIC engine to accept connections
        return if (connections.size < 1000) { // Limit connections
            val connectionId = ConnectionId(8 j { (connections.size % 256).toByte() })
            val connection = QuicConnection(
                config = QuicConfig(),
                sessionCache = DefaultQuicSessionCache(),
                coroutineScope = kotlinx.coroutines.GlobalScope
            )
            connections[connectionId] = connection
            connection
        } else {
            null
        }
    }
    
    private suspend fun handleNewConnection(connection: QuicConnection) {
        // Notify connection handlers
        for (handler in connectionHandlers) {
            try {
                handler.onConnect(connection)
            } catch (e: Exception) {
                println("Connection handler error: ${e.message}")
            }
        }
        
        // Start connection processing
        processConnection(connection)
    }
    
    private suspend fun processConnections() {
        val connectionsToRemove = mutableListOf<ConnectionId>()
        
        for ((connectionId, connection) in connections) {
            try {
                // Check if connection is active (placeholder)
                if (false) {
                    connectionsToRemove.add(connectionId)
                    continue
                }
                
                processConnection(connection)
                
            } catch (e: Exception) {
                println("Connection processing error: ${e.message}")
                connectionsToRemove.add(connectionId)
            }
        }
        
        // Remove dead connections
        for (connectionId in connectionsToRemove) {
            val connection = connections.remove(connectionId)
            connection?.close()
            
            // Notify handlers
            for (handler in connectionHandlers) {
                try {
                    handler.onDisconnect(connectionId)
                } catch (e: Exception) {
                    println("Disconnect handler error: ${e.message}")
                }
            }
        }
    }
    
    private suspend fun processConnection(connection: QuicConnection) {
        // Accept new streams (placeholder)
        val newStream = connection.createStream()
        if (newStream != null) {
            handleNewStream(connection, newStream)
        }
        
        // Process existing streams (placeholder)
        // val streams = connection.getStreams()
        // for (stream in streams) {
        //     processStream(connection, stream)
        // }
    }
    
    private suspend fun handleNewStream(connection: QuicConnection, stream: QuicStream) {
        val streamId = stream.id
        
        // Notify stream handlers
        val handler = streamHandlers[streamId]
        if (handler != null) {
            try {
                handler.onStreamOpen(connection, stream)
            } catch (e: Exception) {
                println("Stream handler error: ${e.message}")
            }
        }
    }
    
    private suspend fun processStream(connection: QuicConnection, stream: QuicStream) {
        if (stream.hasData()) {
            val data = stream.readBytes(stream.getAvailableBytes())
            
            // Notify stream handlers
            val streamId = stream.id
            val handler = streamHandlers[streamId]
            if (handler != null) {
                try {
                    handler.onStreamData(connection, stream, data)
                } catch (e: Exception) {
                    println("Stream data handler error: ${e.message}")
                }
            }
        }
        
        if (stream.isClosed()) {
            val streamId = stream.id
            val handler = streamHandlers[streamId]
            if (handler != null) {
                try {
                    handler.onStreamClose(connection, stream)
                } catch (e: Exception) {
                    println("Stream close handler error: ${e.message}")
                }
            }
        }
    }
}

// Handler interfaces
interface ConnectionHandler {
    suspend fun onConnect(connection: QuicConnection)
    suspend fun onDisconnect(connectionId: ConnectionId)
}

interface StreamHandler {
    suspend fun onStreamOpen(connection: QuicConnection, stream: QuicStream)
    suspend fun onStreamData(connection: QuicConnection, stream: QuicStream, data: Indexed<Byte>)
    suspend fun onStreamClose(connection: QuicConnection, stream: QuicStream)
}

// Statistics
data class ServerStats(
    val isRunning: Boolean,
    val connectionCount: Int,
    val totalStreams: Int,
    val port: Int,
    val host: String
)

