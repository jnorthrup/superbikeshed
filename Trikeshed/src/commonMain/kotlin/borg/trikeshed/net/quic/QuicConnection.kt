package borg.trikeshed.net.quic

import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.net.quic.QuicConfig.Companion.DEFAULT_STREAM_BUFFER_SIZE
import borg.trikeshed.net.quic.QuicConfig.Companion.STREAM_ID_HEADER_SIZE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import borg.trikeshed.nio.PlatformByteBuffer
import borg.trikeshed.nio.PlatformDatagramPacket
import borg.trikeshed.nio.PlatformDatagramSocket
import borg.trikeshed.nio.PlatformInetSocketAddress
import kotlinx.coroutines.CoroutineDispatcher

/**
 * QUIC connection implementation with 0-RTT support and stream multiplexing
 */
class QuicConnection(
    private val config: QuicConfig,
    private val sessionCache: QuicSessionCache,
    private val coroutineScope: CoroutineScope, // Scope for launching background tasks like packet receiver
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default // Injectable dispatcher
) {
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

        val connectedSuccessfully = withContext(dispatcher) {
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
            packetReceiverJob = coroutineScope.launch(dispatcher) { startPacketReceiver() }
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
            packetReceiverJob = coroutineScope.launch(dispatcher) { startPacketReceiver() }
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
    suspend fun sendData(streamId: Long, data: PlatformByteBuffer): Boolean = withContext(dispatcher) {
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
    private fun establishRegularConnection(serverAddr: PlatformInetSocketAddress): PlatformDatagramSocket {
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

    private fun establish0RTTConnection(serverAddr: PlatformInetSocketAddress, session: QuicSessionData): PlatformDatagramSocket {
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
        withContext(dispatcher) {
            socket?.close()
        }
        activeStreams.values.forEach { it.close() }
        activeStreams.clear()
        println("QUIC connection closed.")
    }

    private fun allocateStreamId(): Long {
        // Simple sequential allocation for client-initiated streams
        // In a real implementation, this needs to handle bidirectional/unidirectional and client/server initiated IDs correctly.
        val streamId = nextClientStreamId
        nextClientStreamId += 1
        return streamId
    }
} 