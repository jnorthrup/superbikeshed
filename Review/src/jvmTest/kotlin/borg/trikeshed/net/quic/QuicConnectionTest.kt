package borg.trikeshed.net.quic

import borg.trikeshed.net.quic.crypto.QuicSecrets
import borg.trikeshed.net.quic.crypto.TestAesService // Assuming this path is correct from previous steps
import borg.trikeshed.net.quic.crypto.TestHkdfService // Assuming this path is correct
import borg.trikeshed.net.quic.crypto.hkdfExpandLabel // For manual verification against test service
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import borg.trikeshed.net.quic.tls.KeyShareEntry
import borg.trikeshed.net.quic.tls.ServerHelloData
import borg.trikeshed.net.quic.tls.TLS_AES_128_GCM_SHA256
import borg.trikeshed.net.quic.tls.TLS_VERSION_1_2
import borg.trikeshed.net.quic.tls.TLS_VERSION_1_3
import borg.trikeshed.net.quic.tls.TlsExtensionType
import borg.trikeshed.net.quic.tls.X25519_GROUP
import borg.trikeshed.net.quic.utils.writeByteLengthPrefixed
import borg.trikeshed.net.quic.utils.writeShort
import kotlinx.coroutines.runBlocking
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import borg.trikeshed.net.quic.crypto.generateEcdhKeyPair // Actual Jvm impl
import borg.trikeshed.net.quic.crypto.computeEcdhSharedSecret // Actual Jvm impl
import borg.trikeshed.net.quic.crypto.sha256 // Actual Jvm impl


// Define QUIC_V1_INITIAL_SALT locally for the test if not easily accessible
// This should match the one in QuicConnection.kt
private val QUIC_V1_INITIAL_SALT_TEST = byteArrayOf(
    0x38, 0x76, 0x2c, 0xf7, 0xf5, 0x59, 0x34, 0xb3, 0x4d, 0x17, 0x9a, 0xe6,
    0xa4, 0xc8, 0x0c, 0xad, 0xcc, 0xbb, 0x7f, 0x0a
)

class QuicConnectionTest {

    private lateinit var testHkdfService: TestHkdfService
    private lateinit var testAesService: TestAesService // Will be used in later tests
    private lateinit var connectionData: QuicConnection
    private lateinit var manager: QuicConnectionManager

    @BeforeTest
    fun setup() {
        testHkdfService = TestHkdfService()
        testAesService = TestAesService()
        // Create connection data object only
        connectionData = QuicConnection.newClientConnectionDataOnly(connectionIdLength = 8)
        manager = QuicConnectionManager(connectionData, QuicConnectionManager.ConnectionRole.CLIENT)
    }

    // Helper to manually construct HkdfLabel for info based on hkdfExpandLabel's logic
    private fun constructHkdfLabelForTest(labelString: String, context: ByteArray, length: Int): ByteArray {
        val tlsLabelPrefix = "tls13 "
        val fullLabel = tlsLabelPrefix + labelString
        val labelBytes = fullLabel.encodeToByteArray()

        val lengthBytes = byteArrayOf((length shr 8).toByte(), length.toByte())
        val info = ByteArray(2 + 1 + labelBytes.size + 1 + context.size)
        var offset = 0
        lengthBytes.copyInto(info, offset, 0, 2); offset += 2
        info[offset++] = labelBytes.size.toByte()
        labelBytes.copyInto(info, offset, 0, labelBytes.size); offset += labelBytes.size
        info[offset++] = context.size.toByte()
        context.copyInto(info, offset, 0, context.size)
        return info
    }


    @Test
    fun testDeriveInitialSecrets() = runBlocking {
        // Ensure clientId is not null for this test
        assertNotNull(connectionData.clientId, "Client ID should be initialized")
        val clientId = connectionData.clientId!!

        connectionData.deriveInitialSecrets(testHkdfService, manager)

        val initialSecrets = manager.getCurrentSecretsForSend(EncryptionLevel.INITIAL)
        assertNotNull(initialSecrets, "Initial secrets should be set in manager")

        // --- Verification based on TestHkdfService's predictable behavior ---
        // 1. Initial Secret Extraction
        // TestHkdfService.extract(salt, ikm) returns: salt + ikm (truncated/padded to 32 if needed by TestHkdfService)
        var expectedInitialSecret = QUIC_V1_INITIAL_SALT_TEST + clientId
        if (expectedInitialSecret.size > 32) expectedInitialSecret = expectedInitialSecret.sliceArray(0..31)
        else if (expectedInitialSecret.size < 32) expectedInitialSecret += ByteArray(32 - expectedInitialSecret.size) // Assuming TestHkdfService pads

        // 2. Client Initial Secret (using hkdfExpandLabel's constructed info)
        val clientInLabel = "client in"
        val clientInContext = byteArrayOf()
        val clientInLength = 32
        val clientInInfo = constructHkdfLabelForTest(clientInLabel, clientInContext, clientInLength)
        // TestHkdfService.expand(prk, info, length) returns: (prk + info) truncated/patterned to 'length'
        var expectedClientInitialSecretTemp = expectedInitialSecret + clientInInfo
        val expectedClientInitialSecret = ByteArray(clientInLength)
        for (i in 0 until clientInLength) {
            expectedClientInitialSecret[i] = expectedClientInitialSecretTemp.getOrElse(i % expectedClientInitialSecretTemp.size) { 0.toByte() }
        }

        // 3. Client Initial Key
        val quicKeyLabel = "quic key"
        val quicKeyContext = byteArrayOf() // Empty context for these specific labels
        val quicKeyLength = 16
        val quicKeyInfo = constructHkdfLabelForTest(quicKeyLabel, quicKeyContext, quicKeyLength)
        var expectedKeyTemp = expectedClientInitialSecret + quicKeyInfo
        val expectedKey = ByteArray(quicKeyLength)
        for (i in 0 until quicKeyLength) {
            expectedKey[i] = expectedKeyTemp.getOrElse(i % expectedKeyTemp.size) { 0.toByte() }
        }
        assertContentEquals(expectedKey, initialSecrets.key, "Derived client initial key mismatch")

        // 4. Client Initial IV
        val quicIvLabel = "quic iv"
        val quicIvLength = 12
        val quicIvInfo = constructHkdfLabelForTest(quicIvLabel, quicKeyContext, quicIvLength) // Same empty context
        var expectedIvTemp = expectedClientInitialSecret + quicIvInfo
        val expectedIv = ByteArray(quicIvLength)
        for (i in 0 until quicIvLength) {
            expectedIv[i] = expectedIvTemp.getOrElse(i % expectedIvTemp.size) { 0.toByte() }
        }
        assertContentEquals(expectedIv, initialSecrets.iv, "Derived client initial IV mismatch")

        // 5. Client Initial Header Protection Key
        val quicHpLabel = "quic hp"
        val quicHpLength = 16
        val quicHpInfo = constructHkdfLabelForTest(quicHpLabel, quicKeyContext, quicHpLength) // Same empty context
        var expectedHpKeyTemp = expectedClientInitialSecret + quicHpInfo
        val expectedHpKey = ByteArray(quicHpLength)
        for (i in 0 until quicHpLength) {
            expectedHpKey[i] = expectedHpKeyTemp.getOrElse(i % expectedHpKeyTemp.size) { 0.toByte() }
        }
        assertContentEquals(expectedHpKey, initialSecrets.hpKey, "Derived client initial HP key mismatch")

        // Also check that cryptoSecrets map in QuicConnection itself is populated
        val localInitialSecrets = connectionData.cryptoSecrets[EncryptionLevel.INITIAL]
        assertNotNull(localInitialSecrets, "Initial secrets should be set in QuicConnection's map")
        assertContentEquals(expectedKey, localInitialSecrets.key, "Local derived client initial key mismatch")

    }

