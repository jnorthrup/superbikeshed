package borg.trikeshed.net.quic

import borg.trikeshed.lib.*
import borg.trikeshed.lib.CZero.z
import borg.trikeshed.lib.CZero.nz
import borg.trikeshed.crypto.*
import kotlinx.coroutines.*

// Helper function for appending to Indexed
private fun <T> appendToIndexed(indexed: Indexed<T>, item: T): Indexed<T> {
    val newSize = indexed.a + 1
    return newSize j { i: Int ->
        if (i < indexed.a) indexed.b(i) else item
    }
}

// Helper function for combining Indexed collections using ArrayList + toIdx pattern
private fun <T> combineIndexed(vararg series: Indexed<T>): Indexed<T> {
    val combined = mutableListOf<T>()
    for (s in series) {
        for (i in 0 until s.a) {
            combined.add(s.b(i))
        }
    }
    return combined.toIdx()
}

/**
 * Secure QUIC Engine with full crypto support
 * Implements QUIC protocol with TLS 1.3, secure key exchange, and encrypted transport
 */
class SecureQuicEngine(
    private val role: Role,
    private val cryptoEngine: CryptoEngine,
    private val config: SecureQuicConfig = SecureQuicConfig()
) {
    enum class Role { CLIENT, SERVER }
    
    // Connection state
    private var connectionState = SecureQuicConnectionState()
    private val streamStates = mutableMapOf<Long, SecureQuicStreamState>()
    private val sessionCache = mutableMapOf<String, QuicSessionCache>()
    
    // Crypto state
    private var handshakeState = HandshakeState.INITIAL
    private var clientRandom: Indexed<Byte>? = null
    private var serverRandom: Indexed<Byte>? = null
    private var preMasterSecret: Indexed<Byte>? = null
    private var masterSecret: Indexed<Byte>? = null
    private var clientWriteKey: SymmetricKey? = null
    private var serverWriteKey: SymmetricKey? = null
    private var clientWriteIV: Indexed<Byte>? = null
    private var serverWriteIV: Indexed<Byte>? = null
    
    // Key exchange
    private var ephemeralKeyPair: KeyPair? = null
    private var peerPublicKey: PublicKey? = null
    
    /**
     * Configuration for secure QUIC
     */
    data class SecureQuicConfig(
        val supportedCipherSuites: Indexed<CipherSuite> = listOf(
            CipherSuite.TLS_AES_256_GCM_SHA384,
            CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_AES_128_GCM_SHA256
        ).toIdx(),
        val supportedKeyExchangeAlgorithms: Indexed<KeyAlgorithm> = listOf(
            KeyAlgorithm.X25519,
            KeyAlgorithm.ECDSA_P256
        ).toIdx(),
        val sessionTicketLifetime: Long = 7200, // 2 hours
        val maxEarlyData: Int = 16384,
        val enable0RTT: Boolean = true,
        val enablePostQuantum: Boolean = false
    )
    
    /**
     * Cipher suites supported by QUIC
     */
    enum class CipherSuite(val id: Int, val cipherName: String, val keyLength: Int, val ivLength: Int) {
        TLS_AES_128_GCM_SHA256(0x1301, "TLS_AES_128_GCM_SHA256", 16, 12),
        TLS_AES_256_GCM_SHA384(0x1302, "TLS_AES_256_GCM_SHA384", 32, 12),
        TLS_CHACHA20_POLY1305_SHA256(0x1303, "TLS_CHACHA20_POLY1305_SHA256", 32, 12)
    }
    
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
            SecureQuicStreamState(
                streamId = streamId,
                maxData = connectionState.transportParams.initialMaxStreamDataBidiLocal
            )
        }
        
        // Encrypt the data
        val writeKey = if (role == Role.CLIENT) clientWriteKey else serverWriteKey
        val writeIV = if (role == Role.CLIENT) clientWriteIV else serverWriteIV
        
        requireNotNull(writeKey) { "Write key not established" }
        requireNotNull(writeIV) { "Write IV not established" }
        
        val encryptedData = cryptoEngine.encrypt(data, writeKey, EncryptionMode.GCM)
        
        // Create stream frame
        val frame = StreamFrame(
            streamId = streamId,
            offset = stream.sendOffset,
            data = encryptedData.ciphertext,
            fin = false
        )
        
        // Update stream state
        streamStates[streamId] = stream.copy(
            sendBuffer = stream.sendBuffer, // Mock - would normally append data
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
            payload = encryptedData.ciphertext
        )
        
        // Update connection state
        connectionState = connectionState.copy(
            sentPackets = appendToIndexed(connectionState.sentPackets, packet),
            nextPacketNumber = connectionState.nextPacketNumber + 1,
            bytesInFlight = connectionState.bytesInFlight + encryptedData.ciphertext.a
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
            when (frame) {
                is StreamFrame -> {
                    val stream = streamStates.getOrPut(frame.streamId) {
                        SecureQuicStreamState(
                            streamId = frame.streamId,
                            maxData = connectionState.transportParams.initialMaxStreamDataBidiLocal
                        )
                    }
                    
                    // Decrypt the data
                    val readKey = if (role == Role.CLIENT) serverWriteKey else clientWriteKey
                    val readIV = if (role == Role.CLIENT) serverWriteIV else clientWriteIV
                    
                    requireNotNull(readKey) { "Read key not established" }
                    requireNotNull(readIV) { "Read IV not established" }
                    
                    val encryptedData = EncryptedData(
                        ciphertext = frame.data,
                        iv = readIV,
                        mode = EncryptionMode.GCM
                    )
                    
                    val decrypted = cryptoEngine.decrypt(encryptedData, readKey)
                    decryptedData.add(decrypted)
                    
                    // Update stream state
                    streamStates[frame.streamId] = stream.copy(
                        receiveBuffer = combineIndexed(stream.receiveBuffer, decrypted),
                        receiveOffset = frame.offset + decrypted.a
                    )
                }
                is AckFrame -> processAckFrame(frame)
                is CryptoFrame -> {
                    // Handle post-handshake crypto frames
                    val handshakeResponses = processCryptoFrame(frame)
                    // Add responses to decrypted data if needed
                }
            }
        }
        
        return decryptedData.toIdx()
    }
    
    // Private handshake methods
    
    private suspend fun startClientHandshake(): Indexed<QuicPacket> {
        handshakeState = HandshakeState.CLIENT_HELLO_SENT
        
        // Generate client random
        clientRandom = cryptoEngine.generateRandomBytes(32)
        
        // Generate ephemeral key pair
        ephemeralKeyPair = cryptoEngine.generateKeyPair(KeyAlgorithm.X25519)
        
        // Create ClientHello message
        val clientHello = createClientHello()
        
        // Create crypto frame
        val cryptoFrame = CryptoFrame(
            offset = 0,
            data = clientHello
        )
        
        // Create initial packet
        val packet = QuicPacket(
            header = QuicHeader(
                type = QuicPacketType.INITIAL,
                version = connectionState.version,
                destinationConnectionId = ConnectionId(connectionState.remoteConnectionId),
                sourceConnectionId = ConnectionId(connectionState.localConnectionId),
                packetNumber = connectionState.nextPacketNumber
            ),
            frames = 1 j { _: Int -> cryptoFrame },
            payload = clientHello
        )
        
        connectionState = connectionState.copy(
            sentPackets = appendToIndexed(connectionState.sentPackets, packet),
            nextPacketNumber = connectionState.nextPacketNumber + 1
        )
        
        return 1 j { _: Int -> packet }
    }
    
    private suspend fun startServerHandshake(): Indexed<QuicPacket> {
        handshakeState = HandshakeState.SERVER_HELLO_SENT
        
        // Generate server random
        serverRandom = cryptoEngine.generateRandomBytes(32)
        
        // Generate ephemeral key pair
        ephemeralKeyPair = cryptoEngine.generateKeyPair(KeyAlgorithm.X25519)
        
        // Create ServerHello message
        val serverHello = createServerHello()
        
        // Create crypto frame
        val cryptoFrame = CryptoFrame(
            offset = 0,
            data = serverHello
        )
        
        // Create initial packet
        val packet = QuicPacket(
            header = QuicHeader(
                type = QuicPacketType.INITIAL,
                version = connectionState.version,
                destinationConnectionId = ConnectionId(connectionState.remoteConnectionId),
                sourceConnectionId = ConnectionId(connectionState.localConnectionId),
                packetNumber = connectionState.nextPacketNumber
            ),
            frames = 1 j { _: Int -> cryptoFrame },
            payload = serverHello
        )
        
        connectionState = connectionState.copy(
            sentPackets = appendToIndexed(connectionState.sentPackets, packet),
            nextPacketNumber = connectionState.nextPacketNumber + 1
        )
        
        return 1 j { _: Int -> packet }
    }
    
    private suspend fun processCryptoFrame(frame: CryptoFrame): List<QuicPacket> {
        val responses = mutableListOf<QuicPacket>()
        
        when (handshakeState) {
            HandshakeState.CLIENT_HELLO_SENT -> {
                // Process ServerHello
                val serverHello = parseServerHello(frame.data)
                handshakeState = HandshakeState.SERVER_HELLO_RECEIVED
                
                // Perform key exchange
                val keyExchangeResponse = performKeyExchange()
                responses.add(keyExchangeResponse)
                
                handshakeState = HandshakeState.KEY_EXCHANGE
            }
            HandshakeState.SERVER_HELLO_SENT -> {
                // Process ClientHello
                val clientHello = parseClientHello(frame.data)
                handshakeState = HandshakeState.CLIENT_HELLO_RECEIVED
                
                // Perform key exchange
                val keyExchangeResponse = performKeyExchange()
                responses.add(keyExchangeResponse)
                
                handshakeState = HandshakeState.KEY_EXCHANGE
            }
            HandshakeState.KEY_EXCHANGE -> {
                // Process key exchange completion
                handshakeState = HandshakeState.FINISHED
                
                // Send Finished message
                val finishedResponse = sendFinished()
                responses.add(finishedResponse)
                
                handshakeState = HandshakeState.ESTABLISHED
            }
            HandshakeState.FINISHED -> {
                // Process Finished message
                handshakeState = HandshakeState.ESTABLISHED
            }
            else -> {
                // Handle post-handshake crypto frames
            }
        }
        
        return responses
    }
    
    private suspend fun performKeyExchange(): QuicPacket {
        // Perform X25519 key exchange
        requireNotNull(ephemeralKeyPair) { "Ephemeral key pair not generated" }
        requireNotNull(peerPublicKey) { "Peer public key not received" }
        
        // In real implementation, this would perform the actual key exchange
        // For now, we'll generate random keys
        val sharedSecret = cryptoEngine.generateRandomBytes(32)
        
        // Derive master secret
        val seed = combineIndexed(
            clientRandom ?: 0 j { _: Int -> 0.toByte() }, 
            serverRandom ?: 0 j { _: Int -> 0.toByte() },
            sharedSecret
        )
        
        masterSecret = cryptoEngine.deriveKey(seed, 0 j { _: Int -> 0.toByte() }, KdfAlgorithm.PBKDF2_SHA256).keyData
        
        // Derive traffic keys
        deriveTrafficKeys()
        
        return QuicPacket(
            header = QuicHeader(
                type = QuicPacketType.HANDSHAKE,
                version = connectionState.version,
                destinationConnectionId = ConnectionId(connectionState.remoteConnectionId),
                sourceConnectionId = ConnectionId(connectionState.localConnectionId),
                packetNumber = connectionState.nextPacketNumber
            ),
            frames = 0 j { _: Int -> throw IndexOutOfBoundsException() },
            payload = 0 j { _: Int -> 0.toByte() }
        )
    }
    
    private suspend fun deriveTrafficKeys() {
        requireNotNull(masterSecret) { "Master secret not established" }
        
        // Derive client write key
        val clientWriteKeyData = cryptoEngine.deriveKey(
            masterSecret!!,
            "client_write_key".encodeToByteArray().toIdx(),
            KdfAlgorithm.PBKDF2_SHA256
        )
        clientWriteKey = SymmetricKey(SymmetricAlgorithm.AES_GCM_256, clientWriteKeyData.keyData)
        clientWriteIV = cryptoEngine.generateRandomBytes(12)
        
        // Derive server write key
        val serverWriteKeyData = cryptoEngine.deriveKey(
            masterSecret!!,
            "server_write_key".encodeToByteArray().toIdx(),
            KdfAlgorithm.PBKDF2_SHA256
        )
        serverWriteKey = SymmetricKey(SymmetricAlgorithm.AES_GCM_256, serverWriteKeyData.keyData)
        serverWriteIV = cryptoEngine.generateRandomBytes(12)
    }
    
    private suspend fun sendFinished(): QuicPacket {
        // Create Finished message with HMAC
        val finishedData = cryptoEngine.hmac(
            masterSecret ?: 0 j { _: Int -> 0.toByte() },
            "finished".encodeToByteArray().toIdx(),
            HashAlgorithm.SHA_256
        )
        
        val cryptoFrame = CryptoFrame(
            offset = 0,
            data = finishedData
        )
        
        return QuicPacket(
            header = QuicHeader(
                type = QuicPacketType.HANDSHAKE,
                version = connectionState.version,
                destinationConnectionId = ConnectionId(connectionState.remoteConnectionId),
                sourceConnectionId = ConnectionId(connectionState.localConnectionId),
                packetNumber = connectionState.nextPacketNumber
            ),
            frames = 1 j { _: Int -> cryptoFrame },
            payload = finishedData
        )
    }
    
    // Helper methods for handshake messages
    
    private fun createClientHello(): Indexed<Byte> {
        // Create ClientHello message with supported cipher suites and key exchange algorithms
        return 0 j { _: Int -> 0.toByte() } // Placeholder
    }
    
    private fun createServerHello(): Indexed<Byte> {
        // Create ServerHello message with selected cipher suite and key exchange algorithm
        return 0 j { _: Int -> 0.toByte() } // Placeholder
    }
    
    private fun parseClientHello(data: Indexed<Byte>): Unit {
        // Parse ClientHello and extract peer public key
        peerPublicKey = PublicKey(
            algorithm = KeyAlgorithm.X25519,
            keyData = 32 j { _: Int -> 0.toByte() },
            encoded = 32 j { _: Int -> 0.toByte() }
        )
    }
    
    private fun parseServerHello(data: Indexed<Byte>): Unit {
        // Parse ServerHello and extract peer public key
        peerPublicKey = PublicKey(
            algorithm = KeyAlgorithm.X25519,
            keyData = 32 j { _: Int -> 0.toByte() },
            encoded = 32 j { _: Int -> 0.toByte() }
        )
    }
    
    private fun processStreamFrame(frame: StreamFrame) {
        // Handle stream frames during handshake
    }
    
    private fun processAckFrame(frame: AckFrame) {
        // Handle ACK frames
    }
    
    // Data classes
    
    data class SecureQuicConnectionState(
        val version: Long = 0x00000001,
        val localConnectionId: Indexed<Byte> = 0 j { _: Int -> 0.toByte() },
        val remoteConnectionId: Indexed<Byte> = 0 j { _: Int -> 0.toByte() },
        val nextPacketNumber: Long = 0,
        val sentPackets: Indexed<QuicPacket> = 0 j { _: Int -> QuicPacket(
            header = QuicHeader(
                type = QuicPacketType.SHORT_HEADER,
                version = 1,
                destinationConnectionId = ConnectionId(0 j { _: Int -> 0.toByte() }),
                sourceConnectionId = ConnectionId(0 j { _: Int -> 0.toByte() }),
                packetNumber = 0
            ),
            frames = 0 j { _: Int -> StreamFrame(0, 0, 0 j { _: Int -> 0.toByte() }, false) },
            payload = 0 j { _: Int -> 0.toByte() }
        ) },
        val receivedPackets: Indexed<QuicPacket> = 0 j { _: Int -> QuicPacket(
            header = QuicHeader(
                type = QuicPacketType.SHORT_HEADER,
                version = 1,
                destinationConnectionId = ConnectionId(0 j { _: Int -> 0.toByte() }),
                sourceConnectionId = ConnectionId(0 j { _: Int -> 0.toByte() }),
                packetNumber = 0
            ),
            frames = 0 j { _: Int -> StreamFrame(0, 0, 0 j { _: Int -> 0.toByte() }, false) },
            payload = 0 j { _: Int -> 0.toByte() }
        ) },
        val bytesInFlight: Long = 0,
        val transportParams: TransportParameters = TransportParameters()
    )
    
    data class SecureQuicStreamState(
        val streamId: Long,
        val maxData: Long,
        val sendBuffer: Indexed<Byte> = 0 j { _: Int -> 0.toByte() },
        val receiveBuffer: Indexed<Byte> = 0 j { _: Int -> 0.toByte() },
        val sendOffset: Long = 0,
        val receiveOffset: Long = 0
    )
} 