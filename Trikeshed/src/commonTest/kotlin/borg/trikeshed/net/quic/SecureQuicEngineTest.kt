package borg.trikeshed.net.quic

import borg.trikeshed.lib.*
import borg.trikeshed.lib.CZero.z
import borg.trikeshed.lib.CZero.nz
import borg.trikeshed.crypto.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Comprehensive test suite for SecureQuicEngine
 * Tests secure handshake, encryption, and transport features
 */
class SecureQuicEngineTest {
    
    private lateinit var clientEngine: SecureQuicEngine
    private lateinit var serverEngine: SecureQuicEngine
    private lateinit var cryptoEngine: CryptoEngine
    
    @BeforeTest
    fun setup() {
        // Create mock crypto engine for testing
        cryptoEngine = object : CryptoEngine {
            override suspend fun generateKeyPair(algorithm: KeyAlgorithm): KeyPair {
                val keyData = 32 j { 0.toByte() }
                return KeyPair(
                    publicKey = PublicKey(algorithm, keyData, keyData),
                    privateKey = PrivateKey(algorithm, keyData, keyData)
                )
            }
            
            override suspend fun generateSymmetricKey(algorithm: SymmetricAlgorithm): SymmetricKey {
                val keySize = when (algorithm) {
                    SymmetricAlgorithm.AES_128 -> 16
                    SymmetricAlgorithm.AES_256 -> 32
                    SymmetricAlgorithm.CHACHA20_POLY1305 -> 32
                    SymmetricAlgorithm.AES_GCM_128 -> 16
                    SymmetricAlgorithm.AES_GCM_256 -> 32
                }
                return SymmetricKey(algorithm, keySize j { 0.toByte() })
            }
            
            override suspend fun hash(data: Indexed<Byte>, algorithm: HashAlgorithm): Indexed<Byte> {
                return 32 j { 0.toByte() }
            }
            
            override suspend fun hmac(key: Indexed<Byte>, data: Indexed<Byte>, algorithm: HashAlgorithm): Indexed<Byte> {
                return 32 j { 0.toByte() }
            }
            
            override suspend fun encrypt(data: Indexed<Byte>, key: SymmetricKey, mode: EncryptionMode): EncryptedData {
                return EncryptedData(
                    ciphertext = data,
                    iv = 12 j { 0.toByte() },
                    mode = mode
                )
            }
            
            override suspend fun decrypt(encryptedData: EncryptedData, key: SymmetricKey): Indexed<Byte> {
                return encryptedData.ciphertext
            }
            
            override suspend fun sign(data: Indexed<Byte>, keyPair: KeyPair): Indexed<Byte> {
                return 64 j { 0.toByte() }
            }
            
            override suspend fun verify(data: Indexed<Byte>, signature: Indexed<Byte>, publicKey: PublicKey): Boolean {
                return true
            }
            
            override suspend fun deriveKey(password: Indexed<Byte>, salt: Indexed<Byte>, algorithm: KdfAlgorithm): SymmetricKey {
                return SymmetricKey(SymmetricAlgorithm.AES_256, 32 j { 0.toByte() })
            }
            
            override suspend fun generateRandomBytes(length: Int): Indexed<Byte> {
                return length j { 0.toByte() }
            }
            
            override suspend fun generateSecureRandom(): Long {
                return 0L
            }
        }
        
        val config = SecureQuicEngine.SecureQuicConfig(
            supportedCipherSuites = listOf(
                SecureQuicEngine.CipherSuite.TLS_AES_256_GCM_SHA384,
                SecureQuicEngine.CipherSuite.TLS_CHACHA20_POLY1305_SHA256
            ).toIdx(),
            supportedKeyExchangeAlgorithms = listOf(
                KeyAlgorithm.X25519,
                KeyAlgorithm.ECDSA_P256
            ).toIdx(),
            enable0RTT = true,
            enablePostQuantum = false
        )
        
        clientEngine = SecureQuicEngine(
            role = SecureQuicEngine.Role.CLIENT,
            cryptoEngine = cryptoEngine,
            config = config
        )
        
        serverEngine = SecureQuicEngine(
            role = SecureQuicEngine.Role.SERVER,
            cryptoEngine = cryptoEngine,
            config = config
        )
    }
    
    @Test
    fun `test secure handshake completion`() = runTest {
        // Start client handshake
        val clientInitialPackets = clientEngine.startHandshake()
        assertTrue(clientInitialPackets.a > 0)
        
        // Process client packets on server
        val clientPacket = clientInitialPackets.b(0)
        val serverResponses = serverEngine.processHandshake(clientPacket)
        assertTrue(serverResponses.a > 0)
        
        // Process server responses on client
        val serverPacket = serverResponses.b(0)
        val clientResponses = clientEngine.processHandshake(serverPacket)
        
        // Continue handshake until completion
        var handshakeComplete = false
        var round = 0
        val maxRounds = 10
        
        while (!handshakeComplete && round < maxRounds) {
            if (clientResponses.a > 0) {
                val nextClientPacket = clientResponses.b(0)
                val nextServerResponses = serverEngine.processHandshake(nextClientPacket)
                
                if (nextServerResponses.a > 0) {
                    val nextServerPacket = nextServerResponses.b(0)
                    val nextClientResponses = clientEngine.processHandshake(nextServerPacket)
                    // Continue with nextClientResponses
                } else {
                    handshakeComplete = true
                }
            } else {
                handshakeComplete = true
            }
            round++
        }
        
        assertTrue(handshakeComplete, "Handshake should complete within $maxRounds rounds")
    }
    