    // Helper to construct a ServerHello byte array for testing, similar to QuicCurl mocks
    // but allows injecting specific server public key.
    private fun constructTestServerHelloBytes(
        serverPublicKey: ByteArray,
        serverRandom: ByteArray = Random.nextBytes(32), // Allow overriding for specific scenarios
        cipherSuiteVal: UShort = TLS_AES_128_GCM_SHA256,
        keyShareGroup: UShort = X25519_GROUP,
        supportedVersionVal: UShort = TLS_VERSION_1_3
    ): ByteArray {
        val legacyVersion = TLS_VERSION_1_2.writeShort()
        val sessionId = byteArrayOf().writeByteLengthPrefixed() // Empty session ID
        val cipherSuite = cipherSuiteVal.writeShort()
        val compressionMethod = byteArrayOf(0x00)

        var extensionsPayload = byteArrayOf()

        // Supported Versions extension
        val svExtData = supportedVersionVal.writeShort()
        extensionsPayload += TlsExtensionType.SUPPORTED_VERSIONS.writeShort()
        extensionsPayload += svExtData.size.toUShort().writeShort() // Extension data length
        extensionsPayload += svExtData

        // Key Share extension
        val ksExtData = keyShareGroup.writeShort() + serverPublicKey.size.toUShort().writeShort() + serverPublicKey
        extensionsPayload += TlsExtensionType.KEY_SHARE.writeShort()
        extensionsPayload += ksExtData.size.toUShort().writeShort() // Extension data length
        extensionsPayload += ksExtData

        val extensionsOverallLength = extensionsPayload.size.toUShort().writeShort()

        val payload = legacyVersion + serverRandom + sessionId + cipherSuite + compressionMethod + extensionsOverallLength + extensionsPayload

        // Handshake header (type 0x02 for ServerHello)
        val header = byteArrayOf(
            0x02.toByte(),
            (payload.size shr 16).toByte(),
            (payload.size shr 8).toByte(),
            payload.size.toByte()
        )
        return header + payload
    }


    @Test
    fun testInitiateClientHandshake() = runBlocking {
        // Prerequisite: Initial secrets must be derived
        connectionData.deriveInitialSecrets(testHkdfService, manager)

        val clientHelloBytes = connectionData.initiateClientHandshake(testHkdfService, manager)

        assertNotNull(clientHelloBytes, "ClientHello bytes should not be null")
        assertTrue(clientHelloBytes.isNotEmpty(), "ClientHello bytes should not be empty")

        assertEquals(TlsHandshakeState.EXPECTING_SERVER_HELLO, connectionData.tlsHandshakeState, "TLS handshake state mismatch after ClientHello")
        assertTrue(connectionData.handshakeTranscript.isNotEmpty(), "Handshake transcript should not be empty after ClientHello")
        assertContentEquals(clientHelloBytes, connectionData.handshakeTranscript, "Transcript should contain ClientHello")

        assertNotNull(connectionData.clientEphemeralPrivateKey, "Client ephemeral private key should be set")
        assertNotNull(connectionData.clientEphemeralPublicKey, "Client ephemeral public key should be set")
        assertTrue(connectionData.clientEphemeralPrivateKey!!.isNotEmpty(), "Client ephemeral private key should not be empty")
        assertTrue(connectionData.clientEphemeralPublicKey!!.isNotEmpty(), "Client ephemeral public key should not be empty")
    }

