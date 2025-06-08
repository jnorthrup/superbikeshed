package borg.trikeshed.logging

import borg.trikeshed.io.slabs.MemorySlabManagerService
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull // For checking slab presence

// Expect function for getting the platform-specific service instance for tests
// The defaultSlabSize parameter allows tests to control slab capacity.
expect fun getTestMemorySlabManagerService(defaultSlabSize: Int = 1024): MemorySlabManagerService

class SlabEventWriterTest {

    @Test
    fun testLogAndReadEvents() {
        val slabManager = getTestMemorySlabManagerService(defaultSlabSize = 1024) // Sufficiently large slab
        val writer = SlabEventWriter(slabManager, EmptyCoroutineContext)

        val currentTime = 1678886400000L // Example fixed timestamp

        val event1Payload = "Hello Slab 1".encodeToByteArray()
        val event1 = SimpleEvent(currentTime, 1, event1Payload)
        writer.logEvent(event1)

        val event2Payload = "Hello Slab 2 with more data to ensure it fits".encodeToByteArray()
        val event2 = SimpleEvent(currentTime + 10, 2, event2Payload)
        writer.logEvent(event2)

        val readEvents = writer.readEventsFromCurrentSlab()

        assertEquals(2, readEvents.size, "Should have read 2 events")

        assertEquals(event1.timestamp, readEvents[0].timestamp)
        assertEquals(event1.type, readEvents[0].type)
        assertTrue(event1.payload.contentEquals(readEvents[0].payload), "Event 1 payload mismatch")

        assertEquals(event2.timestamp, readEvents[1].timestamp)
        assertEquals(event2.type, readEvents[1].type)
        assertTrue(event2.payload.contentEquals(readEvents[1].payload), "Event 2 payload mismatch")

        writer.close()
    }

    @Test
    fun testSlabRollover() {
        // Use a small slab size to force rollover.
        // Event: Long (8) + Int (4) + Int (4) + Payload
        // Header size = 16 bytes.
        val slabSize = 50
        val slabManager = getTestMemorySlabManagerService(defaultSlabSize = slabSize)
        val writer = SlabEventWriter(slabManager, EmptyCoroutineContext)

        // Event 1: payload "Event A" (7 bytes). Serialized size = 16 + 7 = 23 bytes.
        val payload1 = "Event A".encodeToByteArray()
        val event1 = SimpleEvent(1L, 1, payload1)
        val event1Serialized = event1.serialize()
        assertEquals(23, event1Serialized.size, "Event 1 serialized size check")
        writer.logEvent(event1) // Slab remaining: 50 - 23 = 27 bytes

        val slabAfterEvent1 = writer.getAllSlabsForExternalProcessing().firstOrNull()
        assertNotNull(slabAfterEvent1, "Slab should exist after logging event 1")
        assertEquals(event1Serialized.size, slabAfterEvent1.position(), "Position after event 1 should be its size")

        // Event 2: payload "Event B is longer" (17 bytes). Serialized size = 16 + 17 = 33 bytes.
        // Current slab remaining is 27 bytes. 33 > 27, so this should force a new slab.
        val payload2 = "Event B is longer".encodeToByteArray()
        val event2 = SimpleEvent(2L, 2, payload2)
        val event2Serialized = event2.serialize()
        assertEquals(33, event2Serialized.size, "Event 2 serialized size check")
        writer.logEvent(event2)

        // After rollover, getAllSlabsForExternalProcessing (in its current simple form) returns the NEW active slab.
        val slabAfterEvent2 = writer.getAllSlabsForExternalProcessing().firstOrNull()
        assertNotNull(slabAfterEvent2, "A new slab should be active after event 2 caused rollover")

        // The new slab should contain only event2.
        assertEquals(event2Serialized.size, slabAfterEvent2.position(), "Position in new slab should be size of event 2")

        // Verify that the old slab is not the same as the new slab, if we could access it.
        // For now, we rely on the position of the current slab.
        // assertTrue(slabAfterEvent1 !== slabAfterEvent2, "A new slab instance should have been acquired") // This check is not possible with current getAllSlabs...

        val readEventsFromNewSlab = writer.readEventsFromCurrentSlab()
        assertEquals(1, readEventsFromNewSlab.size, "New slab should contain 1 event (event 2)")
        assertEquals(event2.timestamp, readEventsFromNewSlab[0].timestamp)
        assertEquals(event2.type, readEventsFromNewSlab[0].type)
        assertTrue(event2.payload.contentEquals(readEventsFromNewSlab[0].payload), "Event 2 payload mismatch in new slab")

        writer.close()
    }

