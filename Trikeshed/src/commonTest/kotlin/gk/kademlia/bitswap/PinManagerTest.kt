package gk.kademlia.bitswap

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeUnit
import kotlinx.datetime.plus
import kotlin.test.*

class PinManagerTest {
    private lateinit var blockStore: InMemoryBlockStore
    private lateinit var pinManager: PinManager

    @BeforeTest
    fun setup() {
        blockStore = InMemoryBlockStore()
        pinManager = PinManager(blockStore)
    }

    @Test
    fun `test basic pin and unpin operations`() = runBlocking {
        // Add a block to the store
        val data = "test data".toByteArray()
        val cid = CID(data)
        assertTrue(blockStore.put(cid, data))

        // Test pinning
        assertTrue(pinManager.pin(cid))
        assertTrue(pinManager.isPinned(cid))

        // Test unpinning
        assertTrue(pinManager.unpin(cid))
        assertFalse(pinManager.isPinned(cid))
    }

    @Test
    fun `test pinning non-existent content`() = runBlocking {
        val cid = CID("non-existent".toByteArray())
        assertFalse(pinManager.pin(cid))
        assertFalse(pinManager.isPinned(cid))
    }

    @Test
    fun `test pin metadata and type`() = runBlocking {
        val data = "test data".toByteArray()
        val cid = CID(data)
        assertTrue(blockStore.put(cid, data))

        val metadata = mapOf("purpose" to "test", "owner" to "test-user")
        assertTrue(pinManager.pin(cid, type = PinType.Recursive, metadata = metadata))

        val pinInfo = pinManager.getPinInfo(cid)
        assertNotNull(pinInfo)
        assertEquals(PinType.Recursive, pinInfo.type)
        assertEquals(metadata, pinInfo.metadata)
        assertEquals(data.size.toLong(), pinInfo.size)
    }

    @Test
    fun `test pin expiration`() = runBlocking {
        val data = "test data".toByteArray()
        val cid = CID(data)
        assertTrue(blockStore.put(cid, data))

        // Pin with expiration in 1 second
        val expiresAt = Clock.System.now().plus(1, TimeUnit.SECONDS)
        assertTrue(pinManager.pin(cid, expiresAt = expiresAt))
        assertTrue(pinManager.isPinned(cid))

        // Wait for expiration
        Thread.sleep(1100) // Wait slightly more than 1 second

        // Pin should be expired
        assertFalse(pinManager.isPinned(cid))
        assertEquals(1, pinManager.cleanupExpiredPins())
    }

    @Test
    fun `test list pinned content`() = runBlocking {
        // Add multiple blocks
        val data1 = "test data 1".toByteArray()
        val data2 = "test data 2".toByteArray()
        val cid1 = CID(data1)
        val cid2 = CID(data2)
        assertTrue(blockStore.put(cid1, data1))
        assertTrue(blockStore.put(cid2, data2))

        // Pin both blocks
        assertTrue(pinManager.pin(cid1))
        assertTrue(pinManager.pin(cid2))

        // Test listing
        val pinnedCids = pinManager.listPinned()
        assertEquals(2, pinnedCids.size)
        assertTrue(pinnedCids.contains(cid1))
        assertTrue(pinnedCids.contains(cid2))

        // Test listing pin info
        val pinInfoList = pinManager.listPinInfo()
        assertEquals(2, pinInfoList.size)
        assertEquals(data1.size.toLong() + data2.size.toLong(), pinManager.getTotalPinnedSize())
    }

    @Test
    fun `test pin events`() = runBlocking {
        val data = "test data".toByteArray()
        val cid = CID(data)
        assertTrue(blockStore.put(cid, data))

        // Collect events
        val events = mutableListOf<PinEvent>()
        val job = kotlinx.coroutines.launch {
            pinManager.pinEvents().collect { events.add(it) }
        }

        // Pin and unpin
        assertTrue(pinManager.pin(cid))
        assertTrue(pinManager.unpin(cid))

        // Wait for events
        Thread.sleep(100)

        // Verify events
        assertEquals(2, events.size)
        assertTrue(events[0].isPinned)
        assertFalse(events[1].isPinned)
        assertEquals(cid, events[0].cid)
        assertEquals(cid, events[1].cid)

        job.cancel()
    }

    @Test
    fun `test recursive pin type`() = runBlocking {
        val data = "test data".toByteArray()
        val cid = CID(data)
        assertTrue(blockStore.put(cid, data))

        // Pin with recursive type
        assertTrue(pinManager.pin(cid, type = PinType.Recursive))

        val pinInfo = pinManager.getPinInfo(cid)
        assertNotNull(pinInfo)
        assertEquals(PinType.Recursive, pinInfo.type)
    }

    @Test
    fun `test cleanup expired pins`() = runBlocking {
        val data1 = "test data 1".toByteArray()
        val data2 = "test data 2".toByteArray()
        val cid1 = CID(data1)
        val cid2 = CID(data2)
        assertTrue(blockStore.put(cid1, data1))
        assertTrue(blockStore.put(cid2, data2))

        // Pin one with expiration, one without
        val expiresAt = Clock.System.now().plus(1, TimeUnit.SECONDS)
        assertTrue(pinManager.pin(cid1, expiresAt = expiresAt))
        assertTrue(pinManager.pin(cid2))

        // Wait for expiration
        Thread.sleep(1100)

        // Cleanup should only remove expired pin
        assertEquals(1, pinManager.cleanupExpiredPins())
        assertFalse(pinManager.isPinned(cid1))
        assertTrue(pinManager.isPinned(cid2))
    }
} 