    @Test
    fun testProcessServerHello() = runBlocking {
        // Setup: Initial secrets and ClientHello must have been processed
        connectionData.deriveInitialSecrets(testHkdfService, manager)
        val clientHelloBytes = connectionData.initiateClientHandshake(testHkdfService, manager)
        assertNotNull(clientHelloBytes, "ClientHello generation failed")
        val clientPrivateKey = connectionData.clientEphemeralPrivateKey!!
        // val clientPublicKey = connectionData.clientEphemeralPublicKey!! // Not directly used here

        // Construct Mock ServerHello with a real server ephemeral public key
        val serverEphemeralKeyPair = generateEcdhKeyPair(X25519_GROUP) // Using actual crypto
        val serverPublicKeyBytes = serverEphemeralKeyPair.second
        val mockServerHelloBytes = constructTestServerHelloBytes(serverPublicKeyBytes)

        // Pre-calculate Expected Shared Secret using actual crypto
        val expectedDhSecret = computeEcdhSharedSecret(X25519_GROUP, clientPrivateKey, serverPublicKeyBytes)
        assertNotNull(expectedDhSecret, "DH secret calculation failed")
        assertTrue(expectedDhSecret.isNotEmpty(), "DH secret should not be empty")

        // Execute processServerHandshakeMessage for ServerHello
        val success = connectionData.processServerHandshakeMessage(
            mockServerHelloBytes,
            EncryptionLevel.INITIAL,
            testHkdfService,
            testAesService,
            manager
        )
        assertTrue(success, "processServerHandshakeMessage for ServerHello failed")

        // Assert States and Transcript
        assertEquals(TlsHandshakeState.EXPECTING_ENCRYPTED_EXTENSIONS, connectionData.tlsHandshakeState)
        assertTrue(connectionData.handshakeTranscript.size > clientHelloBytes.size, "Transcript should have grown")
        assertContentEquals(clientHelloBytes + mockServerHelloBytes, connectionData.handshakeTranscript, "Transcript content mismatch")

        // Assert Derived Secrets (using TestHkdfService's predictable behavior)
        val storedDerivedHandshakeSecret = connectionData.derivedHandshakeSecret // This is saltForHandshakeSecret
        assertNotNull(storedDerivedHandshakeSecret, "derivedHandshakeSecret (salt) should be set")

        val clientHsTrafficSecretInternal = connectionData.clientHandshakeTrafficSecretInternal
        assertNotNull(clientHsTrafficSecretInternal, "clientHandshakeTrafficSecretInternal should be set")

        val serverHsTrafficSecretInternal = connectionData.serverHandshakeTrafficSecretInternal
        assertNotNull(serverHsTrafficSecretInternal, "serverHandshakeTrafficSecretInternal should be set")

        // Manually calculate expected handshake secrets based on expectedDhSecret and TestHkdfService logic
        // 1. derived_secret (salt for handshake_secret)
        val expectedEarlySecret = ByteArray(32) // TestHkdfService.extract(ByteArray(0), ByteArray(0)) -> if TestHkdfService returns IKM if salt is empty, this is 0. Otherwise, it's salt+ikm.
                                                 // Assuming the actual code uses ByteArray(32) for early secret when PSK is not used.
                                                 // The `hkdfExpandLabel` in QuicConnection uses `earlySecret = ByteArray(32)`

        val derivedLabel = "derived"
        val derivedContext = byteArrayOf()
        val derivedLength = 32
        val derivedInfo = constructHkdfLabelForTest(derivedLabel, derivedContext, derivedLength)
        var expectedSaltForHandshakeSecretTemp = expectedEarlySecret + derivedInfo
        val expectedSaltForHandshakeSecret = ByteArray(derivedLength)
        for (i in 0 until derivedLength) {
            expectedSaltForHandshakeSecret[i] = expectedSaltForHandshakeSecretTemp.getOrElse(i % expectedSaltForHandshakeSecretTemp.size) { 0.toByte() }
        }
        assertContentEquals(expectedSaltForHandshakeSecret, storedDerivedHandshakeSecret, "Stored derivedHandshakeSecret (salt) mismatch")

        // 2. handshake_secret
        var expectedHandshakeSecretTemp = expectedSaltForHandshakeSecret + expectedDhSecret // TestHkdfService.extract behavior
        val expectedHandshakeSecret = ByteArray(32) // Assuming SHA-256 hash output length for PRK
         for (i in 0 until 32) { // Adapting to TestHkdfService.extract logic (salt+ikm, then truncate/pad to 32 for PRK)
            if (expectedHandshakeSecretTemp.size > 32) expectedHandshakeSecretTemp = expectedHandshakeSecretTemp.sliceArray(0..31)
            else if (expectedHandshakeSecretTemp.size < 32) expectedHandshakeSecretTemp += ByteArray(32 - expectedHandshakeSecretTemp.size)
        }
        if (expectedHandshakeSecretTemp.size == 32) System.arraycopy(expectedHandshakeSecretTemp, 0, expectedHandshakeSecret, 0, 32)
        else if (expectedHandshakeSecretTemp.size > 32) System.arraycopy(expectedHandshakeSecretTemp, 0, expectedHandshakeSecret, 0, 32)
        else { System.arraycopy(expectedHandshakeSecretTemp, 0, expectedHandshakeSecret, 0, expectedHandshakeSecretTemp.size) }


        // 3. Client Handshake Traffic Secret
        // Transcript for this derivation is ClientHello + ServerHello
        val transcriptForHandshakeSecrets = connectionData.handshakeTranscript
        val clientHsLabel = "c hs traffic"
        val clientHsLength = 32
        val clientHsInfo = constructHkdfLabelForTest(clientHsLabel, transcriptForHandshakeSecrets, clientHsLength)
        var expectedClientHsTrafficSecretTemp = expectedHandshakeSecret + clientHsInfo
        val expectedClientHsTrafficSecret = ByteArray(clientHsLength)
        for (i in 0 until clientHsLength) {
            expectedClientHsTrafficSecret[i] = expectedClientHsTrafficSecretTemp.getOrElse(i % expectedClientHsTrafficSecretTemp.size) { 0.toByte() }
        }
        assertContentEquals(expectedClientHsTrafficSecret, clientHsTrafficSecretInternal, "Client handshake traffic secret mismatch")

        // 4. Server Handshake Traffic Secret
        val serverHsLabel = "s hs traffic"
        val serverHsLength = 32
        val serverHsInfo = constructHkdfLabelForTest(serverHsLabel, transcriptForHandshakeSecrets, serverHsLength)
        var expectedServerHsTrafficSecretTemp = expectedHandshakeSecret + serverHsInfo
        val expectedServerHsTrafficSecret = ByteArray(serverHsLength)
        for (i in 0 until serverHsLength) {
            expectedServerHsTrafficSecret[i] = expectedServerHsTrafficSecretTemp.getOrElse(i % expectedServerHsTrafficSecretTemp.size) { 0.toByte() }
        }
        assertContentEquals(expectedServerHsTrafficSecret, serverHsTrafficSecretInternal, "Server handshake traffic secret mismatch")

        // Assert Manager Update and Internal Key Storage
        val clientHandshakeSecretsFromManager = manager.getCurrentSecretsForSend(EncryptionLevel.HANDSHAKE)
        assertNotNull(clientHandshakeSecretsFromManager, "Client handshake secrets not found in manager")

        // Keys for client sending (derived from client_hs_traffic_secret)
        val expectedClientHsKeyInfo = constructHkdfLabelForTest("quic key", byteArrayOf(), 16)
        var tempExpClientKey = expectedClientHsTrafficSecret + expectedClientHsKeyInfo
        val finalExpClientKey = ByteArray(16)
        for(i in 0 until 16) finalExpClientKey[i] = tempExpClientKey.getOrElse(i % tempExpClientKey.size) {0.toByte()}
        assertContentEquals(finalExpClientKey, clientHandshakeSecretsFromManager.key, "Manager's client handshake key mismatch")

        assertNotNull(connectionData.serverHandshakeSecretsForReception, "Server handshake reception secrets not set in QuicConnection")
        val expectedServerHsKeyInfo = constructHkdfLabelForTest("quic key", byteArrayOf(), 16)
        var tempExpServerKey = expectedServerHsTrafficSecret + expectedServerHsKeyInfo
        val finalExpServerKey = ByteArray(16)
        for(i in 0 until 16) finalExpServerKey[i] = tempExpServerKey.getOrElse(i % tempExpServerKey.size) {0.toByte()}
        assertContentEquals(finalExpServerKey, connectionData.serverHandshakeSecretsForReception!!.key, "QuicConnection's server handshake reception key mismatch")
    }

