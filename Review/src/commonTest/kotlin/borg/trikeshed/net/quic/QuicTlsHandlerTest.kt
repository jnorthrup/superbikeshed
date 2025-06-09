package borg.trikeshed.net.quic

import borg.trikeshed.net.quic.crypto.QuicCryptoUtils
import borg.trikeshed.net.quic.crypto.QuicSecrets
import borg.trikeshed.net.tls.TlsAlert
import borg.trikeshed.net.tls.TlsConnection
import borg.trikeshed.net.tls.TlsEncryptionLevel
import borg.trikeshed.net.tls.TlsHandshakeCallbacks
import borg.trikeshed.net.tls.TlsService
import borg.trikeshed.net.tls.TlsServiceKey
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals


// --- Mock Implementations for Dependencies ---

class MockTlsService(private val testScheduler: TestCoroutineScheduler? = null) : TlsService {
    var handshakeStarted = false
    var providedHostname: String? = null
    var providedAlpn: List<String>? = null
    var providedQuicParams: ByteArray? = null
    var providedCallbacks: TlsHandshakeCallbacks? = null

    var mockTlsConnection: MockTlsConnection? = null

    override suspend fun startClientHandshake(
        hostname: String,
        alpnProtocols: List<String>,
        quicTransportParams: ByteArray,
        callbacks: TlsHandshakeCallbacks
    ): Result<TlsConnection> {
        handshakeStarted = true
        providedHostname = hostname
        providedAlpn = alpnProtocols
        providedQuicParams = quicTransportParams
        providedCallbacks = callbacks
        mockTlsConnection = MockTlsConnection(callbacks)
        return Result.success(mockTlsConnection!!)
    }
}

class MockTlsConnection(val callbacks: TlsHandshakeCallbacks) : TlsConnection {
    var processedData: ByteArray? = null
    var closed = false
    override fun processHandshakeData(data: ByteArray, level: TlsEncryptionLevel): Result<Unit> {
        processedData = data
        // Simulate some action, e.g., triggering a callback
        // callbacks.onHandshakeDataToSend("mock_response_data".encodeToByteArray(), TlsEncryptionLevel.HANDSHAKE)
        return Result.success(Unit)
    }
    override fun providePskTicket(ticket: ByteArray) {}
    override fun close() { closed = true }
}

class MockQuicConnectionManager(val connection: QuicConnection) : QuicConnectionManager(connection, ConnectionRole.CLIENT) {
    val updatedSecrets = mutableMapOf<EncryptionLevel, QuicSecrets>()
    var lastStateSet: QuicConnectionStateEnum? = null

    override fun updateSecrets(level: EncryptionLevel, secrets: QuicSecrets) {
        super.updateSecrets(level, secrets) // Call super if it has logic to preserve
        updatedSecrets[level] = secrets
    }
    override fun setState(newState: QuicConnectionStateEnum) {
        super.setState(newState)
        lastStateSet = newState
    }
}

// Minimal mock, actual crypto utils would use real services.
class MockQuicCryptoUtils : QuicCryptoUtils(EmptyCoroutineContext) {
    override suspend fun hkdfExpandLabel(secret: ByteArray, label: String, context: ByteArray, length: Int): ByteArray {
        return ByteArray(length) { label.firstOrNull()?.code?.toByte() ?: 0 } // Dummy implementation
    }
     override suspend fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        return ByteArray(32) { salt.firstOrNull() ?: ikm.firstOrNull() ?: 0 } // Dummy
    }
}


@OptIn(ExperimentalCoroutinesApi::class)
class QuicTlsHandlerTest {

    private fun createTestCoroutineContext(mockTlsService: TlsService): CoroutineContext {
        return EmptyCoroutineContext + CoroutineName("QuicTlsHandlerTest") + (TlsServiceKey to mockTlsService)
    }

