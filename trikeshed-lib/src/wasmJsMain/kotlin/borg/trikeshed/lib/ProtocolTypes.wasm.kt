package borg.trikeshed.lib

import kotlinx.serialization.Serializable

@Serializable
actual value class NodeId actual constructor(actual val bytes: UByteArray) {
    init { require(bytes.size == 32) { "NodeId must be 32 bytes" } }
}

@Serializable
actual value class MessageId actual constructor(actual val bytes: UByteArray) {
    init { require(bytes.size == 32) { "MessageId must be 32 bytes" } }
}

@Serializable
actual value class DataKey actual constructor(actual val bytes: UByteArray)

@Serializable
actual value class DataValue actual constructor(actual val bytes: UByteArray)

@Serializable
actual value class KademliaNodeId actual constructor(actual val bytes: UByteArray) {
    init { require(bytes.size == 32) { "KademliaNodeId must be 32 bytes" } }
}

@Serializable
actual value class ProtocolVersion actual constructor(actual val version: UByte)

@Serializable
actual value class NetworkAddress actual constructor(actual val value: String)

@Serializable
actual value class NetworkPort actual constructor(actual val value: Int)

@Serializable
actual value class Checksum actual constructor(actual val crc32: UInt)

@Serializable
actual value class Timestamp actual constructor(actual val epochMillis: Long)

@Serializable
actual value class TimeToLive actual constructor(actual val seconds: Int)

@Serializable
actual value class TrikeShedProtocol actual constructor(actual val id: UByte)

@Serializable
actual value class Duration actual constructor(actual val millis: Long)
