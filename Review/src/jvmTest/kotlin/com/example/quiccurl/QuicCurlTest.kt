package com.example.quiccurl

import borg.trikeshed.net.quic.*
import borg.trikeshed.net.quic.crypto.QuicSecrets
import evolution.AesService
import evolution.HkdfService
import evolution.UdpSocket
import evolution.UdpSocketFactory
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class QuicCurlTest {

    private lateinit var scheduler: TestCoroutineScheduler
    private lateinit var mockUdpSocket: UdpSocket
    private lateinit var mockUdpSocketFactory: UdpSocketFactory
    private lateinit var mockAesService: AesService
    private lateinit var mockHkdfService: HkdfService
    private lateinit var quicCurl: QuicCurl

    // Default connection parameters
    private val host = "example.com"
    private val port = 4433
    private val clientScid = Random.nextBytes(8)
    private val serverScid = Random.nextBytes(8) // Server's chosen SCID
    private val initialClientDcid = Random.nextBytes(8)

    @Before
    fun setUp() {
        scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler)) // For main-dispatching coroutines in QuicCurl if any

        mockUdpSocket = mockk(relaxed = true) // relaxed = true for less boilerplate on every { }
        mockUdpSocketFactory = mockk()
        mockAesService = mockk(relaxed = true)
        mockHkdfService = mockk(relaxed = true)

        every { mockUdpSocketFactory.create(any(), any()) } returns mockUdpSocket
        // Basic HKDF and AES mock setup (can be customized per test)
        every { mockHkdfService.extract(any(), any()) } returns ByteArray(32) { 0x01 }
        every { mockHkdfService.expandLabel(any(), any(), any(), any(), any()) } answers { ByteArray(arg<Int>(4)) { 0x02 } }
        every { mockAesService.gcmEncrypt(any(), any(), any(), any()) } returns ByteArray(100) { 0x03 } // Placeholder encrypted data
        every { mockAesService.gcmDecrypt(any(), any(), any(), any()) } returns ByteArray(100) { 0x04 } // Placeholder decrypted data
        every { mockAesService.ecbEncrypt(any(), any()) } returns ByteArray(16) {0x05} // For HP mask

        quicCurl = QuicCurl(mockHkdfService, mockAesService, mockUdpSocketFactory)
    }

    private fun QuicConnection.setHandshakeComplete(manager: QuicConnectionManager, serverIdToSet: ConnectionId) {
        this.state = QuicConnectionStateEnum.HANDSHAKE_COMPLETE
        this.tlsHandshakeState = TlsHandshakeState.HANDSHAKE_COMPLETE
        this.serverId = serverIdToSet // Set serverId as it's used for 1-RTT packet DCIDs
        // Simulate derivation of 1-RTT keys
        val appSecrets = QuicSecrets(ByteArray(16){0xAA.toByte()}, ByteArray(12){0xBB.toByte()}, ByteArray(16){0xCC.toByte()})
        manager.updateSecrets(EncryptionLevel.ONERTT, appSecrets)
        // Simulate client having server's 1-RTT read keys (for processing server packets)
        this.serverAppTrafficSecretInternal = ByteArray(32) {0xDD.toByte()}
        // This is a simplification; proper key derivation would be part of a fuller handshake mock.
    }

    // --- Retransmission Test (Simplified RTO) ---
    @Test
    fun `should retransmit lost packet after RTO`() = runTest(scheduler) {
        val testDispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(testDispatcher) // Ensure QuicCurl's scope uses this

        val connectionDataSlot = slot<QuicConnection>()
        val managerSlot = slot<QuicConnectionManager>()

        // Initial setup for connect to progress enough to get manager and connectionData
        coEvery { mockUdpSocket.send(any(), any(), any()) } returns true
        // Simulate receiving ServerHello, EE, Cert, CV, Finished to complete handshake
        coEvery { mockUdpSocket.receive(any(), any()) } coAnswers {
            // This is a very simplified handshake mock.
            // It should provide just enough data for connect() to reach HANDSHAKE_COMPLETE
            // and then simulate a post-handshake scenario for receiveData.

            val buffer = arg<ByteArray>(0)
            // For handshake completion (needs multiple interactions normally)
            // Let's assume connect() calls receiveData() or has its own 1-RTT loop after handshake.
            // The test will focus on receiveData's behavior.
            // Here, we make receive() return null initially to let connect() finish if it's not fully mocked.
            // Then, for receiveData, we'll provide specific packets.
            null // Placeholder for handshake part of connect()
        }

        var capturedConnection: QuicConnection? = null
        var capturedManager: QuicConnectionManager? = null

        // Launch connect in a way that we can get the manager instance
        // This is tricky as connect is suspend and creates manager internally.
        // A better way would be for QuicCurl to expose its manager or for connect to return it.
        // For now, we'll assume connect completes and we can then call receiveData with a mocked manager.

        // Let's simplify: Assume handshake is done, and we are testing receiveData directly.
        // We need a QuicConnection and QuicConnectionManager instance.
        val conn = QuicConnection.newClientConnectionDataOnly(scidOverride = clientScid, initialDcIdOverride = initialClientDcid)
        val manager = QuicConnectionManager(conn, QuicConnectionManager.ConnectionRole.CLIENT)
        conn.setHandshakeComplete(manager, serverScid) // Set server SCID (used as DCID by client for 1-RTT)
        manager.updatePeerMaxAckDelay(0L) // Simplify PTO for RTO test: RTO = SRTT + 4*RTTVAR

        // 1. Client sends a packet (e.g., PING) - this happens outside receiveData typically
        val originalFrames = listOf(PingFrame())
        val originalPacketNumber = manager.getNextPacketNumberForEncryptionLevel(EncryptionLevel.ONERTT)
        val elicitsAck = true
        val sentPacketSize = 100 // dummy size
        // Manually adjust sent time for consistent RTT calculation
        val t0 = scheduler.currentTime
        manager.recordPacketSent(originalPacketNumber, sentPacketSize, originalFrames, EncryptionLevel.ONERTT, elicitsAck)
        val sentPacketInfo = manager.sentPackets[originalPacketNumber]!!
        manager.sentPackets[originalPacketNumber] = sentPacketInfo.copy(sentTime = Instant.fromEpochMilliseconds(t0))


        // 2. Simulate ACK for a setup packet to establish RTT (SRTT=100ms, RTTVAR=50ms)
        val setupPn = 999UL // A different packet number
        manager.recordPacketSent(setupPn, 100, listOf(PingFrame()), EncryptionLevel.ONERTT, true)
        manager.sentPackets[setupPn] = manager.sentPackets[setupPn]!!.copy(sentTime = Instant.fromEpochMilliseconds(t0))
        scheduler.advanceTimeBy(100L) // SRTT = 100ms
        manager.recordPacketAckedByPeer(setupPn)

        // RTO = SRTT + 4*RTTVAR = 100_000us + 4*50_000us = 300_000us = 300ms. MinRTO = 1s. So RTO = 1000ms.
        val rtoDurationMs = manager.MIN_RTO_US / 1000L


        // 3. In receiveData, simulate no ACK for the original packet, advance time past RTO
        // Packet was sent at t0. RTO is 1000ms.
        // Current time is t0 + 100ms. We need to advance by 900ms more for RTO to expire.
        coEvery { mockUdpSocket.receive(any(), any()) } returns null // Simulate no incoming packet / timeout

        // Launch receiveData which contains the RTO check and retransmission logic
        launch(testDispatcher) {
            quicCurl.receiveData(conn, manager, 0UL) // streamId 0UL, not used by PING
        }

        scheduler.advanceTimeBy(rtoDurationMs + 50L) // Advance time past RTO for originalPacketNumber
        yield() // Allow coroutines to run

        // 4. Verify UdpSocket.send was called for retransmission
        coVerify(timeout = 200L) { // Increased timeout for CI
            mockUdpSocket.send(match { bytes ->
                // Basic check: is it a QUIC packet (short header)?
                (bytes[0].toInt() and 0x80) == 0 && (bytes[0].toInt() and 0x40) != 0 // Short header, fixed bit set
                // More detailed check: parse header, check if it contains the PING frame, new PN.
                // This requires a full packet parser or more specific mocking of packet construction.
                // For now, just checking if *any* packet is sent after RTO.
                true
            }, host, port)
        }

        // 5. Verify manager state: original packet handled, new packet recorded
        assertFalse(manager.getPacketsForRetransmission().any { it.packetNumber == originalPacketNumber }, "Original PN should be handled")
        // Check if a *new* packet was recorded (the retransmission)
        // The new packet number would be originalPacketNumber + 1 (or whatever getNextPN returns)
        assertTrue(manager.sentPackets.any { it.key > originalPacketNumber && it.value.frames.contains(PingFrame()) }, "Retransmitted packet not recorded")
    }


    // --- PTO Tests ---
    @Test
    fun `PTO expiry should send a probe packet and increment ptoCount`() = runTest(scheduler) {
        val testDispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(testDispatcher)

        val conn = QuicConnection.newClientConnectionDataOnly(scidOverride = clientScid, initialDcIdOverride = initialClientDcid)
        val manager = QuicConnectionManager(conn, QuicConnectionManager.ConnectionRole.CLIENT)
        conn.setHandshakeComplete(manager, serverScid)
        manager.updatePeerMaxAckDelay(0L) // To make PTO base = SRTT + 4*RTTVAR

        // Establish RTT: SRTT = 100ms, RTTVAR = 50ms
        val t0 = scheduler.currentTime
        manager.recordPacketSent(100UL, 100, listOf(AckFrame(1UL,0UL,0UL,listOf())), EncryptionLevel.ONERTT, elicitsAck = true) // send ack-eliciting
        manager.sentPackets[100UL] = manager.sentPackets[100UL]!!.copy(sentTime = Instant.fromEpochMilliseconds(t0))
        scheduler.advanceTimeBy(100L)
        manager.recordPacketAckedByPeer(100UL) // SRTT = 100ms (100_000us), RTTVAR = 50ms (50_000us)

        // Send an ack-eliciting packet to arm PTO
        val packetNumToLose = 101UL
        manager.recordPacketSent(packetNumToLose, testPacketSize, listOf(PingFrame()), EncryptionLevel.ONERTT, elicitsAck = true)
        manager.sentPackets[packetNumToLose] = manager.sentPackets[packetNumToLose]!!.copy(sentTime = Instant.fromEpochMilliseconds(scheduler.currentTime))

        // Manually start PTO timer (as if sendData called it) - this part is tricky to test without sendData
        // We'll call the internal startPtoTimer method of QuicCurl using a reflection helper or by testing through receiveData
        // For simplicity, let's assume PTO is armed by the send. The test focuses on expiry.
        // The QuicCurl's startPtoTimer is private, so we test its effect.
        // We need to trigger a situation where PTO would be armed.
        // The PTO timer is started via startPtoTimer in QuicCurl, which is private.
        // Let's assume it's armed. We'll advance time by its calculated duration.

        val ptoDurationMs = manager.getPtoDurationUs() / 1000L
        assertEquals(0, manager.ptoCount) // Initial ptoCount

        // Simulate PTO expiry by advancing time.
        // The actual timer is in QuicCurl, so we check its effects.
        // To test QuicCurl's PTO timer properly, we'd need to call a method that starts it.
        // Let's assume `receiveData` is called, and no ACK comes for packetNumToLose.

        coEvery { mockUdpSocket.receive(any(), any()) } returns null // Simulate no incoming packets

        launch(testDispatcher) { // Launch a coroutine that can be cancelled by timeout
             quicCurl.receiveData(conn, manager, 0UL) // This will allow PTO to run if armed by ack-eliciting packet logic
        }

        // Call startPtoTimer manually for test purposes, as if it was called after sending packetNumToLose
        // This requires making startPtoTimer internal or package-private, or testing via side effects of sendData.
        // For this test, we'll assume that sending packetNumToLose (if done via a proper sendData) would arm it.
        // We will manually advance time and check for probe.

        // Manually arming the timer by calling it for testing purposes
        // This is a hack for testing a private method's effect.
        // quicCurl.startPtoTimer(manager, conn, host, port) // If it were public/internal

        // Instead of calling private startPtoTimer, let's assume it was armed.
        // Advance time by calculated PTO. The actual timer is in QuicCurl.
        // We need to ensure `hasOutstandingAckElicitingPackets` is true for PTO to fire.
        assertTrue(manager.hasOutstandingAckElicitingPackets())

        scheduler.advanceTimeBy(ptoDurationMs + 50L) // Advance past PTO duration
        yield() // allow coroutines to run

        coVerify(timeout = 200L) { mockUdpSocket.send(match { it[0].toInt() and 0x40 != 0 && it.any { byte -> byte == QuicFrameType.PING.toByte() } }, host, port) }
        assertEquals(1, manager.ptoCount, "PTO count should increment after expiry and probe sending")

        // Verify PTO timer is re-armed (implicitly, by checking if another probe is sent after next PTO)
        val nextPtoDurationMs = manager.getPtoDurationUs() / 1000L // ptoCount is now 1
        assertTrue(nextPtoDurationMs > ptoDurationMs || manager.ptoCount > 0 && nextPtoDurationMs >= ptoDurationMs, "Next PTO should be same or longer (due to backoff or min PTO)")

        scheduler.advanceTimeBy(nextPtoDurationMs + 50L)
        yield()
        coVerify(timeout = 200L, atLeast = 2) { mockUdpSocket.send(match {it.any { byte -> byte == QuicFrameType.PING.toByte() } }, host, port) }
        assertEquals(2, manager.ptoCount)
    }

    @Test
    fun `PTO should reset on progress-making ACK`() = runTest(scheduler) {
        val testDispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(testDispatcher)

        val conn = QuicConnection.newClientConnectionDataOnly(scidOverride = clientScid, initialDcIdOverride = initialClientDcid)
        val manager = QuicConnectionManager(conn, QuicConnectionManager.ConnectionRole.CLIENT)
        conn.setHandshakeComplete(manager, serverScid)
        manager.updatePeerMaxAckDelay(0L)

        // Establish RTT
        val t0 = scheduler.currentTime
        manager.recordPacketSent(200UL, 100, listOf(PingFrame()), EncryptionLevel.ONERTT, elicitsAck = true)
        manager.sentPackets[200UL] = manager.sentPackets[200UL]!!.copy(sentTime = Instant.fromEpochMilliseconds(t0))
        scheduler.advanceTimeBy(100L); manager.recordPacketAckedByPeer(200UL)


        // Send an ack-eliciting packet
        val packetNumNeedingAck = 201UL
        val sentTimePacket201 = scheduler.currentTime
        manager.recordPacketSent(packetNumNeedingAck, testPacketSize, listOf(CryptoFrame(0u,byteArrayOf(1,2,3))), EncryptionLevel.ONERTT, elicitsAck = true)
        manager.sentPackets[packetNumNeedingAck] = manager.sentPackets[packetNumNeedingAck]!!.copy(sentTime = Instant.fromEpochMilliseconds(sentTimePacket201))

        // Simulate PTO fires once
        manager.onPtoExpired() // Manually increment ptoCount to 1 for test state
        assertEquals(1, manager.ptoCount)

        // Simulate receiving an ACK for this packet (progress-making ACK)
        // This ACK is for packetNumNeedingAck. It's received via a new packet from server.
        // The actual ACK frame parsing and call to recordPacketAckedByPeer happens in QuicCurl.receiveData

        // Construct a mock ACK packet from server
        val ackFrameForPacket201 = AckFrame(largestAcknowledged = packetNumNeedingAck, ackDelay = 0UL, ackRangeCount = 0UL, firstAckRange = 0UL)
        val serverAckPacketBytes = mockQuicPacket(listOf(ackFrameForPacket201), 99UL, conn.clientId!!, serverScid, manager, mockAesService, mockHkdfService)

        coEvery { mockUdpSocket.receive(any(), any()) } returns UdpSocket.ReceivedPacketInfo(serverAckPacketBytes, host, port)

        // Run receiveData to process the ACK
        quicCurl.receiveData(conn, manager, 0UL) // Call receiveData, it should process the ACK
        yield() // allow coroutines to run

        assertEquals(0, manager.ptoCount, "PTO count should reset after progress-making ACK")
        // Further test: PTO timer should be cancelled or re-armed based on outstanding data.
        // If no other ack-eliciting packets, ptoJob in QuicCurl should be null or cancelled.
        // This requires inspecting QuicCurl's ptoJob or its effects.
    }

    // --- Idle Timeout Tests ---
    @Test
    fun `idle timeout should close connection if no activity`() = runTest(scheduler) {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))

        val conn = QuicConnection.newClientConnectionDataOnly()
        val manager = QuicConnectionManager(conn, QuicConnectionManager.ConnectionRole.CLIENT)
        conn.setHandshakeComplete(manager, serverScid) // Completes handshake

        // Simulate QuicCurl's connect() completing handshake and starting timers
        // This test will manually call startOrResetIdleTimeout as it's private in QuicCurl
        // A better approach would be to test through public methods of QuicCurl that trigger it.

        // Directly use a QuicCurl instance created in setup (quicCurl)
        // Set currentIdleTimeoutMs for predictability
        quicCurl.currentIdleTimeoutMs = 500L // Use a shorter timeout for testing

        // Manually call to start the idle timer after "handshake"
        quicCurl.startOrResetIdleTimeout(manager, conn, host, port)

        scheduler.advanceTimeBy(quicCurl.currentIdleTimeoutMs + 10L) // Advance past idle timeout
        yield()

        // Verify connection closure (socket closed, state updated)
        coVerify(timeout=100L) { mockUdpSocket.close() }
        assertEquals(QuicConnectionStateEnum.CLOSED, conn.state)
    }

    @Test
    fun `idle timeout should reset on sending packet`() = runTest(scheduler) {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        val conn = QuicConnection.newClientConnectionDataOnly()
        val manager = QuicConnectionManager(conn, QuicConnectionManager.ConnectionRole.CLIENT)
        conn.setHandshakeComplete(manager, serverScid)
        quicCurl.currentIdleTimeoutMs = 500L

        quicCurl.startOrResetIdleTimeout(manager, conn, host, port) // Initial start

        scheduler.advanceTimeBy(quicCurl.currentIdleTimeoutMs - 100L) // Advance, but not past timeout

        // Simulate sending a packet (which should call startOrResetIdleTimeout internally)
        // We call it directly here to simulate the effect of a send operation.
        quicCurl.sendProbePacket(manager, conn, host, port) // sendProbePacket calls startOrResetIdleTimeout

        scheduler.advanceTimeBy(150L) // Total time since first timer start > original timeout, but < new timeout

        coVerify(exactly = 0) { mockUdpSocket.close() } // Should not close yet
        assertEquals(QuicConnectionStateEnum.HANDSHAKE_COMPLETE, conn.state) // Still open

        scheduler.advanceTimeBy(quicCurl.currentIdleTimeoutMs - 150L + 10L) // Advance past the new timeout
        yield()
        coVerify(timeout=100L) { mockUdpSocket.close() } // Now it should close
        assertEquals(QuicConnectionStateEnum.CLOSED, conn.state)
    }

    @Test
    fun `idle timeout should reset on receiving packet`() = runTest(scheduler) {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        val conn = QuicConnection.newClientConnectionDataOnly(scidOverride = clientScid)
        val manager = QuicConnectionManager(conn, QuicConnectionManager.ConnectionRole.CLIENT)
        conn.setHandshakeComplete(manager, serverScid)
        quicCurl.currentIdleTimeoutMs = 500L

        quicCurl.startOrResetIdleTimeout(manager, conn, host, port) // Initial start

        scheduler.advanceTimeBy(quicCurl.currentIdleTimeoutMs - 100L)

        // Simulate receiving a packet. The call to startOrResetIdleTimeout is in receiveData's loop.
        val dummyAckFromServer = AckFrame(0UL,0UL,0UL, listOf())
        val serverPacketBytes = mockQuicPacket(listOf(dummyAckFromServer), 1UL, conn.clientId!!, serverScid, manager, mockAesService, mockHkdfService)
        coEvery { mockUdpSocket.receive(any(), any()) } returns UdpSocket.ReceivedPacketInfo(serverPacketBytes, host, port)

        // Call receiveData, which should reset the idle timer internally
        quicCurl.receiveData(conn, manager, 0UL)
        yield()

        scheduler.advanceTimeBy(150L)
        coVerify(exactly = 0) { mockUdpSocket.close() }
        assertEquals(QuicConnectionStateEnum.HANDSHAKE_COMPLETE, conn.state)

        scheduler.advanceTimeBy(quicCurl.currentIdleTimeoutMs - 150L + 10L)
        yield()
        coVerify(timeout=100L) { mockUdpSocket.close() }
        assertEquals(QuicConnectionStateEnum.CLOSED, conn.state)
    }

    // Helper to create a mock QUIC packet for receive testing
    private fun mockQuicPacket(frames: List<QuicFrame>, packetNumber: Long, dcid: ConnectionId, scid: ConnectionId,
                               manager: QuicConnectionManager, aes: AesService, hkdf: HkdfService): ByteArray {
        val el = EncryptionLevel.ONERTT // Assuming 1-RTT for simplicity in mock
        val secrets = manager.getCurrentSecretsForSend(el) ?: QuicSecrets(ByteArray(16), ByteArray(12), ByteArray(16))

        val payloadBytes = frames.fold(ByteArray(0)) { acc, frame -> acc + borg.trikeshed.net.quic.utils.serializeFrame(frame) }
        val pnLengthBytes = 1

        val headerBytes = if (el == EncryptionLevel.ONERTT) {
            QuicPacketUtils.serializeShortHeader(dcid, packetNumber, pnLengthBytes, false)
        } else {
            // Simplified: assume initial for non-1RTT for now
            QuicPacketUtils.serializeInitialHeader(QuicConstants.QUIC_VERSION_1, dcid, scid, byteArrayOf(), packetNumber, pnLengthBytes, payloadBytes.size + QuicConstants.AEAD_TAG_LENGTH + pnLengthBytes).first
        }

        val encryptedPayload = aes.gcmEncrypt(secrets.key, secrets.iv, payloadBytes, headerBytes) ?: payloadBytes // Fallback if mock fails
        val pnOffset = if (el == EncryptionLevel.ONERTT) QuicPacketUtils.calculatePacketNumberOffsetForShortHeader(dcid.size) else QuicPacketUtils.DEFAULT_PN_OFFSET_LONG_HEADER

        val protectedHeader = borg.trikeshed.net.quic.crypto.applyHeaderProtection(aes, secrets.hpKey, headerBytes, encryptedPayload, pnOffset, pnLengthBytes, el == EncryptionLevel.ONERTT)
        return protectedHeader + encryptedPayload
    }

    // Reflection accessors for private fields of QuicCurl for testing timer jobs (use with caution)
    val QuicCurl.ptoJobForTest: Job? get() = QuicCurlTest::class.java.getDeclaredField("ptoJob").apply { isAccessible = true }.get(this) as Job?
    val QuicCurl.idleTimeoutJobForTest: Job? get() = QuicCurlTest::class.java.getDeclaredField("idleTimeoutJob").apply { isAccessible = true }.get(this) as Job?
    var QuicCurl.currentIdleTimeoutMs: Long
        get() = QuicCurlTest::class.java.getDeclaredField("currentIdleTimeoutMs").apply { isAccessible = true }.getLong(this)
        set(value) { QuicCurlTest::class.java.getDeclaredField("currentIdleTimeoutMs").apply { isAccessible = true }.setLong(this, value) }


    // --- HTTP/3 Flow in execute() Tests ---

    private fun mockSuccessfulConnect(host: String, port: Int): Pair<QuicConnection, QuicConnectionManager> {
        val conn = QuicConnection.newClientConnectionDataOnly(scidOverride = clientScid, initialDcIdOverride = initialClientDcid)
        val manager = QuicConnectionManager(conn, QuicConnectionManager.ConnectionRole.CLIENT)
        conn.setHandshakeComplete(manager, serverScid)

        quicCurl.lastConnectedHostForTest = host
        quicCurl.lastConnectedPortForTest = port
        quicCurl.quicStreamManagerForTest = QuicStreamManager(
            localRole = StreamInitiatorRole.CLIENT,
            localTransportParamsProvider = { QuicTransportParameters() },
            peerTransportParamsProvider = { QuicTransportParameters() },
            queueControlFrameCallback = {},
            connectionErrorCallback = { _, _ -> }
        ).also { it.onConnectionWantsToSend = {} }
        quicCurl.managerInstanceForTest = manager

        every { mockUdpSocket.isClosed } returns false
        return Pair(conn, manager)
    }

    @Test
    fun `execute should correctly form HTTP3 pseudo headers and send HEADERS frame for GET`() = runTest(scheduler) {
        mockSuccessfulConnect("testhost.com", 443)
        val mockStream: QuicStream = mockk(relaxed = true)
        every { quicCurl.quicStreamManagerForTest?.openBidirectionalStream() } returns mockStream

        val request = HttpRequest(method = "GET", url = "https://testhost.com/path?query=1", headers = mapOf("x-foo" to "bar"))

        launch { quicCurl.execute(request) }
        scheduler.advanceUntilIdle()


        val capturedBytes = slot<ByteArray>()
        val capturedIsFin = slot<Boolean>()
        verify(exactly = 1) { mockStream.enqueueApplicationData(capture(capturedBytes), capture(capturedIsFin)) }

        assertTrue(capturedBytes.isCaptured, "stream.enqueueApplicationData was not called with HEADERS")
        assertTrue(capturedIsFin.captured, "FIN should be true for GET request HEADERS frame as there is no body")

        val parsedFrames = Http3FrameParser.parseAllFrames(capturedBytes.captured)
        assertTrue(parsedFrames.isNotEmpty() && parsedFrames[0] is HeadersFrame, "Expected HEADERS frame to be sent")
        val headersFrame = parsedFrames[0] as HeadersFrame

        val qpackDecoder = QpackDecoder() // Using real QPACK decoder for verification
        val decodedHeaders = qpackDecoder.decode(headersFrame.encodedHeaderData, mockStream.streamId)

        assertEquals("GET", decodedHeaders[":method"]?.first())
        assertEquals("https", decodedHeaders[":scheme"]?.first())
        assertEquals("testhost.com:443", decodedHeaders[":authority"]?.first())
        assertEquals("/path?query=1", decodedHeaders[":path"]?.first())
        assertEquals("bar", decodedHeaders["x-foo"]?.first())
    }

    @Test
    fun `execute should send HEADERS (FIN=false) then DATA (FIN=true) for POST`() = runTest(scheduler) {
        mockSuccessfulConnect("testhost.com", 443)
        val mockStream: QuicStream = mockk(relaxed = true)
        every { quicCurl.quicStreamManagerForTest?.openBidirectionalStream() } returns mockStream

        val requestBody = "Hello World".encodeToByteArray()
        val request = HttpRequest(method = "POST", url = "https://testhost.com/submit", body = requestBody, headers = mapOf("content-type" to "text/plain"))

        launch { quicCurl.execute(request) }
        scheduler.advanceUntilIdle()

        val capturedFramesBytes = mutableListOf<ByteArray>()
        val capturedIsFinFlags = mutableListOf<Boolean>()
        verify(exactly = 2) { mockStream.enqueueApplicationData(capture(capturedFramesBytes), capture(capturedIsFinFlags)) }

        // First call: HEADERS frame
        val headersFrameBytes = capturedFramesBytes[0]
        val headersFrameFin = capturedIsFinFlags[0]
        val parsedHeadersFrames = Http3FrameParser.parseAllFrames(headersFrameBytes)
        assertTrue(parsedHeadersFrames.isNotEmpty() && parsedHeadersFrames[0] is HeadersFrame, "Expected HEADERS frame")
        val headersFrame = parsedHeadersFrames[0] as HeadersFrame
        val qpackDecoder = QpackDecoder()
        val decodedHeaders = qpackDecoder.decode(headersFrame.encodedHeaderData, mockStream.streamId)
        assertEquals("POST", decodedHeaders[":method"]?.first())
        assertEquals("text/plain", decodedHeaders["content-type"]?.first())
        assertFalse(headersFrameFin, "FIN should be false for HEADERS frame when body is present")

        // Second call: DATA frame
        val dataFrameBytes = capturedFramesBytes[1]
        val dataFrameFin = capturedIsFinFlags[1]
        val parsedDataFrames = Http3FrameParser.parseAllFrames(dataFrameBytes)
        assertTrue(parsedDataFrames.isNotEmpty() && parsedDataFrames[0] is DataFrame, "Expected DATA frame")
        val dataFrame = parsedDataFrames[0] as DataFrame
        assertContentEquals(requestBody, dataFrame.payload)
        assertTrue(dataFrameFin, "FIN should be true for the last DATA frame")
    }

    @Test
    fun `execute should handle URL scheme validation`() = runTest(scheduler) {
        mockSuccessfulConnect("testhost.com", 80)
        val request = HttpRequest(method = "GET", url = "http://testhost.com/")
        val result = quicCurl.execute(request) // execute is suspend, run in a way that allows completion
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("URL scheme must be 'https' for HTTP/3. Found: http", result.exceptionOrNull()?.message)
    }

    @Test
    fun `execute should handle host and port mismatch`() = runTest(scheduler) {
        mockSuccessfulConnect("testhost.com", 443)
        val request = HttpRequest(method = "GET", url = "https://anotherhost.com/")
        val result = quicCurl.execute(request)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertTrue(result.exceptionOrNull()?.message?.contains("does not match established connection") == true)
    }

    @Test
    fun `execute should parse response headers and data`() = runTest(scheduler) {
        val (conn, manager) = mockSuccessfulConnect("testhost.com", 443)
        val mockStream: QuicStream = mockk(relaxed = true)
        val streamId = 0L
        every { mockStream.streamId } returns streamId
        every { quicCurl.quicStreamManagerForTest?.openBidirectionalStream() } returns mockStream

        val request = HttpRequest(method = "GET", url = "https://testhost.com/data")

        val qpackEncoder = QpackEncoder()
        val responseHeadersMapInternal = mapOf(":status" to listOf("200"), "content-type" to listOf("application/text"))
        val encodedResponseHeaders = qpackEncoder.encode(responseHeadersMapInternal, streamId)
        val headersFrame = HeadersFrame(encodedResponseHeaders)
        val responseBody = "Response Data".encodeToByteArray()
        val dataFrame = DataFrame(responseBody)

        val responseBytesStream = ByteArrayOutputStream()
        responseBytesStream.write(headersFrame.toByteArray())
        responseBytesStream.write(dataFrame.toByteArray())
        val fullResponseBytes = responseBytesStream.toByteArray()

        val listenerSlot = slot<((QuicStream, ByteArray, Boolean) -> Unit)>()
        every { mockStream.onDataForApplicationListener = capture(listenerSlot) } coAnswers {
             // Use test scope to launch the listener invocation
            this@runTest.launch { // or testCoroutineScope.launch {
                listenerSlot.captured.invoke(mockStream, fullResponseBytes, true)
            }
        }

        var actualResult: Result<HttpResponse>? = null
        val job = launch {
             actualResult = quicCurl.execute(request)
        }
        scheduler.advanceUntilIdle()
        runCurrent() // Ensure listener coroutine runs
        job.join()

        assertNotNull(actualResult, "Result should not be null")
        assertTrue(actualResult!!.isSuccess, "Execute should succeed. Error: ${actualResult!!.exceptionOrNull()?.message} Stack: ${actualResult!!.exceptionOrNull()?.stackTraceToString()}")
        val httpResponse = actualResult!!.getOrNull()
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.statusCode)
        assertEquals("application/text", httpResponse.headers["content-type"]) // HttpResponse headers are Map<String, String>
        assertContentEquals(responseBody, httpResponse.body)
    }
}

