package borg.trikeshed.net.quic

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import borg.trikeshed.nio.PlatformByteBuffer
import borg.trikeshed.nio.PlatformDatagramSocket
import borg.trikeshed.nio.PlatformInetSocketAddress
import borg.trikeshed.nio.PlatformDatagramPacket
import borg.trikeshed.nio.platformCurrentTimeMillis

/**
 * QUIC connection implementation with 0-RTT support and stream multiplexing
 */
class QuicConnection(
    private val config: QuicConfig,
    private val sessionCache: QuicSessionCache,
    private val coroutineScope: CoroutineScope // Scope for launching background tasks like packet receiver
) {
    companion object {
        const val MAX_STREAMS = 1L shl 62 // 2^62 concurrent streams
        const val DEFAULT_STREAM_BUFFER_SIZE = 64 * 1024 // 64KB
        const val STREAM_ID_HEADER_SIZE = Long.SIZE_BYTES // 8 bytes for Stream ID
    }

    private var socket: PlatformDatagramSocket? = null
    private var remoteServerAddress: PlatformInetSocketAddress? = null // New property to store the remote server address
    private val activeStreams = mutableMapOf<Long, QuicStream>()
    private val streamBufferSize = config.streamBufferSize ?: DEFAULT_STREAM_BUFFER_SIZE
    private var packetReceiverJob: Job? = null

    // Connection-level flow control (simplified)
    private var bytesSentSinceLastWindowUpdate: Long = 0L
    private var currentConnectionFlowControlWindow: Long = 0L // Initialized from config
    private var nextClientStreamId = 0L // Used for allocating client-initiated bidirectional streams

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

        val connectedSuccessfully = withContext(Dispatchers.IO) {
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
            packetReceiverJob = coroutineScope.launch(Dispatchers.IO) { startPacketReceiver() }
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
            packetReceiverJob = coroutineScope.launch(Dispatchers.IO) { startPacketReceiver() }
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
    suspend fun sendData(streamId: Long, data: PlatformByteBuffer): Boolean = withContext(Dispatchers.IO) {
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

    private suspend fun startPacketReceiver() {
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
                    receivedBuffer.get(streamData.array(), 0, receivedBuffer.remaining()) // Read remaining data into streamData
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

    // receiveData now consumes from the stream's internal channel
    fun receiveData(streamId: Long): Flow<PlatformByteBuffer> {
        val stream = activeStreams[streamId] ?: return flow { /* emit nothing if stream doesn't exist */ }
        return stream.internalReceiveChannel.consumeAsFlow()
    }


    /**
     * Closes a specific stream
     */
    suspend fun closeStream(streamId: Long) {
        activeStreams[streamId]?.close()
        activeStreams.remove(streamId)
    }

    /**
     * Closes the entire connection
     */
    suspend fun close() {
        packetReceiverJob?.cancel() // Stop the packet receiver
        activeStreams.values.forEach { it.close() }
        activeStreams.clear()
        withContext(Dispatchers.IO) {
            socket?.close()
        }
        socket = null
        remoteServerAddress = null // Clear the remote server address on close
    }

    private suspend fun establish0RTTConnection(
        serverAddr: PlatformInetSocketAddress,
        sessionData: QuicSessionData
    ): PlatformDatagramSocket = withContext(Dispatchers.IO) {
        val socket = PlatformDatagramSocket()
        socket.connect(serverAddr)
        
        // Send 0-RTT packet with session data
        val initialPacket = create0RTTPacket(sessionData)
        socket.send(PlatformDatagramPacket(initialPacket, initialPacket.size, serverAddr))
        
        // Wait for server response
        val responseBuffer = ByteArray(streamBufferSize)
        val responsePacket = PlatformDatagramPacket(responseBuffer, responseBuffer.size)
        socket.receive(responsePacket)
        
        return@withContext socket
    }

    private suspend fun establishRegularConnection(
        serverAddr: PlatformInetSocketAddress
    ): PlatformDatagramSocket = withContext(Dispatchers.IO) {
        val socket = PlatformDatagramSocket()
        socket.connect(serverAddr)
        
        // Send initial handshake packet
        val initialPacket = createInitialPacket()
        socket.send(PlatformDatagramPacket(initialPacket, initialPacket.size, serverAddr))
        
        // Wait for server response
        val responseBuffer = ByteArray(streamBufferSize)
        val responsePacket = PlatformDatagramPacket(responseBuffer, responseBuffer.size)
        socket.receive(responsePacket)

        // Assume server sends session ticket after handshake in regular connection
        // This is a simplification. In a real QUIC implementation, this would be part of
        // the cryptographic handshake and NEW_SESSION_TICKET frame.
        val sessionTicket = extractSessionTicket(responsePacket.data, responsePacket.length)
        if (sessionTicket != null) {
            val sessionData = QuicSessionData(
                serverAddress = serverAddr.hostName,
                port = serverAddr.port,
                sessionId = "SESSION_ID".toByteArray(), // Placeholder for session ID
                ticket = sessionTicket,
                expirationTime = platformCurrentTimeMillis() + (3600 * 1000) // Example: ticket valid for 1 hour
            )
            sessionCache.storeSession(serverAddr.hostName, serverAddr.port, sessionData)
        }
        
        return@withContext socket
    }

    private fun extractSessionTicket(bytes: ByteArray, length: Int): ByteArray? {
        // Placeholder: A real implementation would parse the packet
        // to extract the session ticket according to QUIC protocol.
        // For this example, let's assume the ticket is the packet data itself if it's not empty
        // and not the initial packet placeholder.
        val packetData = bytes.copyOfRange(0, length)
        if (length > 0 && !packetData.contentEquals("INITIAL_PACKET".toByteArray())) {
            return packetData
        }
        return null
    }

    private fun allocateStreamId(): Long {
        val id = nextClientStreamId
        nextClientStreamId += 4 // Client initiated streams are even, server initiated are odd. Increment by 4 for bidirectional.
        return id
    }

    private fun create0RTTPacket(sessionData: QuicSessionData): ByteArray {
        // For simplicity, let's assume the ticket is sent as is.
        // A real implementation would involve more complex packet construction.
        return sessionData.ticket
    }

    private fun createInitialPacket(): ByteArray {
        // TODO: Implement proper initial packet creation
        // This would involve cryptographic handshake messages.
        // For now, returning a placeholder.
        return "INITIAL_PACKET".toByteArray()
    }
}

/**
 * Configuration for QUIC connection
 */
data class QuicConfig(
    // Existing parameters
    val streamBufferSize: Int? = null,
    val maxConcurrentStreams: Long = QuicConnection.MAX_STREAMS,
    val enable0RTT: Boolean = true,

    // New parameters for protocol optimization
    val congestionControlAlgorithm: String = "cubic", // e.g., "cubic", "bbr", "reno", "custom_db_optimized"
    val initialConnectionFlowControlWindow: Long = 64 * 1024, // 64KB default
    val initialStreamFlowControlWindow: Long = 32 * 1024, // 32KB default per stream
    val maxAckDelayMs: Long = 25, // Max time in ms receiver can delay sending an ACK (conceptual)
    val defaultStreamPriority: Int = 10 // Default priority for new streams (e.g., 0=high, 10=medium, 20=low)
) {
    init {
        require(initialConnectionFlowControlWindow >= 0) { "Initial connection flow control window cannot be negative." }
        require(initialStreamFlowControlWindow >= 0) { "Initial stream flow control window cannot be negative." }
        require(maxAckDelayMs >= 0) { "Max ACK delay cannot be negative." }
        require(congestionControlAlgorithm.isNotBlank()) { "Congestion control algorithm name cannot be blank."}
        require(defaultStreamPriority >= 0) { "Default stream priority cannot be negative."}
    }
}

/**
 * Represents a QUIC stream
 */
class QuicStream(
    val id: Long,
    private val bufferSize: Int,
    initialWindowSize: Long, // Passed from QuicConfig.initialStreamFlowControlWindow
    val priority: Int, // Stream priority
    // Channel for this stream's incoming data, to be populated by the central packet receiver
    internal val internalReceiveChannel: Channel<PlatformByteBuffer> = Channel(Channel.BUFFERED)
) {
    @Volatile
    private var closed = false
    var remoteAddress: PlatformInetSocketAddress? = null // This should be set when stream is created or by first packet

    // Stream-level flow control properties
    var bytesSentOnStream: Long = 0L
    var currentStreamFlowControlWindow: Long = initialWindowSize
        private set // Window size can be updated by WINDOW_UPDATE frames (conceptually)

    fun close() {
        closed = true
        internalReceiveChannel.close() // Close the channel when stream is closed
    }

    fun isClosed(): Boolean = closed

    // Conceptual method for updating stream window by a WINDOW_UPDATE frame
    fun updateFlowControlWindow(newMaxData: Long) {
        // In QUIC, WINDOW_UPDATE typically provides the new maximum absolute byte offset allowed.
        // This translates to increasing the window size if newMaxData is larger than current sent + window.
        // For simplicity here, let's assume it can directly increase the current window size or reset sent bytes.
        // This is a simplification.
        val newWindow = newMaxData - bytesSentOnStream
        if (newWindow > currentStreamFlowControlWindow) {
            currentStreamFlowControlWindow = newWindow
            println("Stream $id window updated to $currentStreamFlowControlWindow (Max data: $newMaxData)")
        }
    }
}