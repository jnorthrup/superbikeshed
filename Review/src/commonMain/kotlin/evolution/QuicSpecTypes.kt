package evolution

import kotlin.jvm.JvmInline

// QUIC Type Aliases and Value Classes based on IETF RFC Terminology

// --- Identifiers ---

/**
 * A QUIC Connection ID. Used to identify a QUIC connection.
 * RFC 9000: "Endpoints use one or more connection IDs to ensure that packets are routed to the correct recipient."
 */
@JvmInline
value class ConnectionID(val value: ByteArray) {
    // Consider adding a factory method for fixed-length IDs or specific representations
    fun toHexString(): String = value.joinToString("") { "%02x".format(it) }

    companion object {
        fun fromHexString(hex: String): ConnectionID {
            val bytes = ByteArray(hex.length / 2)
            for (i in bytes.indices) {
                val index = i * 2
                bytes[i] = hex.substring(index, index + 2).toInt(16).toByte()
            }
            return ConnectionID(bytes)
        }
    }
}

/**
 * A QUIC Stream ID. Used to identify a stream within a connection.
 * RFC 9000: "Streams are identified by a Stream ID"
 */
@JvmInline
value class StreamID(val value: ULong)

/**
 * A QUIC Packet Number. Each QUIC packet has a packet number.
 * RFC 9000: "Packet numbers are integers in the range 0 to 2^62-1."
 */
@JvmInline
value class PacketNumber(val value: ULong) {
    init {
        require(value < (1UL shl 62)) { "PacketNumber must be less than 2^62" }
    }
}

// Simple typealias for a generic QUIC Tag (often used for cryptographic tags)
typealias QuicTag = ByteArray

// --- Frame Types ---

/**
 * Represents a QUIC Frame Type.
 * RFC 9000: Section 12.4. Frame Types
 * Using a UByte as frame types are variable-length integers, but often fit in a byte.
 * A value class provides type safety over a raw UByte.
 */
@JvmInline
value class FrameType(val value: UByte) {
    companion object {
        // Common Frame Types (non-exhaustive)
        val PADDING: FrameType = FrameType(0x00u)
        val PING: FrameType = FrameType(0x01u)
        val ACK: FrameType = FrameType(0x02u) // and 0x03 for ACK with ECN
        val RESET_STREAM: FrameType = FrameType(0x04u)
        val STOP_SENDING: FrameType = FrameType(0x05u)
        val CRYPTO: FrameType = FrameType(0x06u)
        val NEW_TOKEN: FrameType = FrameType(0x07u)
        val STREAM: FrameType = FrameType(0x08u) // through 0x0f (variants with FIN, LEN, OFF bits)
        val MAX_DATA: FrameType = FrameType(0x10u)
        val MAX_STREAM_DATA: FrameType = FrameType(0x11u)
        val MAX_STREAMS_BIDI: FrameType = FrameType(0x12u)
        val MAX_STREAMS_UNI: FrameType = FrameType(0x13u)
        val DATA_BLOCKED: FrameType = FrameType(0x14u)
        val STREAM_DATA_BLOCKED: FrameType = FrameType(0x15u)
        val STREAMS_BLOCKED_BIDI: FrameType = FrameType(0x16u)
        val STREAMS_BLOCKED_UNI: FrameType = FrameType(0x17u)
        val NEW_CONNECTION_ID: FrameType = FrameType(0x18u)
        val RETIRE_CONNECTION_ID: FrameType = FrameType(0x19u)
        val PATH_CHALLENGE: FrameType = FrameType(0x1au)
        val PATH_RESPONSE: FrameType = FrameType(0x1bu)
        val CONNECTION_CLOSE: FrameType = FrameType(0x1cu) // and 0x1d for application close
        val HANDSHAKE_DONE: FrameType = FrameType(0x1eu)
    }
}

/**
 * Represents the data payload of a STREAM frame.
 * This is a simple wrapper around a ByteArray for type safety.
 */
@JvmInline
value class StreamFrameData(val payload: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as StreamFrameData
        return payload.contentEquals(other.payload)
    }
    override fun hashCode(): Int = payload.contentHashCode()
}