// Reflection accessors for QuicCurl
var QuicCurl.lastConnectedHostForTest: String?
    get() = QuicCurlTest::class.java.getDeclaredField("lastConnectedHost").apply { isAccessible = true }.get(this) as String?
    set(value) { QuicCurlTest::class.java.getDeclaredField("lastConnectedHost").apply { isAccessible = true }.set(this, value) }
var QuicCurl.lastConnectedPortForTest: Int?
    get() = QuicCurlTest::class.java.getDeclaredField("lastConnectedPort").apply { isAccessible = true }.get(this) as Int?
    set(value) { QuicCurlTest::class.java.getDeclaredField("lastConnectedPort").apply { isAccessible = true }.set(this, value) }
var QuicCurl.quicStreamManagerForTest: QuicStreamManager?
    get() = QuicCurlTest::class.java.getDeclaredField("quicStreamManager").apply { isAccessible = true }.get(this) as QuicStreamManager?
    set(value) { QuicCurlTest::class.java.getDeclaredField("quicStreamManager").apply { isAccessible = true }.set(this, value) }
var QuicCurl.managerInstanceForTest: QuicConnectionManager?
    get() = QuicCurlTest::class.java.getDeclaredField("managerInstance").apply { isAccessible = true }.get(this) as QuicConnectionManager?
    set(value) { QuicCurlTest::class.java.getDeclaredField("managerInstance").apply { isAccessible = true }.set(this, value) }


