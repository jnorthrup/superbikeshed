package borg.trikeshed.net.quic.tls

import borg.trikeshed.net.quic.cidMaxLength
import borg.trikeshed.net.quic.varIntToByteArray
import borg.trikeshed.net.quic.writeVarInt

// QUIC Transport Parameter IDs (subset from RFC 9000, Section 18.2)
// Values are UShort because they are typically represented as varints,
// and UShort covers the range needed for these standard IDs.
object QuicTransportParameterId {
    const val ORIGINAL_DESTINATION_CONNECTION_ID: UShort = 0x0000u
    const val MAX_IDLE_TIMEOUT: UShort = 0x0001u
    const val STATELESS_RESET_TOKEN: UShort = 0x0002u // Present if server is able to stateless reset
    const val MAX_UDP_PAYLOAD_SIZE: UShort = 0x0003u
    const val INITIAL_MAX_DATA: UShort = 0x0004u
    const val INITIAL_MAX_STREAM_DATA_BIDI_LOCAL: UShort = 0x0005u
    const val INITIAL_MAX_STREAM_DATA_BIDI_REMOTE: UShort = 0x0006u
    const val INITIAL_MAX_STREAM_DATA_UNI: UShort = 0x0007u
    const val INITIAL_MAX_STREAMS_BIDI: UShort = 0x0008u
    const val INITIAL_MAX_STREAMS_UNI: UShort = 0x0009u
    const val ACK_DELAY_EXPONENT: UShort = 0x000au
    const val MAX_ACK_DELAY: UShort = 0x000bu
    const val DISABLE_ACTIVE_MIGRATION: UShort = 0x000cu // Value is zero-length
    const val PREFERRED_ADDRESS: UShort = 0x000du // Complex structure
    const val ACTIVE_CONNECTION_ID_LIMIT: UShort = 0x000eu
    const val INITIAL_SOURCE_CONNECTION_ID: UShort = 0x000fu
    const val RETRY_SOURCE_CONNECTION_ID: UShort = 0x0010u
}

sealed class TransportParameter(val id: UShort, val value: ByteArray) {
    // Each parameter type will define how to serialize its specific 'value'
    abstract fun encodeValue(): ByteArray

    fun toByteArray(): ByteArray {
        val idBytes = id.varIntToByteArray() // Represent ID as varint
        val valBytes = encodeValue()
        val lenBytes = valBytes.size.toULong().varIntToByteArray() // Represent length as varint
        return idBytes + lenBytes + valBytes
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TransportParameter) return false
        if (id != other.id) return false
        if (!value.contentEquals(other.value)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }
}

// Example concrete parameter types
// Using ULong for most numeric values as they are often varints.
class InitialMaxStreamDataBidiLocal(data: ULong) :
    TransportParameter(QuicTransportParameterId.INITIAL_MAX_STREAM_DATA_BIDI_LOCAL, data.varIntToByteArray()) {
    override fun encodeValue(): ByteArray = value
}

class InitialMaxStreamDataBidiRemote(data: ULong) :
    TransportParameter(QuicTransportParameterId.INITIAL_MAX_STREAM_DATA_BIDI_REMOTE, data.varIntToByteArray()) {
    override fun encodeValue(): ByteArray = value
}

class InitialMaxStreamDataUni(data: ULong) :
    TransportParameter(QuicTransportParameterId.INITIAL_MAX_STREAM_DATA_UNI, data.varIntToByteArray()) {
    override fun encodeValue(): ByteArray = value
}

class InitialMaxData(data: ULong) :
    TransportParameter(QuicTransportParameterId.INITIAL_MAX_DATA, data.varIntToByteArray()) {
    override fun encodeValue(): ByteArray = value
}

class InitialMaxStreamsBidi(count: ULong) :
    TransportParameter(QuicTransportParameterId.INITIAL_MAX_STREAMS_BIDI, count.varIntToByteArray()) {
    override fun encodeValue(): ByteArray = value
}

class InitialMaxStreamsUni(count: ULong) :
    TransportParameter(QuicTransportParameterId.INITIAL_MAX_STREAMS_UNI, count.varIntToByteArray()) {
    override fun encodeValue(): ByteArray = value
}

class MaxIdleTimeout(timeout: ULong) : // In milliseconds
    TransportParameter(QuicTransportParameterId.MAX_IDLE_TIMEOUT, timeout.varIntToByteArray()) {
    override fun encodeValue(): ByteArray = value
}

class MaxUdpPayloadSize(size: ULong) : // In bytes, defaults to 65527
    TransportParameter(QuicTransportParameterId.MAX_UDP_PAYLOAD_SIZE, size.varIntToByteArray()) {
    override fun encodeValue(): ByteArray = value
}

class AckDelayExponent(exponent: ULong) : // Defaults to 3
    TransportParameter(QuicTransportParameterId.ACK_DELAY_EXPONENT, exponent.varIntToByteArray()) {
    override fun encodeValue(): ByteArray = value
}

class MaxAckDelay(delay: ULong) : // In milliseconds, defaults to 25
    TransportParameter(QuicTransportParameterId.MAX_ACK_DELAY, delay.varIntToByteArray()) {
    override fun encodeValue(): ByteArray = value
}

class ActiveConnectionIdLimit(limit: ULong) : // Minimum value of 2
    TransportParameter(QuicTransportParameterId.ACTIVE_CONNECTION_ID_LIMIT, limit.varIntToByteArray()) {
    override fun encodeValue(): ByteArray = value
}

class DisableActiveMigration : // Value is zero-length
    TransportParameter(QuicTransportParameterId.DISABLE_ACTIVE_MIGRATION, byteArrayOf()) {
    override fun encodeValue(): ByteArray = value // Empty byte array
}

class InitialSourceConnectionId(cid: ByteArray) :
    TransportParameter(QuicTransportParameterId.INITIAL_SOURCE_CONNECTION_ID, cid) {
    init { require(cid.size <= cidMaxLength) }
    override fun encodeValue(): ByteArray = value
}

// Main container for transport parameters
data class QuicTransportParameters(
    val parameters: List<TransportParameter>
)

fun serializeQuicTransportParameters(params: QuicTransportParameters): ByteArray {
    var result = byteArrayOf()
    // Overall length of the parameters list - not part of the TLS extension value directly,
    // but the TLS extension itself will have a length field for all these serialized params.
    // Each parameter is ID (VarInt), Length (VarInt), Value.
    for (param in params.parameters) {
        result += param.toByteArray()
    }
    return result
}

// Helper function to convert UShort to VarInt ByteArray (simplified, assumes small values for now)
// A proper varint encoding is needed here. This is a placeholder.
fun UShort.varIntToByteArray(): ByteArray = writeVarInt(this.toULong())
fun ULong.varIntToByteArray(): ByteArray = writeVarInt(this)
