package borg.trikeshed.lib

import kotlinx.serialization.Serializable

@Serializable
actual value class NodeId(val bytes: UByteArray) {
    init { require(bytes.size == 32) { "NodeId must be 32 bytes" } }
}

@Serializable
actual value class MessageId(val bytes: UByteArray) {
    init { require(bytes.size == 32) { "MessageId must be 32 bytes" } }
}

@Serializable
actual value class DataKey(val bytes: UByteArray)

@Serializable
actual value class DataValue(val bytes: UByteArray)

@Serializable
actual value class KademliaNodeId(val bytes: UByteArray) {
    init { require(bytes.size == 32) { "KademliaNodeId must be 32 bytes" } }
}

@Serializable
actual value class ProtocolVersion(val version: UByte)

@Serializable
actual value class NetworkAddress(val value: String)

@Serializable
actual value class NetworkPort(val value: Int)

@Serializable
actual value class Checksum(val crc32: UInt)

@Serializable
actual value class Timestamp(val epochMillis: Long)

@Serializable
actual value class TimeToLive(val seconds: Int)

@Serializable
actual value class TrikeShedProtocol(val id: UByte)

@Serializable
actual value class Duration(val millis: Long)