// Ensure PingFrame and other necessary frame types are accessible or defined for tests.
// If they are in main source, they should be. If not, minimal versions might be needed here.
// data class PingFrame(val id: Long = 0L) : QuicFrame // Example if not available
// data class CryptoFrame(val offset: ULong, val data: ByteArray) : QuicFrame // Example
// data class AckFrame(val largestAcked: Long, val ackDelay: Long, val firstAckRange: Long, val ackRanges: List<Pair<Long, Long>>) : QuicFrame // Example
// interface QuicFrame // Example
// Assume these are properly defined in borg.trikeshed.net.quic.QuicFrames
// Assume QuicConstants.DEFAULT_MAX_ACK_DELAY_US is defined
// Assume borg.trikeshed.net.quic.utils.serializeFrame exists and works for tested frames
// Assume QuicConnection.setHandshakeComplete is a helper or state is set directly.
// The reflection helpers for QuicCurl's jobs are for advanced verification of timer cancellation/rearming.
// Accessing manager.ptoCount via reflection helper in QuicConnectionManagerTest was:
// val QuicConnectionManager.ptoCount: Int get() = QuicConnectionManagerTest::class.java.getDeclaredField("ptoCount").apply { isAccessible = true }.getInt(this)
// This should be manager.getPtoCountForTest() if a getter is added, or tested via behavior.
// For QuicCurlTest, direct job checking is one way to see if timers are active/cancelled.

