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
  ACK(0x02u),
  ACK_ECN(0x03u),
  CRYPTO(0x06u),
  STREAM(0x08u),
  CONNECTION_CLOSE_QUIC(0x1Cu), // Renamed from CONNECTION_CLOSE, Transport error
  CONNECTION_CLOSE_APP(0x1Du),  // Application error
  HANDSHAKE_DONE(0x1eu);

  companion object {
    fun fromValue(value: UByte): QuicFrameType? = entries.find { it.value == value }
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
@Suppress("unused") // Suppress warnings for unused enum values if not all are immediately referenced
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
    APPLICATION_ERROR(0x0CL), // Used when an application callback indicates an error
    CRYPTO_BUFFER_EXCEEDED(0x0DL),
    KEY_UPDATE_ERROR(0x0EL),
    AEAD_LIMIT_REACHED(0x0FL),
    NO_VIABLE_PATH(0x10L),

    // CRYPTO_ERROR_BASE is 0x0100, so up to 0x01FF are crypto errors.
    // Using a simplified range here for specific crypto errors if needed.
    CRYPTO_ERROR_TLS_ALERT_BASE(0x0100L), // For mapping TLS alerts
    CRYPTO_ERROR_HANDSHAKE_FAILURE(0x0128L); // Example: TLS alert handshake_failure (40) + base

    companion object {
        fun fromValue(value: Long): TransportErrorCode? = entries.find { it.value == value }
    }
}
