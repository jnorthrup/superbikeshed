@file:Suppress("NOTHING_TO_INLINE")

package borg.trikeshed.parse.grpc

import borg.trikeshed.lib.*
import kotlin.jvm.JvmInline

/**
 * TrikeShed gRPC Parser - Graph Building from Wire Format
 * Builds hierarchical message structure from scanned wire fields
 */

// Parser Type Aliases
typealias MessageGraph = Indexed<MessageNode>
typealias MessagePath = Indexed<WireFieldNumber>
typealias MessageDepth = Int
typealias MessageNodeId = Int

// Message Node Types
@JvmInline
value class MessageNode(val data: Join<NodeType, NodeData>)

@JvmInline
value class NodeType(val value: UByte) {
    companion object {
        val MESSAGE = NodeType(1u)
        val FIELD = NodeType(2u)
        val REPEATED = NodeType(3u)
        val MAP = NodeType(4u)
        val PRIMITIVE = NodeType(5u)
    }
}

typealias NodeData = Join<WireField, MessageNodeId> // field data j parent node id
typealias NodeChildren = Indexed<MessageNodeId>
typealias MessageTree = Join<MessageNode, NodeChildren>

/**
 * gRPC Message Parser - Builds graph representation
 */
object GrpcParser {
    
    /**
     * Parse wire fields into message graph
     */
    fun parse(fields: WireFieldSeries, bytes: Indexed<Byte>): WireResult<MessageGraph> {
        if (fields.a == 0) return Result.success(emptyIndexed())
        
        return try {
            val graph = buildMessageGraph(fields, bytes)
            Result.success(graph)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Build hierarchical message graph from flat field series
     */
    private fun buildMessageGraph(fields: WireFieldSeries, bytes: Indexed<Byte>): MessageGraph {
        val nodes = mutableListOf<MessageNode>()
        val nodeIdCounter = AtomicCounter()
        
        // Create root message node
        val rootId = nodeIdCounter.next()
        val rootField = createRootField()
        val rootData = rootField j -1 // no parent
        nodes.add(MessageNode(NodeType.MESSAGE j rootData))
        
        // Group fields by field number
        val grouped = fields.groupByFieldNumber()
        
        // Process each field group
        for (i in 0 until grouped.a) {
            val (fieldNumber, fieldGroup) = grouped.b(i)
            processFieldGroup(fieldNumber, fieldGroup, bytes, rootId, nodes, nodeIdCounter)
        }
        
        val nodeArray = nodes.toTypedArray()
        return nodeArray.size j nodeArray::get
    }
    
    /**
     * Process a group of fields with the same field number
     */
    private fun processFieldGroup(
        fieldNumber: WireFieldNumber,
        fields: WireFieldSeries,
        bytes: Indexed<Byte>,
        parentId: MessageNodeId,
        nodes: MutableList<MessageNode>,
        idCounter: AtomicCounter
    ) {
        when {
            fields.a == 1 -> {
                // Single field
                val field = fields.b(0)
                processField(field, bytes, parentId, nodes, idCounter)
            }
            fields.a > 1 -> {
                // Repeated field
                val nodeId = idCounter.next()
                val firstField = fields.b(0)
                val nodeData = firstField j parentId
                nodes.add(MessageNode(NodeType.REPEATED j nodeData))
                
                // Add each occurrence as child
                for (j in 0 until fields.a) {
                    processField(fields.b(j), bytes, nodeId, nodes, idCounter)
                }
            }
        }
    }
    
    /**
     * Process individual field
     */
    private fun processField(
        field: WireField,
        bytes: Indexed<Byte>,
        parentId: MessageNodeId,
        nodes: MutableList<MessageNode>,
        idCounter: AtomicCounter
    ) {
        val (fieldTag, fieldPos) = field
        val (fieldNumber, wireType) = fieldTag
        
        val nodeId = idCounter.next()
        val nodeData = field j parentId
        
        when (wireType) {
            WireTypes.LENGTH_DELIMITED -> {
                // Could be string, bytes, or embedded message
                if (isLikelyMessage(bytes, fieldPos)) {
                    nodes.add(MessageNode(NodeType.MESSAGE j nodeData))
                    // Parse embedded message
                    parseEmbeddedMessage(bytes, fieldPos, nodeId, nodes, idCounter)
                } else {
                    nodes.add(MessageNode(NodeType.PRIMITIVE j nodeData))
                }
            }
            else -> {
                // Primitive types
                nodes.add(MessageNode(NodeType.PRIMITIVE j nodeData))
            }
        }
    }
    
    /**
     * Parse embedded message recursively
     */
    private fun parseEmbeddedMessage(
        bytes: Indexed<Byte>,
        fieldPos: WireFieldPosition,
        parentId: MessageNodeId,
        nodes: MutableList<MessageNode>,
        idCounter: AtomicCounter
    ) {
        val (start, length) = fieldPos
        
        // Extract message bounds
        var valueStart = start
        val (_, tagEnd) = GrpcScanner.readVarint(bytes, start)
        valueStart = tagEnd
        val (msgLength, lengthEnd) = GrpcScanner.readVarint(bytes, valueStart)
        valueStart = lengthEnd
        
        val msgBytes = msgLength.toInt() j { i: Int -> bytes.b(valueStart + i) }
        
        // Recursively parse embedded message
        val embeddedResult = GrpcScanner.scan(msgBytes)
        embeddedResult.fold(
            onSuccess = { embeddedFields ->
                val grouped = embeddedFields.groupByFieldNumber()
                for (i in 0 until grouped.a) {
                    val (fieldNum, fieldGroup) = grouped.b(i)
                    processFieldGroup(fieldNum, fieldGroup, msgBytes, parentId, nodes, idCounter)
                }
            },
            onFailure = { /* Skip malformed embedded messages */ }
        )
    }
    
    /**
     * Heuristic to detect if bytes likely represent a message
     */
    private fun isLikelyMessage(bytes: Indexed<Byte>, fieldPos: WireFieldPosition): Boolean {
        val (start, length) = fieldPos
        
        // Skip tag and length prefix
        var pos = start
        val (_, tagEnd) = GrpcScanner.readVarint(bytes, pos)
        pos = tagEnd
        val (msgLength, lengthEnd) = GrpcScanner.readVarint(bytes, pos)
        pos = lengthEnd
        
        // Check if content looks like valid protobuf
        if (msgLength > 0u && pos + msgLength.toInt() <= bytes.a) {
            // Try to read first tag
            return try {
                val (tag, _) = GrpcScanner.readVarint(bytes, pos)
                val wireType = (tag and 0x7u).toUByte()
                wireType in setOf(WireTypes.VARINT, WireTypes.FIXED32, WireTypes.FIXED64, 
                                 WireTypes.LENGTH_DELIMITED)
            } catch (e: Exception) {
                false
            }
        }
        return false
    }
    
    /**
     * Create root field for message
     */
    private fun createRootField(): WireField {
        val fieldTag = 0 j WireTypes.LENGTH_DELIMITED
        val fieldPos = 0 j 0
        return fieldTag j fieldPos
    }
    
    /**
     * Extract message schema from graph
     */
    fun extractSchema(graph: MessageGraph): MessageSchema {
        val fields = mutableMapOf<WireFieldNumber, FieldSchema>()
        
        // Traverse graph to extract schema
        for (i in 0 until graph.a) {
            val node = graph.b(i)
            val (nodeType, nodeData) = node.data
            val (field, parentId) = nodeData
            val (fieldTag, _) = field
            val (fieldNumber, wireType) = fieldTag
            
            if (fieldNumber > 0) {  // Skip root
                val fieldType = when (nodeType) {
                    NodeType.MESSAGE -> FieldType.MESSAGE
                    NodeType.REPEATED -> FieldType.REPEATED
                    NodeType.PRIMITIVE -> wireTypeToFieldType(wireType)
                    else -> FieldType.UNKNOWN
                }
                
                fields[fieldNumber] = FieldSchema(fieldNumber, fieldType, nodeType == NodeType.REPEATED)
            }
        }
        
        return MessageSchema(fields)
    }
    
    /**
     * Convert wire type to field type
     */
    private fun wireTypeToFieldType(wireType: WireType): FieldType = when (wireType) {
        WireTypes.VARINT -> FieldType.VARINT
        WireTypes.FIXED32 -> FieldType.FIXED32
        WireTypes.FIXED64 -> FieldType.FIXED64
        WireTypes.LENGTH_DELIMITED -> FieldType.BYTES
        else -> FieldType.UNKNOWN
    }
}

/**
 * Message schema types
 */
data class MessageSchema(
    val fields: Map<WireFieldNumber, FieldSchema>
)

data class FieldSchema(
    val number: WireFieldNumber,
    val type: FieldType,
    val repeated: Boolean
)

@JvmInline
value class FieldType(val value: UByte) {
    companion object {
        val VARINT = FieldType(1u)
        val FIXED32 = FieldType(2u)
        val FIXED64 = FieldType(3u)
        val BYTES = FieldType(4u)
        val MESSAGE = FieldType(5u)
        val REPEATED = FieldType(6u)
        val UNKNOWN = FieldType(255u)
    }
}

/**
 * Simple atomic counter for node IDs
 */
private class AtomicCounter {
    private var value = 0
    fun next(): Int = value++
}

/**
 * Extension functions
 */
fun WireFieldSeries.parse(bytes: Indexed<Byte>): WireResult<MessageGraph> = 
    GrpcParser.parse(this, bytes)

fun MessageGraph.extractSchema(): MessageSchema = 
    GrpcParser.extractSchema(this)

/**
 * Example usage
 */
object GrpcParserExample {
    fun demonstrateParsing() {
        // Example: message Person { string name = 1; int32 age = 2; }
        val wireBytes = byteArrayOf(
            0x0a, 0x04, 0x4a, 0x6f, 0x68, 0x6e,  // field 1: "John"
            0x10, 0x1e                            // field 2: 30
        )
        
        val indexedBytes = wireBytes.size j { i: Int -> wireBytes[i] }
        
        // Scan and parse
        val scanResult = indexedBytes.scanWire()
        
        scanResult.fold(
            onSuccess = { fields ->
                val parseResult = fields.parse(indexedBytes)
                
                parseResult.fold(
                    onSuccess = { graph ->
                        val schema = graph.extractSchema()
                        println("Message graph nodes: ${graph.a}")
                        println("Schema fields: ${schema.fields}")
                    },
                    onFailure = { error ->
                        println("Parse error: ${error.message}")
                    }
                )
            },
            onFailure = { error ->
                println("Scan error: ${error.message}")
            }
        )
    }
}