    private fun constructHandshakeMsgBytes(type: UByte, payload: ByteArray): ByteArray {
        val header = byteArrayOf(
            type.toByte(),
            (payload.size shr 16).toByte(),
            (payload.size shr 8).toByte(),
            payload.size.toByte()
        )
        return header + payload
    }

    @Test
    fun testProcessEncryptedExtensions() = runBlocking {
        // 1. Setup: Bring connection to EXPECTING_ENCRYPTED_EXTENSIONS state
        connectionData.deriveInitialSecrets(testHkdfService, manager)
        val clientHelloBytes = connectionData.initiateClientHandshake(testHkdfService, manager)
        assertNotNull(clientHelloBytes)
        val serverEphemeralKP = generateEcdhKeyPair(X25519_GROUP)
        val mockServerHelloBytes = constructTestServerHelloBytes(serverEphemeralKP.second)
        assertTrue(connectionData.processServerHandshakeMessage(mockServerHelloBytes, EncryptionLevel.INITIAL, testHkdfService, testAesService, manager))
        assertEquals(TlsHandshakeState.EXPECTING_ENCRYPTED_EXTENSIONS, connectionData.tlsHandshakeState)
        val transcriptAfterServerHello = connectionData.handshakeTranscript.copyOf()

        // 2. Construct Mock EncryptedExtensions
        // An empty EncryptedExtensions message (TLS Handshake Type 0x08)
        // Payload contains an "extensions" field which is a list of extensions.
        // For an empty list, the "extensions" field is 2 bytes: 0x0000 (length of list).
        val mockEncryptedExtensionsPayload = byteArrayOf(0x00, 0x00) // Empty extensions list (length 0)
        val mockEncryptedExtensionsBytes = constructHandshakeMsgBytes(0x08u, mockEncryptedExtensionsPayload)

        // 3. Execute
        val success = connectionData.processServerHandshakeMessage(mockEncryptedExtensionsBytes, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager)
        assertTrue(success, "Processing EncryptedExtensions failed")

        // 4. Assert
        assertEquals(TlsHandshakeState.EXPECTING_CERTIFICATE, connectionData.tlsHandshakeState, "State should transition to EXPECTING_CERTIFICATE")
        val expectedTranscript = transcriptAfterServerHello + mockEncryptedExtensionsBytes
        assertContentEquals(expectedTranscript, connectionData.handshakeTranscript, "Transcript mismatch after EncryptedExtensions")
    }

