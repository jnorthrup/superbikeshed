package borg.trikeshed.net.http3

import borg.trikeshed.net.quic.util.BufferReader
import borg.trikeshed.net.quic.util.BufferWriter
// Http3FrameType is in Http3Enums.kt in the same package, so it should be directly accessible.
// No explicit import needed if they are in the same module and package.
// However, for clarity or if in different modules, an import would be:
// import borg.trikeshed.net.http3.Http3FrameType

/**
 * Base interface for all HTTP/3 frames.
 */
sealed interface Http3Frame {
    val type: Http3FrameType

    /**
     * Serializes the frame into a ByteArray including its type and length.
     * This is the complete wire format for a single frame.
     */
    fun toByteArray(): ByteArray
}

/**
 * HTTP/3 DATA Frame (Type 0x00).
 * @property payload The application data payload.
 */
data class DataFrame(val payload: ByteArray) : Http3Frame {
    override val type: Http3FrameType get() = Http3FrameType.DATA

    override fun toByteArray(): ByteArray {
        val writer = BufferWriter()
        writer.writeVarint(type.value.toLong()) // Frame Type
        writer.writeVarint(payload.size.toLong()) // Length
        writer.writeBytes(payload) // Payload
        return writer.toByteArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DataFrame) return false
        if (type != other.type) return false // Should always be DATA
        return payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/**
 * HTTP/3 HEADERS Frame (Type 0x01).
 * @property encodedHeaderData The QPACK encoded header block.
 */
data class HeadersFrame(val encodedHeaderData: ByteArray) : Http3Frame {
    override val type: Http3FrameType get() = Http3FrameType.HEADERS

    override fun toByteArray(): ByteArray {
        val writer = BufferWriter()
        writer.writeVarint(type.value.toLong()) // Frame Type
        writer.writeVarint(encodedHeaderData.size.toLong()) // Length
        writer.writeBytes(encodedHeaderData) // Encoded Header Block
        return writer.toByteArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HeadersFrame) return false
        if (type != other.type) return false
        return encodedHeaderData.contentEquals(other.encodedHeaderData)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + encodedHeaderData.contentHashCode()
        return result
    }
}

/**
 * HTTP/3 SETTINGS Frame (Type 0x04).
 * @property settings A map of setting identifiers to their values.
 */
data class SettingsFrame(val settings: Map<Long, Long> = emptyMap()) : Http3Frame {
    override val type: Http3FrameType get() = Http3FrameType.SETTINGS

    companion object {
        // Common HTTP/3 Setting Identifiers (from RFC 9114, Section 7.2.4)
        const val SETTINGS_QPACK_MAX_TABLE_CAPACITY = 0x01L
        const val SETTINGS_MAX_FIELD_SECTION_SIZE = 0x06L // Renamed from SETTINGS_HEADER_TABLE_SIZE
        const val SETTINGS_QPACK_BLOCKED_STREAMS = 0x07L
        // WebTransport specific settings
        const val SETTINGS_H3_DATAGRAM_DPLPMTUD = 0x276L // For WebTransport over HTTP/3, draft value
        const val SETTINGS_ENABLE_WEBTRANSPORT = 0x2B66L // draft-ietf-webtrans-http3
        const val SETTINGS_ENABLE_CONNECT_PROTOCOL = 0x08L // From an earlier draft, now ENABLE_EXTENDED_CONNECT
        const val SETTINGS_ENABLE_EXTENDED_CONNECT = 0x08L // Official name for 0x08
    }

    override fun toByteArray(): ByteArray {
        val payloadWriter = BufferWriter()
        settings.forEach { (id, value) ->
            payloadWriter.writeVarint(id)
            payloadWriter.writeVarint(value)
        }
        val payloadBytes = payloadWriter.toByteArray()

        val finalWriter = BufferWriter()
        finalWriter.writeVarint(type.value.toLong()) // Frame Type
        finalWriter.writeVarint(payloadBytes.size.toLong()) // Length
        finalWriter.writeBytes(payloadBytes) // Settings Payload
        return finalWriter.toByteArray()
    }
    // Standard equals/hashCode for Map is fine.
}

/**
 * HTTP/3 GOAWAY Frame (Type 0x07).
 * @property streamId The Stream ID of the last stream the sender was willing to process.
 */
data class GoAwayFrame(val streamId: Long) : Http3Frame {
    override val type: Http3FrameType get() = Http3FrameType.GOAWAY

    override fun toByteArray(): ByteArray {
        val payloadWriter = BufferWriter()
        payloadWriter.writeVarint(streamId) // Push ID or Stream ID
        val payloadBytes = payloadWriter.toByteArray()

        val finalWriter = BufferWriter()
        finalWriter.writeVarint(type.value.toLong()) // Frame Type
        finalWriter.writeVarint(payloadBytes.size.toLong()) // Length
        finalWriter.writeBytes(payloadBytes) // Payload
        return finalWriter.toByteArray()
    }
}
