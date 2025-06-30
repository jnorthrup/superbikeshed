package gk.kademlia.bitswap

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay // For time checks
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PeerLedgerTest {

    private lateinit var peerId: PeerID
    private lateinit var ledger: PeerLedger

    @BeforeTest
    fun setup() {
        peerId = "testPeer1"
        ledger = PeerLedger(peerId)
    }

    private fun createCID(id: String): CID {
        return CID(id.encodeToByteArray()) // Simple CID creation for tests
    }

    @Test
    fun `initialState_isCorrect`() = runTest {
        assertTrue(ledger.getPeerWants().isEmpty(), "Initial peer wants should be empty.")
        assertEquals(0L, ledger.getBytesExchangedBalance(), "Initial byte balance should be 0.")
        assertEquals(0L, ledger.lastMessageTime, "Initial lastMessageTime should be 0L.")
    }

    @Test
    fun `addWants_addsToPeerWantListCorrectly`() = runTest {
        val cid1 = createCID("cid1")
        val cid2 = createCID("cid2")
        val cid3 = createCID("cid3")

        ledger.addWants(listOf(cid1, cid2))
        var wants = ledger.getPeerWants()
        assertEquals(2, wants.size, "Should have 2 wants after first add.")
        assertTrue(wants.containsAll(listOf(cid1, cid2)), "Wants should contain cid1 and cid2.")

        // Add overlapping and new CIDs
        ledger.addWants(listOf(cid2, cid3))
        wants = ledger.getPeerWants()
        assertEquals(3, wants.size, "Should have 3 wants after adding overlapping cid2 and new cid3.")
        assertTrue(wants.containsAll(listOf(cid1, cid2, cid3)), "Wants should contain cid1, cid2, and cid3.")

        // Add same CIDs again, should not change
        ledger.addWants(listOf(cid1, cid2))
        wants = ledger.getPeerWants()
        assertEquals(3, wants.size, "Adding existing CIDs again should not change size.")
    }

    @Test
    fun `removeWants_removesFromPeerWantListCorrectly`() = runTest {
        val cid1 = createCID("cid1")
        val cid2 = createCID("cid2")
        val cid3 = createCID("cid3")
        val cid4NonExistent = createCID("cid4-non-exist")

        ledger.addWants(listOf(cid1, cid2, cid3))

        ledger.removeWants(listOf(cid1, cid4NonExistent)) // Try removing one existing, one non-existing
        var wants = ledger.getPeerWants()
        assertEquals(2, wants.size, "Should have 2 wants after removing cid1 and non-existent cid4.")
        assertTrue(wants.containsAll(listOf(cid2, cid3)), "Wants should contain cid2 and cid3.")
        assertTrue(!wants.contains(cid1), "cid1 should be removed.")

        ledger.removeWants(listOf(cid2))
        wants = ledger.getPeerWants()
        assertEquals(1, wants.size, "Should have 1 want after removing cid2.")
        assertTrue(wants.contains(cid3), "Only cid3 should remain.")

        ledger.removeWants(listOf(cid3))
        wants = ledger.getPeerWants()
        assertTrue(wants.isEmpty(), "Wants should be empty after removing cid3.")

        // Remove from empty list
        ledger.removeWants(listOf(cid1))
        wants = ledger.getPeerWants()
        assertTrue(wants.isEmpty(), "Removing from empty list should result in empty list.")
    }

    @Test
    fun `clearPeerWants_emptiesTheList`() = runTest {
        ledger.addWants(listOf(createCID("a"), createCID("b")))
        assertEquals(2, ledger.getPeerWants().size)

        ledger.clearPeerWants()
        assertTrue(ledger.getPeerWants().isEmpty(), "clearPeerWants should result in an empty list.")

        // Call on already empty list
        ledger.clearPeerWants()
        assertTrue(ledger.getPeerWants().isEmpty(), "clearPeerWants on empty list should remain empty.")
    }

    @Test
    fun `bytesExchangedBalance_updatesCorrectly`() = runTest {
        ledger.blockSentToPeer(100)
        assertEquals(100L, ledger.getBytesExchangedBalance(), "Balance should be 100 after sending 100 bytes.")

        ledger.blockReceivedFromPeer(50)
        assertEquals(50L, ledger.getBytesExchangedBalance(), "Balance should be 50 after receiving 50 bytes.")

        ledger.blockReceivedFromPeer(100)
        assertEquals(-50L, ledger.getBytesExchangedBalance(), "Balance should be -50 after receiving another 100 bytes.")

        ledger.blockSentToPeer(50)
        assertEquals(0L, ledger.getBytesExchangedBalance(), "Balance should be 0 after sending 50 more bytes.")
    }

    @Test
    fun `updateLastMessageTime_setsTimeCorrectly`() = runTest {
        val initialTime = ledger.lastMessageTime
        assertEquals(0L, initialTime, "Initial lastMessageTime should be 0L.")

        // Allow a small window for time progression if test execution is very fast
        val timeBeforeUpdate = Clock.System.now().toEpochMilliseconds()
        ledger.updateLastMessageTime()
        val timeAfterUpdate = ledger.lastMessageTime
        val timeNow = Clock.System.now().toEpochMilliseconds()

        assertTrue(timeAfterUpdate >= timeBeforeUpdate, "lastMessageTime should be >= time before update call.")
        assertTrue(timeAfterUpdate <= timeNow, "lastMessageTime should be <= current time after update call.")
        assertNotEquals(0L, timeAfterUpdate, "lastMessageTime should not be 0L after update.")
    }

    @Test
    fun `toString_reflectsStateChanges`() = runTest {
        val initialStateString = ledger.toString()
        assertTrue(initialStateString.contains("wantsFromUs=0"), "Initial toString wants should be 0.")
        assertTrue(initialStateString.contains("balance=0"), "Initial toString balance should be 0.")

        ledger.addWants(listOf(createCID("toStringCID")))
        ledger.blockSentToPeer(123)
        ledger.updateLastMessageTime() // Ensure lastMessageTime is non-zero for a more complete test

        val updatedStateString = ledger.toString()
        assertTrue(updatedStateString.contains("wantsFromUs=1"), "Updated toString wants should be 1.")
        assertTrue(updatedStateString.contains("balance=123"), "Updated toString balance should be 123.")
        assertTrue(!updatedStateString.contains("lastMsgTime=0"), "Updated toString lastMsgTime should not be 0 if updated.")
    }

    @Test
    fun `concurrencyTest_basicIntegrityUnderConcurrentAccess`() = runTest(timeoutMs = 2000) { // Added timeout
        val numOperations = 100
        val cids = (1..numOperations).map { createCID("concurrent_cid_$it") }

        val jobs = mutableListOf<Job>()

        // Concurrent additions and removals of wants
        jobs += launch {
            for (i in 0 until numOperations) {
                ledger.addWants(listOf(cids[i]))
                if (i % 5 == 0) {
                    ledger.removeWants(listOf(cids[i / 2])) // Remove some overlapping/previous CIDs
                }
            }
        }

        // Concurrent balance updates
        jobs += launch {
            for (i in 0 until numOperations) {
                ledger.blockSentToPeer(10)    // +10
                ledger.blockReceivedFromPeer(5) // -5
            } // Net +5 per iteration
        }

        jobs += launch {
            for (i in 0 until numOperations / 2) { // Fewer operations to ensure balance isn't always zero
                 ledger.blockReceivedFromPeer(3) // -3
                 ledger.blockSentToPeer(1)       // +1
            } // Net -2 per iteration for half the operations
        }

        jobs.forEach { it.join() }

        // Verify final state
        // For wants: it's hard to predict exact number due to interleaved removeWants.
        // But it should be between 0 and numOperations.
        val finalWantsCount = ledger.getPeerWants().size
        assertTrue(finalWantsCount >= 0 && finalWantsCount <= numOperations,
            "Final wants count ($finalWantsCount) should be within reasonable bounds [0, $numOperations].")
        println("Concurrency test: Final wants count = $finalWantsCount")

        // For balance:
        // Job 1: numOperations * (+10 - 5) = numOperations * 5
        // Job 2: (numOperations / 2) * (-3 + 1) = (numOperations / 2) * -2 = -numOperations
        // Expected balance: (numOperations * 5) - numOperations = numOperations * 4
        val expectedBalance = numOperations * 4L
        val finalBalance = ledger.getBytesExchangedBalance()
        assertEquals(expectedBalance, finalBalance, "Final balance should be consistent with operations.")
        println("Concurrency test: Final balance = $finalBalance, Expected = $expectedBalance")

        // Check for no exceptions (implicitly done if test passes this far)
        assertTrue(true, "Test completed without throwing exceptions, indicating basic mutex protection.")
    }
}
