package evolution // Assuming services and QuicConnection are in or accessible from here

import borg.trikeshed.io.network.NetworkAddress
import borg.trikeshed.io.network.QuicNetworkService
import borg.trikeshed.io.network.QuicNetworkServiceKey
import borg.trikeshed.net.quic.* // For QuicConnection, QuicConnectionManager, etc.
import borg.trikeshed.net.quic.crypto.QuicCryptoUtils
import borg.trikeshed.net.quic.crypto.QuicSecrets // Assuming this is the type for secrets
import borg.trikeshed.net.tls.TlsServiceKey
import borg.trikeshed.net.tls.TlsService
import borg.trikeshed.lib.Series // Placeholder
import borg.trikeshed.lib.Join   // Placeholder
import borg.trikeshed.lib.toSeries // Placeholder for List<T>.toSeries()
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*

// Expect functions to get actual service implementations for the test platform
expect fun getActualQuicNetworkServiceForTest(coroutineContext: CoroutineContext): QuicNetworkService
expect fun getActualTlsServiceForTest(coroutineContext: CoroutineContext): TlsService
expect fun getActualHkdfServiceForTest(coroutineContext: CoroutineContext): HkdfService // Re-declare for clarity if needed
expect fun getActualAesServiceForTest(coroutineContext: CoroutineContext): AesService   // Re-declare for clarity if needed

// Helper to create a test CoroutineContext populated with actual services
@OptIn(ExperimentalCoroutinesApi::class) // For UnconfinedTestDispatcher if used, or other test dispatchers
fun createIntegrationTestContext(scope: CoroutineScope): CoroutineContext {
    val networkService = getActualQuicNetworkServiceForTest(scope.coroutineContext)
    val tlsService = getActualTlsServiceForTest(scope.coroutineContext)
    val hkdfService = getActualHkdfServiceForTest(scope.coroutineContext) // Use the test util
    val aesService = getActualAesServiceForTest(scope.coroutineContext)   // Use the test util

    return scope.coroutineContext +
            (QuicNetworkServiceKey to networkService) +
            (TlsServiceKey to tlsService) +
            (HkdfServiceKey to hkdfService) +
            (AesServiceKey to aesService) +
            CoroutineName("QuicHandshakeIntegrationTest")
}


class QuicHandshakeIntegrationTest {

    // Test against a known public QUIC server
    // Note: Public servers might have anti-abuse mechanisms or specific expectations.
    // This test might be flaky or require adjustments.
    private val targetHost = "google.com" // Popular choice, but can change behavior
    // private val targetHost = "quic.rocks" // Another test server
    // private val targetHost = "cloudflare.com"
    private val targetPort = 443

