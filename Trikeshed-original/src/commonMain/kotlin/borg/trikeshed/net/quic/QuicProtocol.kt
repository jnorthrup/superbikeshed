package borg.trikeshed.net.quic


import borg.trikeshed.lib.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.random.Random

/**
 * Pure TrikeShed implementation of QUIC protocol
 * No external dependencies - only TrikeShed types and kotlinx-serialization
 */

// QUIC packet types
enum class QuicPacketType(val value: Byte) {
    INITIAL(0x00),
    ZERO_RTT(0x01),
    HANDSHAKE(0x02),
    RETRY(0x03),
    VERSION_NEGOTIATION(0x04),
    SHORT_HEADER(0x40)
}

// QUIC frame types
enum class QuicFrameType(val value: Byte) {
    PADDING(0x00),
    PING(0x01),
    ACK(0x02),
    RESET_STREAM(0x04),
    STOP_SENDING(0x05),
    CRYPTO(0x06),
    NEW_TOKEN(0x07),
    STREAM(0x08),
    MAX_DATA(0x10),
    MAX_STREAM_DATA(0x11),
    MAX_STREAMS(0x12),
    DATA_BLOCKED(0x14),
    STREAM_DATA_BLOCKED(0x15),
    STREAMS_BLOCKED(0x16),
    NEW_CONNECTION_ID(0x18),
    RETIRE_CONNECTION_ID(0x19),
    PATH_CHALLENGE(0x1A),
    PATH_RESPONSE(0x1B),
    CONNECTION_CLOSE(0x1C),
    HANDSHAKE_DONE(0x1E)
}

// Connection ID using Indexed<Byte>
@Serializable
data class ConnectionId(
    val bytes: Indexed<Byte>
) {
    val length: Int get() = bytes.a
    
    fun toHexString(): String = buildString {
        for (i in 0 until bytes.a) {
            append(bytes.b(i).toUByte().toString(16).padStart(2, '0'))
        }
    }
    
    companion object {
        fun random(length: Int = 8): ConnectionId {
            val bytes = ByteArray(length) { Random.nextBytes(1)[0] }
            return ConnectionId(bytes.size j { bytes[it] })
        }
    }
}

// QUIC packet header
@Serializable
data class QuicHeader(
    val type: QuicPacketType,
    val version: Long,
    val destinationConnectionId: ConnectionId,
    val sourceConnectionId: ConnectionId,
    val packetNumber: Long,
    val token: Indexed<Byte>? = null
)

// QUIC frame base
@Serializable
sealed class QuicFrame {
    abstract val type: QuicFrameType
}

// Stream frame
@Serializable
data class StreamFrame(
    val streamId: Long,
    val offset: Long,
    val data: Indexed<Byte>,
    val fin: Boolean = false
) : QuicFrame() {
    override val type = QuicFrameType.STREAM
}

// ACK frame
@Serializable
data class AckFrame(
    val largestAcknowledged: Long,
    val ackDelay: Long,
    val ackRanges: Indexed<Join<Long, Long>> // Join<start, end>
) : QuicFrame() {
    override val type = QuicFrameType.ACK
}

// Crypto frame
@Serializable
data class CryptoFrame(
    val offset: Long,
    val data: Indexed<Byte>
) : QuicFrame() {
    override val type = QuicFrameType.CRYPTO
}

// QUIC packet
@Serializable
data class QuicPacket(
    val header: QuicHeader,
    val frames: Indexed<QuicFrame>,
    val payload: Indexed<Byte>
)

// QUIC transport parameters
@Serializable
data class TransportParameters(
    val maxStreamData: Long = 1_048_576,
    val maxData: Long = 10_485_760,
    val maxBidiStreams: Long = 100,
    val maxUniStreams: Long = 100,
    val idleTimeout: Long = 30_000,
    val maxPacketSize: Long = 1350,
    val ackDelayExponent: Int = 3,
    val maxAckDelay: Long = 25,
    val activeConnectionIdLimit: Long = 4,
    val initialMaxData: Long = 10_485_760,
    val initialMaxStreamDataBidiLocal: Long = 1_048_576,
    val initialMaxStreamDataBidiRemote: Long = 1_048_576,
    val initialMaxStreamDataUni: Long = 1_048_576,
    val initialMaxStreamsBidi: Long = 100,
    val initialMaxStreamsUni: Long = 100
)

// QUIC connection state
@Serializable
data class QuicConnectionState(
    val localConnectionId: ConnectionId,
    val remoteConnectionId: ConnectionId,
    val version: Long = 0x00000001, // QUIC v1
    val transportParams: TransportParameters = TransportParameters(),
    val streams: Indexed<QuicStreamState> = emptyIndex(),
    val sentPackets: Indexed<QuicPacket> = emptyIndex(),
    val receivedPackets: Indexed<QuicPacket> = emptyIndex(),
    val nextPacketNumber: Long = 0,
    val nextStreamId: Long = 0,
    val congestionWindow: Long = 14720, // 10 * max_packet_size
    val bytesInFlight: Long = 0,
    val rtt: Long = 100 // Initial RTT estimate in ms
)

// Stream state
@Serializable
data class QuicStreamState(
    val streamId: Long,
    val sendBuffer: Indexed<Byte> = emptyIndex(),
    val receiveBuffer: Indexed<Byte> = emptyIndex(),
    val sendOffset: Long = 0,
    val receiveOffset: Long = 0,
    val maxData: Long = 1_048_576,
    val state: StreamState = StreamState.IDLE
) {
    enum class StreamState {
        IDLE, OPEN, HALF_CLOSED_LOCAL, HALF_CLOSED_REMOTE, CLOSED
    }
}

// Helper to create empty Indexed
fun <T> emptyIndex(): Indexed<T> = 0 j { throw NoSuchElementException() }