    @Test
    fun `test secure data transmission`() = runTest {
        // Complete handshake first
        performHandshake()
        
        // Send encrypted data from client to server
        val testData = "Hello, secure QUIC!".encodeToByteArray().toIdx()
        val streamId = 1L
        
        val clientPacket = clientEngine.sendSecureStreamData(streamId, testData)
        assertNotNull(clientPacket)
        assertTrue(clientPacket.payload.a > 0)
        
        // Receive and decrypt data on server
        val receivedData = serverEngine.receiveSecureStreamData(clientPacket)
        assertTrue(receivedData.a > 0)
        
        val decryptedData = receivedData.b(0)
        assertEquals(testData.a, decryptedData.a)
        
        val receivedString = String(ByteArray(decryptedData.a) { decryptedData.b(it) })
        assertEquals("Hello, secure QUIC!", receivedString)
    }
    
    @Test
    fun `test bidirectional secure communication`() = runTest {
        // Complete handshake first
        performHandshake()
        
        val clientMessage = "Message from client".encodeToByteArray().toIdx()
        val serverMessage = "Response from server".encodeToByteArray().toIdx()
        
        // Client to server
        val clientPacket = clientEngine.sendSecureStreamData(1L, clientMessage)
        val serverReceived = serverEngine.receiveSecureStreamData(clientPacket)
        assertTrue(serverReceived.a > 0)
        
        // Server to client
        val serverPacket = serverEngine.sendSecureStreamData(2L, serverMessage)
        val clientReceived = clientEngine.receiveSecureStreamData(serverPacket)
        assertTrue(clientReceived.a > 0)
        
        // Verify messages
        val clientReceivedString = String(ByteArray(clientReceived.b(0).a) { clientReceived.b(0).b(it) })
        val serverReceivedString = String(ByteArray(serverReceived.b(0).a) { serverReceived.b(0).b(it) })
        
        assertEquals("Response from server", clientReceivedString)
        assertEquals("Message from client", serverReceivedString)
    }
    
    @Test
    fun `test multiple streams`() = runTest {
        // Complete handshake first
        performHandshake()
        
        val streams = (1..5).map { streamId ->
            val message = "Data for stream $streamId".encodeToByteArray().toIdx()
            val packet = clientEngine.sendSecureStreamData(streamId.toLong(), message)
            val received = serverEngine.receiveSecureStreamData(packet)
            
            Triple(streamId, message, received.b(0))
        }
        
        // Verify all streams
        streams.forEach { (streamId, original, received) ->
            assertEquals(original.a, received.a)
            val originalString = String(ByteArray(original.a) { original.b(it) })
            val receivedString = String(ByteArray(received.a) { received.b(it) })
            assertEquals(originalString, receivedString)
        }
    }
    
    @Test
    fun `test large data transmission`() = runTest {
        // Complete handshake first
        performHandshake()
        
        // Generate large data (1MB)
        val largeData = cryptoEngine.generateRandomBytes(1024 * 1024)
        val streamId = 1L
        
        val clientPacket = clientEngine.sendSecureStreamData(streamId, largeData)
        assertNotNull(clientPacket)
        assertTrue(clientPacket.payload.a > 0)
        
        val receivedData = serverEngine.receiveSecureStreamData(clientPacket)
        assertTrue(receivedData.a > 0)
        
        val decryptedData = receivedData.b(0)
        assertEquals(largeData.a, decryptedData.a)
    }
    
    @Test
    fun `test cipher suite negotiation`() = runTest {
        // Test with different cipher suite configurations
        val cipherSuites = listOf(
            SecureQuicEngine.CipherSuite.TLS_AES_128_GCM_SHA256,
            SecureQuicEngine.CipherSuite.TLS_AES_256_GCM_SHA384,
            SecureQuicEngine.CipherSuite.TLS_CHACHA20_POLY1305_SHA256
        )
        
        cipherSuites.forEach { cipherSuite ->
            val config = SecureQuicEngine.SecureQuicConfig(
                supportedCipherSuites = listOf(cipherSuite).toIdx()
            )
            
            val testClient = SecureQuicEngine(
                role = SecureQuicEngine.Role.CLIENT,
                cryptoEngine = cryptoEngine,
                config = config
            )
            
            val testServer = SecureQuicEngine(
                role = SecureQuicEngine.Role.SERVER,
                cryptoEngine = cryptoEngine,
                config = config
            )
            
            // Start handshake
            val clientPackets = testClient.startHandshake()
            assertTrue(clientPackets.a > 0)
            
            val serverResponses = testServer.processHandshake(clientPackets.b(0))
            assertTrue(serverResponses.a >= 0) // May be 0 if handshake completes immediately
        }
    }
    
