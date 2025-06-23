package borg.trikeshed.net.quic

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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * QUIC connection implementation with 0-RTT support and stream multiplexing
 */
class QuicConnection(
    private val config: QuicConfig,
    private val sessionCache: QuicSessionCache,
    private val coroutineScope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private var socket: PlatformDatagramSocket? = null
    private var remoteServerAddress: PlatformInetSocketAddress? = null
    private val activeStreams = mutableMapOf<Long, QuicStream>()
    private val streamBufferSize = config.streamBufferSize ?: DEFAULT_STREAM_BUFFER_SIZE
    private var packetReceiverJob: Job? = null
    private val connectionMutex = Mutex()

    // Connection-level flow control
    private var bytesSentSinceLastWindowUpdate: Long = 0L
    private var currentConnectionFlowControlWindow: Long = 0L
    private var nextClientStreamId = 0L
    private var isClosed = false

    // Flow control backpressure channels
    private val connectionFlowControlChannel = Channel<Unit>(Channel.BUFFERED)
    private val streamFlowControlChannels = mutableMapOf<Long, Channel<Unit>>()

    init {
        this.currentConnectionFlowControlWindow = config.initialConnectionFlowControlWindow
        println("QUIC Connection: Using congestion control algorithm: ${config.congestionControlAlgorithm}")
        if (config.congestionControlAlgorithm.equals("custom_db_optimized", ignoreCase = true)) {
            println("QUIC Connection: Applying custom database-optimized CC parameters.")
        }
        println("QUIC Connection: Initial connection flow control window: $currentConnectionFlowControlWindow bytes")
        println("QUIC Connection: Initial stream flow control window: ${config.initialStreamFlowControlWindow} bytes (per stream)")
        println("QUIC Connection: Max ACK delay: ${config.maxAckDelayMs} ms")
    }

    /**
     * Attempts to establish a 0-RTT connection using cached session data
     * @throws QuicError.ConnectionError if connection fails
     */
    suspend fun connectWith0RTT(serverAddress: String, port: Int): Boolean = connectionMutex.withLock {
        if (isClosed) throw QuicError.ConnectionError.ConnectionClosed()
        
        bytesSentSinceLastWindowUpdate = 0L
        try {
            val cachedSession = sessionCache.getSession(serverAddress, port)
            val serverAddr = PlatformInetSocketAddress(serverAddress, port)
            
            if (cachedSession != null && config.enable0RTT) {
                try {
                    socket = establish0RTTConnection(serverAddr, cachedSession)
                    println("Attempted 0-RTT connection to $serverAddress:$port")
                    true
                } catch (e: Exception) {
                    println("0-RTT connection failed, falling back to regular connection: ${e.message}")
                    sessionCache.clearSession(serverAddress, port)
                    socket = establishRegularConnection(serverAddr)
                    false
                }
            } else {
                socket = establishRegularConnection(serverAddr)
                false
            }
        } catch (e: Exception) {
            throw QuicError.ConnectionError.HandshakeFailed(e)
        }

        if (socket?.isConnected == true) {
            this.remoteServerAddress = PlatformInetSocketAddress(serverAddress, port)
            packetReceiverJob = coroutineScope.launch(dispatcher) { startPacketReceiver() }
            println("Connection established to $serverAddress:$port. Receiver started.")
            return true
        }
        throw QuicError.ConnectionError.HandshakeFailed()
    }

    /**
     * Creates a new stream for data transfer
     * @throws QuicError.StreamError if stream creation fails
     * @throws QuicError.ConnectionError if connection is not established
     */
    suspend fun createStream(priority: Int? = null): QuicStream = connectionMutex.withLock {
        if (isClosed) throw QuicError.ConnectionError.ConnectionClosed()
        
        val streamId = allocateStreamId()
        if (activeStreams.size >= config.maxConcurrentStreams) {
            throw QuicError.StreamError.StreamLimitExceeded()
        }

        val streamRemoteAddress = remoteServerAddress ?: 
            throw QuicError.ConnectionError.NotConnected()

        val actualPriority = priority ?: config.defaultStreamPriority
        val stream = QuicStream(
            id = streamId,
            bufferSize = streamBufferSize,
            initialWindowSize = config.initialStreamFlowControlWindow,
            priority = actualPriority
        )
        stream.remoteAddress = streamRemoteAddress
        activeStreams[streamId] = stream
        streamFlowControlChannels[streamId] = Channel(Channel.BUFFERED)
        
        println("Created stream $streamId with priority $actualPriority and flow control window ${stream.currentStreamFlowControlWindow} bytes.")
        return stream
    }

    /**
     * Sends data over a specific stream with flow control
     * @throws QuicError.StreamError if stream is not found or closed
     * @throws QuicError.ConnectionError if connection issues occur
     */
    suspend fun sendData(streamId: Long, data: PlatformByteBuffer): Boolean = withContext(dispatcher) {
        val stream = activeStreams[streamId] ?: 
            throw QuicError.StreamError.StreamNotFound(streamId)
        
        if (stream.isClosed()) {
            throw QuicError.StreamError.StreamClosed(streamId)
        }

        val currentSocket = socket ?: 
            throw QuicError.ConnectionError.NotConnected()

        val dataSize = data.remaining()

        // Connection-Level Flow Control
        connectionMutex.withLock {
            if (bytesSentSinceLastWindowUpdate + dataSize > currentConnectionFlowControlWindow) {
                throw QuicError.ConnectionError.FlowControlBlocked(
                    currentConnectionFlowControlWindow,
                    bytesSentSinceLastWindowUpdate + dataSize
                )
            }
        }

        // Stream-Level Flow Control
        if (stream.bytesSentOnStream + dataSize > stream.currentStreamFlowControlWindow) {
            throw QuicError.StreamError.FlowControlBlocked(
                streamId,
                stream.currentStreamFlowControlWindow,
                stream.bytesSentOnStream + dataSize
            )
        }

        // Priority handling (log only for now)
        if (stream.priority < config.defaultStreamPriority) {
            println("Sending data for high-priority stream ${stream.id} (Priority: ${stream.priority})")
        }

        val packetBuffer = PlatformByteBuffer.allocate(STREAM_ID_HEADER_SIZE + dataSize)
        packetBuffer.putLong(streamId)
        packetBuffer.put(data.array(), data.position(), data.remaining())
        packetBuffer.flip()

        try {
            val destinationAddress = stream.remoteAddress ?:
                throw QuicError.ConnectionError.InvalidState("Stream ${stream.id} has no remote address")

            val datagramPacket = PlatformDatagramPacket(
                packetBuffer.array(),
                packetBuffer.limit(),
                destinationAddress
            )

            if (packetBuffer.limit() > config.mtu) {
                throw QuicError.TransportError.PacketTooLarge(packetBuffer.limit(), config.mtu)
            }

            currentSocket.send(datagramPacket)

            // Update flow control windows
            connectionMutex.withLock {
                bytesSentSinceLastWindowUpdate += dataSize
                stream.bytesSentOnStream += dataSize

                // Simulate receiving WINDOW_UPDATEs
                if (bytesSentSinceLastWindowUpdate > currentConnectionFlowControlWindow * 0.75) {
                    println("Connection WINDOW_UPDATE: Resetting bytesSentSinceLastWindowUpdate (was $bytesSentSinceLastWindowUpdate)")
                    bytesSentSinceLastWindowUpdate = 0
                    connectionFlowControlChannel.trySend(Unit)
                }

                if (stream.bytesSentOnStream > stream.currentStreamFlowControlWindow * 0.75) {
                    println("Stream ${stream.id} WINDOW_UPDATE: Resetting bytesSentOnStream (was ${stream.bytesSentOnStream})")
                    stream.bytesSentOnStream = 0
                    streamFlowControlChannels[streamId]?.trySend(Unit)
                }
            }
            return@withContext true
        } catch (e: Exception) {
            when (e) {
                is QuicError -> throw e
                else -> throw QuicError.TransportError.NetworkError("Error sending data on stream $streamId", e)
            }
        }
    }

    /**
     * Closes the connection and all associated resources
     * @throws QuicError.ConnectionError if already closed
     */
    suspend fun close() = connectionMutex.withLock {
        if (isClosed) throw QuicError.ConnectionError.ConnectionClosed()
        
        isClosed = true
        packetReceiverJob?.cancel()
        withContext(dispatcher) {
            socket?.close()
        }
        activeStreams.values.forEach { it.close() }
        activeStreams.clear()
        streamFlowControlChannels.values.forEach { it.close() }
        streamFlowControlChannels.clear()
        connectionFlowControlChannel.close()
        println("QUIC connection closed.")
    }

    private fun allocateStreamId(): Long {
        // Simple sequential allocation for client-initiated streams
        val streamId = nextClientStreamId
        nextClientStreamId += 1
        return streamId
    }

    private suspend fun startPacketReceiver() {
        val currentSocket = socket ?: return
        val receiveBuffer = PlatformByteBuffer.allocate(streamBufferSize + STREAM_ID_HEADER_SIZE)
        val placeholderAddress = remoteServerAddress ?: PlatformInetSocketAddress("0.0.0.0", 0)

        while (!isClosed && currentSocket.isConnected && !currentSocket.isClosed) {
            try {
                val datagramPacket = PlatformDatagramPacket(
                    receiveBuffer.array(),
                    receiveBuffer.limit(),
                    placeholderAddress
                )
                currentSocket.receive(datagramPacket)

                if (datagramPacket.length < STREAM_ID_HEADER_SIZE) {
                    throw QuicError.ProtocolError.InvalidPacket(
                        "Received packet too small: ${datagramPacket.length} bytes"
                    )
                }

                val receivedBuffer = PlatformByteBuffer.wrap(datagramPacket.data, 0, datagramPacket.length)
                val streamId = receivedBuffer.getLong()

                connectionMutex.withLock {
                    val stream = activeStreams[streamId]
                    if (stream != null && !stream.isClosed()) {
                        val streamData = PlatformByteBuffer.allocate(receivedBuffer.remaining())
                        receivedBuffer.get(streamData.array())
                        streamData.flip()
                        stream.internalReceiveChannel.send(streamData)
                    } else {
                        throw QuicError.StreamError.StreamNotFound(streamId)
                    }
                }
            } catch (e: Exception) {
                when {
                    currentSocket.isClosed || !currentSocket.isConnected -> {
                        println("Socket closed, stopping packet receiver.")
                        break
                    }
                    e is QuicError -> println("QUIC error in packet receiver: ${e.message}")
                    else -> println("Error in packet receiver: ${e.message}")
                }
            }
        }
    }

    // Placeholder functions for handshake implementation
    private fun establishRegularConnection(serverAddr: PlatformInetSocketAddress): PlatformDatagramSocket {
        println("Establishing regular 1-RTT connection...")
        val newSocket = PlatformDatagramSocket.create()
        newSocket.connect(serverAddr)
        return newSocket
    }

    private fun establish0RTTConnection(
        serverAddr: PlatformInetSocketAddress,
        session: QuicSessionData
    ): PlatformDatagramSocket {
        println("Establishing 0-RTT connection with cached session...")
        val newSocket = PlatformDatagramSocket.create()
        newSocket.connect(serverAddr)
        return newSocket
    }
} 