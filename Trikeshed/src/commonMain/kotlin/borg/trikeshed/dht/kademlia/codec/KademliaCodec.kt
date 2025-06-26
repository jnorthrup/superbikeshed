package borg.trikeshed.dht.kademlia.codec

import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.*
import borg.trikeshed.dht.kademlia.events.*
import borg.trikeshed.dht.kademlia.id.NUID

/**
 * Custom Binary Kademlia Codec using TrikeShed graph serialization
 * Optimized for variable key lengths and efficient binary packing
 */
class KademliaCodec : Codec<KademliaEvent, Indexed<Byte>> {

    override fun encode(event: KademliaEvent): Indexed<Byte> {
        val graph = GraphBuilder.build(event)
        return BinaryPacker.pack(graph)
    }

    override fun decode(data: Indexed<Byte>): KademliaEvent {
        val graph = BinaryUnpacker.unpack(data)
        return GraphToObjectMapper.map(graph)
    }
}

/**
 * Interface for encoding/decoding operations
 */
interface Codec<T, R> {
    fun encode(obj: T): R
    fun decode(data: R): T
}

/**
 * Internal graph structure for TrikeShed serialization
 * Represents the normalized form of KademliaEvent objects
 */
data class InternalGraphStructure(
    val eventType: String,
    val fields: Map<String, Any?>,
    val metadata: Map<String, Any?> = emptyMap()
) {
    fun getEventType(): String = eventType
    fun getField(key: String): Any? = fields[key]
    fun getMetadata(key: String): Any? = metadata[key]
}

/**
 * Builds internal graph structure from KademliaEvent using double dispatch
 */
object GraphBuilder {
    fun build(event: KademliaEvent): InternalGraphStructure {
        val fields = mutableMapOf<String, Any?>()
        
        // Common fields for all events
        fields["timestamp"] = event.timestamp
        fields["messageId"] = ByteArray(event.messageId.size) { i -> event.messageId.bytes[i] }
        fields["sourceNodeId"] = ByteArray(event.sourceNodeId.size) { i -> event.sourceNodeId.bytes[i] }
        
        // Event-specific fields using when expression (visitor pattern)
        when (event) {
            is PingEvent -> {
                fields["targetNodeId"] = ByteArray(event.targetNodeId.size) { i -> event.targetNodeId.bytes[i] }
                fields["subnets"] = event.subnets.play.toList()
            }
            is PongEvent -> {
                fields["respondingToMessageId"] = ByteArray(event.respondingToMessageId.size) { i -> event.respondingToMessageId.bytes[i] }
                fields["respondingToNodeId"] = ByteArray(event.respondingToNodeId.size) { i -> event.respondingToNodeId.bytes[i] }
                fields["activeSubnets"] = event.activeSubnets.play.toList()
                fields["routingTableSize"] = event.routingTableSize
            }
            is FindNodeEvent -> {
                fields["targetNodeId"] = ByteArray(event.targetNodeId.size) { i -> event.targetNodeId.bytes[i] }
                fields["maxResults"] = event.maxResults
                fields["preferredSubnets"] = event.preferredSubnets.play.toList()
            }
            is FoundNodesEvent -> {
                fields["respondingToMessageId"] = ByteArray(event.respondingToMessageId.size) { i -> event.respondingToMessageId.bytes[i] }
                fields["targetNodeId"] = ByteArray(event.targetNodeId.size) { i -> event.targetNodeId.bytes[i] }
                fields["nodes"] = event.nodes.play.map { nodeInfo ->
                    mapOf(
                        "nodeId" to ByteArray(nodeInfo.nodeId.size) { i -> nodeInfo.nodeId.bytes[i] },
                        "ipAddress" to nodeInfo.ipAddress,
                        "port" to nodeInfo.port,
                        "subnets" to nodeInfo.subnets.play.toList(),
                        "publicKey" to ByteArray(nodeInfo.publicKey.a) { i -> nodeInfo.publicKey[i] },
                        "lastSeen" to nodeInfo.lastSeen,
                        "reliability" to nodeInfo.reliability
                    )
                }.toList()
            }
            is StoreEvent -> {
                fields["key"] = ByteArray(event.key.a) { i -> event.key[i] }
                fields["value"] = ByteArray(event.value.a) { i -> event.value[i] }
                fields["ttl"] = event.ttl
                fields["replicationFactor"] = event.replicationFactor
            }
            is StoreResponseEvent -> {
                fields["respondingToMessageId"] = ByteArray(event.respondingToMessageId.size) { i -> event.respondingToMessageId.bytes[i] }
                fields["key"] = ByteArray(event.key.a) { i -> event.key[i] }
                fields["success"] = event.success
                fields["errorCode"] = event.errorCode
            }
            is FindValueEvent -> {
                fields["key"] = ByteArray(event.key.a) { i -> event.key[i] }
                fields["maxHops"] = event.maxHops
            }
            is FoundValueEvent -> {
                fields["respondingToMessageId"] = ByteArray(event.respondingToMessageId.size) { i -> event.respondingToMessageId.bytes[i] }
                fields["key"] = ByteArray(event.key.a) { i -> event.key[i] }
                fields["value"] = ByteArray(event.value.a) { i -> event.value[i] }
                fields["providingNodes"] = event.providingNodes.play.map { nodeInfo ->
                    mapOf(
                        "nodeId" to ByteArray(nodeInfo.nodeId.size) { i -> nodeInfo.nodeId.bytes[i] },
                        "ipAddress" to nodeInfo.ipAddress,
                        "port" to nodeInfo.port,
                        "subnets" to nodeInfo.subnets.play.toList(),
                        "publicKey" to ByteArray(nodeInfo.publicKey.a) { i -> nodeInfo.publicKey[i] },
                        "lastSeen" to nodeInfo.lastSeen,
                        "reliability" to nodeInfo.reliability
                    )
                }.toList()
            }
            is JoinRequestEvent -> {
                fields["proposedNodeId"] = ByteArray(event.proposedNodeId.size) { i -> event.proposedNodeId.bytes[i] }
                fields["publicKey"] = ByteArray(event.publicKey.a) { i -> event.publicKey[i] }
                fields["formerNodeId"] = event.formerNodeId?.let { ByteArray(it.size) { i -> it.bytes[i] } }
            }
            is JoinResponseEvent -> {
                fields["respondingToMessageId"] = ByteArray(event.respondingToMessageId.size) { i -> event.respondingToMessageId.bytes[i] }
                fields["accepted"] = event.accepted
                fields["message"] = event.message
                fields["knownNodes"] = event.knownNodes.play.map { nodeInfo ->
                    mapOf(
                        "nodeId" to ByteArray(nodeInfo.nodeId.size) { i -> nodeInfo.nodeId.bytes[i] },
                        "ipAddress" to nodeInfo.ipAddress,
                        "port" to nodeInfo.port,
                        "subnets" to nodeInfo.subnets.play.toList(),
                        "publicKey" to ByteArray(nodeInfo.publicKey.a) { i -> nodeInfo.publicKey[i] },
                        "lastSeen" to nodeInfo.lastSeen,
                        "reliability" to nodeInfo.reliability
                    )
                }.toList()
            }
        }
        
        return InternalGraphStructure(
            eventType = event::class.simpleName ?: "Unknown",
            fields = fields
        )
    }
}