    @Test
    fun testProcessCertificate() = runBlocking {
        // 1. Setup: Bring connection to EXPECTING_CERTIFICATE state
        connectionData.deriveInitialSecrets(testHkdfService, manager)
        val clientHelloBytes = connectionData.initiateClientHandshake(testHkdfService, manager)
        assertNotNull(clientHelloBytes)
        val serverEphemeralKP = generateEcdhKeyPair(X25519_GROUP)
        val mockServerHelloBytes = constructTestServerHelloBytes(serverEphemeralKP.second)
        assertTrue(connectionData.processServerHandshakeMessage(mockServerHelloBytes, EncryptionLevel.INITIAL, testHkdfService, testAesService, manager))

        val mockEncryptedExtensionsPayload = byteArrayOf(0x00, 0x00) // Empty extensions list
        val mockEncryptedExtensionsBytes = constructHandshakeMsgBytes(0x08u, mockEncryptedExtensionsPayload)
        assertTrue(connectionData.processServerHandshakeMessage(mockEncryptedExtensionsBytes, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager))
        assertEquals(TlsHandshakeState.EXPECTING_CERTIFICATE, connectionData.tlsHandshakeState)
        val transcriptAfterEE = connectionData.handshakeTranscript.copyOf()

        // 2. Construct Mock Certificate
        val dummyCertDataBytes = byteArrayOf(0x01, 0x02, 0x03) // Dummy X.509 cert
        var certEntryPayload = byteArrayOf(0x00, 0x00, dummyCertDataBytes.size.toByte()) + dummyCertDataBytes // cert_data len + data
        certEntryPayload += byteArrayOf(0x00, 0x00) // empty extensions for cert_entry (len 0)

        var certificatePayload = byteArrayOf(0x00) // cert_request_context_len = 0
        certificatePayload += byteArrayOf(0x00, 0x00, certEntryPayload.size.toByte()) + certEntryPayload // cert_list_len + list
        val mockCertificateBytes = constructHandshakeMsgBytes(0x0Bu, certificatePayload) // Type 0x0B for Certificate

        // 3. Execute
        val success = connectionData.processServerHandshakeMessage(mockCertificateBytes, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager)
        assertTrue(success, "Processing Certificate failed")

        // 4. Assert
        assertEquals(TlsHandshakeState.EXPECTING_CERTIFICATE_VERIFY, connectionData.tlsHandshakeState, "State should transition to EXPECTING_CERTIFICATE_VERIFY")
        val expectedTranscript = transcriptAfterEE + mockCertificateBytes
        assertContentEquals(expectedTranscript, connectionData.handshakeTranscript, "Transcript mismatch after Certificate")
        assertNotNull(connectionData.serverCertificates, "Server certificates should be stored")
        assertEquals(1, connectionData.serverCertificates!!.size, "Should be one certificate entry stored")
        assertContentEquals(dummyCertDataBytes, connectionData.serverCertificates!![0].certificateData, "Stored certificate data mismatch")
        assertTrue(connectionData.serverCertificates!![0].extensions.isEmpty(), "Stored certificate entry extensions should be empty")
    }

    // TODO: Add testProcessCertificateVerify and testProcessServerFinished in subsequent steps

    @Test
    fun testProcessCertificateVerify() = runBlocking {
        // 1. Setup: Bring connection to EXPECTING_CERTIFICATE_VERIFY state
        connectionData.deriveInitialSecrets(testHkdfService, manager)
        val clientHelloBytes = connectionData.initiateClientHandshake(testHkdfService, manager)
        assertNotNull(clientHelloBytes)
        val serverEphemeralKP = generateEcdhKeyPair(X25519_GROUP)
        val mockServerHelloBytes = constructTestServerHelloBytes(serverEphemeralKP.second)
        assertTrue(connectionData.processServerHandshakeMessage(mockServerHelloBytes, EncryptionLevel.INITIAL, testHkdfService, testAesService, manager))

        val mockEncryptedExtensionsPayload = byteArrayOf(0x00, 0x00) // Empty extensions list
        val mockEncryptedExtensionsBytes = constructHandshakeMsgBytes(0x08u, mockEncryptedExtensionsPayload)
        assertTrue(connectionData.processServerHandshakeMessage(mockEncryptedExtensionsBytes, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager))

        val dummyCertDataBytes = byteArrayOf(0x01, 0x02, 0x03)
        var certEntryPayload = byteArrayOf(0x00, 0x00, dummyCertDataBytes.size.toByte()) + dummyCertDataBytes
        certEntryPayload += byteArrayOf(0x00, 0x00)
        var certificatePayload = byteArrayOf(0x00)
        certificatePayload += byteArrayOf(0x00, 0x00, certEntryPayload.size.toByte()) + certEntryPayload
        val mockCertificateBytes = constructHandshakeMsgBytes(0x0Bu, certificatePayload)
        assertTrue(connectionData.processServerHandshakeMessage(mockCertificateBytes, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager))
        assertEquals(TlsHandshakeState.EXPECTING_CERTIFICATE_VERIFY, connectionData.tlsHandshakeState)
        val transcriptAfterCert = connectionData.handshakeTranscript.copyOf()

        // 2. Construct Mock CertificateVerify
        // Using ecdsa_secp256r1_sha256 (0x0403) as an example
        val verifyPayload = borg.trikeshed.net.quic.tls.TlsSignatureScheme.ECDSA_SECP256R1_SHA256.writeShort() +
                            byteArrayOf(0,4) + // signature length = 4
                            byteArrayOf(1,2,3,4) // dummy signature
        val mockCertificateVerifyBytes = constructHandshakeMsgBytes(0x0Fu, verifyPayload) // Type 0x0F for CertificateVerify


        // 3. Execute
        val success = connectionData.processServerHandshakeMessage(mockCertificateVerifyBytes, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager)
        // verifySignature is a placeholder returning true, so this should pass
        assertTrue(success, "Processing CertificateVerify failed")

        // 4. Assert
        assertEquals(TlsHandshakeState.EXPECTING_SERVER_FINISHED, connectionData.tlsHandshakeState, "State should transition to EXPECTING_SERVER_FINISHED")
        val expectedTranscript = transcriptAfterCert + mockCertificateVerifyBytes
        assertContentEquals(expectedTranscript, connectionData.handshakeTranscript, "Transcript mismatch after CertificateVerify")
    }

