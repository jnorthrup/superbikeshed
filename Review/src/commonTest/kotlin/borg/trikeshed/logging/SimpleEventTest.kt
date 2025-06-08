package borg.trikeshed.logging

import borg.trikeshed.io.ByteBufferFactory
import borg.trikeshed.io.ByteBuffer // For type hint
import kotlin.test.*

class SimpleEventTest {
    @Test
    fun testSerializeDeserializeHappyPath() {
        val originalEvent = SimpleEvent(12345L, 1, "TestPayload".encodeToByteArray())
        val serialized = originalEvent.serialize()

        // Ensure serialization produces a non-empty array
        assertTrue(serialized.isNotEmpty(), "Serialized byte array should not be empty")

        val buffer = ByteBufferFactory.wrap(serialized)
        val deserializedEvent = SimpleEvent.deserialize(buffer)

        assertNotNull(deserializedEvent, "Deserialized event should not be null")
        assertEquals(originalEvent.timestamp, deserializedEvent.timestamp, "Timestamp mismatch")
        assertEquals(originalEvent.type, deserializedEvent.type, "Type mismatch")
        assertTrue(originalEvent.payload.contentEquals(deserializedEvent.payload), "Payload mismatch")
        assertFalse(buffer.hasRemaining(), "Buffer should be fully consumed after deserialization")
    }

    @Test
    fun testDeserializeFromPartialHeader_NotEnoughForTimestamp() {
        val buffer = ByteBufferFactory.wrap(ByteArray(7)) // Not enough for Long (timestamp)
        val event = SimpleEvent.deserialize(buffer)
        assertNull(event, "Should be null if buffer is too short for a timestamp")
        assertEquals(0, buffer.position(), "Position should remain 0 on insufficient data for header")
    }

    @Test
    fun testDeserializeFromPartialHeader_NotEnoughForType() {
        val buffer = ByteBufferFactory.wrap(ByteArray(8 + 3)) // Enough for timestamp, but not for type
        buffer.putLong(123L) // Put a timestamp
        buffer.flip() // Prepare for reading

        val initialPosition = buffer.position()
        val event = SimpleEvent.deserialize(buffer)
        assertNull(event, "Should be null if buffer is too short for type")
        assertEquals(initialPosition, buffer.position(), "Position should be reset on insufficient data for header")
    }

    @Test
    fun testDeserializeFromPartialHeader_NotEnoughForPayloadLength() {
        val buffer = ByteBufferFactory.wrap(ByteArray(8 + 4 + 3)) // Enough for ts, type, but not for payloadLength
        buffer.putLong(123L)
        buffer.putInt(1)
        buffer.flip()

        val initialPosition = buffer.position()
        val event = SimpleEvent.deserialize(buffer)
        assertNull(event, "Should be null if buffer is too short for payloadLength")
        assertEquals(initialPosition, buffer.position(), "Position should be reset on insufficient data for header")
    }

    @Test
    fun testDeserializeWithNegativePayloadLength() {
        val buffer = ByteBufferFactory.allocate(16) // Enough for header
        buffer.putLong(12345L)
        buffer.putInt(1)
        buffer.putInt(-100) // Negative payload length
        buffer.flip()

        val initialPosition = buffer.position()
        val event = SimpleEvent.deserialize(buffer)
        assertNull(event, "Should return null for negative payload length")
        assertEquals(initialPosition, buffer.position(), "Position should be reset on negative payload length")
    }


    @Test
    fun testDeserializeFromPartialPayload() {
        val originalEvent = SimpleEvent(12345L, 1, "TestPayloadMoreData".encodeToByteArray())
        val serialized = originalEvent.serialize()

        // Buffer has full header but only partial payload
        // Header is 8 (long) + 4 (int) + 4 (int) = 16 bytes
        val partialPayloadBuffer = ByteBufferFactory.wrap(serialized.copyOfRange(0, serialized.size - 5))

        val initialBufferPosition = partialPayloadBuffer.position() // Should be 0
        val event = SimpleEvent.deserialize(partialPayloadBuffer)
        assertNull(event, "Should return null for partial payload")
        // According to SimpleEvent.deserialize, position should be reset to before reading this event's header
        assertEquals(initialBufferPosition, partialPayloadBuffer.position(), "Position should be reset on partial payload fail")
    }

    @Test
    fun testDeserializeWithExtraData() {
        val event1 = SimpleEvent(1L, 1, "Event1".encodeToByteArray())
        val event2 = SimpleEvent(2L, 2, "Event2".encodeToByteArray())
        val combinedBytes = event1.serialize() + event2.serialize()
        val buffer = ByteBufferFactory.wrap(combinedBytes)

        val deserialized1 = SimpleEvent.deserialize(buffer)
        assertNotNull(deserialized1, "First deserialized event should not be null")
        assertEquals(event1.timestamp, deserialized1.timestamp)
        assertTrue(event1.payload.contentEquals(deserialized1.payload))

        val deserialized2 = SimpleEvent.deserialize(buffer)
        assertNotNull(deserialized2, "Second deserialized event should not be null")
        assertEquals(event2.timestamp, deserialized2.timestamp)
        assertTrue(event2.payload.contentEquals(deserialized2.payload))

        assertFalse(buffer.hasRemaining(), "Buffer should be fully consumed after both deserializations")
    }

    @Test
    fun testDeserializeEmptyPayload() {
        val originalEvent = SimpleEvent(123L, 1, byteArrayOf())
        val serialized = originalEvent.serialize()
        val buffer = ByteBufferFactory.wrap(serialized)
        val deserializedEvent = SimpleEvent.deserialize(buffer)

        assertNotNull(deserializedEvent, "Deserialized event with empty payload should not be null")
        assertEquals(originalEvent.timestamp, deserializedEvent.timestamp)
        assertEquals(originalEvent.type, deserializedEvent.type)
        assertEquals(0, deserializedEvent.payload.size, "Payload size should be 0")
        assertTrue(originalEvent.payload.contentEquals(deserializedEvent.payload), "Payloads should be contentEquals")
        assertFalse(buffer.hasRemaining(), "Buffer should be fully consumed")
    }

    @Test
    fun testDeserializeFromEmptyBuffer() {
        val emptyBuffer = ByteBufferFactory.allocate(0)
        val event = SimpleEvent.deserialize(emptyBuffer)
        assertNull(event, "Should return null when deserializing from an empty buffer")
    }

    @Test
    fun testDeserializeBufferJustEnoughForHeaderNoPayload() {
        // Event with empty payload. Serialized size = 8 (long) + 4 (int) + 4 (int) = 16 bytes.
        val eventWithEmptyPayload = SimpleEvent(1L, 1, byteArrayOf())
        val serialized = eventWithEmptyPayload.serialize()
        assertEquals(16, serialized.size)

        val buffer = ByteBufferFactory.wrap(serialized)
        val deserialized = SimpleEvent.deserialize(buffer)
        assertNotNull(deserialized)
        assertEquals(0, deserialized.payload.size)
        assertFalse(buffer.hasRemaining())
    }
}