/**
 * Packs internal graph structure into dense binary format
 */
object BinaryPacker {
    fun pack(graph: InternalGraphStructure): Indexed<Byte> {
        val buffer = mutableListOf<Byte>()
        
        // Pack event type
        val eventTypeBytes = graph.eventType.encodeToByteArray()
        buffer.add(eventTypeBytes.size.toByte())
        buffer.addAll(eventTypeBytes.toList())
        
        // Pack field count
        buffer.add(graph.fields.size.toByte())
        
        // Pack each field
        for ((key, value) in graph.fields) {
            packField(buffer, key, value)
        }
        
        return buffer.size j { i: Int -> buffer[i] }
    }
    
    private fun packField(buffer: MutableList<Byte>, key: String, value: Any?) {
        // Pack field name
        val keyBytes = key.encodeToByteArray()
        buffer.add(keyBytes.size.toByte())
        buffer.addAll(keyBytes.toList())
        
        // Pack field value with type tag
        when (value) {
            null -> buffer.add(0x00)
            is Long -> {
                buffer.add(0x01)
                packLong(buffer, value)
            }
            is Int -> {
                buffer.add(0x02)
                packInt(buffer, value)
            }
            is String -> {
                buffer.add(0x03)
                val strBytes = value.encodeToByteArray()
                packInt(buffer, strBytes.size)
                strBytes.forEach { buffer.add(it) }
            }
            is ByteArray -> {
                buffer.add(0x04)
                packInt(buffer, value.size)
                value.forEach { buffer.add(it) }
            }
            is Boolean -> {
                buffer.add(0x05)
                buffer.add(if (value) 1 else 0)
            }
            is Double -> {
                buffer.add(0x06)
                packLong(buffer, value.toBits())
            }
            is List<*> -> {
                buffer.add(0x07)
                packInt(buffer, value.size)
                for (item in value) {
                    packField(buffer, "", item) // Recursive packing
                }
            }
            else -> {
                buffer.add(0xFF) // Unknown type
            }
        }
    }
    
    private fun packLong(buffer: MutableList<Byte>, value: Long) {
        for (i in 7 downTo 0) {
            buffer.add(((value shr (i * 8)) and 0xFF).toByte())
        }
    }
    