    @Test
    fun testProcessServerFinished_ValidVerifyData() = runBlocking {
        // 1. Setup: Bring connection to EXPECTING_SERVER_FINISHED state
        connectionData.deriveInitialSecrets(testHkdfService, manager)
        val clientHelloBytes = connectionData.initiateClientHandshake(testHkdfService, manager)
        assertNotNull(clientHelloBytes)
        val serverEphemeralKP = generateEcdhKeyPair(X25519_GROUP)
        val mockServerHelloBytes = constructTestServerHelloBytes(serverEphemeralKP.second)
        assertTrue(connectionData.processServerHandshakeMessage(mockServerHelloBytes, EncryptionLevel.INITIAL, testHkdfService, testAesService, manager))

        val mockEncryptedExtensionsPayload = byteArrayOf(0x00, 0x00)
        val mockEncryptedExtensionsBytes = constructHandshakeMsgBytes(0x08u, mockEncryptedExtensionsPayload)
        assertTrue(connectionData.processServerHandshakeMessage(mockEncryptedExtensionsBytes, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager))

        val dummyCertDataBytes = byteArrayOf(0x01, 0x02, 0x03)
        var certEntryPayload = byteArrayOf(0x00, 0x00, dummyCertDataBytes.size.toByte()) + dummyCertDataBytes
        certEntryPayload += byteArrayOf(0x00, 0x00)
        var certificatePayload = byteArrayOf(0x00)
        certificatePayload += byteArrayOf(0x00, 0x00, certEntryPayload.size.toByte()) + certEntryPayload
        val mockCertificateBytes = constructHandshakeMsgBytes(0x0Bu, certificatePayload)
        assertTrue(connectionData.processServerHandshakeMessage(mockCertificateBytes, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager))

        val verifyPayload = borg.trikeshed.net.quic.tls.TlsSignatureScheme.ECDSA_SECP256R1_SHA256.writeShort() +
                            byteArrayOf(0,4) + byteArrayOf(1,2,3,4)
        val mockCertificateVerifyBytes = constructHandshakeMsgBytes(0x0Fu, verifyPayload)
        assertTrue(connectionData.processServerHandshakeMessage(mockCertificateVerifyBytes, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager))
        assertEquals(TlsHandshakeState.EXPECTING_SERVER_FINISHED, connectionData.tlsHandshakeState)
        val transcriptBeforeServerFinished = connectionData.handshakeTranscript.copyOf()

        // 2. Calculate expected verify_data
        assertNotNull(connectionData.serverHandshakeTrafficSecretInternal, "Server handshake traffic secret should be set")
        val finishedKey = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(testHkdfService, connectionData.serverHandshakeTrafficSecretInternal!!, "finished", ByteArray(0), 32)
        val transcriptHash = borg.trikeshed.net.quic.crypto.sha256(transcriptBeforeServerFinished) // sha256 from PlatformCrypto.jvm.kt
        val expectedVerifyData = borg.trikeshed.net.quic.crypto.hmacSha256(testHkdfService, finishedKey, transcriptHash)
        val mockServerFinishedBytes = constructHandshakeMsgBytes(0x14u, expectedVerifyData) // Type 0x14 for Finished

        // 3. Execute
        val success = connectionData.processServerHandshakeMessage(mockServerFinishedBytes, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager)
        assertTrue(success, "Processing valid ServerFinished failed")

        // 4. Assert
        assertEquals(TlsHandshakeState.READY_TO_SEND_CLIENT_FINISHED, connectionData.tlsHandshakeState, "State should transition to READY_TO_SEND_CLIENT_FINISHED")
        val expectedTranscript = transcriptBeforeServerFinished + mockServerFinishedBytes
        assertContentEquals(expectedTranscript, connectionData.handshakeTranscript, "Transcript mismatch after valid ServerFinished")
    }

    @Test
    fun testProcessServerFinished_InvalidVerifyData() = runBlocking {
        // 1. Setup: Bring connection to EXPECTING_SERVER_FINISHED state (similar to above)
        connectionData.deriveInitialSecrets(testHkdfService, manager)
        connectionData.initiateClientHandshake(testHkdfService, manager)
        val serverEphemeralKP = generateEcdhKeyPair(X25519_GROUP)
        val mockServerHelloBytes = constructTestServerHelloBytes(serverEphemeralKP.second)
        connectionData.processServerHandshakeMessage(mockServerHelloBytes, EncryptionLevel.INITIAL, testHkdfService, testAesService, manager)
        val mockEncryptedExtensionsBytes = constructHandshakeMsgBytes(0x08u, byteArrayOf(0,0))
        connectionData.processServerHandshakeMessage(mockEncryptedExtensionsBytes, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager)
        val dummyCertDataBytes = byteArrayOf(0x01,0x02,0x03); var certEntryPayload = byteArrayOf(0x00,0x00,dummyCertDataBytes.size.toByte()) + dummyCertDataBytes + byteArrayOf(0,0); var certificatePayload = byteArrayOf(0x00) + byteArrayOf(0x00,0x00,certEntryPayload.size.toByte()) + certEntryPayload
        val mockCertificateBytes = constructHandshakeMsgBytes(0x0Bu, certificatePayload)
        connectionData.processServerHandshakeMessage(mockCertificateBytes, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager)
        val verifyPl = borg.trikeshed.net.quic.tls.TlsSignatureScheme.ECDSA_SECP256R1_SHA256.writeShort() + byteArrayOf(0,4) + byteArrayOf(1,2,3,4)
        val mockCertificateVerifyBytes = constructHandshakeMsgBytes(0x0Fu, verifyPl)
        connectionData.processServerHandshakeMessage(mockCertificateVerifyBytes, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager)
        val transcriptBeforeServerFinished = connectionData.handshakeTranscript.copyOf()

        // 2. Calculate expected verify_data and then create invalid verify_data
        assertNotNull(connectionData.serverHandshakeTrafficSecretInternal)
        val finishedKey = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(testHkdfService, connectionData.serverHandshakeTrafficSecretInternal!!, "finished", ByteArray(0), 32)
        val transcriptHash = borg.trikeshed.net.quic.crypto.sha256(transcriptBeforeServerFinished)
        val expectedVerifyData = borg.trikeshed.net.quic.crypto.hmacSha256(testHkdfService, finishedKey, transcriptHash)
        val invalidVerifyData = expectedVerifyData.clone().also { if (it.isNotEmpty()) it[0] = it[0].inc() else { /* handle empty if possible, though should be 32 bytes */ } }
        val mockInvalidServerFinishedBytes = constructHandshakeMsgBytes(0x14u, invalidVerifyData)

        // 3. Execute
        val success = connectionData.processServerHandshakeMessage(mockInvalidServerFinishedBytes, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager)
        kotlin.test.assertFalse(success, "Processing invalid ServerFinished should fail")

        // 4. Assert
        assertEquals(TlsHandshakeState.EXPECTING_SERVER_FINISHED, connectionData.tlsHandshakeState, "State should not change on invalid ServerFinished")
        assertContentEquals(transcriptBeforeServerFinished, connectionData.handshakeTranscript, "Transcript should not update with invalid ServerFinished")
    }

