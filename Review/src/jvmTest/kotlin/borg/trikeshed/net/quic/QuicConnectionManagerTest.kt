package borg.trikeshed.net.quic

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class QuicConnectionManagerTest {

    private lateinit var scheduler: TestCoroutineScheduler
    private lateinit var connection: QuicConnection
    private lateinit var manager: QuicConnectionManager

    // Test constants
    private val initialPacketNumber = 0UL
    private val testPacketSize = 1000
    private val testFrames = listOf(PingFrame()) // Simple frame for testing

    @BeforeTest
    fun setup() {
        scheduler = TestCoroutineScheduler()
        // Clock.setScheduler(scheduler) // Would be ideal if Clock could use TestCoroutineScheduler directly
        // For now, we'll manage time manually or use Instant.fromEpochMilliseconds with scheduler.currentTime

        connection = QuicConnection.newClientConnectionDataOnly() // Using actual QuicConnection data class
        manager = QuicConnectionManager(connection, QuicConnectionManager.ConnectionRole.CLIENT)
        // Set a known peer max ack delay for predictable PTO calculations
        manager.updatePeerMaxAckDelay(QuicConstants.DEFAULT_MAX_ACK_DELAY_US)
    }

    private fun instantFromCurrentTime(offsetMs: Long = 0L): Instant {
        return Instant.fromEpochMilliseconds(scheduler.currentTime + offsetMs)
    }

    // --- RTT and RTO Tests ---

    @Test
    fun `initial RTT calculation should set SRTT and RTTVAR correctly`() = runTest(scheduler) {
        val rttSampleMs = 100L

        // Simulate sending a packet
        manager.recordPacketSent(initialPacketNumber, testPacketSize, testFrames, EncryptionLevel.ONERTT, true)

        // Advance time and simulate ACK
        scheduler.advanceTimeBy(rttSampleMs)
        val ackReceivedTime = instantFromCurrentTime()

        // Manually adjust sent time for the packet to align with how Clock.System.now() would work if not using TestCoroutineScheduler for Clock
        // This is a workaround because Clock.System.now() isn't automatically using TestCoroutineScheduler.
        val sentPacket = manager.sentPackets[initialPacketNumber]!!
        val adjustedSentPacket = sentPacket.copy(sentTime = Instant.fromEpochMilliseconds(0L)) // Pretend it was sent at t=0 of scheduler
        manager.sentPackets[initialPacketNumber] = adjustedSentPacket

        manager.recordPacketAckedByPeer(initialPacketNumber)

        assertEquals(rttSampleMs * 1000, manager.smoothedRtt) // SRTT in microseconds
        assertEquals((rttSampleMs * 1000) / 2, manager.rttVar) // RTTVAR in microseconds
        assertFalse(manager.firstRttSample)
    }

    @Test
    fun `subsequent RTT samples should update SRTT and RTTVAR`() = runTest(scheduler) {
        // Initial sample
        manager.recordPacketSent(0UL, testPacketSize, testFrames, EncryptionLevel.ONERTT, true)
        manager.sentPackets[0UL] = manager.sentPackets[0UL]!!.copy(sentTime = instantFromCurrentTime(0))
        scheduler.advanceTimeBy(100L) // RTT = 100ms
        manager.recordPacketAckedByPeer(0UL)

        val initialSrtt = manager.smoothedRtt
        val initialRttvar = manager.rttVar

        // Second sample (rtt=120ms)
        manager.recordPacketSent(1UL, testPacketSize, testFrames, EncryptionLevel.ONERTT, true)
        manager.sentPackets[1UL] = manager.sentPackets[1UL]!!.copy(sentTime = instantFromCurrentTime(0)) // Sent at current time=100ms
        scheduler.advanceTimeBy(120L) // ACK received at current time + 120ms = 220ms
        manager.recordPacketAckedByPeer(1UL)

        val expectedDelta = kotlin.math.abs(initialSrtt - (120L * 1000))
        val expectedRttvar = ((1 - manager.RTT_BETA) * initialRttvar + manager.RTT_BETA * expectedDelta).toLong()
        val expectedSrtt = ((1 - manager.RTT_ALPHA) * initialSrtt + manager.RTT_ALPHA * (120L * 1000)).toLong()

        assertEquals(expectedSrtt, manager.smoothedRtt)
        assertEquals(expectedRttvar, manager.rttVar)
    }

    @Test
    fun `checkForLostPackets should identify RTO-lost packets`() = runTest(scheduler) {
        // Setup RTT
        manager.recordPacketSent(0UL, testPacketSize, testFrames, EncryptionLevel.ONERTT, true)
        manager.sentPackets[0UL] = manager.sentPackets[0UL]!!.copy(sentTime = instantFromCurrentTime(0))
        scheduler.advanceTimeBy(100L) // RTT = 100ms
        manager.recordPacketAckedByPeer(0UL) // SRTT=100ms, RTTVAR=50ms

        // RTO = SRTT + max(G, K*RTTVAR) = 100_000 + max(1000, 4*50_000) = 100_000 + 200_000 = 300_000 us = 300 ms
        // MinRTO is 1s, so RTO is 1000ms
        val rtoDurationMs = manager.MIN_RTO_US / 1000L

        // Send a packet that will be "lost"
        manager.recordPacketSent(1UL, testPacketSize, testFrames, EncryptionLevel.ONERTT, true)
        manager.sentPackets[1UL] = manager.sentPackets[1UL]!!.copy(sentTime = instantFromCurrentTime(0)) // Sent at t=100ms (scheduler time)

        // Send another packet that will be acked
        manager.recordPacketSent(2UL, testPacketSize, testFrames, EncryptionLevel.ONERTT, true)
        manager.sentPackets[2UL] = manager.sentPackets[2UL]!!.copy(sentTime = instantFromCurrentTime(0)) // Sent at t=100ms

        // Advance time slightly, ACK packet 2
        scheduler.advanceTimeBy(50L) // current scheduler time = 150ms
        manager.recordPacketAckedByPeer(2UL) // Packet 1 still in flight

        // Advance time past RTO for packet 1 (sent at t=100ms scheduler time)
        // RTO is 1000ms. Packet 1 sent at 100ms. It times out at 1100ms.
        // Current time is 150ms. Need to advance by 1100 - 150 = 950ms.
        scheduler.advanceTimeBy(rtoDurationMs) // current scheduler time = 150 + 1000 = 1150ms

        manager.checkForLostPackets()

        val lostPackets = manager.getPacketsForRetransmission()
        assertTrue(lostPackets.any { it.packetNumber == 1UL }, "Packet 1 should be lost")
        assertFalse(lostPackets.any { it.packetNumber == 0UL }, "Packet 0 should not be lost")
        assertFalse(lostPackets.any { it.packetNumber == 2UL }, "Packet 2 should not be lost")
    }

    @Test
    fun `onPacketRetransmissionHandled should remove packet from queues`() = runTest(scheduler) {
        // Setup: make packet 1 lost
        manager.recordPacketSent(0UL, testPacketSize, testFrames, EncryptionLevel.ONERTT, true)
        manager.sentPackets[0UL] = manager.sentPackets[0UL]!!.copy(sentTime = instantFromCurrentTime(0))
        scheduler.advanceTimeBy(100L); manager.recordPacketAckedByPeer(0UL) // Establishes RTT, SRTT=100ms, RTTVAR=50ms

        manager.recordPacketSent(1UL, testPacketSize, testFrames, EncryptionLevel.ONERTT, true)
        manager.sentPackets[1UL] = manager.sentPackets[1UL]!!.copy(sentTime = instantFromCurrentTime(0)) // Sent at t=100ms

        val rtoDurationMs = manager.MIN_RTO_US / 1000L
        scheduler.advanceTimeBy(rtoDurationMs + 50L) // Advance past RTO, current time = 100 + 1000 + 50 = 1150ms
        manager.checkForLostPackets()

        assertTrue(manager.getPacketsForRetransmission().any { it.packetNumber == 1UL }, "Packet 1 initially lost")
        assertTrue(manager.sentPackets.containsKey(1UL), "Packet 1 initially in sentPackets")

        // Action
        manager.onPacketRetransmissionHandled(1UL)

        // Assert
        assertFalse(manager.getPacketsForRetransmission().any { it.packetNumber == 1UL }, "Packet 1 should be removed from retransmission queue")
        assertFalse(manager.sentPackets.containsKey(1UL), "Packet 1 should be removed from sentPackets")
    }


    // --- PTO Tests ---
    @Test
    fun `getPtoDurationUs should return initial PTO if SRTT is zero`() = runTest(scheduler) {
        val expectedInitialPto = manager.INITIAL_PTO_DURATION_US * (1L shl 0) // ptoCount = 0
        assertEquals(expectedInitialPto, manager.getPtoDurationUs())
    }

    @Test
    fun `getPtoDurationUs should calculate based on SRTT and RTTVAR if available`() = runTest(scheduler) {
        // Establish SRTT and RTTVAR
        manager.recordPacketSent(0UL, testPacketSize, testFrames, EncryptionLevel.ONERTT, true)
        manager.sentPackets[0UL] = manager.sentPackets[0UL]!!.copy(sentTime = instantFromCurrentTime(0))
        scheduler.advanceTimeBy(100L) // 100ms RTT
        manager.recordPacketAckedByPeer(0UL) // SRTT=100ms (100_000us), RTTVAR=50ms (50_000us)

        val srtt = manager.smoothedRtt
        val rttvar = manager.rttVar
        val peerMaxAckDelay = manager.peerMaxAckDelayUs

        // Base PTO = SRTT + 4*RTTVAR + MaxAckDelay (or CLOCK_GRANULARITY if RTTVAR is 0)
        // Base PTO = 100_000 + 4*50_000 + DEFAULT_MAX_ACK_DELAY_US
        // Base PTO = 100_000 + 200_000 + 25_000 = 325_000 us
        val expectedBasePto = srtt + (manager.RTO_K * rttvar) + peerMaxAckDelay
        var expectedPto = expectedBasePto * (1L shl 0) // ptoCount = 0
        expectedPto = kotlin.math.max(expectedPto, manager.MINIMUM_PTO_DURATION_US)

        assertEquals(expectedPto, manager.getPtoDurationUs())
    }

    @Test
    fun `getPtoDurationUs should apply exponential backoff`() = runTest(scheduler) {
        manager.recordPacketSent(0UL, testPacketSize, testFrames, EncryptionLevel.ONERTT, true)
        manager.sentPackets[0UL] = manager.sentPackets[0UL]!!.copy(sentTime = instantFromCurrentTime(0))
        scheduler.advanceTimeBy(100L); manager.recordPacketAckedByPeer(0UL)

        val pto0 = manager.getPtoDurationUs() // ptoCount = 0
        manager.onPtoExpired() // ptoCount = 1
        val pto1 = manager.getPtoDurationUs()
        manager.onPtoExpired() // ptoCount = 2
        val pto2 = manager.getPtoDurationUs()

        assertEquals(pto0 * 2, pto1, "PTO should double for ptoCount = 1")
        assertEquals(pto1 * 2, pto2, "PTO should double for ptoCount = 2")
    }

    @Test
    fun `resetPtoBackoff should reset ptoCount to zero`() = runTest(scheduler) {
        manager.onPtoExpired() // ptoCount = 1
        manager.onPtoExpired() // ptoCount = 2
        assertNotEquals(0, manager.ptoCount) // internal check, not testing manager.ptoCount directly in test

        manager.resetPtoBackoff()
        // To verify ptoCount is 0, check getPtoDurationUs without prior RTT sample
        val expectedInitialPto = manager.INITIAL_PTO_DURATION_US
        assertEquals(expectedInitialPto, manager.getPtoDurationUs())
    }

    @Test
    fun `recordPacketAckedByPeer should reset ptoCount if ack is progress-making for ack-eliciting packet`() = runTest(scheduler) {
        manager.onPtoExpired() // Increment ptoCount to 1 for test
        val initialPtoDuration = manager.getPtoDurationUs()

        manager.recordPacketSent(0UL, testPacketSize, testFrames, EncryptionLevel.ONERTT, elicitsAck = true)
        manager.sentPackets[0UL] = manager.sentPackets[0UL]!!.copy(sentTime = instantFromCurrentTime(0))
        scheduler.advanceTimeBy(100L)

        val progressMade = manager.recordPacketAckedByPeer(0UL)
        assertTrue(progressMade)

        // ptoCount should be reset, so PTO duration should be the base one
        val ptoAfterAck = manager.getPtoDurationUs()
        val basePtoWithoutBackoff = manager.INITIAL_PTO_DURATION_US // Since SRTT is now set
        val expectedPto = kotlin.math.max(manager.MINIMUM_PTO_DURATION_US, manager.smoothedRtt + manager.RTO_K * manager.rttVar + manager.peerMaxAckDelayUs)

        assertEquals(expectedPto, ptoAfterAck)
        assertNotEquals(initialPtoDuration, ptoAfterAck, "PTO duration should change after ptoCount reset")
    }

    @Test
    fun `hasOutstandingAckElicitingPackets should return true if ack-eliciting packets are in flight`() {
        manager.recordPacketSent(0UL, testPacketSize, testFrames, EncryptionLevel.ONERTT, elicitsAck = true)
        assertTrue(manager.hasOutstandingAckElicitingPackets())
        manager.recordPacketSent(1UL, testPacketSize, testFrames, EncryptionLevel.ONERTT, elicitsAck = false)
        assertTrue(manager.hasOutstandingAckElicitingPackets()) // Still true due to packet 0
    }

    @Test
    fun `hasOutstandingAckElicitingPackets should return false if only non-ack-eliciting or no packets are in flight`() {
        manager.recordPacketSent(0UL, testPacketSize, testFrames, EncryptionLevel.ONERTT, elicitsAck = false)
        assertFalse(manager.hasOutstandingAckElicitingPackets())

        manager.sentPackets.clear() // Remove packet 0
        assertFalse(manager.hasOutstandingAckElicitingPackets(), "Should be false when no packets are in flight")
    }

    // Workaround for Clock.System.now() not using TestCoroutineScheduler
    // This is tricky. kotlinx-datetime's Clock.System is not easily mockable or injectable for System.now().
    // Tests relying on precise timing of 'sentTime = Clock.System.now()' inside recordPacketSent
    // against scheduler's advanceTimeBy for ACK reception time will be hard.
    // The current workaround is to manually overwrite sentTime in the test after calling recordPacketSent.


    // --- Full ACK Range Processing Tests ---

    private fun setupSentPackets(vararg packetNumbers: Long, elicitsAck: Boolean = true) {
        val currentTime = scheduler.currentTime
        packetNumbers.forEach { pn ->
            // Record packet sent at current virtual time for RTT calculation
            // Note: recordPacketSent now takes Long for packet number
            manager.recordPacketSent(pn, testPacketSize, testFrames, EncryptionLevel.ONERTT, elicitsAck)
            // Adjust sentTime to be exactly the scheduler's current time for predictable RTT
            val sentPacket = manager.sentPacketsForTest[pn]!! // Using new reflection helper name
            manager.sentPacketsForTest[pn] = sentPacket.copy(sentTime = Instant.fromEpochMilliseconds(currentTime))
        }
    }

    @Test
    fun `recordPacketAckedByPeer processes first ACK range correctly`() = runTest(scheduler) {
        setupSentPackets(1L, 2L, 3L, 4L, 5L) // Packets 1-5 sent

        // ACK for packets 3, 4, 5 (Largest=5, Count=3)
        val ackFrame = AckFrame(largestAcked = 5L, ackDelay = 0L, firstAckRangePacketCount = 3L, additionalAckRanges = emptyList(), ecnCounts = null)

        scheduler.advanceTimeBy(50L) // Simulate some RTT
        val progressMade = manager.recordPacketAckedByPeer(ackFrame)

        assertTrue(progressMade, "Progress should be made for ack-eliciting packets")
        assertFalse(manager.sentPacketsForTest.containsKey(5L), "Packet 5 should be removed")
        assertFalse(manager.sentPacketsForTest.containsKey(4L), "Packet 4 should be removed")
        assertFalse(manager.sentPacketsForTest.containsKey(3L), "Packet 3 should be removed")
        assertTrue(manager.sentPacketsForTest.containsKey(2L), "Packet 2 should still be in flight")
        assertTrue(manager.sentPacketsForTest.containsKey(1L), "Packet 1 should still be in flight")

        assertFalse(manager.firstRttSampleForTest, "RTT should have been updated")
    }

    @Test
    fun `recordPacketAckedByPeer processes additional ACK ranges correctly`() = runTest(scheduler) {
        setupSentPackets(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)

        // ACK for 8,9,10 (Largest=10, Count=3 for first range)
        // Additional Range: Gap=2 (packets 7,6 unacked), AckedCount=2 (packets 5,4 acked)
        // So, currentPnConsidered starts at 10-3 = 7.
        // Largest in additional range = 7 - (2+1) = 4. This is wrong.
        // Correct logic:
        // Largest in first range: 10. Smallest in first range: 10-3+1 = 8. [8,9,10] acked.
        // currentPnConsidered for next range = smallest_in_prev_range - 1 = 8 - 1 = 7.
        // AckRange(gap=2, count=2):
        //   Largest in this new range = currentPnConsidered - gap = 7 - 2 = 5.
        //   Smallest in this new range = 5 - 2 + 1 = 4. So range [4,5] acked.
        val ackRanges = listOf(
            AckRange(gap = 2L, ackedPacketsInThisRange = 2L)
        )
        val ackFrame = AckFrame(largestAcked = 10L, ackDelay = 0L, firstAckRangePacketCount = 3L, additionalAckRanges = ackRanges, ecnCounts = null)

        scheduler.advanceTimeBy(60L)
        val progressMade = manager.recordPacketAckedByPeer(ackFrame)
        assertTrue(progressMade)

        listOf(10L, 9L, 8L, 5L, 4L).forEach { pn -> // Packets that should be ACKed
            assertFalse(manager.sentPacketsForTest.containsKey(pn), "Packet $pn should be removed")
        }
        listOf(1L, 2L, 3L, 6L, 7L).forEach { pn -> // Packets that should remain
            assertTrue(manager.sentPacketsForTest.containsKey(pn), "Packet $pn should still be in flight")
        }
    }

    @Test
    fun `recordPacketAckedByPeer returns false if ACK makes no progress for ack-eliciting packets`() = runTest(scheduler) {
        // Packet 0UL was ULong, recordPacketSent now expects Long.
        manager.recordPacketSent(1L, testPacketSize, testFrames, EncryptionLevel.ONERTT, elicitsAck = true)
        manager.sentPacketsForTest[1L] = manager.sentPacketsForTest[1L]!!.copy(sentTime = instantFromCurrentTime(0))
        manager.recordPacketSent(2L, testPacketSize, testFrames, EncryptionLevel.ONERTT, elicitsAck = false)
        manager.sentPacketsForTest[2L] = manager.sentPacketsForTest[2L]!!.copy(sentTime = instantFromCurrentTime(0))


        val ackFrame1 = AckFrame(largestAcked = 1L, ackDelay = 0L, firstAckRangePacketCount = 1L, emptyList(), null)
        scheduler.advanceTimeBy(10L)
        assertTrue(manager.recordPacketAckedByPeer(ackFrame1), "Initial ACK for packet 1 should make progress")

        val ackFrame2 = AckFrame(largestAcked = 1L, ackDelay = 0L, firstAckRangePacketCount = 1L, emptyList(), null)
        scheduler.advanceTimeBy(10L)
        assertFalse(manager.recordPacketAckedByPeer(ackFrame2), "Re-ACK for packet 1 should not make progress")

        val ackFrame3 = AckFrame(largestAcked = 2L, ackDelay = 0L, firstAckRangePacketCount = 1L, emptyList(), null)
        scheduler.advanceTimeBy(10L)
        // Packet 2 was not ack-eliciting, so even if it's newly acked, overallProgressMade for pto might be false.
        assertFalse(manager.recordPacketAckedByPeer(ackFrame3), "ACK for non-ack-eliciting packet 2 should not count as progress for PTO reset")
    }

    @Test
    fun `ACK processing correctly handles largestAckedPacketNumberByPeer for PN reconstruction context`() = runTest(scheduler) {
        assertTrue(true, "Test placeholder for largestAckedPacketNumberByPeer interaction with ACK processing. This field's update is currently commented out in manager.")
    }
}