/**
 * Represents a CRYPTO frame, which carries cryptographic handshake messages.
 */
data class CryptoFrame(
    val offset: ULong,
    val length: ULong, // Length of the crypto data
    val cryptoData: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as CryptoFrame
        if (offset != other.offset) return false
        if (length != other.length) return false
        if (!cryptoData.contentEquals(other.cryptoData)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = offset.hashCode()
        result = 31 * result + length.hashCode()
        result = 31 * result + cryptoData.contentHashCode()
        return result
    }
}

/**
 * Represents an ACK frame, acknowledging received packets.
 * This is a simplified example; a full ACK frame is more complex.
 */
data class AckFrame(
    val largestAcknowledged: PacketNumber,
    val ackDelay: ULong,
    val ackRanges: List<AckRange> // Simplified
)

data class AckRange(val gap: ULong, val ackedCount: ULong)


// --- Transport Parameters ---

/**
 * Represents a QUIC Transport Parameter ID.
 * RFC 9000: Section 18. QUIC Transport Parameters are identified by a name and a numeric identifier.
 * Transport Parameter IDs are variable-length integers. ULong can represent this.
 */
@JvmInline
value class TransportParameterID(val id: ULong) {
    companion object {
        // Common Transport Parameter IDs (non-exhaustive from RFC 9000, Section 18.2)
        val ORIGINAL_DESTINATION_CONNECTION_ID: TransportParameterID = TransportParameterID(0x00uL)
        val MAX_IDLE_TIMEOUT: TransportParameterID = TransportParameterID(0x01uL)
        val STATELESS_RESET_TOKEN: TransportParameterID = TransportParameterID(0x02uL)
        val MAX_UDP_PAYLOAD_SIZE: TransportParameterID = TransportParameterID(0x03uL)
        val INITIAL_MAX_DATA: TransportParameterID = TransportParameterID(0x04uL)
        val INITIAL_MAX_STREAM_DATA_BIDI_LOCAL: TransportParameterID = TransportParameterID(0x05uL)
        val INITIAL_MAX_STREAM_DATA_BIDI_REMOTE: TransportParameterID = TransportParameterID(0x06uL)
        val INITIAL_MAX_STREAM_DATA_UNI: TransportParameterID = TransportParameterID(0x07uL)
        val INITIAL_MAX_STREAMS_BIDI: TransportParameterID = TransportParameterID(0x08uL)
        val INITIAL_MAX_STREAMS_UNI: TransportParameterID = TransportParameterID(0x09uL)
        val ACK_DELAY_EXPONENT: TransportParameterID = TransportParameterID(0x0auL)
        val MAX_ACK_DELAY: TransportParameterID = TransportParameterID(0x0buL)
        val DISABLE_ACTIVE_MIGRATION: TransportParameterID = TransportParameterID(0x0cuL)
        val PREFERRED_ADDRESS: TransportParameterID = TransportParameterID(0x0duL)
        val ACTIVE_CONNECTION_ID_LIMIT: TransportParameterID = TransportParameterID(0x0euL)
        val INITIAL_SOURCE_CONNECTION_ID: TransportParameterID = TransportParameterID(0x0fuL)
        val RETRY_SOURCE_CONNECTION_ID: TransportParameterID = TransportParameterID(0x10uL)
    }
}

/**
 * Represents the value for the 'initial_max_data' transport parameter.
 * RFC 9000: "The initial_max_data transport parameter (0x04) contains the initial value for
 * the maximum amount of data that can be sent on the connection."
 */
@JvmInline
value class InitialMaxData(val value: ULong)

/**
 * Represents the value for the 'max_idle_timeout' transport parameter in milliseconds.
 */
@JvmInline
value class MaxIdleTimeout(val milliseconds: ULong)

/**
 * Represents the value for the 'ack_delay_exponent' transport parameter.
 */
@JvmInline
value class AckDelayExponent(val value: UByte) {
    init {
        require(value <= 20u) { "ack_delay_exponent must be <= 20" }
    }
    companion object {
        val DEFAULT = AckDelayExponent(3u)
    }
}