    @Test
    fun testStartClientHandshake_invokesTlsService() = runTest {
        val mockTlsService = MockTlsService(this.testScheduler)
        val testContext = createTestCoroutineContext(mockTlsService)

        val quicConnection = QuicConnection.newClientConnection()
        val mockConnManager = MockQuicConnectionManager(quicConnection)
        val mockCryptoUtils = MockQuicCryptoUtils()
        val dummyTransportParams = "quic_tp_payload".encodeToByteArray()

        val handler = QuicTlsHandler(
            parentCoroutineContext = testContext,
            connection = quicConnection,
            connectionManager = mockConnManager,
            cryptoUtils = mockCryptoUtils,
            localQuicTransportParams = dummyTransportParams,
            onHandshakeCompleteCallback = {},
            onHandshakeDataToSendCallback = { _, _ -> },
            onTlsAlertCallback = { _, _ -> }
        )

        handler.startClientHandshake("example.com", listOf("h3"))

        assertTrue(mockTlsService.handshakeStarted, "TlsService.startClientHandshake should have been called")
        assertEquals("example.com", mockTlsService.providedHostname)
        assertContentEquals(dummyTransportParams, mockTlsService.providedQuicParams)
        assertNotNull(mockTlsService.providedCallbacks)

        handler.close() // Clean up handler's scope
    }

    @Test
    fun testProcessIncomingCryptoData_passesToTlsConnection() = runTest {
        val mockTlsService = MockTlsService(this.testScheduler)
        val testContext = createTestCoroutineContext(mockTlsService)

        val quicConnection = QuicConnection.newClientConnection()
        val mockConnManager = MockQuicConnectionManager(quicConnection)
        val mockCryptoUtils = MockQuicCryptoUtils()
        val dummyData = "crypto_data".encodeToByteArray()

        val handler = QuicTlsHandler(
            parentCoroutineContext = testContext,
            connection = quicConnection,
            connectionManager = mockConnManager,
            cryptoUtils = mockCryptoUtils,
            localQuicTransportParams = ByteArray(0),
            onHandshakeCompleteCallback = {},
            onHandshakeDataToSendCallback = { _, _ -> },
            onTlsAlertCallback = { _, _ -> }
        )
        // First, start the handshake to get a mockTlsConnection instance
        handler.startClientHandshake("example.com", listOf("h3"))
        val mockTlsConnection = mockTlsService.mockTlsConnection
        assertNotNull(mockTlsConnection, "MockTlsConnection should be initialized")

        handler.processIncomingCryptoData(dummyData, EncryptionLevel.HANDSHAKE)

        // Advance dispatcher to allow launch block in processIncomingCryptoData to run
        this.testScheduler.advanceUntilIdle()

        assertContentEquals(dummyData, mockTlsConnection.processedData, "Data should be passed to TlsConnection.processHandshakeData")

        handler.close()
    }

    @Test
    fun testOnNewEncryptionSecretsReady_updatesConnectionManager() = runTest {
        val mockTlsService = MockTlsService(this.testScheduler) // Not directly used by this callback test path
        val testContext = createTestCoroutineContext(mockTlsService)

        val quicConnection = QuicConnection.newClientConnection()
        val mockConnManager = MockQuicConnectionManager(quicConnection)
        val mockCryptoUtils = MockQuicCryptoUtils() // Using mock for derivation

        val handler = QuicTlsHandler(
            parentCoroutineContext = testContext,
            connection = quicConnection,
            connectionManager = mockConnManager,
            cryptoUtils = mockCryptoUtils,
            localQuicTransportParams = ByteArray(0),
            onHandshakeCompleteCallback = {},
            onHandshakeDataToSendCallback = { _, _ -> },
            onTlsAlertCallback = { _, _ -> }
        )

        val readSecret = "read_secret".encodeToByteArray()
        val writeSecret = "write_secret".encodeToByteArray()
        val cipherSuite = 0x1301 // TLS_AES_128_GCM_SHA256

        // Simulate callback from TlsService
        handler.onNewEncryptionSecretsReady(TlsEncryptionLevel.HANDSHAKE, readSecret, writeSecret, cipherSuite)

        assertNotNull(mockConnManager.updatedSecrets[EncryptionLevel.HANDSHAKE], "Handshake secrets should be updated in ConnectionManager")
        // We can also check if quicConnection's internal base secrets are updated
        assertContentEquals(writeSecret, quicConnection.clientHandshakeTrafficSecretInternal)
        assertContentEquals(readSecret, quicConnection.serverHandshakeTrafficSecretInternal)

        handler.close()
    }
}