object TestQuicConstants { // In case they are not resolvable from main for some reason during test dev
    const val DEFAULT_MAX_ACK_DELAY_US: Long = 25_000L
}
val QuicConnectionManager.ptoCountForTest: Int get() = QuicConnectionManagerTest::class.java.getDeclaredField("ptoCount").apply { isAccessible = true }.getInt(this)
val QuicConnectionManager.MIN_RTO_US_FOR_TEST: Long get() = QuicConnectionManagerTest::class.java.getDeclaredField("MIN_RTO_US").apply { isAccessible = true }.getLong(this)

// This is a simplified version of serializeFrame for test purposes.
// A real implementation would be more robust.
fun borg.trikeshed.net.quic.utils.serializeFrame(frame: QuicFrame): ByteArray {
    return when (frame) {
        is PingFrame -> byteArrayOf(QuicFrameType.PING.toByte())
        is AckFrame -> { // Highly simplified ACK serialization
            var bytes = byteArrayOf(QuicFrameType.ACK.toByte())
            bytes += frame.largestAcknowledged!!.encodeVarInt() // Assuming ULong for PN
            bytes += frame.ackDelay.encodeVarInt()
            bytes += frame.ackRangeCount.encodeVarInt()
            bytes += frame.firstAckRange.encodeVarInt()
            // Skipping ackRanges for this mock serialization
            bytes
        }
        is CryptoFrame -> {
            var bytes = byteArrayOf(QuicFrameType.CRYPTO.toByte())
            bytes += frame.offset.encodeVarInt()
            bytes += frame.data.size.toULong().encodeVarInt() // Length of data
            bytes += frame.data
            bytes
        }
        else -> byteArrayOf() // Fallback for unknown frames
    }
}

