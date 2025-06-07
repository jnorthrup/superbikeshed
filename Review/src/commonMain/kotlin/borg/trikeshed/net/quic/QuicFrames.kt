package borg.trikeshed.net.quic

import borg.trikeshed.lib.Join // Assuming this is the correct path for Join
import borg.trikeshed.net.quic.PacketNumber // From QuicTypes.kt
import borg.trikeshed.net.quic.ConnectionId // From QuicTypes.kt

enum class StreamType {
  BIDIRECTIONAL,
  UNIDIRECTIONAL
}

sealed interface QuicFrame

object PaddingFrame : QuicFrame

object PingFrame : QuicFrame

/**
 * Represents a range of acknowledged packets within an ACK frame, excluding the first range.
 * @property gap The number of unacknowledged packets preceding this range.
 * @property ackedPacketsInThisRange The number of acknowledged packets in this specific range.
 */
data class AckRange(val gap: Long, val ackedPacketsInThisRange: Long)

/**
 * Represents ECN (Explicit Congestion Notification) counts reported in an ACK frame.
 * @property ect0 Count of ECT(0) marked packets received.
 * @property ect1 Count of ECT(1) marked packets received.
 * @property ce Count of CE (Congestion Experienced) marked packets received.
 */
data class EcnCounts(val ect0: Long, val ect1: Long, val ce: Long)

/**
 * Represents a QUIC ACK frame, used to acknowledge received packets and provide ECN feedback.
 * @property largestAcked The largest packet number being acknowledged.
 * @property ackDelay The time elapsed in microseconds between receiving the largest acknowledged packet
 *                    and sending this ACK frame.
 * @property firstAckRangePacketCount The number of contiguous packets in the first ACK range, ending at `largestAcked`.
 *                                    (i.e., packets from `largestAcked - firstAckRangePacketCount + 1` to `largestAcked` are acked).
 * @property additionalAckRanges A list of additional [AckRange] objects, sorted in descending order of packet numbers they represent.
 * @property ecnCounts Optional [EcnCounts] providing feedback on ECN-marked packets. Present if frame type is ACK_ECN.
 */
data class AckFrame(
  val largestAcked: Long, // Was PacketNumber (ULong)
  val ackDelay: Long,     // Was ULong
  val firstAckRangePacketCount: Long,
  val additionalAckRanges: List<AckRange>,
  val ecnCounts: EcnCounts?
) : QuicFrame
// Note: Removed old AckBlock as its role is covered by AckRange and firstAckRangePacketCount.

data class ResetStreamFrame(
  val streamId: Long, // Changed from ULong
  val applicationProtocolErrorCode: Long, // Changed from UShort for varint consistency
  val finalSize: Long // Changed from ULong
) : QuicFrame

data class StopSendingFrame(
  val streamId: Long, // Changed from ULong
  val applicationProtocolErrorCode: Long // Changed from UShort for varint consistency
) : QuicFrame

data class CryptoFrame(
  val offset: Long, // Changed from ULong to Long for varint compatibility with BufferReader/Writer
  val data: ByteArray
) : QuicFrame {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is CryptoFrame) return false
    if (offset != other.offset) return false
    if (!data.contentEquals(other.data)) return false
    return true
  }

  override fun hashCode(): Int {
    var result = offset.hashCode()
    result = 31 * result + data.contentHashCode()
    return result
  }
}

data class NewTokenFrame(
  val token: ByteArray
) : QuicFrame {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is NewTokenFrame) return false
    if (!token.contentEquals(other.token)) return false
    return true
  }

  override fun hashCode(): Int {
    return token.contentHashCode()
  }
}

data class StreamFrame(
  val streamId: Long, // Changed from ULong
  val offset: Long,   // Changed from ULong?, represents actual offset (0 if OFF bit not set in type)
  val data: ByteArray,
  val isFin: Boolean, // Renamed from fin
  val sendLengthExplicitly: Boolean // If true, LEN bit is set and length is encoded
) : QuicFrame {
  // val length: Long get() = data.size.toLong() // Length is implicit or explicit via sendLengthExplicitly

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is StreamFrame) return false
    if (streamId != other.streamId) return false
    if (offset != other.offset) return false
    if (!data.contentEquals(other.data)) return false
    if (isFin != other.isFin) return false
    if (sendLengthExplicitly != other.sendLengthExplicitly) return false
    return true
  }

  override fun hashCode(): Int {
    var result = streamId.hashCode()
    result = 31 * result + offset.hashCode()
    result = 31 * result + data.contentHashCode()
    result = 31 * result + isFin.hashCode()
    result = 31 * result + sendLengthExplicitly.hashCode()
    return result
  }
}

/**
 * Represents a QUIC MAX_DATA frame, used to update connection-level flow control limits.
 * @property maximumData The maximum absolute byte offset the peer is allowed to send on this connection.
 */