    @Test
    fun testGenerateClientFinishedMessage() = runBlocking {
        // 1. Setup: Bring connection to READY_TO_SEND_CLIENT_FINISHED state
        connectionData.deriveInitialSecrets(testHkdfService, manager)
        connectionData.initiateClientHandshake(testHkdfService, manager)
        val serverEphemeralKP = generateEcdhKeyPair(X25519_GROUP)
        val mockSH = constructTestServerHelloBytes(serverEphemeralKP.second)
        connectionData.processServerHandshakeMessage(mockSH, EncryptionLevel.INITIAL, testHkdfService, testAesService, manager)
        val mockEE = constructHandshakeMsgBytes(0x08u, byteArrayOf(0,0))
        connectionData.processServerHandshakeMessage(mockEE, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager)
        val certPayload = byteArrayOf(0x00) + byteArrayOf(0,0,5) + byteArrayOf(0,0,1, 0xFF.toByte()) + byteArrayOf(0,0)
        val mockCert = constructHandshakeMsgBytes(0x0Bu, certPayload)
        connectionData.processServerHandshakeMessage(mockCert, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager)
        val cvPayload = borg.trikeshed.net.quic.tls.TlsSignatureScheme.ECDSA_SECP256R1_SHA256.writeShort() + byteArrayOf(0,4) + byteArrayOf(1,2,3,4)
        val mockCV = constructHandshakeMsgBytes(0x0Fu, cvPayload)
        connectionData.processServerHandshakeMessage(mockCV, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager)

        assertNotNull(connectionData.serverHandshakeTrafficSecretInternal, "Prerequisite: Server handshake traffic secret must be set")
        val sfFinishedKey = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(testHkdfService, connectionData.serverHandshakeTrafficSecretInternal!!, "finished", ByteArray(0), 32)
        val sfTranscriptHash = borg.trikeshed.net.quic.crypto.sha256(connectionData.handshakeTranscript)
        val sfExpectedVerifyData = borg.trikeshed.net.quic.crypto.hmacSha256(testHkdfService, sfFinishedKey, sfTranscriptHash)
        val mockServerFin = constructHandshakeMsgBytes(0x14u, sfExpectedVerifyData)
        assertTrue(connectionData.processServerHandshakeMessage(mockServerFin, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager), "Server Finished processing failed in setup")
        assertEquals(TlsHandshakeState.READY_TO_SEND_CLIENT_FINISHED, connectionData.tlsHandshakeState)
        val transcriptBeforeClientFinished = connectionData.handshakeTranscript.copyOf()

        // 2. Execute
        val clientFinishedBytes = connectionData.generateClientFinishedMessage(testHkdfService, manager)
        assertNotNull(clientFinishedBytes, "generateClientFinishedMessage returned null")

        // 3. Assert
        // Verify verify_data
        assertNotNull(connectionData.clientHandshakeTrafficSecretInternal, "Client handshake traffic secret must be set")
        val finishedKey = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(testHkdfService, connectionData.clientHandshakeTrafficSecretInternal!!, "finished", ByteArray(0), 32)
        val transcriptHash = borg.trikeshed.net.quic.crypto.sha256(transcriptBeforeClientFinished) // Transcript *before* client finished was added
        val expectedVerifyData = borg.trikeshed.net.quic.crypto.hmacSha256(testHkdfService, finishedKey, transcriptHash)

        // Deserialize clientFinishedBytes to check its verify_data
        // Finished message structure: type(1) + length(3) + verify_data(payloadLength)
        assertTrue(clientFinishedBytes.size >= 4 + 32, "Client Finished message too short")
        assertEquals(0x14.toByte(), clientFinishedBytes[0], "Client Finished message type incorrect")
        val cfPayloadLength = (clientFinishedBytes[1].toInt() and 0xFF shl 16) or (clientFinishedBytes[2].toInt() and 0xFF shl 8) or (clientFinishedBytes[3].toInt() and 0xFF)
        assertEquals(expectedVerifyData.size, cfPayloadLength, "Client Finished payload length incorrect")
        val actualVerifyData = clientFinishedBytes.sliceArray(4 until 4 + cfPayloadLength)
        assertContentEquals(expectedVerifyData, actualVerifyData, "Client Finished verify_data mismatch")

        assertEquals(TlsHandshakeState.CLIENT_FINISHED_SENT, connectionData.tlsHandshakeState, "State should be CLIENT_FINISHED_SENT")
        val expectedTranscriptAfterClientFinished = transcriptBeforeClientFinished + clientFinishedBytes
        assertContentEquals(expectedTranscriptAfterClientFinished, connectionData.handshakeTranscript, "Transcript mismatch after ClientFinished")
    }