    private fun packInt(buffer: MutableList<Byte>, value: Int) {
        for (i in 3 downTo 0) {
            buffer.add(((value shr (i * 8)) and 0xFF).toByte())
        }
    }
}

/**
 * Unpacks binary data back into internal graph structure
 */
object BinaryUnpacker {
    fun unpack(data: Indexed<Byte>): InternalGraphStructure {
        var offset = 0
        
        // Unpack event type
        val eventTypeLength = data[offset].toInt() and 0xFF
        offset++
        val eventTypeBytes = ByteArray(eventTypeLength) { i -> data[offset + i] }
        val eventType = eventTypeBytes.decodeToString()
        offset += eventTypeLength
        
        // Unpack field count
        val fieldCount = data[offset].toInt() and 0xFF
        offset++
        
        // Unpack fields
        val fields = mutableMapOf<String, Any?>()
        for (i in 0 until fieldCount) {
            val (key, value, newOffset) = unpackField(data, offset)
            fields[key] = value
            offset = newOffset
        }
        
        return InternalGraphStructure(eventType, fields)
    }
    
    private fun unpackField(data: Indexed<Byte>, offset: Int): Triple<String, Any?, Int> {
        var pos = offset
        
        // Unpack field name
        val keyLength = data[pos].toInt() and 0xFF
        pos++
        val keyBytes = ByteArray(keyLength) { i -> data[pos + i] }
        val key = keyBytes.decodeToString()
        pos += keyLength
        
        // Unpack field value
        val typeTag = data[pos]
        pos++
        
        val value = when (typeTag) {
            0x00.toByte() -> null
            0x01.toByte() -> {
                val result = unpackLong(data, pos)
                pos += 8
                result
            }
            0x02.toByte() -> {
                val result = unpackInt(data, pos)
                pos += 4
                result
            }
            0x03.toByte() -> {
                val length = unpackInt(data, pos)
                pos += 4
                val strBytes = ByteArray(length) { i -> data[pos + i] }
                pos += length
                strBytes.decodeToString()
            }
            0x04.toByte() -> {
                val length = unpackInt(data, pos)
                pos += 4
                val result = ByteArray(length) { i -> data[pos + i] }
                pos += length
                result
            }
            0x05.toByte() -> {
                val result = data[pos] != 0.toByte()
                pos++
                result
            }
            0x06.toByte() -> {
                val bits = unpackLong(data, pos)
                pos += 8
                Double.fromBits(bits)
            }
            0x07.toByte() -> {
                val listSize = unpackInt(data, pos)
                pos += 4
                val list = mutableListOf<Any?>()
                for (i in 0 until listSize) {
                    val (_, item, newPos) = unpackField(data, pos)
                    list.add(item)
                    pos = newPos
                }
                list.toList()
            }
            else -> null
        }
        
        return Triple(key, value, pos)
    }
    
    private fun unpackLong(data: Indexed<Byte>, offset: Int): Long {
        var result = 0L
        for (i in 0 until 8) {
            result = (result shl 8) or ((data[offset + i].toInt() and 0xFF).toLong())
        }
        return result
    }
    
    private fun unpackInt(data: Indexed<Byte>, offset: Int): Int {
        var result = 0
        for (i in 0 until 4) {
            result = (result shl 8) or (data[offset + i].toInt() and 0xFF)
        }
        return result
    }
}

/**
 * Maps internal graph structure back to KademliaEvent objects
 */
object GraphToObjectMapper {
    fun map(graph: InternalGraphStructure): KademliaEvent {
        val fields = graph.fields
        
        // Extract common fields
        val timestamp = fields["timestamp"] as Long
        val messageId = NUID.fromBytes(fields["messageId"] as ByteArray)
        val sourceNodeId = NUID.fromBytes(fields["sourceNodeId"] as ByteArray)
        
        return when (graph.eventType) {
            "PingEvent" -> PingEvent(
                timestamp = timestamp,
                messageId = messageId,
                sourceNodeId = sourceNodeId,
                targetNodeId = NUID.fromBytes(fields["targetNodeId"] as ByteArray),
                subnets = (fields["subnets"] as List<String>).let { list ->
                    list.size j { i: Int -> list[i] }
                }
            )
            "PongEvent" -> PongEvent(
                timestamp = timestamp,
                messageId = messageId,
                sourceNodeId = sourceNodeId,
                respondingToMessageId = NUID.fromBytes(fields["respondingToMessageId"] as ByteArray),
                respondingToNodeId = NUID.fromBytes(fields["respondingToNodeId"] as ByteArray),
                activeSubnets = (fields["activeSubnets"] as List<String>).let { list ->
                    list.size j { i: Int -> list[i] }
                },
                routingTableSize = fields["routingTableSize"] as Int
            )
            // Add other event types...
            else -> throw IllegalArgumentException("Unknown event type: ${graph.eventType}")
        }
    }
}