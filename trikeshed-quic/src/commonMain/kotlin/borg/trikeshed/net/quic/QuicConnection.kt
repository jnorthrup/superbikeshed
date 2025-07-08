@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.quic

import borg.trikeshed.lib.*
import borg.trikeshed.io.IOContext
import borg.trikeshed.net.quic.QuicConfig.Companion.DEFAULT_STREAM_BUFFER_SIZE
import borg.trikeshed.net.quic.QuicConfig.Companion.STREAM_ID_HEADER_SIZE
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

// Simple platform types for QUIC (replaces missing nio package)
class PlatformByteBuffer(internal val data: ByteArray) {
    internal var position = 0
    internal var limit = data.size
    
    fun remaining(): Int = limit - position
    fun array(): ByteArray = data
    fun position(): Int = position
    fun flip() { limit = position; position = 0 }
    fun putLong(value: Long) { /* mock */ }
    fun put(bytes: ByteArray, offset: Int, length: Int) { /* mock */ }
    fun get(bytes: ByteArray) { /* mock */ }
    fun getLong(): Long = 0L
    
    companion object {
        fun allocate(size: Int) = PlatformByteBuffer(ByteArray(size))
        fun wrap(bytes: ByteArray) = PlatformByteBuffer(bytes)
    }
}

class PlatformInetSocketAddress(val host: String, val port: Int)

class PlatformDatagramPacket(val data: ByteArray, val length: Int, val address: PlatformInetSocketAddress)

class PlatformDatagramSocket {
    var isConnected = false
    var isClosed = false
    
    fun connect(address: PlatformInetSocketAddress) { isConnected = true }
    fun send(packet: PlatformDatagramPacket) { /* mock */ }
    fun receive(packet: PlatformDatagramPacket) { /* mock */ }
    fun close() { isClosed = true; isConnected = false }
    
    companion object {
        fun create() = PlatformDatagramSocket()
    }
}

/**
 * QUIC connection implementation with 0-RTT support and stream multiplexing
 */
