package borg.trikeshed.net.quic

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import borg.trikeshed.nio.PlatformByteBuffer
import borg.trikeshed.nio.PlatformDatagramSocket
import borg.trikeshed.nio.PlatformInetSocketAddress
import borg.trikeshed.nio.PlatformDatagramPacket
import borg.trikeshed.nio.PlatformSocketException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class FrameType {
    PADDING, PING, ACK, STREAM, CONNECTION_CLOSE //, ... other common types
}

data class StreamFrame(
    val streamId: Long,
    val offset: Long,
    val length: Int,
    val fin: Boolean,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StreamFrame) return false

        if (streamId != other.streamId) return false
        if (offset != other.offset) return false
        if (length != other.length) return false
        if (fin != other.fin) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = streamId.hashCode()
        result = 31 * result + offset.hashCode()
        result = 31 * result + length
        result = 31 * result + fin.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

enum class QuicPacketType {
    INITIAL, ZERO_RTT, HANDSHAKE, SHORT_HEADER
}

data class QuicPacketHeader(
    val type: QuicPacketType,
    val destinationConnectionId: ByteArray,
    val sourceConnectionId: ByteArray,
    val packetNumber: Long,
    val version: Int = 0x00000001, // Default to QUIC v1
    val token: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QuicPacketHeader) return false

        if (type != other.type) return false
        if (!destinationConnectionId.contentEquals(other.destinationConnectionId)) return false
        if (!sourceConnectionId.contentEquals(other.sourceConnectionId)) return false
        if (packetNumber != other.packetNumber) return false
        if (version != other.version) return false
        if (token != null) {
            if (other.token == null) return false
            if (!token.contentEquals(other.token)) return false
        } else if (other.token != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + destinationConnectionId.contentHashCode()
        result = 31 * result + sourceConnectionId.contentHashCode()
        result = 31 * result + packetNumber.hashCode()
        result = 31 * result + version
        result = 31 * result + (token?.contentHashCode() ?: 0)
        return result
    }
}

data class QuicPacket(
    val header: QuicPacketHeader,
    val frames: List<Any> // TODO: Replace List<Any> with a sealed class hierarchy for specific frame types for type safety.
)


/**
 * Enhanced QUIC connection implementation with 0-RTT support and stream multiplexing
 */
