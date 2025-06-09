package borg.trikeshed.net.quic

object QuicConstants {
    const val QUIC_VERSION_1: UInt = 0x00000001u // IETF QUIC Version 1
    // Salt for Initial Packets in QUIC v1 (RFC 9001, Section 5.2)
    val QUIC_V1_INITIAL_SALT: ByteArray = byteArrayOf(
        0x38, 0x76, 0x2c, 0xf7.toByte(), 0xf5.toByte(), 0x59.toByte(), 0x34.toByte(), 0xb3.toByte(),
        0x4d.toByte(), 0x17.toByte(), 0x9a.toByte(), 0xe6.toByte(), 0xa4.toByte(), 0xc8.toByte(),
        0x0c.toByte(), 0xad.toByte(), 0xcc.toByte(), 0xbb.toByte(), 0x7f.toByte(), 0x0a.toByte()
    )
    const val AEAD_TAG_LENGTH = 16 // Standard for AES-GCM and ChaCha20-Poly1305 in QUIC
    const val DEFAULT_MAX_ACK_DELAY_US: Long = 25_000L // Default max_ack_delay in microseconds (25ms), per RFC 9000 Section 13.2.1
}

/**
 * QUIC Packet Types (for the Long Header type field).
 * These are the 2 bits after the Fixed Bit and before Reserved Bits in the first byte.
 * Combined with Header Form (1st bit = 1 for Long) and Fixed Bit (2nd bit = 1),
 * the first byte will typically be:
 * Initial: 0xC0 to 0xCF (1100_XXXX where XXXX includes PN length)
 * 0-RTT:   0xD0 to 0xDF (1101_XXXX)
 * Handshake: 0xE0 to 0xEF (1110_XXXX)
 * Retry:   0xF0 to 0xFF (1111_XXXX)
 */
object QuicPacketType {
    const val INITIAL: UByte = 0x00u
    const val ZERORTT: UByte = 0x01u // 0-RTT Protected
    const val HANDSHAKE: UByte = 0x02u
    const val RETRY: UByte = 0x03u
}

// Other constants like Frame Types can be added here or in a separate file.
object QuicFrameType {
    const val PADDING: UByte = 0x00u
    const val PING: UByte = 0x01u
    const val ACK: UByte = 0x02u
    const val ACK_ECN: UByte = 0x03u // Explicitly defined for ACK with ECN
    const val RESET_STREAM: UByte = 0x04u
    const val STOP_SENDING: UByte = 0x05u
    const val CRYPTO: UByte = 0x06u
    const val NEW_TOKEN: UByte = 0x07u
    // STREAM frames: 0x08 to 0x0f
    const val MAX_DATA: UByte = 0x10u
    const val MAX_STREAM_DATA: UByte = 0x11u
    const val MAX_STREAMS_BIDI: UByte = 0x12u
    const val MAX_STREAMS_UNI: UByte = 0x13u
    const val DATA_BLOCKED: UByte = 0x14u
    const val STREAM_DATA_BLOCKED: UByte = 0x15u
    const val STREAMS_BLOCKED_BIDI: UByte = 0x16u
    const val STREAMS_BLOCKED_UNI: UByte = 0x17u
    const val NEW_CONNECTION_ID: UByte = 0x18u
    const val RETIRE_CONNECTION_ID: UByte = 0x19u
    const val PATH_CHALLENGE: UByte = 0x1au
    const val PATH_RESPONSE: UByte = 0x1bu
    const val CONNECTION_CLOSE_QUIC: UByte = 0x1cu // Transport-level close
    const val CONNECTION_CLOSE_APP: UByte = 0x1du  // Application-level close
    const val HANDSHAKE_DONE: UByte = 0x1eu
}