    @Test
    fun testDeriveApplicationSecrets() = runBlocking {
        // 1. Setup: Bring connection to CLIENT_FINISHED_SENT state
        connectionData.deriveInitialSecrets(testHkdfService, manager)
        connectionData.initiateClientHandshake(testHkdfService, manager)
        val serverEphemeralKP = generateEcdhKeyPair(X25519_GROUP)
        val mockSH = constructTestServerHelloBytes(serverEphemeralKP.second)
        connectionData.processServerHandshakeMessage(mockSH, EncryptionLevel.INITIAL, testHkdfService, testAesService, manager)
        val mockEE = constructHandshakeMsgBytes(0x08u, byteArrayOf(0,0))
        connectionData.processServerHandshakeMessage(mockEE, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager)
        val certPayload = byteArrayOf(0x00) + byteArrayOf(0,0,5) + byteArrayOf(0,0,1, 0xFF.toByte()) + byteArrayOf(0,0)
        val mockCert = constructHandshakeMsgBytes(0x0Bu, certPayload)
        connectionData.processServerHandshakeMessage(mockCert, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager)
        val cvPayload = borg.trikeshed.net.quic.tls.TlsSignatureScheme.ECDSA_SECP256R1_SHA256.writeShort() + byteArrayOf(0,4) + byteArrayOf(1,2,3,4)
        val mockCV = constructHandshakeMsgBytes(0x0Fu, cvPayload)
        connectionData.processServerHandshakeMessage(mockCV, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager)
        assertNotNull(connectionData.serverHandshakeTrafficSecretInternal)
        val sfFinishedKey = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(testHkdfService, connectionData.serverHandshakeTrafficSecretInternal!!, "finished", ByteArray(0), 32)
        val sfTranscriptHash = borg.trikeshed.net.quic.crypto.sha256(connectionData.handshakeTranscript)
        val sfExpectedVerifyData = borg.trikeshed.net.quic.crypto.hmacSha256(testHkdfService, sfFinishedKey, sfTranscriptHash)
        val mockServerFin = constructHandshakeMsgBytes(0x14u, sfExpectedVerifyData)
        connectionData.processServerHandshakeMessage(mockServerFin, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager)
        val clientFinishedBytes = connectionData.generateClientFinishedMessage(testHkdfService, manager)
        assertNotNull(clientFinishedBytes)
        assertEquals(TlsHandshakeState.CLIENT_FINISHED_SENT, connectionData.tlsHandshakeState)
        val fullHandshakeTranscript = connectionData.handshakeTranscript.copyOf() // Transcript including Client Finished

        // 2. Execute
        connectionData.deriveApplicationSecrets(testHkdfService, manager)

        // 3. Assert
        assertEquals(TlsHandshakeState.HANDSHAKE_COMPLETE, connectionData.tlsHandshakeState)
        assertNotNull(connectionData.derivedHandshakeSecret, "derivedHandshakeSecret (salt for master) should not be null")

        val fullTranscriptHash = borg.trikeshed.net.quic.crypto.sha256(fullHandshakeTranscript)
        // TestHkdfService.extract returns salt + ikm (potentially truncated/padded)
        var expectedMasterSecretTemp = connectionData.derivedHandshakeSecret!! + ByteArray(32) // IKM is 32 zeros
        if (expectedMasterSecretTemp.size > 32) expectedMasterSecretTemp = expectedMasterSecretTemp.sliceArray(0..31)
        else if (expectedMasterSecretTemp.size < 32) expectedMasterSecretTemp += ByteArray(32 - expectedMasterSecretTemp.size)
        val expectedMasterSecret = expectedMasterSecretTemp


        val expectedClientAppTrafficSecretInfo = constructHkdfLabelForTest("c ap traffic", fullTranscriptHash, 32)
        var expectedClientAppTrafficSecretTemp = expectedMasterSecret + expectedClientAppTrafficSecretInfo
        val expectedClientAppTrafficSecret = ByteArray(32)
        for(i in 0 until 32) expectedClientAppTrafficSecret[i] = expectedClientAppTrafficSecretTemp.getOrElse(i % expectedClientAppTrafficSecretTemp.size) {0.toByte()}


        val clientAppSecretsFromManager = manager.getCurrentSecretsForSend(EncryptionLevel.ONERTT)
        assertNotNull(clientAppSecretsFromManager, "Client 1-RTT secrets not found in manager")

        val expectedClientAppKeyInfo = constructHkdfLabelForTest("quic key", byteArrayOf(), 16)
        var tempExpClientAppKey = expectedClientAppTrafficSecret + expectedClientAppKeyInfo
        val finalExpClientAppKey = ByteArray(16)
        for(i in 0 until 16) finalExpClientAppKey[i] = tempExpClientAppKey.getOrElse(i % tempExpClientAppKey.size) {0.toByte()}
        assertContentEquals(finalExpClientAppKey, clientAppSecretsFromManager.key, "Manager's client 1-RTT key mismatch")

        // Similarly for IV and HP key for client
        val expectedClientAppIvInfo = constructHkdfLabelForTest("quic iv", byteArrayOf(), 12)
        var tempExpClientAppIv = expectedClientAppTrafficSecret + expectedClientAppIvInfo
        val finalExpClientAppIv = ByteArray(12)
        for(i in 0 until 12) finalExpClientAppIv[i] = tempExpClientAppIv.getOrElse(i % tempExpClientAppIv.size) {0.toByte()}
        assertContentEquals(finalExpClientAppIv, clientAppSecretsFromManager.iv, "Manager's client 1-RTT IV mismatch")

        val expectedClientAppHpKeyInfo = constructHkdfLabelForTest("quic hp", byteArrayOf(), 16)
        var tempExpClientAppHpKey = expectedClientAppTrafficSecret + expectedClientAppHpKeyInfo
        val finalExpClientAppHpKey = ByteArray(16)
        for(i in 0 until 16) finalExpClientAppHpKey[i] = tempExpClientAppHpKey.getOrElse(i % tempExpClientAppHpKey.size) {0.toByte()}
        assertContentEquals(finalExpClientAppHpKey, clientAppSecretsFromManager.hpKey, "Manager's client 1-RTT HP key mismatch")

        // TODO: Could also verify server 1-RTT keys if they were stored distinctly in QuicConnection
        // val serverAppSecrets = connectionData.cryptoSecrets[EncryptionLevel.APPLICATION_1_RTT_SERVER_KEYS]
        // assertNotNull(serverAppSecrets)
        // ... verify serverAppSecrets.key, .iv, .hpKey based on expectedServerAppTrafficSecret
    }
}