    @Test
    fun testPartialHandshakeWithPublicServer() = runTest(timeout = 30000L) { // Generous timeout for network ops
        val testSpecificContext = createIntegrationTestContext(this) // `this` is TestScope

        val networkService = testSpecificContext[QuicNetworkServiceKey]!!
        val cryptoUtils = QuicCryptoUtils(testSpecificContext) // Needs context with HKDF/AES

        val quicConnection = QuicConnection.newClientConnection()
        quicConnection.expectedServerName = targetHost // For TLS SNI and cert validation
        // quicConnection.trustedCaCertBytes = ... // TODO: Load platform CAs or skip validation for this test

        val connectionManager = QuicConnectionManager(quicConnection, QuicConnectionManager.ConnectionRole.CLIENT)

        // Prepare to collect data to be sent via CRYPTO frames
        // This is a var because Series is immutable. Each "add" creates a new Series.
        var cryptoDataToSendQueue: Series<Join<ByteArray, EncryptionLevel>> = Series.empty()

        val tlsHandler = QuicTlsHandler(
            parentCoroutineContext = testSpecificContext,
            connection = quicConnection,
            connectionManager = connectionManager,
            cryptoUtils = cryptoUtils,
            localQuicTransportParams = 테스트용_QUIC_TP_바이트_생성기(), // Placeholder - generate actual TP bytes
            onHandshakeCompleteCallback = { negotiatedAlpn ->
                println("Integration Test: Handshake complete! ALPN: $negotiatedAlpn")
                // Potentially set a flag or use a CompletableDeferred to signal completion
            },
            onHandshakeDataToSendCallback = { data, level ->
                println("Integration Test: Queuing ${data.size} bytes of CRYPTO data at level $level")
                cryptoDataToSendQueue = cryptoDataToSendQueue.append(Join(data, level))
            },
            onTlsAlertCallback = { alert, quicErrorCode ->
                fail("Integration Test: TLS Alert received/to send: desc=${alert.description}, QUIC error=0x${quicErrorCode.toString(16)}")
            }
        )

        var localBoundAddress: NetworkAddress? = null
        var handhakeCompletedSuccessfully = false
        var job: Job? = null

        try {
            localBoundAddress = networkService.bind(null) // Bind to any local port
            println("Integration Test: Bound to ${localBoundAddress!!.first}:${localBoundAddress.second}")

            quicConnection.deriveInitialSecrets(cryptoUtils, connectionManager)
            assertNotNull(connectionManager.getCurrentSecretsForSend(EncryptionLevel.INITIAL), "Initial secrets should be available")

            // Start TLS Handshake (will trigger onHandshakeDataToSend for ClientHello)
            tlsHandler.startClientHandshake(targetHost, listOf("h3", "h3-29").toSeries()) // Common ALPNs for QUIC/HTTP3

            // Send initial packets
            if (cryptoDataToSendQueue.isNotEmpty()) {
                val firstElement = cryptoDataToSendQueue.head()!! // Join<ByteArray, EncryptionLevel>
                val clientHelloData = firstElement.first
                val clientHelloLevel = firstElement.second
                cryptoDataToSendQueue = cryptoDataToSendQueue.tail()!! // Update queue

                val initialPacket = QuicPacketUtils.createClientInitialPacket(
                    destinationCid = quicConnection.clientId, // Server will use this as SCID initially
                    sourceCid = quicConnection.initialClientChosenDcId ?: quicConnection.clientId, // Client's chosen SCID for its first Initial
                    token = byteArrayOf(), // No token for first initial
                    packetNumber = connectionManager.getNextPacketNumberForEncryptionLevel(clientHelloLevel).toULong(),
                    payload = QuicFrame.CryptoFrame(0uL, clientHelloData).toByteArray() // Offset 0 for first crypto data
                )

                val protectedInitialPacketBytes = QuicPacketProcessor.serializeAndProtectPacket(
                    testSpecificContext, initialPacket, connectionManager, clientHelloLevel
                )
                assertNotNull(protectedInitialPacketBytes, "Failed to protect initial packet")

                val remoteAddress = networkService.resolve(targetHost, targetPort).firstOrNull()
                assertNotNull(remoteAddress, "Failed to resolve target host: $targetHost")

                println("Integration Test: Sending Initial packet (${protectedInitialPacketBytes.size} bytes) to ${remoteAddress.first}:${remoteAddress.second}")
                networkService.send(Join(protectedInitialPacketBytes, Join(remoteAddress, protectedInitialPacketBytes.size)))
                connectionManager.recordPacketSent(initialPacket.header.packetNumber, protectedInitialPacketBytes.size, listOf(QuicFrame.CryptoFrame(0uL, clientHelloData)), clientHelloLevel, true)
            } else {
                fail("ClientHello data was not generated by TlsHandler")
            }

            // Receive Loop (simplified for a few packets)
            job = launch(testSpecificContext) { // Launch in the test context
                for (i in 1..5) { // Try to receive a few packets
                    if (quicConnection.state == QuicConnectionStateEnum.HANDSHAKE_COMPLETED ||
                        quicConnection.state == QuicConnectionStateEnum.CONNECTED) {
                        handhakeCompletedSuccessfully = true
                        break
                    }

                    println("Integration Test: Attempting to receive packet ${i}...")
                    val receivedDatagram = try {
                        withTimeout(5000L) { networkService.receive(2048) }
                    } catch (e: TimeoutCancellationException) {
                        println("Integration Test: Receive timeout on attempt $i")
                        break // Exit loop on timeout
                    }

                    println("Integration Test: Received ${receivedDatagram.data.size} bytes from ${receivedDatagram.address.first}:${receivedDatagram.address.second}")

                    // Determine encryption level for unprotection (this is tricky without more state)
                    // Start with INITIAL, then HANDSHAKE after server hello.
                    val expectedDestCid = quicConnection.clientId // Client's SCID is server's DCID on its Initial/Handshake
                    val unprotectionLevel = if (connectionManager.getCurrentSecretsForReceive(EncryptionLevel.HANDSHAKE) != null && quicConnection.serverHandshakeSecretsForReception != null) {
                        EncryptionLevel.HANDSHAKE
                    } else {
                        EncryptionLevel.INITIAL
                    }

                    // Use serverInitialSecretsForReception for server's Initial, serverHandshakeSecretsForReception for server's Handshake
                    val secretsToUse = when(unprotectionLevel) {
                        EncryptionLevel.INITIAL -> quicConnection.serverInitialSecretsForReception
                        EncryptionLevel.HANDSHAKE -> quicConnection.serverHandshakeSecretsForReception
                        else -> null
                    }
                    if (secretsToUse == null) {
                         println("Integration Test: No secrets for unprotection level $unprotectionLevel, skipping packet.")
                         continue
                    }
                    // Temporarily use a modified QuicPacketProcessor that can take explicit secrets for decryption
                    // Or assume ConnectionManager's getCurrentSecretsForReceive is correctly set up by TlsHandler
                    // For this test, let's assume QuicPacketProcessor uses ConnectionManager,
                    // and ConnectionManager needs to be updated with server's send secrets (client's receive secrets)
                    // This part highlights complexity in managing separate send/receive keys per level in ConnectionManager.
                    // The TlsHandler updates QuicConnection's serverXXXSecretsForReception.
                    // We'd need a way for PacketProcessor to access these for decryption.

                    // Simplified: Assume PacketProcessor can somehow get the right receive keys based on level.
                    // This might mean QuicConnectionManager needs to store receive keys too, or PacketProcessor needs QuicConnection.

                    val unprotectedPacket = QuicPacketProcessor.deserializeAndUnprotectPacket(
                        testSpecificContext, // Contains AES/HKDF services
                        receivedDatagram.data,
                        connectionManager, // Needs to provide correct *receive* keys for the level
                        QuicPacketType.INITIAL, // This is an assumption, needs to be dynamic
                        expectedDestCid
                    )

                    if (unprotectedPacket == null) {
                        println("Integration Test: Failed to unprotect/deserialize packet from server.")
                        continue
                    }
                    println("Integration Test: Successfully unprotected packet from server. Type: ${unprotectedPacket.header.type}, PN: ${unprotectedPacket.header.packetNumber}")

                    // Process frames, especially CRYPTO frames
                    val frames = QuicFrameParser.parseFrames(unprotectedPacket.payload, unprotectedPacket.header.type == QuicPacketType.INITIAL || unprotectedPacket.header.type == QuicPacketType.HANDSHAKE)
                    for (frame in frames) {
                        if (frame is QuicFrame.CryptoFrame) {
                            println("Integration Test: Processing CRYPTO frame (offset ${frame.offset}, len ${frame.data.size}) at level $unprotectionLevel")
                            tlsHandler.processIncomingCryptoData(frame.data, unprotectionLevel)

                            // Send any new handshake data generated by TlsHandler
                            while(cryptoDataToSendQueue.isNotEmpty()) {
                                val elementToSend = cryptoDataToSendQueue.head()!!
                                val dataToSend = elementToSend.first
                                val sendLevel = elementToSend.second
                                cryptoDataToSendQueue = cryptoDataToSendQueue.tail()!! // Update queue

                                val nextPn = connectionManager.getNextPacketNumberForEncryptionLevel(sendLevel).toULong()
                                val packetToSend = QuicPacketUtils.createClientHandshakePacket( // Or Initial if level is Initial
                                    destinationCid = quicConnection.serverId ?: quicConnection.clientId, // Use server's CID if known
                                    sourceCid = quicConnection.clientId,
                                    packetNumber = nextPn,
                                    payload = QuicFrame.CryptoFrame(0uL, dataToSend).toByteArray() // TODO: Correct offset for CRYPTO data
                                )
                                val protectedBytes = QuicPacketProcessor.serializeAndProtectPacket(testSpecificContext, packetToSend, connectionManager, sendLevel)
                                assertNotNull(protectedBytes, "Failed to protect outgoing handshake packet")
                                println("Integration Test: Sending handshake packet (${protectedBytes.size} bytes) at level $sendLevel")
                                networkService.send(Join(protectedBytes, Join(remoteAddress!!, protectedBytes.size)))
                                connectionManager.recordPacketSent(nextPn, protectedBytes.size, listOf(QuicFrame.CryptoFrame(0uL,dataToSend)), sendLevel, true)
                            }
                        }
                        // Handle other frames like ACK, PING, CONNECTION_CLOSE etc.
                    }
                     if (quicConnection.state == QuicConnectionStateEnum.HANDSHAKE_COMPLETED ||
                        quicConnection.state == QuicConnectionStateEnum.CONNECTED) {
                        handhakeCompletedSuccessfully = true
                        break
                    }
                }
            }
            job.join() // Wait for receive loop to finish or test timeout

        } finally {
            job?.cancelAndJoin()
            networkService.close()
            tlsHandler.close() // Close the handler's scope
            println("Integration Test: Final QUIC Connection state: ${quicConnection.state}")
        }
        assertTrue(handhakeCompletedSuccessfully, "QUIC handshake did not complete successfully with $targetHost.")
    }

