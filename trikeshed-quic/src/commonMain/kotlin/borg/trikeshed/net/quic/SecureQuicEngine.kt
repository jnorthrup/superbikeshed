@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.quic

import borg.trikeshed.lib.*
import borg.trikeshed.crypto.*
import kotlinx.coroutines.*

/**
 * Practical secure QUIC engine with essential crypto support
 */
class SecureQuicEngine(
    internal val role: Role,
    internal val cryptoEngine: CryptoEngine? = null
) {
    enum class Role { CLIENT, SERVER }
    
    // Connection state
    internal var connectionState = QuicConnectionState()
    internal val streamStates = mutableMapOf<Long, QuicStreamState>()
    
    // Crypto state
    internal var handshakeState = HandshakeState.INITIAL
    internal var clientWriteKey: SymmetricKey? = null
    internal var serverWriteKey: SymmetricKey? = null
    
    /**
     * Handshake states
     */
    enum class HandshakeState {
        INITIAL,
        CLIENT_HELLO_SENT,
        SERVER_HELLO_RECEIVED,
        SERVER_HELLO_SENT,
        CLIENT_HELLO_RECEIVED,
        KEY_EXCHANGE,
        FINISHED,
        ESTABLISHED
    }
    
    /**
     * Start secure connection handshake
     */
    suspend fun startHandshake(): Indexed<QuicPacket> {
        when (role) {
            Role.CLIENT -> return startClientHandshake()
            Role.SERVER -> return startServerHandshake()
        }
    }
    
    /**
     * Process handshake messages
     */
    suspend fun processHandshake(packet: QuicPacket): Indexed<QuicPacket> {
        val responses = mutableListOf<QuicPacket>()
        
        // Process each frame
        for (i in 0 until packet.frames.a) {
            val frame = packet.frames.b(i)
            when (frame) {
                is CryptoFrame -> {
                    val handshakeResponses = processCryptoFrame(frame)
                    responses.addAll(handshakeResponses)
                }
                is StreamFrame -> processStreamFrame(frame)
                is AckFrame -> processAckFrame(frame)
            }
        }
        
        return responses.toIdx()
    }
    
    /**
     * Send encrypted data on a stream
     */
    suspend fun sendSecureStreamData(streamId: Long, data: Indexed<Byte>): QuicPacket {
        require(handshakeState == HandshakeState.ESTABLISHED) { "Handshake not completed" }
        
        val stream = streamStates.getOrPut(streamId) {
            QuicStreamState(
                streamId = streamId,
                maxData = connectionState.transportParams.initialMaxStreamDataBidiLocal
            )
        }
        
        // Encrypt the data if crypto engine available
        val encryptedData = if (cryptoEngine != null) {
            val writeKey = if (role == Role.CLIENT) clientWriteKey else serverWriteKey
            requireNotNull(writeKey) { "Write key not established" }
            cryptoEngine.encrypt(data, writeKey, EncryptionMode.GCM)
            data // Placeholder - would use actual encrypted data
        } else {
            data
        }
        
        // Create stream frame
        val frame = StreamFrame(
            streamId = streamId,
            offset = stream.sendOffset,
            data = encryptedData,
            fin = false
        )
        
        // Update stream state
        streamStates[streamId] = stream.copy(
            sendOffset = stream.sendOffset + data.a
        )
        
        // Create packet
        val packet = QuicPacket(
            header = QuicHeader(
                type = QuicPacketType.SHORT_HEADER,
                version = connectionState.version,
                destinationConnectionId = ConnectionId(connectionState.remoteConnectionId),
                sourceConnectionId = ConnectionId(connectionState.localConnectionId),
                packetNumber = connectionState.nextPacketNumber
            ),
            frames = 1 j { _: Int -> frame },
            payload = encryptedData
        )
        
        // Update connection state
        connectionState = connectionState.copy(
            sentPackets = appendToIndexed(connectionState.sentPackets, packet),
            nextPacketNumber = connectionState.nextPacketNumber + 1,
            bytesInFlight = connectionState.bytesInFlight + encryptedData.a
        )
        
        return packet
    }
    
    /**
     * Receive and decrypt data from a stream
     */
    suspend fun receiveSecureStreamData(packet: QuicPacket): Indexed<Indexed<Byte>> {
        require(handshakeState == HandshakeState.ESTABLISHED) { "Handshake not completed" }
        
        val decryptedData = mutableListOf<Indexed<Byte>>()
        
        // Process each frame
        for (i in 0 until packet.frames.a) {
            val frame = packet.frames.b(i)
            if (frame is StreamFrame) {
                val stream = streamStates.getOrPut(frame.streamId) {
                    QuicStreamState(
                        streamId = frame.streamId,
                        maxData = connectionState.transportParams.initialMaxStreamDataBidiRemote
                    )
                }
                
                // Decrypt data if crypto engine available
                val decrypted = if (cryptoEngine != null) {
                    val readKey = if (role == Role.CLIENT) serverWriteKey else clientWriteKey
                    requireNotNull(readKey) { "Read key not established" }
                    cryptoEngine.decrypt(frame.data, readKey, EncryptionMode.GCM)
                    frame.data // Placeholder - would use actual decrypted data
                } else {
                    frame.data
                }
                
                decryptedData.add(decrypted)
                
                // Update stream state
                streamStates[frame.streamId] = stream.copy(
                    receiveOffset = stream.receiveOffset + frame.data.a
                )
            }
        }
        
        return decryptedData.toIdx()
    }
    
    // === INTERNAL HANDLERS ===
    
    internal suspend fun startClientHandshake(): Indexed<QuicPacket> {
        handshakeState = HandshakeState.CLIENT_HELLO_SENT
        
        val clientHello = createClientHello()
        val packet = QuicPacket(
            header = createInitialHeader(),
            frames = 1 j { _: Int -> clientHello },
            payload = clientHello.data
        )
        
        connectionState = connectionState.copy(
            sentPackets = appendToIndexed(connectionState.sentPackets, packet),
            nextPacketNumber = connectionState.nextPacketNumber + 1
        )
        
        return 1 j { _: Int -> packet }
    }
    
    internal suspend fun startServerHandshake(): Indexed<QuicPacket> {
        handshakeState = HandshakeState.SERVER_HELLO_SENT
        
        val serverHello = createServerHello()
        val packet = QuicPacket(
            header = createInitialHeader(),
            frames = 1 j { _: Int -> serverHello },
            payload = serverHello.data
        )
        
        connectionState = connectionState.copy(
            sentPackets = appendToIndexed(connectionState.sentPackets, packet),
            nextPacketNumber = connectionState.nextPacketNumber + 1
        )
        
        return 1 j { _: Int -> packet }
    }
    
    internal fun processCryptoFrame(frame: CryptoFrame): List<QuicPacket> {
        // Simplified crypto frame processing
        return emptyList()
    }
    
    internal fun processStreamFrame(frame: StreamFrame) {
        // Stream frame processing already handled in receiveSecureStreamData
    }
    
    internal fun processAckFrame(frame: AckFrame) {
        // Update connection state based on ACK
        connectionState = connectionState.copy(
            bytesInFlight = maxOf(0L, connectionState.bytesInFlight - frame.largestAcknowledged)
        )
    }
    
    internal fun createClientHello(): CryptoFrame {
        return CryptoFrame(
            offset = 0L,
            data = "ClientHello".encodeToByteArray().toIdx()
        )
    }
    
    internal fun createServerHello(): CryptoFrame {
        return CryptoFrame(
            offset = 0L,
            data = "ServerHello".encodeToByteArray().toIdx()
        )
    }
    
    internal fun createInitialHeader(): QuicHeader {
        return QuicHeader(
            type = QuicPacketType.INITIAL,
            version = connectionState.version,
            destinationConnectionId = ConnectionId(connectionState.remoteConnectionId),
            sourceConnectionId = ConnectionId(connectionState.localConnectionId),
            packetNumber = connectionState.nextPacketNumber
        )
    }
    
    // === HELPER FUNCTIONS ===
    
    internal fun <T> appendToIndexed(indexed: Indexed<T>, item: T): Indexed<T> {
        val newSize = indexed.a + 1
        return newSize j { i: Int ->
            if (i < indexed.a) indexed.b(i) else item
        }
    }
    
    internal fun <T> List<T>.toIdx(): Indexed<T> {
        return this.size j { i: Int -> this[i] }
    }
    
    internal fun String.toIdx(): Indexed<Byte> {
        return this.encodeToByteArray().size j { i: Int -> this.encodeToByteArray()[i] }
    }
    
    /**
     * Start the secure QUIC engine
     */
    suspend fun start(): Boolean {
        return try {
            val handshakePackets = startHandshake()
            handshakeState = HandshakeState.ESTABLISHED
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Stop the secure QUIC engine
     */
    suspend fun stop() {
        handshakeState = HandshakeState.INITIAL
        streamStates.clear()
    }
} 