// --- Cryptography Related ---

/**
 * Represents an AEAD (Authenticated Encryption with Associated Data) algorithm.
 * RFC 8446 (TLS 1.3) and RFC 9001 (QUIC TLS Usage) define AEADs.
 */
enum class AEADAlgorithm {
    AES_128_GCM,
    AES_256_GCM,
    CHACHA20_POLY1305;
    // Potentially add more as specified by relevant RFCs or extensions
}

/**
 * Represents a label used in HKDF (HMAC-based Key Derivation Function).
 * RFC 9001: Section 5. HKDF labels are byte strings.
 */
@JvmInline
value class HKDFLabel(val value: ByteArray) {
    fun asString(): String = value.decodeToString() // Assumes UTF-8, adjust if needed

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false // Using javaClass for KMP compatibility, consider this::class
        other as HKDFLabel
        if (!value.contentEquals(other.value)) return false
        return true
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }
}

// --- Error Codes ---

/**
 * Represents a QUIC Transport Error Code.
 * RFC 9000: Section 20. Error codes are variable-length integers.
 * ULong can represent this.
 */
@JvmInline
value class TransportErrorCode(val code: ULong) {
    companion object {
        // Common Transport Error Codes (non-exhaustive from RFC 9000, Section 20.1)
        val NO_ERROR: TransportErrorCode = TransportErrorCode(0x0uL)
        val INTERNAL_ERROR: TransportErrorCode = TransportErrorCode(0x1uL)
        val CONNECTION_REFUSED: TransportErrorCode = TransportErrorCode(0x2uL)
        val FLOW_CONTROL_ERROR: TransportErrorCode = TransportErrorCode(0x3uL)
        val STREAM_LIMIT_ERROR: TransportErrorCode = TransportErrorCode(0x4uL)
        val STREAM_STATE_ERROR: TransportErrorCode = TransportErrorCode(0x5uL)
        val FINAL_SIZE_ERROR: TransportErrorCode = TransportErrorCode(0x6uL)
        val FRAME_ENCODING_ERROR: TransportErrorCode = TransportErrorCode(0x7uL)
        val TRANSPORT_PARAMETER_ERROR: TransportErrorCode = TransportErrorCode(0x8uL)
        val CONNECTION_ID_LIMIT_ERROR: TransportErrorCode = TransportErrorCode(0x9uL)
        val PROTOCOL_VIOLATION: TransportErrorCode = TransportErrorCode(0xAuL)
        val INVALID_TOKEN: TransportErrorCode = TransportErrorCode(0xBuL)
        val APPLICATION_ERROR: TransportErrorCode = TransportErrorCode(0xCuL) // Used for application-specific errors
        val CRYPTO_BUFFER_EXCEEDED: TransportErrorCode = TransportErrorCode(0xDuL)
        val KEY_UPDATE_ERROR: TransportErrorCode = TransportErrorCode(0xEuL)
        val AEAD_LIMIT_REACHED: TransportErrorCode = TransportErrorCode(0xFuL)
        val NO_VIABLE_PATH: TransportErrorCode = TransportErrorCode(0x10uL)
        // Crypto errors (0x100-0x1ff) - example
        fun cryptoError(offset: ULong): TransportErrorCode {
            require(offset <= 0xffuL) { "Crypto error offset too large" }
            return TransportErrorCode(0x100uL + offset)
        }
    }
}

// --- Packet Types ---
/**
 * Represents QUIC Packet Type (Long Header Packet Types)
 * RFC 9000: Section 17.2. Long Header Packets
 */
enum class LongHeaderPacketType(val value: UByte) {
    INITIAL(0x00u), // Type bits 00
    ZERO_RTT(0x01u),// Type bits 01
    HANDSHAKE(0x02u),// Type bits 10
    RETRY(0x03u); // Type bits 11

    companion object {
        // Expects the full first byte of a long header packet.
        // Extracts type from bits 4-5 (00110000 mask is 0x30).
        fun fromByte(byte: UByte): LongHeaderPacketType? =
            entries.find { it.value == ((byte.toInt() and 0x30) shr 4).toUByte() }
    }
}