    @Test
    fun testLogEventWithDirectParameters() {
        val slabManager = getTestMemorySlabManagerService()
        val writer = SlabEventWriter(slabManager, EmptyCoroutineContext)
        val currentTime = System.currentTimeMillis()
        val payload = "Direct Log".encodeToByteArray()

        writer.logEvent(currentTime, 3, payload)
        val readEvents = writer.readEventsFromCurrentSlab()

        assertEquals(1, readEvents.size)
        assertEquals(currentTime, readEvents[0].timestamp)
        assertEquals(3, readEvents[0].type)
        assertTrue(payload.contentEquals(readEvents[0].payload))
        writer.close()
    }

    @Test
    fun testEmptyPayload() {
        val slabManager = getTestMemorySlabManagerService()
        val writer = SlabEventWriter(slabManager, EmptyCoroutineContext)
        val currentTime = System.currentTimeMillis()
        val emptyPayload = ByteArray(0)

        writer.logEvent(SimpleEvent(currentTime, 4, emptyPayload))
        val readEvents = writer.readEventsFromCurrentSlab()

        assertEquals(1, readEvents.size)
        assertEquals(currentTime, readEvents[0].timestamp)
        assertEquals(4, readEvents[0].type)
        assertTrue(emptyPayload.contentEquals(readEvents[0].payload))
        assertEquals(0, readEvents[0].payload.size)
        writer.close()
    }

    @Test
    fun testReadingFromEmptySlab() {
        val slabManager = getTestMemorySlabManagerService()
        val writer = SlabEventWriter(slabManager, EmptyCoroutineContext)
        // Don't log any events
        val readEvents = writer.readEventsFromCurrentSlab()
        assertTrue(readEvents.isEmpty(), "Should read no events from an empty slab")
        writer.close()
    }

    @Test
    fun testSlabExactlyFullThenRollover() {
        // Header size = 16 bytes.
        val slabSize = 16 + 10 // Slab size is 26 bytes.
        val slabManager = getTestMemorySlabManagerService(defaultSlabSize = slabSize)
        val writer = SlabEventWriter(slabManager, EmptyCoroutineContext)

        // Event 1: payload "Ten Bytes." (10 bytes). Serialized size = 16 + 10 = 26 bytes.
        // This should exactly fill the first slab.
        val payload1 = "Ten Bytes.".encodeToByteArray()
        assertEquals(10, payload1.size)
        val event1 = SimpleEvent(1L, 1, payload1)
        writer.logEvent(event1)

        val slabAfterEvent1 = writer.getAllSlabsForExternalProcessing().firstOrNull()
        assertNotNull(slabAfterEvent1)
        assertEquals(slabSize, slabAfterEvent1.position(), "Slab should be exactly full after event 1")
        assertTrue(slabAfterEvent1.isFull(), "Slab.isFull() should be true")

        // Event 2: any event. This should go into a new slab.
        val payload2 = "Event B".encodeToByteArray()
        val event2 = SimpleEvent(2L, 2, payload2)
        val event2SerializedSize = event2.serialize().size
        writer.logEvent(event2)

        val slabAfterEvent2 = writer.getAllSlabsForExternalProcessing().firstOrNull()
        assertNotNull(slabAfterEvent2)
        // assertTrue(slabAfterEvent1 !== slabAfterEvent2, "Should be a new slab instance") // Not checkable with current API
        assertEquals(event2SerializedSize, slabAfterEvent2.position(), "Position in new slab should be size of event 2")

        val eventsInNewSlab = writer.readEventsFromCurrentSlab()
        assertEquals(1, eventsInNewSlab.size)
        assertEquals(event2.timestamp, eventsInNewSlab[0].timestamp)

        writer.close()
    }
}
