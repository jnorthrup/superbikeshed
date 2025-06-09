package borg.trikeshed.net.quic

import borg.trikeshed.net.quic.QuicPacketType // Assuming QuicTypes.kt is in the same package

sealed interface QuicPacketHeader {
  val destinationConnectionId: ConnectionId?
  val packetNumber: PacketNumber
  val rawPacketNumberBytes: ByteArray
}

data class LongHeader(
  val type: QuicPacketType,
  val version: UInt,
  override val destinationConnectionId: ConnectionId,
  val sourceConnectionId: ConnectionId,
  val token: ByteArray? = null,
  override val packetNumber: PacketNumber,
  override val rawPacketNumberBytes: ByteArray,
  val length: ULong
) : QuicPacketHeader {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is LongHeader) return false

    if (type != other.type) return false
    if (version != other.version) return false
    if (!destinationConnectionId.contentEquals(other.destinationConnectionId)) return false
    if (!sourceConnectionId.contentEquals(other.sourceConnectionId)) return false
    if (token != null) {
      if (other.token == null) return false
      if (!token.contentEquals(other.token)) return false
    } else if (other.token != null) return false
    if (packetNumber != other.packetNumber) return false
    if (!rawPacketNumberBytes.contentEquals(other.rawPacketNumberBytes)) return false
    if (length != other.length) return false

    return true
  }

  override fun hashCode(): Int {
    var result = type.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + destinationConnectionId.contentHashCode()
    result = 31 * result + sourceConnectionId.contentHashCode()
    result = 31 * result + (token?.contentHashCode() ?: 0)
    result = 31 * result + packetNumber.hashCode()
    result = 31 * result + rawPacketNumberBytes.contentHashCode()
    result = 31 * result + length.hashCode()
    return result
  }
}

data class ShortHeader(
  val spinBit: Boolean,
  val keyPhaseBit: Boolean,
  override val packetNumber: PacketNumber,
  override val rawPacketNumberBytes: ByteArray,
  override val destinationConnectionId: ConnectionId? = null
) : QuicPacketHeader {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ShortHeader) return false

    if (spinBit != other.spinBit) return false
    if (keyPhaseBit != other.keyPhaseBit) return false
    if (packetNumber != other.packetNumber) return false
    if (!rawPacketNumberBytes.contentEquals(other.rawPacketNumberBytes)) return false
    if (destinationConnectionId != null) {
      if (other.destinationConnectionId == null) return false
      if (!destinationConnectionId.contentEquals(other.destinationConnectionId)) return false
    } else if (other.destinationConnectionId != null) return false

    return true
  }

  override fun hashCode(): Int {
    var result = spinBit.hashCode()
    result = 31 * result + keyPhaseBit.hashCode()
    result = 31 * result + packetNumber.hashCode()
    result = 31 * result + rawPacketNumberBytes.contentHashCode()
    result = 31 * result + (destinationConnectionId?.contentHashCode() ?: 0)
    return result
  }
}

data class QuicPacket(
  val header: QuicPacketHeader,
  val unprotectedPayload: ByteArray
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is QuicPacket) return false

    if (header != other.header) return false
    if (!unprotectedPayload.contentEquals(other.unprotectedPayload)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = header.hashCode()
    result = 31 * result + unprotectedPayload.contentHashCode()
    return result
  }
}