class QuicConnection(
    internal val isClient: Boolean = true,
    internal val config: QuicConfig,
    internal val ioContext: IOContext? = null,
    internal val sessionCache: QuicSessionCache? = null,
    internal val coroutineScope: CoroutineScope = GlobalScope // Scope for launching background tasks like packet receiver
) {
    internal var socket: PlatformDatagramSocket? = null
    internal var remoteServerAddress: PlatformInetSocketAddress? = null // New property to store the remote server address
    internal val activeStreams = mutableMapOf<Long, QuicStream>()
    internal val streamBufferSize = config.streamBufferSize ?: DEFAULT_STREAM_BUFFER_SIZE
    internal var packetReceiverJob: Job? = null

    // Connection-level flow control (simplified)
    internal var bytesSentSinceLastWindowUpdate: Long = 0L
    internal var currentConnectionFlowControlWindow: Long = 0L // Initialized from config
    internal var nextClientStreamId = 0L // Used for allocating client-initiated bidirectional streams

    init {
        // Initialize from config
        this.currentConnectionFlowControlWindow = config.initialConnectionFlowControlWindow
        // Log selected congestion control algorithm (conceptual)
        println("QUIC Connection: Using congestion control algorithm: ${config.congestionControlAlgorithm}")
        if (config.congestionControlAlgorithm.equals("custom_db_optimized", ignoreCase = true)) {
            println("QUIC Connection: Applying custom database-optimized CC parameters (conceptual).")
            // Here, one might adjust internal timers or thresholds if we had a real CC implementation
        }
        println("QUIC Connection: Initial connection flow control window: $currentConnectionFlowControlWindow bytes")
        println("QUIC Connection: Initial stream flow control window: ${config.initialStreamFlowControlWindow} bytes (per stream, conceptual)")
        println("QUIC Connection: Max ACK delay: ${config.maxAckDelayMs} ms (conceptual)")
    }

    /**
     * Attempts to establish a 0-RTT connection using cached session data
     */
    suspend fun connectWith0RTT(serverAddress: String, port: Int): Boolean {
        // Reset flow control counters on new connection attempt
        bytesSentSinceLastWindowUpdate = 0L

        val connectedSuccessfully = withContext(Dispatchers.Default) {
            val cachedSession = sessionCache.getSession(serverAddress, port)
            val serverAddr = PlatformInetSocketAddress(serverAddress, port)
            if (cachedSession != null && config.enable0RTT) {
                try {
                    socket = establish0RTTConnection(serverAddr, cachedSession)
                    println("Attempted 0-RTT connection to $serverAddress:$port")
                    true
                } catch (e: Exception) {
                    println("0-RTT connection failed, falling back to regular connection: ${e.message}")
                    sessionCache.clearSession(serverAddress, port) // Clear potentially problematic session
                    socket = establishRegularConnection(serverAddr)
                    false
                }
            } else {
                socket = establishRegularConnection(serverAddr)
                false
            }
        }
        if (socket != null && socket!!.isConnected) {
            this.remoteServerAddress = PlatformInetSocketAddress(serverAddress, port) // Store the remote address
            packetReceiverJob = coroutineScope.launch { startPacketReceiver() }
            println("Connection established to $serverAddress:$port. Receiver started.")
            return true
        }
        println("Failed to establish connection to $serverAddress:$port.")
        return false
    }

    // Make connect public as it's a primary action
    suspend fun connect(serverAddress: String, port: Int): Boolean {
        // Reset flow control counters on new connection attempt
        bytesSentSinceLastWindowUpdate = 0L

        val serverAddr = PlatformInetSocketAddress(serverAddress, port)
        socket = establishRegularConnection(serverAddr)
        if (socket != null && socket!!.isConnected) {
            this.remoteServerAddress = PlatformInetSocketAddress(serverAddress, port) // Store the remote address
            packetReceiverJob = coroutineScope.launch { startPacketReceiver() }
            println("Regular connection established to $serverAddress:$port. Receiver started.")
            return true
        }
        println("Failed to establish regular connection to $serverAddress:$port.")
        return false
    }

    /**
     * Creates a new stream for data transfer
     */
    suspend fun createStream(priority: Int? = null): QuicStream? {
        val streamId = allocateStreamId()
        // Ensure remoteServerAddress is available before creating a stream
        val streamRemoteAddress = remoteServerAddress ?: run {
            println("Cannot create stream: Remote server address unknown. Connect first.")
            return null
        }

        // Get priority from arguments or use default from config
        val actualPriority = priority ?: config.defaultStreamPriority

        val stream = QuicStream(
            id = streamId,
            bufferSize = streamBufferSize,
            initialWindowSize = config.initialStreamFlowControlWindow, // Initialize stream FCW
            priority = actualPriority // Set priority
        )
        stream.remoteAddress = streamRemoteAddress
        activeStreams[streamId] = stream
        println("Created stream $streamId with priority $actualPriority and flow control window ${stream.currentStreamFlowControlWindow} bytes.")
        return stream
    }

    /**
     * Sends data over a specific stream
     */
    suspend fun sendData(streamId: Long, data: PlatformByteBuffer): Boolean = withContext(Dispatchers.Default) {
        val stream = activeStreams[streamId] ?: run {
            println("Stream $streamId not found for sending data.")
            return@withContext false
        }
        val currentSocket = socket ?: run {
            println("Socket not initialized for sending data.")
            return@withContext false
        }

        val dataSize = data.remaining()

        // Simplified Connection-Level Flow Control Check
        if (bytesSentSinceLastWindowUpdate + dataSize > currentConnectionFlowControlWindow) {
            println("Connection flow control window exceeded. Would block/queue. (Sent: $bytesSentSinceLastWindowUpdate, Data: $dataSize, Window: $currentConnectionFlowControlWindow)")
            // In a real implementation, this would trigger backpressure or wait for a WINDOW_UPDATE
            // For this simulation, we'll just log and allow sending, or could return false.
            // return@withContext false // Or allow for now and just log
        }

        // Conceptual Stream-Level Flow Control Check
        if (stream.bytesSentOnStream + dataSize > stream.currentStreamFlowControlWindow) {
            println("Stream ${stream.id} flow control window exceeded. Would block/queue. (Sent: ${stream.bytesSentOnStream}, Data: $dataSize, Window: ${stream.currentStreamFlowControlWindow})")
            // In a real impl, this would backpressure or wait for a stream-specific WINDOW_UPDATE
            // For simulation, we can choose to block (return false) or allow (log and proceed)
            // return@withContext false // Option to block
        }

        // Log priority consideration (actual sending logic doesn't reorder based on priority here)
        // True prioritization would involve a scheduler or prioritized send queues.
        if (stream.priority < config.defaultStreamPriority) { // Example: lower number = higher priority
            println("Sending data for high-priority stream ${stream.id} (Priority: ${stream.priority})")
        }


        val packetBuffer = PlatformByteBuffer.allocate(STREAM_ID_HEADER_SIZE + dataSize)
        packetBuffer.putLong(streamId)
        packetBuffer.put(data.array(), data.position(), data.remaining()) // Read from input buffer
        packetBuffer.flip()

        try {
            val destinationAddress = stream.remoteAddress ?: run {
                println("Stream ${stream.id} has no remote address set.")
                return@withContext false
            }
            val datagramPacket = PlatformDatagramPacket(
                packetBuffer.array(),
                packetBuffer.limit(),
                destinationAddress
            )
            currentSocket.send(datagramPacket)

            // Update sent bytes counters
            bytesSentSinceLastWindowUpdate += dataSize
            stream.bytesSentOnStream += dataSize

            // Simulate receiving WINDOW_UPDATEs more realistically (still simplified)
            // Connection level:
            if (bytesSentSinceLastWindowUpdate > currentConnectionFlowControlWindow * 0.75) { // Reduce if >75% of window used
                println("Simulating Connection WINDOW_UPDATE: Resetting bytesSentSinceLastWindowUpdate (was $bytesSentSinceLastWindowUpdate).")
                bytesSentSinceLastWindowUpdate = 0
                // A real WINDOW_UPDATE would provide a new absolute offset or increment.
                // For simplicity, we assume the window size itself doesn't change here, just that consumed data is acknowledged.
            }
            // Stream level:
            if (stream.bytesSentOnStream > stream.currentStreamFlowControlWindow * 0.75) { // Reduce if >75% of stream window used
                println("Simulating Stream ${stream.id} WINDOW_UPDATE: Resetting stream.bytesSentOnStream (was ${stream.bytesSentOnStream}).")
                stream.bytesSentOnStream = 0
                // stream.currentStreamFlowControlWindow might also be adjusted by a real WINDOW_UPDATE frame.
            }
            return@withContext true
        } catch (e: Exception) {
            println("Error sending data on stream $streamId: ${e.message}")
            return@withContext false
        }
    }

    internal suspend fun startPacketReceiver() {
        val currentSocket = socket ?: return
        val receiveBuffer = PlatformByteBuffer.allocate(streamBufferSize + STREAM_ID_HEADER_SIZE) // Max possible size

        // Use a placeholder address for the DatagramPacket constructor; the receive method will populate it.
        // This assumes remoteServerAddress is set after a successful connection.
        val placeholderAddress = remoteServerAddress ?: PlatformInetSocketAddress("0.0.0.0", 0) // Fallback dummy address

        while (currentSocket.isConnected && !currentSocket.isClosed) {
            try {
                val datagramPacket = PlatformDatagramPacket(receiveBuffer.array(), receiveBuffer.limit(), placeholderAddress)
                currentSocket.receive(datagramPacket)

                if (datagramPacket.length < STREAM_ID_HEADER_SIZE) {
                    // Packet too small to contain stream ID, log or ignore
                    println("Received packet too small: ${datagramPacket.length} bytes")
                    continue
                }

                val receivedBuffer = PlatformByteBuffer.wrap(datagramPacket.data, 0, datagramPacket.length)
                val streamId = receivedBuffer.getLong()

                val stream = activeStreams[streamId]
                if (stream != null && !stream.isClosed()) {
                    // remaining data for the stream (after stream ID)
                    val streamData = PlatformByteBuffer.allocate(receivedBuffer.remaining())
                    receivedBuffer.get(streamData.array()) // Read remaining data into streamData's backing array
                    streamData.flip()
                    stream.internalReceiveChannel.send(streamData)
                } else {
                    // Stream not found or closed, log or ignore
                    println("Data for unknown or closed stream ID: $streamId")
                }
            } catch (e: Exception) {
                if (currentSocket.isClosed || !currentSocket.isConnected) {
                    println("Socket closed, stopping packet receiver.")
                    break
                }
                println("Error in packet receiver: ${e.message}")
                // Depending on the exception, may need to break or continue
            }
        }
    }

    // These are placeholder functions that would need a real cryptographic and transport handshake implementation.
    internal fun establishRegularConnection(serverAddr: PlatformInetSocketAddress): PlatformDatagramSocket {
        println("Establishing regular 1-RTT connection (placeholder)...")
        // In a real implementation:
        // 1. Create a UDP socket.
        // 2. Perform a TLS 1.3 handshake over UDP to establish keys.
        // 3. Negotiate transport parameters.
        // 4. Store the session ticket for future 0-RTT.
        val newSocket = PlatformDatagramSocket.create()
        newSocket.connect(serverAddr)
        return newSocket
    }

    internal fun establish0RTTConnection(serverAddr: PlatformInetSocketAddress, session: QuicSessionData): PlatformDatagramSocket {
        println("Establishing 0-RTT connection with cached session (placeholder)...")
        // In a real implementation:
        // 1. Create UDP socket.
        // 2. Use the cached session ticket to encrypt initial data (0-RTT).
        // 3. Send initial data along with handshake packets.
        // 4. Handle server rejecting 0-RTT (fallback to 1-RTT).
        val newSocket = PlatformDatagramSocket.create()
        newSocket.connect(serverAddr)
        // Here you would use session.ticket and session.transportParams
        return newSocket
    }

    /**
     * Get stream by ID
     */
    fun getStream(streamId: Long): QuicStream? {
        return activeStreams[streamId]
    }

    /**
     * Get stream count
     */
    fun getStreamCount(): Int {
        return activeStreams.size
    }

    /**
     * Closes the connection and all associated resources.
     */
    suspend fun close() {
        packetReceiverJob?.cancel() // Stop the receiver loop
        withContext(Dispatchers.Default) {
            socket?.close()
        }
        activeStreams.values.forEach { it.close() }
        activeStreams.clear()
        println("QUIC connection closed.")
    }

    internal fun allocateStreamId(): Long {
        // Simple sequential allocation for client-initiated streams
        // In a real implementation, this needs to handle bidirectional/unidirectional and client/server initiated IDs correctly.
        val streamId = nextClientStreamId
        nextClientStreamId += 1
        return streamId
    }
    
    // Additional methods needed by QuicClient and QuicServer
    
    /**
     * Check if connection is alive and active
     */
    fun isAlive(): Boolean {
        return socket?.isConnected == true && !socket!!.isClosed
    }
    
    /**
     * Connect using 0-RTT if possible
     */
    suspend fun connect0RTT(host: String, port: Int, session: QuicSessionData, alpn: List<String>) {
        val serverAddr = PlatformInetSocketAddress(host, port)
        socket = establish0RTTConnection(serverAddr, session)
        if (socket?.isConnected == true) {
            this.remoteServerAddress = serverAddr
            packetReceiverJob = coroutineScope.launch { startPacketReceiver() }
        }
    }
    
    /**
     * Get current session data for caching
     */
    fun getSession(): QuicSessionData? {
        // Return cached session data if available
        return remoteServerAddress?.let { addr ->
            QuicSessionData(
                ticket = ByteArray(32) { it.toByte() }, // Mock session ticket
                transportParams = mapOf("max_streams" to "100"),
                createdAt = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Open bidirectional stream
     */
    suspend fun openBidirectionalStream(): QuicStream {
        return createStream() ?: throw IllegalStateException("Failed to create stream")
    }
    
    /**
     * Open unidirectional stream  
     */
    suspend fun openUnidirectionalStream(): QuicStream {
        return createStream() ?: throw IllegalStateException("Failed to create stream")
    }
    
    /**
     * Accept incoming stream (for server connections)
     */
    suspend fun acceptStream(): QuicStream? {
        if (isClient) return null
        // For server: accept incoming stream requests
        return createStream()
    }
    
    /**
     * Migrate connection to new path
     */
    suspend fun migrateConnection(newLocalAddress: String?): Boolean {
        // Connection migration is complex in QUIC
        // This is a simplified implementation
        return try {
            // In real implementation: validate new path, update routing
            println("Connection migration to $newLocalAddress (simulated)")
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get current RTT estimate
     */
    fun getRTT(): Duration {
        // Return estimated RTT - in real implementation this would be calculated
        return 50.milliseconds
    }
    
    /**
     * Get congestion window size
     */
    fun getCongestionWindow(): Long {
        // Return current congestion window size
        return currentConnectionFlowControlWindow
    }
    
    /**
     * Get bytes currently in flight
     */
    fun getBytesInFlight(): Long {
        return bytesSentSinceLastWindowUpdate
    }
    
    /**
     * Get packet loss count
     */
    fun getPacketsLost(): Long {
        // In real implementation: track packet loss statistics
        return 0L
    }
    
    /**
     * Get active stream count
     */
    fun getActiveStreamCount(): Int {
        return activeStreams.size
    }
    
    /**
     * Close connection with error code and reason
     */
    suspend fun close(errorCode: Long = 0, reason: String = "Connection closing") {
        println("Closing QUIC connection: $reason (code: $errorCode)")
        close()
    }
} 