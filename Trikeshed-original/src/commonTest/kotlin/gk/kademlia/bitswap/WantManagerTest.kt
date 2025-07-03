package gk.kademlia.bitswap

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WantManagerTest {

    private lateinit var wantManager: WantManager
    private val testDispatcher = StandardTestDispatcher() // For controlling execution if needed, though runTest manages its own.
    private lateinit var testScope : TestScope // testScope will be provided by runTest

    @BeforeTest
    fun setup() {
        wantManager = WantManager()
        // testScope = TestScope(testDispatcher) // runTest provides its own scope
    }

    private fun createCID(id: String): CID {
        return CID(id.encodeToByteArray()) // Simple CID creation for tests
    }

    @Test
    fun `wantBlock_addNewWant_emitsToFlowAndUpdatesActiveWants`() = runTest {
        testScope = this // Assign the scope from runTest
        val cid1 = createCID("cid1")
        val priority1 = 10

        val activeWantsBefore = wantManager.getActiveWants()
        assertTrue(activeWantsBefore.none { it.block == cid1 }, "CID1 should not be in active wants initially.")

        val changesCollector = mutableListOf<Wantlist.Entry>()
        val collectionJob = launch {
            wantManager.wantlistChangesFlow.toList(changesCollector)
        }

        wantManager.wantBlock(cid1, priority1)

        val activeWantsAfter = wantManager.getActiveWants()
        assertNotNull(activeWantsAfter.find { it.block == cid1 && it.priority == priority1 }, "CID1 should be in active wants with correct priority.")

        assertEquals(1, changesCollector.size, "Wantlist changes flow should have emitted one entry.")
        val emittedEntry = changesCollector.first()
        assertEquals(cid1, emittedEntry.block)
        assertEquals(priority1, emittedEntry.priority)
        assertFalse(emittedEntry.cancel)
        assertEquals(Wantlist.WantType.Block, emittedEntry.wantType) // Default type

        collectionJob.cancel() // Stop collecting
    }

    @Test
    fun `wantBlock_updatePriority_emitsToFlowAndUpdatesActiveWants`() = runTest {
        testScope = this
        val cid1 = createCID("cid1")
        val oldPriority = 5
        val newPriority = 10

        wantManager.wantBlock(cid1, oldPriority) // Initial add

        val changesCollector = mutableListOf<Wantlist.Entry>()
        val collectionJob = launch {
            wantManager.wantlistChangesFlow.toList(changesCollector)
        }

        wantManager.wantBlock(cid1, newPriority) // Update priority

        val activeWants = wantManager.getActiveWants()
        val wantEntry = activeWants.find { it.block == cid1 }
        assertNotNull(wantEntry, "CID1 should still be in active wants.")
        assertEquals(newPriority, wantEntry.priority, "Priority for CID1 should be updated.")

        // The flow should emit the updated entry.
        // Depending on how WantManager handles updates (e.g., if it emits only on actual change of priority)
        // and if initial add was collected. Here we collect after first add.
        assertEquals(1, changesCollector.size, "Wantlist changes flow should have emitted one entry for the update.")
        val emittedEntry = changesCollector.first()
        assertEquals(cid1, emittedEntry.block)
        assertEquals(newPriority, emittedEntry.priority)
        assertFalse(emittedEntry.cancel)

        collectionJob.cancel()
    }

    @Test
    fun `wantBlock_samePriority_doesNotReEmitOrChangeActiveWantCount`() = runTest {
        testScope = this
        val cid1 = createCID("cid1")
        val priority = 7

        wantManager.wantBlock(cid1, priority) // First call

        val activeWantsInitial = wantManager.getActiveWants()
        assertEquals(1, activeWantsInitial.size)

        val changesCollector = mutableListOf<Wantlist.Entry>()
        val collectionJob = launch {
            wantManager.wantlistChangesFlow.toList(changesCollector)
        }

        wantManager.wantBlock(cid1, priority) // Second call with same priority

        val activeWantsAfter = wantManager.getActiveWants()
        assertEquals(1, activeWantsAfter.size, "Active want count should remain 1.")
        assertEquals(priority, activeWantsAfter.first().priority, "Priority should remain the same.")

        assertTrue(changesCollector.isEmpty(), "Wantlist changes flow should not emit if priority is the same and want exists.")

        collectionJob.cancel()
    }

    @Test
    fun `cancelWant_existingWant_emitsCancelAndRemovesFromActive`() = runTest {
        testScope = this
        val cid1 = createCID("cid1")
        wantManager.wantBlock(cid1, 5) // Add a want first

        assertTrue(wantManager.hasActiveWant(cid1), "CID1 should be active before cancel.")

        val changesCollector = mutableListOf<Wantlist.Entry>()
        val collectionJob = launch {
            wantManager.wantlistChangesFlow.toList(changesCollector)
        }

        wantManager.cancelWant(cid1)

        assertFalse(wantManager.hasActiveWant(cid1), "CID1 should not be active after cancel.")
        assertTrue(wantManager.getActiveWants().none { it.block == cid1 }, "CID1 should be removed from getActiveWants list.")

        assertEquals(1, changesCollector.size, "Wantlist changes flow should emit one cancel entry.")
        val emittedEntry = changesCollector.first()
        assertEquals(cid1, emittedEntry.block)
        assertTrue(emittedEntry.cancel, "Emitted entry should be a cancel type.")

        collectionJob.cancel()
    }

    @Test
    fun `cancelWant_nonExistingWant_doesNothing`() = runTest {
        testScope = this
        val cidNonExisting = createCID("cid-non-exist")

        val changesCollector = mutableListOf<Wantlist.Entry>()
        val collectionJob = launch {
            wantManager.wantlistChangesFlow.toList(changesCollector)
        }

        wantManager.cancelWant(cidNonExisting)

        assertTrue(wantManager.getActiveWants().isEmpty(), "Active wants should remain empty.")
        assertTrue(changesCollector.isEmpty(), "Wantlist changes flow should not emit for a non-existing want.")

        collectionJob.cancel()
    }

    @Test
    fun `blockReceived_activeWant_emitsToBlockReceivedFlowAndRemovesFromActive`() = runTest {
        testScope = this
        val cid1 = createCID("cid-recv")
        wantManager.wantBlock(cid1, 3) // Add want

        assertTrue(wantManager.hasActiveWant(cid1), "CID should be active before blockReceived.")

        val receivedCollector = mutableListOf<CID>()
        val collectionJob = launch {
            wantManager.blockReceivedFlow.toList(receivedCollector)
        }

        wantManager.blockReceived(cid1)

        assertFalse(wantManager.hasActiveWant(cid1), "CID should not be active after blockReceived.")

        assertEquals(1, receivedCollector.size, "Block received flow should emit one CID.")
        assertEquals(cid1, receivedCollector.first(), "Emitted CID should match.")

        collectionJob.cancel()
    }

    @Test
    fun `blockReceived_notActiveWant_doesNothing`() = runTest {
        testScope = this
        val cidNotWanted = createCID("cid-not-wanted")

        val receivedCollector = mutableListOf<CID>()
        val collectionJob = launch {
            wantManager.blockReceivedFlow.toList(receivedCollector)
        }

        wantManager.blockReceived(cidNotWanted) // Block received for a CID not in active wants

        assertTrue(wantManager.getActiveWants().isEmpty(), "Active wants should remain empty.")
        assertTrue(receivedCollector.isEmpty(), "Block received flow should not emit if CID was not actively wanted.")

        collectionJob.cancel()
    }

    @Test
    fun `sentToPeers_tracking_addsAndRemovesPeersCorrectly`() = runTest {
        val cid1 = createCID("cid-track")
        val peer1 = "peer1"
        val peer2 = "peer2"

        wantManager.wantBlock(cid1, 1) // Add want

        wantManager.addSentToPeer(cid1, peer1)
        var wantEntry = wantManager.getWantEntry(cid1)
        assertNotNull(wantEntry, "Want entry should exist.")
        assertTrue(wantEntry.sentToPeers.contains(peer1), "Peer1 should be in sentToPeers.")
        assertEquals(1, wantEntry.sentToPeers.size)

        wantManager.addSentToPeer(cid1, peer2)
        wantEntry = wantManager.getWantEntry(cid1)
        assertNotNull(wantEntry)
        assertTrue(wantEntry.sentToPeers.contains(peer1), "Peer1 should still be in sentToPeers.")
        assertTrue(wantEntry.sentToPeers.contains(peer2), "Peer2 should be in sentToPeers.")
        assertEquals(2, wantEntry.sentToPeers.size)

        // Add same peer again, should not change set
        wantManager.addSentToPeer(cid1, peer1)
        wantEntry = wantManager.getWantEntry(cid1)
        assertNotNull(wantEntry)
        assertEquals(2, wantEntry.sentToPeers.size)


        val removed = wantManager.removeSentToPeer(cid1, peer1)
        assertTrue(removed, "removeSentToPeer should return true when peer was present.")
        wantEntry = wantManager.getWantEntry(cid1)
        assertNotNull(wantEntry)
        assertFalse(wantEntry.sentToPeers.contains(peer1), "Peer1 should be removed from sentToPeers.")
        assertTrue(wantEntry.sentToPeers.contains(peer2), "Peer2 should still be in sentToPeers.")
        assertEquals(1, wantEntry.sentToPeers.size)

        val removedNonExistent = wantManager.removeSentToPeer(cid1, "peerNonExistent")
        assertFalse(removedNonExistent, "removeSentToPeer should return false for a non-existent peer.")

        val removedAgain = wantManager.removeSentToPeer(cid1, peer1) // Try removing already removed peer
        assertFalse(removedAgain, "removeSentToPeer should return false if peer was already removed.")
        wantEntry = wantManager.getWantEntry(cid1)
        assertNotNull(wantEntry)
        assertEquals(1, wantEntry.sentToPeers.size) // Size should remain 1
    }

    @Test
    fun `getWantEntry_returnsNullForNonExistingWant`() = runTest {
        val cidNonExist = createCID("non-exist")
        val entry = wantManager.getWantEntry(cidNonExist)
        assertNull(entry, "getWantEntry should return null for a CID not actively wanted.")
    }
}