// Short Header packets don't have an explicit type field in the same way,
// their nature is determined by other factors (e.g., connection state, CID length).
// A simple representation could be:
object ShortHeaderPacket

// --- Other common QUIC concepts ---

/**
 * Represents a version number in QUIC.
 * RFC 9000: Versions are 32-bit unsigned integers.
 */
@JvmInline
value class QuicVersion(val value: UInt) {
    companion object {
        val VERSION_1: QuicVersion = QuicVersion(0x00000001u)
        val NEGOTIATION: QuicVersion = QuicVersion(0x00000000u) // Version Negotiation Packet
        // Add other known versions or reserved versions
    }
}

/**
 * Represents a length for variable-length integers in QUIC.
 * Can be 1, 2, 4, or 8 bytes.
 */
@JvmInline
value class VarIntLength(val lengthInBytes: UByte) {
    init {
        require(lengthInBytes == 1u.toUByte() || lengthInBytes == 2u.toUByte() || lengthInBytes == 4u.toUByte() || lengthInBytes == 8u.toUByte()) {
            "VarIntLength must be 1, 2, 4, or 8 bytes"
        }
    }
}

/**
 * Represents data that might be subject to Header Protection.
 */
@JvmInline
value class ProtectedPayload(val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as ProtectedPayload
        return data.contentEquals(other.data)
    }
    override fun hashCode(): Int = data.contentHashCode()
}

/**
 * Represents a stateless reset token.
 * RFC 9000: Section 10.3. Endpoints that receive a packet that they cannot associate with an active connection
 * MAY send a Stateless Reset packet.
 */
@JvmInline
value class StatelessResetToken(val value: ByteArray) {
    init {
        require(value.size == 16) { "Stateless Reset Token must be 16 bytes" }
    }
     override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as StatelessResetToken
        return value.contentEquals(other.value)
    }
    override fun hashCode(): Int = value.contentHashCode()
}

// --- QUIC Transport Layer Types (Moved from QuicTypes.kt) ---

@JvmInline
value class Bandwidth(val bitsPerSecond: ULong)

@JvmInline
value class RTT(val microseconds: ULong)

@JvmInline
value class CongestionWindow(val bytes: ULong)


// --- TLS-Related Types for QUIC Handshake (RFC 9001) ---

/**
 * Represents a TLS Protocol Version relevant to QUIC.
 * E.g., TLS 1.3 is 0x0304.
 */
@JvmInline
value class QuicTlsVersion(val value: UShort) {
    companion object {
        val TLS_1_3 = QuicTlsVersion(0x0304u)
        // Add other versions if needed
    }
}

/**
 * Represents a TLS Cipher Suite relevant to QUIC.
 * RFC 9001 specifies mandatory-to-implement cipher suites.
 * E.g., TLS_AES_128_GCM_SHA256 is 0x1301.
 */
@JvmInline
value class CipherSuite(val value: UShort) {
    companion object {
        // From RFC 8446 / 9001
        val TLS_AES_128_GCM_SHA256 = CipherSuite(0x1301u)
        val TLS_AES_256_GCM_SHA384 = CipherSuite(0x1302u)
        val TLS_CHACHA20_POLY1305_SHA256 = CipherSuite(0x1303u)
        // val TLS_AES_128_CCM_SHA256 = CipherSuite(0x1304u) // Optional in QUIC
        // val TLS_AES_128_CCM_8_SHA256 = CipherSuite(0x1305u) // Optional in QUIC
    }
}

/**
 * Represents a generic TLS Extension.
 * RFC 8446: Handshake Message Extensions
 */