class EnhancedQuicConnection(
    private val config: QuicConfig,
    private val sessionCache: QuicSessionCache
) {
    companion object {
        const val MAX_STREAMS = 1L shl 62 // 2^62 concurrent streams
        const val DEFAULT_STREAM_BUFFER_SIZE = 64 * 1024 // 64KB
    }

    private var socket: PlatformDatagramSocket? = null
    private val activeStreams = mutableMapOf<Long, QuicStream>()
    private val streamBufferSize = config.streamBufferSize ?: DEFAULT_STREAM_BUFFER_SIZE
    private var nextClientStreamId = 0L // Used for allocating client-initiated bidirectional streams

    // Connection State
    private var localConnectionId: ByteArray = Random.nextBytes(8) // Placeholder
    private var remoteConnectionId: ByteArray = ByteArray(0)      // To be set after handshake
    private var nextPacketNumber: Long = 0L
    // TODO: Actual Connection IDs should be established during the handshake.
    private var receivingJob: Job? = null


    /**
     * Attempts to establish a connection, using 0-RTT if possible and enabled.
     * Returns `true` if a connection was established (either 0-RTT or 1-RTT), `false` otherwise.
     * Note that `true` does not guarantee 0-RTT was used.
     * // NOTE: For 0-RTT to function, the server must be configured to issue session tickets (RFC 8446 for TLS 1.3)
     * // and accept 0-RTT data. The client needs a robust mechanism to handle server rejection of 0-RTT
     * // and fall back to a 1-RTT handshake.
     */
    suspend fun connectWith0RTT(serverAddress: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        val cachedSession = sessionCache.getSession(serverAddress, port)
        if (cachedSession != null && config.enable0RTT) {
            try {
                socket = establish0RTTConnection(serverAddress, port, cachedSession)
                // TODO: Process server's handshake. If 0-RTT is accepted, the server's response will allow us to proceed.
                // If rejected (e.g., server sends a Retry packet or initiates a full handshake),
                // we must handle that state, potentially clear the session ticket, and not assume 0-RTT was successful.
                // The current implementation incorrectly assumes any response after sending a 0-RTT packet means 0-RTT was successful.
                return@withContext true // Connection established, potentially 0-RTT
            } catch (e: Exception) {
                // Fall back to regular connection if 0-RTT fails
                sessionCache.clearSession(serverAddress, port) // Clear potentially problematic session
                socket = establishRegularConnection(serverAddress, port)
                return@withContext true // Connection established via 1-RTT fallback
            }
        } else {
            // Proceed with a regular 1-RTT connection
            socket = establishRegularConnection(serverAddress, port)
            return@withContext true // Connection established via 1-RTT
        }
    }

    /**
     * Creates a new stream for data transfer
     */
    suspend fun createStream(): QuicStream {
        val streamId = allocateStreamId()
        // Ensure streamBufferSize is not null, providing a default if necessary.
        val resolvedStreamBufferSize = streamBufferSize ?: DEFAULT_STREAM_BUFFER_SIZE
        val stream = QuicStream(streamId, resolvedStreamBufferSize, this)
        stream.state = QuicStreamState.OPEN // Set initial state to OPEN
        activeStreams[streamId] = stream
        return stream
    }

    /**
     * Sends data over a specific stream
     */
    suspend fun sendData(streamId: Long, data: PlatformByteBuffer): Boolean = withContext(Dispatchers.IO) {
        val stream = activeStreams[streamId]
        if (stream == null) {
            // Log error: stream not found
            return@withContext false
        }

        // TODO: Refine stream states for sending.
        // Stream should be OPEN or only closed by the remote side (half-closed remote) to allow sending.
        if (stream.state != QuicStreamState.OPEN && stream.state != QuicStreamState.REMOTE_CLOSED) {
            println("Stream ${stream.id} not in valid state for sending data. State: ${stream.state}")
            return@withContext false
        }

        val dataBytes = ByteArray(data.remaining())
        data.get(dataBytes)

        val streamFrame = StreamFrame(
            streamId = stream.id,
            offset = stream.sendOffset,
            length = dataBytes.size,
            fin = false, // TODO: Allow sending FIN bit via an extra parameter or stream state change.
            data = dataBytes
        )

        val packetHeader = QuicPacketHeader(
            type = QuicPacketType.SHORT_HEADER, // Assuming connection established
            destinationConnectionId = remoteConnectionId,
            sourceConnectionId = localConnectionId, // Note: CIDs might be zero-length for Short Headers post-handshake in some scenarios.
            packetNumber = nextPacketNumber++
        )

        val packet = QuicPacket(header = packetHeader, frames = listOf(streamFrame))
        val serializedPacketBytes = serializePacket(packet)

        val currentSocket = this.socket ?: return@withContext false
        // Use the connection's remote address, as socket is connected.
        val currentRemoteAddress = currentSocket.remoteSocketAddress as? PlatformInetSocketAddress
            ?: return@withContext false // Should be set if socket.connect() was called.

        val datagramPacket = PlatformDatagramPacket(
            serializedPacketBytes,
            serializedPacketBytes.size,
            currentRemoteAddress
        )
        
        try {
            currentSocket.send(datagramPacket)
            stream.sendOffset += dataBytes.size
            return@withContext true
        } catch (e: Exception) {
            // Log exception e
            println("Failed to send data on stream ${stream.id}: ${e.message}")
            return@withContext false
        }
    }


    suspend fun sendFin(streamId: Long): Boolean = withContext(Dispatchers.IO) {
        val stream = activeStreams[streamId]
        if (stream == null) {
            println("sendFin: Stream $streamId not found.")
            return@withContext false
        }

        // Allow sending FIN if stream is OPEN, or if locally closed (meaning we are initiating the FIN now),
        // or if remotely closed (meaning we are responding to their FIN with our own).
        if (stream.state != QuicStreamState.OPEN &&
            stream.state != QuicStreamState.LOCAL_CLOSED && // If close() just set this state and called sendFin
            stream.state != QuicStreamState.REMOTE_CLOSED) {
            println("sendFin: Stream $streamId is not in a state where FIN can be sent. State: ${stream.state}")
            return@withContext false
        }

        val streamFrame = StreamFrame(
            streamId = stream.id,
            offset = stream.sendOffset, // FIN might consume an offset if spec requires for 0-len data
            length = 0,
            fin = true,
            data = ByteArray(0)
        )

        val packetHeader = QuicPacketHeader(
            type = QuicPacketType.SHORT_HEADER,
            destinationConnectionId = remoteConnectionId,
            sourceConnectionId = localConnectionId,
            packetNumber = nextPacketNumber++
        )

        val packet = QuicPacket(header = packetHeader, frames = listOf(streamFrame))
        val serializedPacketBytes = serializePacket(packet)

        val currentSocket = this.socket ?: return@withContext false
        val currentRemoteAddress = currentSocket.remoteSocketAddress as? PlatformInetSocketAddress ?: return@withContext false

        val datagramPacket = PlatformDatagramPacket(
            serializedPacketBytes,
            serializedPacketBytes.size,
            currentRemoteAddress
        )

        try {
            currentSocket.send(datagramPacket)
            // Note: A FIN frame itself doesn't increment sendOffset unless it carries data.
            // If it were to consume a sendOffset value, it would be: stream.sendOffset += 0 (or 1 if it's like a sequence number)
            println("Sent FIN for stream ${stream.id}")
            return@withContext true
        } catch (e: Exception) {
            println("Failed to send FIN for stream ${stream.id}: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Receives data from a specific stream
     */
    fun receiveData(streamId: Long): Flow<PlatformByteBuffer> {
        val stream = activeStreams[streamId] ?: return flow { /* emit nothing or error for non-existent stream */ }
        return stream.dataFlow()
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
        // TODO: Consider proper coroutine cancellation and join strategy.
        receivingJob?.cancel()
        socket?.close() // This should interrupt the blocking receive in the loop
        receivingJob?.join() // Wait for the receiving loop to finish
        socket = null
        activeStreams.values.forEach { it.close() } // Close all active streams
        activeStreams.clear()
    }

    private suspend fun establish0RTTConnection(
        serverAddress: String,
        port: Int,
        sessionData: QuicSessionData
    ): PlatformDatagramSocket = withContext(Dispatchers.IO) {
        val socket = PlatformDatagramSocket()
        val serverAddr = PlatformInetSocketAddress(serverAddress, port)
        socket.connect(serverAddr) // Connect the socket
        
        // Send 0-RTT packet with session data
        val initialPacket = create0RTTPacket(sessionData)
        // Use serverAddr for initial packet as socket.remoteSocketAddress might not be immediately available
        // or could be an issue if connect is async, though for DatagramSocket.connect it's synchronous.
        socket.send(PlatformDatagramPacket(initialPacket, initialPacket.size, serverAddr))
        
        // Wait for server response
        val responseBuffer = ByteArray(streamBufferSize)
        val responsePacket = PlatformDatagramPacket(responseBuffer, responseBuffer.size)
        socket.receive(responsePacket)
        // TODO: Crucially, parse 'responsePacket' to determine server's action.
        // Did it accept 0-RTT (e.g., by sending Handshake packets with 0-RTT accepted indication)?
        // Did it reject (e.g., by sending a Retry packet, or starting a full 1-RTT handshake)?
        // Did it send Version Negotiation? The client's subsequent actions and the actual success of 0-RTT depend entirely on this.
        // This may involve updating security keys and transitioning the TLS state machine.
        
        this.remoteConnectionId = Random.nextBytes(8) // Placeholder, should be from handshake
        startReceivingLoop()
        return@withContext socket
    }

    private suspend fun establishRegularConnection(
        serverAddress: String,
        port: Int
    ): PlatformDatagramSocket = withContext(Dispatchers.IO) {
        val socket = PlatformDatagramSocket()
        val serverAddr = PlatformInetSocketAddress(serverAddress, port)
        socket.connect(serverAddr) // Connect the socket
        
        // Send initial handshake packet
        val initialPacket = createInitialPacket()
        socket.send(PlatformDatagramPacket(initialPacket, initialPacket.size, serverAddr))
        
        // Wait for server response
        val responseBuffer = ByteArray(streamBufferSize)
        val responsePacket = PlatformDatagramPacket(responseBuffer, responseBuffer.size)
        socket.receive(responsePacket)
        // TODO: Parse 'responsePacket' to drive the rest of the 1-RTT handshake.
        // This involves processing the server's cryptographic handshake messages
        // (e.g., ServerHello, EncryptedExtensions, Certificate, CertificateVerify, Finished for TLS 1.3).
        // The client must send its own corresponding messages (e.g., ClientFinished).
        // This is a complex state machine involving cryptographic operations.
        
        this.remoteConnectionId = Random.nextBytes(8) // Placeholder, should be from handshake
        startReceivingLoop()
        return@withContext socket
    }

    private fun startReceivingLoop() {
        if (receivingJob?.isActive == true) {
            return // Loop already running
        }
        val currentSocket = socket ?: return // Should not happen if called after connection setup

        receivingJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val receiveBuffer = ByteArray(streamBufferSize) // Assuming streamBufferSize is appropriate for max packet size
            val datagramPacket = PlatformDatagramPacket(receiveBuffer, receiveBuffer.size)
            try {
                while (currentSocket.isClosed == false && isActive) {
                    try {
                        currentSocket.receive(datagramPacket) // Blocking call
                        val packet = deserializePacket(datagramPacket.data, datagramPacket.length)
                        if (packet != null) {
                            for (frame in packet.frames) {
                                when (frame) {
                                    is StreamFrame -> {
                                        val stream = activeStreams[frame.streamId]
                                        if (stream != null) {
                                            stream.deliverData(PlatformByteBuffer.wrap(frame.data.copyOfRange(0, frame.length), 0, frame.length)) // Ensure correct length
                                            if (frame.fin) {
                                                stream.onRemoteFin()
                                            }
                                        } else {
                                            // Log unknown stream ID
                                            println("Received StreamFrame for unknown streamId: ${frame.streamId}")
                                        }
                                    }
                                    // TODO: Handle other frame types like ACK, CONNECTION_CLOSE etc.
                                }
                            }
                        }
                    } catch (se: PlatformSocketException) {
                        // Socket closed, loop should terminate. Check isActive.
                        if (!isActive || currentSocket.isClosed) break
                        // Log other socket exceptions if needed
                        println("SocketException in receive loop: $se")
                    } catch (e: Exception) {
                        // Log other exceptions during packet processing
                        println("Exception in receive loop: $e")
                        // Depending on error, may want to continue or break
                    }
                }
            } finally {
                println("QUIC Receiving loop stopped.")
            }
        }
    }


    // TODO: This is a highly simplified packet deserializer. A production QUIC implementation
    // requires strict adherence to RFC 9000/9001 for packet formatting,
    // variable-length integer encoding, cryptographic protection of headers and payloads, etc.
    private fun deserializePacket(bytes: ByteArray, length: Int): QuicPacket? {
        if (length == 0) return null
        val buffer = PlatformByteBuffer.wrap(bytes, 0, length)

        try {
            // Deserialize Header (Simplified)
            // TODO: Implement RFC 9000 compliant packet header deserialization.
            val packetTypeOrdinal = buffer.get().toInt()
            val packetType = QuicPacketType.values().getOrNull(packetTypeOrdinal) ?: return null // Unknown type

            val destCid = ByteArray(8)
            buffer.get(destCid)
            val srcCid = ByteArray(8)
            buffer.get(srcCid)
            val packetNumber = buffer.getLong()

            val header = QuicPacketHeader(
                type = packetType,
                destinationConnectionId = destCid,
                sourceConnectionId = srcCid,
                packetNumber = packetNumber
                // Version and token omitted for simplicity
            )

            val frames = mutableListOf<Any>()
            // Deserialize Frames (StreamFrame only for now, assuming one frame per packet)
            // TODO: Support multiple frames per packet and other frame types (ACK, PADDING, etc.).
            if (buffer.remaining() > 0) { // Check remaining before getting frame type
                val frameTypeOrdinal = buffer.get().toInt()
                val frameType = FrameType.values().getOrNull(frameTypeOrdinal)

                if (frameType == FrameType.STREAM) {
                    if (buffer.remaining() < (Long.SIZE_BYTES * 2 + Int.SIZE_BYTES + Byte.SIZE_BYTES)) return null // Not enough for stream frame header
                    val streamId = buffer.getLong()
                    val offset = buffer.getLong()
                    val dataLength = buffer.getInt()
                    val fin = buffer.get() == 1.toByte()

                    if (buffer.remaining() < dataLength) return null // Not enough data for payload
                    val data = ByteArray(dataLength)
                    buffer.get(data)

                    frames.add(StreamFrame(streamId, offset, dataLength, fin, data))
                } else {
                    // Unknown or unhandled frame type, skip or log
                }
            }
            return QuicPacket(header, frames)

        } catch (e: Exception) { // Catch BufferUnderflowException, IndexOutOfBoundsException, etc.
            // Log error e
            println("Error deserializing packet: $e")
            return null
        }
    }

    private fun allocateStreamId(): Long {
        val id = nextClientStreamId
        nextClientStreamId += 4 // Client initiated streams are even, server initiated are odd. Increment by 4 for bidirectional.
        return id
    }

    private fun serializePacket(packet: QuicPacket): ByteArray {
        val headerBuffer = PlatformByteBuffer.allocate(256) // Max header size for simplicity
        // TODO: Implement RFC 9000 compliant packet header serialization.
        // This simplified version writes basic fields.
        headerBuffer.put(packet.header.type.ordinal.toByte())
        headerBuffer.put(packet.header.destinationConnectionId.padEnd(8)) // Ensure 8 bytes
        headerBuffer.put(packet.header.sourceConnectionId.padEnd(8))     // Ensure 8 bytes
        headerBuffer.putLong(packet.header.packetNumber)

        // Serialize Frames (StreamFrame only for now)
        for (frame in packet.frames) {
            if (frame is StreamFrame) {
                headerBuffer.put(FrameType.STREAM.ordinal.toByte())
                headerBuffer.putLong(frame.streamId)
                headerBuffer.putLong(frame.offset)
                headerBuffer.putInt(frame.length)
                headerBuffer.put(if (frame.fin) 1.toByte() else 0.toByte())
                headerBuffer.put(frame.data)
            }
        }
        headerBuffer.flip()
        val result = ByteArray(headerBuffer.remaining())
        headerBuffer.get(result)
        return result
    }

    private fun ByteArray.padEnd(length: Int, padByte: Byte = 0): ByteArray {
        if (size >= length) return this
        return ByteArray(length) { i -> if (i < size) this[i] else padByte }
    }

    // TODO: This is a highly simplified initial packet creation.
    // A real 0-RTT packet requires a valid QUIC CRYPTO frame with TLS 1.3 Early Data,
    // which includes a client_hello message encrypted with keys derived from a previous session ticket.
    // This also needs to handle anti-replay protection.
    private fun create0RTTPacket(sessionData: QuicSessionData): ByteArray {
        // Placeholder for 0-RTT packet creation.
        // In a real scenario, this would involve using the sessionData (ticket, keys)
        // to encrypt early data.
        val header = QuicPacketHeader(
            type = QuicPacketType.ZERO_RTT,
            destinationConnectionId = remoteConnectionId, // This would be derived from the session ticket
            sourceConnectionId = localConnectionId,
            packetNumber = nextPacketNumber++,
            token = sessionData.token // Example: use the token from the cached session
        )
        // TODO: Add actual crypto frames and data
        return serializePacket(QuicPacket(header, listOf()))
    }

    // TODO: This is a highly simplified initial packet creation.
    // A real initial packet for 1-RTT requires a CRYPTO frame with TLS 1.3 ClientHello,
    // including handshake messages, cryptographic parameters, and initial connection IDs.
    private fun createInitialPacket(): ByteArray {
        // Placeholder for initial packet creation.
        val header = QuicPacketHeader(
            type = QuicPacketType.INITIAL,
            destinationConnectionId = ByteArray(0), // Initial packets may have zero-length CIDs or well-known CIDs
            sourceConnectionId = localConnectionId,
            packetNumber = nextPacketNumber++
        )
        // TODO: Add actual crypto frames for TLS 1.3 handshake
        return serializePacket(QuicPacket(header, listOf()))
    }
}

enum class QuicStreamState {
    IDLE, OPEN, LOCAL_CLOSED, REMOTE_CLOSED, CLOSED
}

class QuicStream(
    val id: Long,
    private val bufferSize: Int,
    private val connection: EnhancedQuicConnection
) {
    var state: QuicStreamState = QuicStreamState.IDLE
    var sendOffset: Long = 0L
    var receiveOffset: Long = 0L

    // Using Channel for flow of incoming data
    private val _dataChannel = Channel<PlatformByteBuffer>(Channel.UNLIMITED)
    fun dataFlow(): Flow<PlatformByteBuffer> = _dataChannel.receiveAsFlow()

    fun deliverData(data: PlatformByteBuffer) { // Use PlatformByteBuffer
        _dataChannel.trySend(data).getOrThrow()
        receiveOffset += data.remaining()
    }

    suspend fun close() { // Make it suspend if sendFin is suspend
        if (state == QuicStreamState.CLOSED || state == QuicStreamState.LOCAL_CLOSED) return
        state = QuicStreamState.LOCAL_CLOSED
        connection.sendFin(id) // Send FIN frame to remote
        _dataChannel.close() // Close the channel for new incoming data
        println("Stream $id locally closed.")
    }

    fun onRemoteFin() {
        if (state == QuicStreamState.CLOSED || state == QuicStreamState.REMOTE_CLOSED) return
        state = if (state == QuicStreamState.LOCAL_CLOSED) QuicStreamState.CLOSED else QuicStreamState.REMOTE_CLOSED
        _dataChannel.close() // Close the channel for new incoming data
        println("Stream $id remote closed. Final state: $state")
    }
}

data class QuicConfig(
    val enable0RTT: Boolean = false,
    val initialMaxData: Long = 65536, // Connection-wide flow control limit
    val initialMaxStreamData: Long = 16384, // Per-stream flow control limit
    val idleTimeoutMillis: Long = 30000,
    val streamBufferSize: Int? = null
)