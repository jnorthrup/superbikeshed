package borg.trikeshed.net.quic

typealias ConnectionId = ByteArray
typealias PacketNumber = ULong

enum class QuicPacketType(val typeValue: UByte) {
  INITIAL(0x0u),
  HANDSHAKE(0x1u),
  ZERO_RTT(0x2u),
  RETRY(0x3u)
}

enum class QuicFrameType(val value: UByte) {
  PADDING(0x00u),
  PING(0x01u),
  ACK(0x02u),               // ACK frame without ECN
  ACK_ECN(0x03u),           // ACK frame with ECN
  RESET_STREAM(0x04u),
  STOP_SENDING(0x05u),
  CRYPTO(0x06u),
  NEW_TOKEN(0x07u),
  // STREAM frames: 0x08-0x0f. Bits 0-2 (FIN, LEN, OFF) modify behavior.
  // For enum purposes, we can define a base or common variants.
  // The parser/serializer will handle the specific bits.
  // For now, keep STREAM as a general type, actual type determined by bits.
  STREAM_BASE(0x08u), // Represents the range 0x08-0x0f

  MAX_DATA(0x10u),
  MAX_STREAM_DATA(0x11u),
  MAX_STREAMS_BIDI(0x12u),    // Max Streams (bidirectional)
  MAX_STREAMS_UNI(0x13u),     // Max Streams (unidirectional)
  DATA_BLOCKED(0x14u),
  STREAM_DATA_BLOCKED(0x15u),
  STREAMS_BLOCKED_BIDI(0x16u), // Streams Blocked (bidirectional)
  STREAMS_BLOCKED_UNI(0x17u),  // Streams Blocked (unidirectional)
  NEW_CONNECTION_ID(0x18u),
  RETIRE_CONNECTION_ID(0x19u),
  PATH_CHALLENGE(0x1au),
  PATH_RESPONSE(0x1bu),
  CONNECTION_CLOSE_QUIC(0x1cu), // Transport error
  CONNECTION_CLOSE_APP(0x1du),  // Application error
  HANDSHAKE_DONE(0x1eu);
  // EXTENSION_RESERVED_1 (0x20u), // Example for future extension points
  // EXTENSION_RESERVED_2 (0x21u);


  companion object {
    fun fromValue(value: UByte): QuicFrameType? {
        // Handle STREAM frame range
        if (value >= STREAM_BASE.value && value <= 0x0fu) {
            return STREAM_BASE
        }
        return entries.find { it.value == value }
    }
  }
}

/** Represents a bandwidth estimate in bytes per second. */
typealias BandwidthEstimate = Long

/** Represents a round-trip time in microseconds. */
typealias RTTMicros = Long

/** Represents a congestion window in bytes. */
typealias CongestionWindow = Long

/**
 * QUIC Transport Error Codes (RFC 9000 Section 22.1).
 * Values are varints.
 */
@Suppress("unused")
enum class TransportErrorCode(val value: Long) {
    NO_ERROR(0x00L),
    INTERNAL_ERROR(0x01L),
    CONNECTION_REFUSED(0x02L),
    FLOW_CONTROL_ERROR(0x03L),
    STREAM_LIMIT_ERROR(0x04L),
    STREAM_STATE_ERROR(0x05L),
    FINAL_SIZE_ERROR(0x06L),
    FRAME_ENCODING_ERROR(0x07L),
    TRANSPORT_PARAMETER_ERROR(0x08L),
    CONNECTION_ID_LIMIT_ERROR(0x09L),
    PROTOCOL_VIOLATION(0x0AL),
    INVALID_TOKEN(0x0BL),
    APPLICATION_ERROR(0x0CL),
    CRYPTO_BUFFER_EXCEEDED(0x0DL),
    KEY_UPDATE_ERROR(0x0EL),
    AEAD_LIMIT_REACHED(0x0FL),
    NO_VIABLE_PATH(0x10L),
    INVALID_MIGRATION(0x11L), // Added from RFC 9000

    CRYPTO_ERROR_TLS_ALERT_BASE(0x0100L),
    // Example specific crypto error if needed beyond alert mapping
    // CRYPTO_ERROR_HANDSHAKE_FAILURE(CRYPTO_ERROR_TLS_ALERT_BASE.value + 0x28L); // Example: TLS alert handshake_failure (40)

    // Placeholder for future error codes if any get standardized with specific values in this range
    RESERVED_ERROR_1(0x7fff_ffff_ffff_fffdL), // Example, not standard
    RESERVED_ERROR_2(0x7fff_ffff_ffff_ffeL); // Example, not standard

    companion object {
        fun fromValue(value: Long): TransportErrorCode? = entries.find { it.value == value }

        fun isCryptoError(code: Long): Boolean {
            return code >= CRYPTO_ERROR_TLS_ALERT_BASE.value && code <= (CRYPTO_ERROR_TLS_ALERT_BASE.value + 0xFFL)
        }
    }
}