    // Placeholder for a function that generates valid QUIC Transport Parameter bytes
    // In a real scenario, this would use the structures from borg.trikeshed.net.quic.tls
    private fun 테스트용_QUIC_TP_바이트_생성기(): ByteArray {
        // Example: Create a QuicTransportParameters object and serialize it.
        // This is highly simplified. Real TPs are complex.
        val params = borg.trikeshed.net.quic.tls.QuicTransportParameters(
            listOf(
                borg.trikeshed.net.quic.tls.InitialMaxData(1024 * 1024),
                borg.trikeshed.net.quic.tls.MaxIdleTimeout(30000u)
                // Other params...
            )
        )
        return borg.trikeshed.net.quic.tls.serializeQuicTransportParameters(params)
    }
}

// --- Need expect/actual for test service DI in platform test source sets ---
// jvmTest/kotlin/evolution/ActualIntegrationTestUtils.jvm.kt
// actual fun getActualQuicNetworkServiceForTest(coroutineContext: CoroutineContext): QuicNetworkService = JvmQuicNetworkService
// actual fun getActualTlsServiceForTest(coroutineContext: CoroutineContext): TlsService = TODO("Provide actual JvmTlsService")
// ... and so on for HkdfService, AesService (can reuse from other test utils if in scope)

// nativeTest/kotlin/evolution/ActualIntegrationTestUtils.native.kt
// actual fun getActualQuicNetworkServiceForTest(coroutineContext: CoroutineContext): QuicNetworkService = PosixQuicNetworkService
// actual fun getActualTlsServiceForTest(coroutineContext: CoroutineContext): TlsService = TODO("Provide actual NativeTlsService")
// ...

// jsTest/kotlin/evolution/ActualIntegrationTestUtils.js.kt
// (JS typically won't run this server-style QUIC integration test with raw UDP)
// actual fun getActualQuicNetworkServiceForTest(coroutineContext: CoroutineContext): QuicNetworkService = TODO()
// actual fun getActualTlsServiceForTest(coroutineContext: CoroutineContext): TlsService = TODO()
// ...