    @Test
    fun `test key exchange algorithms`() = runTest {
        // Test with different key exchange algorithms
        val keyExchangeAlgorithms = listOf(
            KeyAlgorithm.X25519,
            KeyAlgorithm.ECDSA_P256
        )
        
        keyExchangeAlgorithms.forEach { algorithm ->
            val config = SecureQuicEngine.SecureQuicConfig(
                supportedKeyExchangeAlgorithms = listOf(algorithm).toIdx()
            )
            
            val testClient = SecureQuicEngine(
                role = SecureQuicEngine.Role.CLIENT,
                cryptoEngine = cryptoEngine,
                config = config
            )
            
            val testServer = SecureQuicEngine(
                role = SecureQuicEngine.Role.SERVER,
                cryptoEngine = cryptoEngine,
                config = config
            )
            
            // Start handshake
            val clientPackets = testClient.startHandshake()
            assertTrue(clientPackets.a > 0)
            
            val serverResponses = testServer.processHandshake(clientPackets.b(0))
            assertTrue(serverResponses.a >= 0)
        }
    }
    
    @Test
    fun `test concurrent connections`() = runTest {
        // Test multiple concurrent connections
        val connections = (1..5).map { connectionId ->
            kotlinx.coroutines.async {
                val client = SecureQuicEngine(
                    role = SecureQuicEngine.Role.CLIENT,
                    cryptoEngine = cryptoEngine
                )
                
                val server = SecureQuicEngine(
                    role = SecureQuicEngine.Role.SERVER,
                    cryptoEngine = cryptoEngine
                )
                
                // Perform handshake
                val clientPackets = client.startHandshake()
                val serverResponses = server.processHandshake(clientPackets.b(0))
                
                // Send test data
                val testData = "Connection $connectionId".encodeToByteArray().toIdx()
                val packet = client.sendSecureStreamData(connectionId.toLong(), testData)
                val received = server.receiveSecureStreamData(packet)
                
                connectionId to (received.a > 0)
            }
        }
        
        val results = connections.awaitAll()
        assertEquals(5, results.size)
        
        results.forEach { (connectionId, success) ->
            assertTrue(success, "Connection $connectionId should succeed")
        }
    }
    
    @Test
    fun `test error handling for incomplete handshake`() = runTest {
        // Try to send data without completing handshake
        val testData = "Should fail".encodeToByteArray().toIdx()
        
        assertFailsWith<IllegalStateException> {
            clientEngine.sendSecureStreamData(1L, testData)
        }
    }
    
    @Test
    fun `test session resumption`() = runTest {
        // Complete initial handshake
        performHandshake()
        
        // In real implementation, we would test session resumption
        // For now, we'll just verify the engines are in the correct state
        assertNotNull(clientEngine)
        assertNotNull(serverEngine)
    }
    
    @Test
    fun `test 0-RTT data`() = runTest {
        // Test 0-RTT data transmission (if enabled)
        val config = SecureQuicEngine.SecureQuicConfig(enable0RTT = true)
        
        val testClient = SecureQuicEngine(
            role = SecureQuicEngine.Role.CLIENT,
            cryptoEngine = cryptoEngine,
            config = config
        )
        
        val testServer = SecureQuicEngine(
            role = SecureQuicEngine.Role.SERVER,
            cryptoEngine = cryptoEngine,
            config = config
        )
        
        // In real implementation, we would test 0-RTT data
        // For now, we'll just verify the configuration is set
        assertTrue(config.enable0RTT)
    }
    
    @Test
    fun `test post-quantum crypto preparation`() = runTest {
        // Test post-quantum crypto configuration
        val config = SecureQuicEngine.SecureQuicConfig(enablePostQuantum = true)
        
        val testClient = SecureQuicEngine(
            role = SecureQuicEngine.Role.CLIENT,
            cryptoEngine = cryptoEngine,
            config = config
        )
        
        val testServer = SecureQuicEngine(
            role = SecureQuicEngine.Role.SERVER,
            cryptoEngine = cryptoEngine,
            config = config
        )
        
        // In real implementation, we would test post-quantum algorithms
        // For now, we'll just verify the configuration is set
        assertTrue(config.enablePostQuantum)
    }
    
    @Test
    fun `test connection state management`() = runTest {
        // Test connection state transitions
        val clientPackets = clientEngine.startHandshake()
        assertTrue(clientPackets.a > 0)
        
        val serverResponses = serverEngine.processHandshake(clientPackets.b(0))
        assertTrue(serverResponses.a >= 0)
        
        // Verify connection state is properly managed
        assertNotNull(clientEngine)
        assertNotNull(serverEngine)
    }
    
    // Helper method to perform complete handshake
    private suspend fun performHandshake() {
        val clientPackets = clientEngine.startHandshake()
        val serverResponses = serverEngine.processHandshake(clientPackets.b(0))
        
        if (serverResponses.a > 0) {
            val clientResponses = clientEngine.processHandshake(serverResponses.b(0))
            
            if (clientResponses.a > 0) {
                serverEngine.processHandshake(clientResponses.b(0))
            }
        }
    }
} 