data class MaxDataFrame(
  val maximumData: Long // Changed from ULong
) : QuicFrame

data class MaxStreamDataFrame(
  val streamId: Long, // Changed from ULong
  val maximumStreamData: Long // Changed from ULong
) : QuicFrame

/**
 * Represents a QUIC MAX_STREAMS frame, used to inform the peer of the cumulative number of streams
 * of a given type it is permitted to initiate.
 * @property maximumStreams The maximum number of streams of the specified type that can be opened.
 * @property isBidirectional True if this limit applies to bidirectional streams, false for unidirectional streams.
 */
data class MaxStreamsFrame(
  val maximumStreams: Long, // Changed from ULong
  val isBidirectional: Boolean // Replaces streamType
) : QuicFrame

/**
 * Represents a QUIC DATA_BLOCKED frame, indicating the sender is blocked by connection-level flow control.
 * @property dataLimit The connection-level data limit that caused the blockage.
 */
data class DataBlockedFrame(
  val dataLimit: Long // Renamed from maximumData and changed from ULong
) : QuicFrame

data class StreamDataBlockedFrame(
  val streamId: Long, // Changed from ULong
  val streamDataLimit: Long // Renamed from maximumStreamData and changed from ULong
) : QuicFrame

data class StreamsBlockedFrame(
  val streamType: StreamType,
  val streamLimit: Long // Changed from ULong, corresponds to the stream limit that was blocked
) : QuicFrame

data class NewConnectionIdFrame(
  val sequenceNumber: Long, // Changed from ULong
  val retirePriorTo: Long,  // Changed from ULong
  val connectionId: ConnectionId,
  val statelessResetToken: ByteArray
) : QuicFrame {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is NewConnectionIdFrame) return false
    if (sequenceNumber != other.sequenceNumber) return false
    if (retirePriorTo != other.retirePriorTo) return false
    if (!connectionId.contentEquals(other.connectionId)) return false
    if (!statelessResetToken.contentEquals(other.statelessResetToken)) return false
    return true
  }

  override fun hashCode(): Int {
    var result = sequenceNumber.hashCode()
    result = 31 * result + retirePriorTo.hashCode()
    result = 31 * result + connectionId.contentHashCode()
    result = 31 * result + statelessResetToken.contentHashCode()
    return result
  }
}

data class RetireConnectionIdFrame(
  val sequenceNumber: Long // Changed from ULong
) : QuicFrame

data class PathChallengeFrame(
  val data: ByteArray // 8 bytes
) : QuicFrame {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PathChallengeFrame) return false
    if (!data.contentEquals(other.data)) return false
    return true
  }

  override fun hashCode(): Int {
    return data.contentHashCode()
  }
}

data class PathResponseFrame(
  val data: ByteArray // 8 bytes
) : QuicFrame {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PathResponseFrame) return false
    if (!data.contentEquals(other.data)) return false
    return true
  }

  override fun hashCode(): Int {
    return data.contentHashCode()
  }
}

/**
 * Represents a QUIC CONNECTION_CLOSE frame, used to signal connection termination.
 * This single class covers both transport-level (0x1c) and application-level (0x1d) close frames.
 *
 * @property errorCode The error code indicating the reason for closing the connection.
 *                     For transport errors, this is a QUIC error code.
 *                     For application errors, this is an application-defined error code.
 * @property offendingFrameType The type of frame that triggered the error. This is only present for
 *                              transport-level errors (isApplicationError = false) and may be null
 *                              if the error was not directly caused by a specific frame type (e.g. 0).
 * @property reasonPhrase A human-readable explanation for why the connection was closed.
 * @property isApplicationError True if this is an application-level close (type 0x1d), meaning the
 *                              errorCode is application-defined and offendingFrameType is absent.
 *                              False if this is a transport-level close (type 0x1c).
 */
data class ConnectionCloseFrame(
    val errorCode: Long, // Changed from UShort for varint compatibility
    val offendingFrameType: Long?, // Present only if !isApplicationError (type 0x1c)
    val reasonPhrase: String,
    val isApplicationError: Boolean
) : QuicFrame
// Note: The previous ConnectionCloseQuicFrame and ConnectionCloseAppFrame are replaced by this.

object HandshakeDoneFrame : QuicFrame

/**
 * Represents a frame whose type is unknown or not yet supported by the parser.
 *
 * @property type The byte value representing the unknown frame type.
 * @property rawData The raw byte data associated with this frame, if any could be determined.
 *                   For simple unknown frames, this might be empty or the rest of the payload.
 */
data class UnknownFrame(val type: UByte, val rawData: ByteArray) : QuicFrame {
    // Auto-generated equals/hashCode might be problematic for rawData.
    // Consider implementing contentEquals if this class is used in sets/maps extensively.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UnknownFrame) return false
        if (type != other.type) return false
        if (!rawData.contentEquals(other.rawData)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + rawData.contentHashCode()
        return result
    }
}
