package borg.trikeshed.logging

import borg.trikeshed.nio.ByteBuffer // Required for deserialize
import borg.trikeshed.nio.ByteBufferFactory // Required for serialize

/**
 * A simple data class representing an event with a timestamp, type, and a byte array payload.
 * This class is used as a sample data structure for demonstrating storage in [borg.trikeshed.nio.slabs.MemorySlab] instances
 * via the [SlabEventWriter].
 *
 * @property timestamp The time at which the event occurred, typically in milliseconds since epoch.
 * @property type An integer representing the type of event (e.g., 0 for generic message, 1 for system event).
 * @property payload The actual data content of the event as a ByteArray.
 */
data class SimpleEvent(
    val timestamp: Long,
    val type: Int,
    val payload: ByteArray
) {
    /**
     * Serializes the [SimpleEvent] into a ByteArray.
     * The binary format is as follows:
     * - Timestamp (Long): 8 bytes
     * - Type (Int): 4 bytes
     * - Payload Length (Int): 4 bytes (length of the upcoming payload)
     * - Payload (ByteArray): variable number of bytes as specified by Payload Length.
     *
     * @return A ByteArray containing the serialized event data.
     */
    fun serialize(): ByteArray {
        val payloadLength = payload.size
        // Calculate size: Long (8) + Int (4) + Int (4) + payload variable length
        val buffer = ByteBufferFactory.allocate(8 + 4 + 4 + payloadLength)
        buffer.putLong(timestamp)
        buffer.putInt(type)
        buffer.putInt(payloadLength)
        buffer.put(payload)

        // Extract bytes from buffer
        val result = ByteArray(buffer.position())
        buffer.flip() // Prepare for reading from the beginning
        buffer.get(result) // Read data into the result ByteArray
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SimpleEvent

        if (timestamp != other.timestamp) return false
        if (type != other.type) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + type
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        /**
         * Deserializes a [SimpleEvent] from the given [source] ByteBuffer.
         * Reads data according to the format defined in [serialize].
         *
         * @param source The ByteBuffer to read the event data from. The buffer's position will be advanced
         *               by the number of bytes read for the event.
         * @return A deserialized [SimpleEvent] if successful. Returns `null` if the [source] buffer
         *         contains insufficient data for a complete event (either for the header or the payload),
         *         or if the payload length is found to be negative.
         *         If deserialization fails due to insufficient data for the header or payload,
         *         this method attempts to reset the buffer's position to its state before the
         *         deserialization attempt began for this specific event. This allows for robust
         *         processing of streams or buffers that might contain partial data.
         */
        fun deserialize(source: ByteBuffer): SimpleEvent? {
            // Minimum size for header: Long (8 for timestamp) + Int (4 for type) + Int (4 for payloadLength)
            val headerSize = 8 + 4 + 4
            if (source.remaining() < headerSize) return null

            val initialPosition = source.position() // Save position in case of partial read or invalid data

            val timestamp = source.getLong()
            val type = source.getInt()
            val payloadLength = source.getInt()

            if (payloadLength < 0) { // Basic sanity check for payload length
                source.position(initialPosition) // Rewind to before this attempt
                return null // Invalid payload length, indicates corruption
            }

            if (source.remaining() < payloadLength) {
                // Not enough data for payload, might indicate corruption or incomplete write
                // Reset position to before this attempt to allow other processing or future reads
                source.position(initialPosition)
                return null
            }
            val payload = ByteArray(payloadLength)
            source.get(payload)
            return SimpleEvent(timestamp, type, payload)
        }
    }
}
