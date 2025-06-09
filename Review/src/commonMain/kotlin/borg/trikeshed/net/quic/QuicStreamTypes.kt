package borg.trikeshed.net.quic

/**
 * Represents the various states a QUIC stream can be in.
 */
enum class QuicStreamState {
    /** Initial state before any operations. */
    IDLE,
    /** Stream is open for sending and/or receiving data. */
    OPEN,
    /** Local endpoint has sent a FIN, indicating no more data will be sent from this side. */
    LOCAL_HALF_CLOSED,
    /** Remote endpoint has sent a FIN, indicating no more data will be received on this side. */
    REMOTE_HALF_CLOSED,
    /** Both sides have sent FINs, or the stream has been reset. No more data can be sent or received. */
    CLOSED,
    /** Local endpoint has sent a RESET_STREAM frame. */
    RESET_SENT,
    /** Local endpoint has received a RESET_STREAM frame from the peer. */
    RESET_RECEIVED
    // DATA_SENT, DATA_RECEIVED could be considered implicit in OPEN or managed via offsets/flags.
}

/**
 * Defines which endpoint initiated a stream.
 */
enum class StreamInitiatorRole {
    CLIENT,
    SERVER
}

/**
 * Defines the directionality of a stream.
 */
enum class StreamDirectionality {
    UNIDIRECTIONAL,
    BIDIRECTIONAL
}

/**
 * Represents the resolved type of a stream, including its directionality and initiator.
 * @property directionality Whether the stream is unidirectional or bidirectional.
 * @property initiator Which role (Client or Server) initiated this stream.
 */
data class ResolvedStreamType(
    val directionality: StreamDirectionality,
    val initiator: StreamInitiatorRole
)

/**
 * Resolves the type of a stream (directionality and initiator) based on its Stream ID
 * and the role of the local endpoint.
 *
 * Stream ID encoding:
 * - Client-initiated bidirectional: ID is even (0, 4, 8...). (Type 0b00)
 * - Server-initiated bidirectional: ID is odd (1, 5, 9...). (Type 0b01)
 * - Client-initiated unidirectional: ID is 2 mod 4 (2, 6, 10...). (Type 0b10)
 * - Server-initiated unidirectional: ID is 3 mod 4 (3, 7, 11...). (Type 0b11)
 *
 * @param streamId The ID of the stream.
 * @return [ResolvedStreamType] indicating the stream's properties.
 */
fun resolveStreamType(streamId: Long): ResolvedStreamType {
    if (streamId < 0) throw IllegalArgumentException("Stream ID cannot be negative.")

    val typeBits = streamId and 0x03L // Get the last two bits
    return when (typeBits) {
        0x00L -> ResolvedStreamType(StreamDirectionality.BIDIRECTIONAL, StreamInitiatorRole.CLIENT)
        0x01L -> ResolvedStreamType(StreamDirectionality.BIDIRECTIONAL, StreamInitiatorRole.SERVER)
        0x02L -> ResolvedStreamType(StreamDirectionality.UNIDIRECTIONAL, StreamInitiatorRole.CLIENT)
        0x03L -> ResolvedStreamType(StreamDirectionality.UNIDIRECTIONAL, StreamInitiatorRole.SERVER)
        else -> throw IllegalStateException("Unreachable: streamId and 0x03L can only result in 0, 1, 2, or 3.")
    }
}

/**
 * Determines if the local endpoint can initiate a stream of a given type.
 * This is a convenience function based on the stream ID encoding rules.
 *
 * @param localRole The role of the local endpoint (Client or Server).
 * @param directionality The desired directionality of the stream.
 * @param initiator The desired initiator of the stream.
 * @return True if the local endpoint can initiate this type of stream, false otherwise.
 */
fun canLocalInitiateStream(localRole: StreamInitiatorRole, directionality: StreamDirectionality, initiator: StreamInitiatorRole): Boolean {
    return localRole == initiator
}

/**
 * Gets the stream type bits (lowest two bits) for a given directionality and initiator.
 * This is useful for constructing new stream IDs.
 *
 * @param directionality The stream's directionality.
 * @param initiator The stream's initiator.
 * @return The type bits (0b00, 0b01, 0b10, or 0b11) for the stream ID.
 */
fun getStreamTypeBits(directionality: StreamDirectionality, initiator: StreamInitiatorRole): Long {
    return when (initiator) {
        StreamInitiatorRole.CLIENT -> if (directionality == StreamDirectionality.BIDIRECTIONAL) 0x00L else 0x02L
        StreamInitiatorRole.SERVER -> if (directionality == StreamDirectionality.BIDIRECTIONAL) 0x01L else 0x03L
    }
}