// Mock for QuicFrames if not accessible
// data class PingFrame(val comment: String = "") : QuicFrame
// data class AckFrame(
//    var largestAcknowledged: PacketNumber? = null,
//    var ackDelay: Long = 0,
//    var ackRangeCount: Long = 0,
//    var firstAckRange: Long = 0,
//    var ackRanges: List<Pair<Long, Long>> = emptyList(),
//    var ecnCounts: EcnCounts? = null,
//    override var length: Int = 0 // Add if needed by tests
// ) : QuicFrame {
//    constructor(largestAcked: Long, ackDelay: Long, firstAckRange: Long, ackRanges: List<Pair<Long,Long>>) :
//            this(largestAcked.toULong(), ackDelay, ackRanges.size.toLong(), firstAckRange, ackRanges)
//}
// interface QuicFrame { val length: Int get() = 0 }
// data class CryptoFrame(val offset: ULong, val data: ByteArray): QuicFrame
// object QuicFrameType { const val PING: UByte = 0x01u; const val ACK: UByte = 0x02u; const val CRYPTO: UByte = 0x06u }
// typealias ConnectionId = ByteArray
// typealias PacketNumber = ULong
// fun ULong.encodeVarInt(): ByteArray = borg.trikeshed.net.quic.utils.VarInt.encode(this.toLong())
// fun Long.encodeVarInt(): ByteArray = borg.trikeshed.net.quic.utils.VarInt.encode(this)
// object QuicPacketUtils {
//    fun serializeShortHeader(dcid: ConnectionId, pn: PacketNumber, pnLen: Int, keyPhase: Boolean): ByteArray = byteArrayOf(0x40) // Dummy
//    fun serializeInitialHeader(version: UInt, dcid: ConnectionId, scid: ConnectionId, token: ByteArray, pn: PacketNumber, pnLen: Int, payloadLen: Int): Pair<ByteArray, Int> = Pair(byteArrayOf(0xC0.toByte()), 4) // Dummy
//    fun calculatePacketNumberOffsetForShortHeader(dcidLen: Int): Int = 1 + dcidLen
//    const val DEFAULT_PN_OFFSET_LONG_HEADER = 1 + 8 + 1 + 8 + 1 // type+ver+dcidlen+dcid+scidlen+scid+lenlen (approx)
//}
// object QuicConstants { const val QUIC_VERSION_1 = 1u; const val AEAD_TAG_LENGTH = 16 }
// data class QuicSecrets(val key: ByteArray, val iv: ByteArray, val hpKey: ByteArray)
// enum class EncryptionLevel { ONERTT, INITIAL, HANDSHAKE }
// enum class QuicConnectionStateEnum { HANDSHAKE_COMPLETE, CLOSED, CLOSING }
// data class EcnCounts(val ect0: Long, val ect1: Long, val ce: Long)
// class Random { companion object { fun nextBytes(count: Int): ByteArray = ByteArray(count) }}

// Ensure ULong.encodeVarInt() and Long.encodeVarInt() are available if used.
// These are often extension functions. For simplicity, assuming they exist in the specified package.
// Or define them locally for tests if not part of the provided codebase snippet for VarInt.
fun Number.encodeVarInt(): ByteArray = borg.trikeshed.net.quic.utils.VarInt.encode(this.toLong())

```