data class TlsExtension(val type: UShort, val data: ByteArray) {
    // type values from IANA TLS ExtensionType Values registry
    companion object {
        const val SERVER_NAME: UShort = 0u // server_name (SNI)
        const val SUPPORTED_GROUPS: UShort = 10u // supported_groups (Elliptic Curves)
        const val SIGNATURE_ALGORITHMS: UShort = 13u // signature_algorithms
        const val APPLICATION_LAYER_PROTOCOL_NEGOTIATION: UShort = 16u // application_layer_protocol_negotiation (ALPN)
        const val KEY_SHARE: UShort = 51u // key_share
        const val SUPPORTED_VERSIONS: UShort = 43u // supported_versions (for ClientHello)
        const val PSK_KEY_EXCHANGE_MODES: UShort = 45u // psk_key_exchange_modes
        const val PRE_SHARED_KEY: UShort = 41u // pre_shared_key
        // QUIC specific transport parameters extension
        const val QUIC_TRANSPORT_PARAMETERS_V1_LEGACY: UShort = 0x39u // Old ID, for interop
        const val QUIC_TRANSPORT_PARAMETERS: UShort = 0x57u // IANA assigned 57 (0x0039 was old) - RFC 9001 uses 0x39 for legacy reasons in some contexts
                                                       // but official IANA is 0x0039 for early data, 0x0057 for QUIC TP
                                                       // For simplicity, let's use a distinct value if needed or clarify which RFC version.
                                                       // RFC 9000 / 9001 uses 0x39 for the QUIC Transport Parameters extension.
                                                       // It's better to use the values from RFCs.
                                                       // RFC 9001 Section 8.2: The extension_type for this extension is 57 (0x0039).
                                                       // This seems contradictory. Let's stick to one for now.
                                                       // IANA registry "Transport Layer Security (TLS) Extensions" has 0x0039 (quic_transport_parameters).
        const val QUIC_TRANSPORT_PARAMS_EXTENSION_TYPE: UShort = 0x0039u
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as TlsExtension
        if (type != other.type) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }
    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * Represents the payload of a TLS ClientHello message relevant to QUIC.
 * Simplified structure.
 */
data class ClientHelloPayload(
    val protocolVersion: QuicTlsVersion, // e.g., TLS 1.2 (0x0303) or TLS 1.3 (0x0304)
    val random: ByteArray, // 32 bytes
    val sessionId: ByteArray, // 0 to 32 bytes
    val cipherSuites: List<CipherSuite>, // List of offered cipher suites
    val compressionMethods: List<UByte>, // Typically [0x00] for null compression
    val extensions: List<TlsExtension>
) {
    // Ensure proper equals/hashCode for ByteArray fields
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as ClientHelloPayload
        if (protocolVersion != other.protocolVersion) return false
        if (!random.contentEquals(other.random)) return false
        if (!sessionId.contentEquals(other.sessionId)) return false
        if (cipherSuites != other.cipherSuites) return false
        if (compressionMethods != other.compressionMethods) return false
        if (extensions != other.extensions) return false
        return true
    }
    override fun hashCode(): Int {
        var result = protocolVersion.hashCode()
        result = 31 * result + random.contentHashCode()
        result = 31 * result + sessionId.contentHashCode()
        result = 31 * result + cipherSuites.hashCode()
        result = 31 * result + compressionMethods.hashCode()
        result = 31 * result + extensions.hashCode()
        return result
    }
}

/**
 * Represents the payload of a TLS ServerHello message relevant to QUIC.
 * Simplified structure.
 */
data class ServerHelloPayload(
    val protocolVersion: QuicTlsVersion,
    val random: ByteArray, // 32 bytes
    val sessionId: ByteArray, // Echoed or new session ID
    val cipherSuite: CipherSuite, // Chosen cipher suite
    val compressionMethod: UByte, // Chosen compression method (null)
    val extensions: List<TlsExtension>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as ServerHelloPayload
        if (protocolVersion != other.protocolVersion) return false
        if (!random.contentEquals(other.random)) return false
        if (!sessionId.contentEquals(other.sessionId)) return false
        if (cipherSuite != other.cipherSuite) return false
        if (compressionMethod != other.compressionMethod) return false
        if (extensions != other.extensions) return false
        return true
    }
    override fun hashCode(): Int {
        var result = protocolVersion.hashCode()
        result = 31 * result + random.contentHashCode()
        result = 31 * result + sessionId.contentHashCode()
        result = 31 * result + cipherSuite.hashCode()
        result = 31 * result + compressionMethod.hashCode()
        result = 31 * result + extensions.hashCode()
        return result
    }
}