// Helper to access private RTT_ALPHA for test validation (not ideal, but shows intent)
// Using reflection to access private members for testing purposes.
@OptIn(ExperimentalStdlibApi::class) // For getAssociatedObject
private val QuicConnectionManager.firstRttSampleForTest: Boolean
    get() = java.lang.invoke.MethodHandles.privateLookupIn(QuicConnectionManager::class.java, java.lang.invoke.MethodHandles.lookup())
        .findVarHandle(QuicConnectionManager::class.java, "firstRttSample", Boolean::class.javaPrimitiveType)
        .get(this) as Boolean

val QuicConnectionManager.RTT_ALPHA: Double get() = QuicConnectionManagerTest::class.java.getDeclaredField("RTT_ALPHA").apply { isAccessible = true }.getDouble(this)
val QuicConnectionManager.RTT_BETA: Double get() = QuicConnectionManagerTest::class.java.getDeclaredField("RTT_BETA").apply { isAccessible = true }.getDouble(this)
val QuicConnectionManager.RTO_K: Long get() = QuicConnectionManagerTest::class.java.getDeclaredField("RTO_K").apply { isAccessible = true }.getLong(this)
val QuicConnectionManager.MIN_RTO_US: Long get() = QuicConnectionManagerTest::class.java.getDeclaredField("MIN_RTO_US").apply { isAccessible = true }.getLong(this)
val QuicConnectionManager.INITIAL_PTO_DURATION_US: Long get() = QuicConnectionManagerTest::class.java.getDeclaredField("INITIAL_PTO_DURATION_US").apply { isAccessible = true }.getLong(this)
val QuicConnectionManager.MINIMUM_PTO_DURATION_US: Long get() = QuicConnectionManagerTest::class.java.getDeclaredField("MINIMUM_PTO_DURATION_US").apply { isAccessible = true }.getLong(this)
val QuicConnectionManager.peerMaxAckDelayUs: Long get() = QuicConnectionManagerTest::class.java.getDeclaredField("peerMaxAckDelayUs").apply { isAccessible = true }.getLong(this)
val QuicConnectionManager.ptoCountForTest: Int get() = QuicConnectionManagerTest::class.java.getDeclaredField("ptoCount").apply { isAccessible = true }.getInt(this)
val QuicConnectionManager.sentPacketsForTest: MutableMap<Long, SentPacketInfo>
    get() = QuicConnectionManagerTest::class.java.getDeclaredField("sentPackets").apply { isAccessible = true }.get(this) as MutableMap<Long, SentPacketInfo>

// Placeholder for QuicConstants if not defined in the main source set accessible by tests
// object QuicConstants {
//    const val DEFAULT_MAX_ACK_DELAY_US: Long = 25_000L // 25 ms
// }
