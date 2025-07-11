package borg.trikeshed.wave

import borg.trikeshed.services.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Real-time CRDT for Mermaid/DOT/HTML Documents
 * 
 * Supports live collaboration on:
 * - Mermaid diagrams
 * - Graphviz DOT files  
 * - HTML documents
 * - Mixed content with embedded graphs
 */
@Serializable
data class DocumentCRDT(
    val id: String,
    val type: DocumentType,
    val content: String,
    val version: Long = 0,
    val operations: MutableList<DocumentOperation> = mutableListOf(),
    val participants: MutableSet<String> = mutableSetOf()
) {
    @Serializable
    enum class DocumentType {
        MERMAID, DOT, HTML, MIXED
    }
}

@Serializable
sealed class DocumentOperation {
    @Serializable
    data class Insert(
        val position: Int,
        val content: String,
        val timestamp: Long,
        val author: String
    ) : DocumentOperation()
    
    @Serializable
    data class Delete(
        val position: Int,
        val length: Int,
        val timestamp: Long,
        val author: String
    ) : DocumentOperation()
    
    @Serializable
    data class Replace(
        val position: Int,
        val oldContent: String,
        val newContent: String,
        val timestamp: Long,
        val author: String
    ) : DocumentOperation()
    
    @Serializable
    data class GraphUpdate(
        val graphId: String,
        val operation: GraphOperation,
        val timestamp: Long,
        val author: String
    ) : DocumentOperation()
}

@Serializable
sealed class GraphOperation {
    @Serializable
    data class AddNode(
        val nodeId: String,
        val label: String,
        val attributes: Map<String, String> = emptyMap()
    ) : GraphOperation()
    
    @Serializable
    data class RemoveNode(val nodeId: String) : GraphOperation()
    
    @Serializable
    data class AddEdge(
        val fromId: String,
        val toId: String,
        val label: String = "",
        val attributes: Map<String, String> = emptyMap()
    ) : GraphOperation()
    
    @Serializable
    data class RemoveEdge(val fromId: String, val toId: String) : GraphOperation()
    
    @Serializable
    data class UpdateNode(
        val nodeId: String,
        val newLabel: String? = null,
        val newAttributes: Map<String, String>? = null
    ) : GraphOperation()
}

class RealtimeDocumentCRDT(
    private val waveIntegration: WaveProtocolIntegration = WaveProtocolIntegration()
) {
    private val documents = mutableMapOf<String, DocumentCRDT>()
    private val graphParsers = mutableMapOf<DocumentCRDT.DocumentType, GraphParser>()
    private val updateFlows = mutableMapOf<String, MutableSharedFlow<DocumentUpdate>>()
    
    init {
        setupGraphParsers()
    }
    
    /**
     * Create a new collaborative document
     */
    fun createDocument(
        id: String,
        type: DocumentCRDT.DocumentType,
        initialContent: String = ""
    ): DocumentCRDT {
        val document = DocumentCRDT(id, type, initialContent)
        documents[id] = document
        updateFlows[id] = MutableSharedFlow()
        
        // Create wave session for this document
        waveIntegration.createSession("doc-$id", id)
        
        return document
    }
    
    /**
     * Join document collaboration
     */
    suspend fun joinDocument(documentId: String, participantId: String): Flow<DocumentUpdate> {
        val document = documents[documentId] 
            ?: throw IllegalArgumentException("Document not found: $documentId")
        
        document.participants.add(participantId)
        
        // Join wave session
        waveIntegration.joinSession("doc-$documentId", participantId)
        
        return updateFlows[documentId]?.asSharedFlow() 
            ?: flow { emit(DocumentUpdate.Error("Document not found")) }
    }
    
    /**
     * Apply operation to document with CRDT consistency
     */
    suspend fun applyOperation(
        documentId: String,
        participantId: String,
        operation: DocumentOperation
    ): DocumentUpdate {
        val document = documents[documentId]
            ?: return DocumentUpdate.Error("Document not found")
        
        // Transform operation for CRDT consistency
        val transformedOp = transformOperation(operation, document.operations)
        
        // Apply to document
        applyOperationToDocument(document, transformedOp)
        
        // Broadcast to other participants
        broadcastOperation(documentId, participantId, transformedOp)
        
        // Parse and update graph if needed
        if (operation is DocumentOperation.GraphUpdate) {
            updateGraphVisualization(documentId, operation)
        }
        
        val update = DocumentUpdate.OperationApplied(transformedOp)
        updateFlows[documentId]?.emit(update)
        
        return update
    }
    
    /**
     * Get current document state
     */
    fun getDocument(documentId: String): DocumentCRDT? = documents[documentId]
    
    /**
     * Get graph data from document
     */
    fun getGraphData(documentId: String): GraphData? {
        val document = documents[documentId] ?: return null
        val parser = graphParsers[document.type] ?: return null
        
        return parser.parseGraph(document.content)
    }
    
    /**
     * Subscribe to real-time updates
     */
    fun subscribeToUpdates(documentId: String): Flow<DocumentUpdate> {
        return updateFlows[documentId]?.asSharedFlow() 
            ?: flow { emit(DocumentUpdate.Error("Document not found")) }
    }
    
    // Private methods
    
    private fun setupGraphParsers() {
        graphParsers[DocumentCRDT.DocumentType.MERMAID] = MermaidParser()
        graphParsers[DocumentCRDT.DocumentType.DOT] = DotParser()
        graphParsers[DocumentCRDT.DocumentType.HTML] = HtmlGraphParser()
        graphParsers[DocumentCRDT.DocumentType.MIXED] = MixedContentParser()
    }
    
    private fun transformOperation(
        operation: DocumentOperation,
        existingOps: List<DocumentOperation>
    ): DocumentOperation {
        // Simple operational transformation for now
        // In production, this would implement proper OT algorithms
        return operation
    }
    
    private fun applyOperationToDocument(document: DocumentCRDT, operation: DocumentOperation) {
        document.operations.add(operation)
        document.version++
        
        when (operation) {
            is DocumentOperation.Insert -> {
                val before = document.content.substring(0, operation.position)
                val after = document.content.substring(operation.position)
                document.content = before + operation.content + after
            }
            is DocumentOperation.Delete -> {
                val before = document.content.substring(0, operation.position)
                val after = document.content.substring(operation.position + operation.length)
                document.content = before + after
            }
            is DocumentOperation.Replace -> {
                val before = document.content.substring(0, operation.position)
                val after = document.content.substring(operation.position + operation.oldContent.length)
                document.content = before + operation.newContent + after
            }
            is DocumentOperation.GraphUpdate -> {
                // Graph operations are handled by the parser
                updateDocumentGraph(document, operation)
            }
        }
    }
    
    private fun updateDocumentGraph(document: DocumentCRDT, operation: DocumentOperation.GraphUpdate) {
        val parser = graphParsers[document.type] ?: return
        val graphData = parser.parseGraph(document.content) ?: return
        
        // Apply graph operation
        val updatedGraph = when (operation.operation) {
            is GraphOperation.AddNode -> graphData.addNode(operation.operation)
            is GraphOperation.RemoveNode -> graphData.removeNode(operation.operation.nodeId)
            is GraphOperation.AddEdge -> graphData.addEdge(operation.operation)
            is GraphOperation.RemoveEdge -> graphData.removeEdge(operation.operation.fromId, operation.operation.toId)
            is GraphOperation.UpdateNode -> graphData.updateNode(operation.operation)
        }
        
        // Update document content with new graph representation
        document.content = parser.generateContent(updatedGraph)
    }
    
    private suspend fun broadcastOperation(
        documentId: String,
        senderId: String,
        operation: DocumentOperation
    ) {
        val document = documents[documentId] ?: return
        
        document.participants.forEach { participantId ->
            if (participantId != senderId) {
                // Send via wave integration
                waveIntegration.applyOperation("doc-$documentId", participantId, operation)
            }
        }
    }
    
    private suspend fun updateGraphVisualization(documentId: String, operation: DocumentOperation.GraphUpdate) {
        val update = DocumentUpdate.GraphVisualizationUpdate(
            documentId = documentId,
            graphData = getGraphData(documentId),
            operation = operation
        )
        updateFlows[documentId]?.emit(update)
    }
}

@Serializable
sealed class DocumentUpdate {
    @Serializable
    data class OperationApplied(val operation: DocumentOperation) : DocumentUpdate()
    
    @Serializable
    data class GraphVisualizationUpdate(
        val documentId: String,
        val graphData: GraphData?,
        val operation: DocumentOperation.GraphUpdate
    ) : DocumentUpdate()
    
    @Serializable
    data class ParticipantJoined(val participantId: String) : DocumentUpdate()
    
    @Serializable
    data class ParticipantLeft(val participantId: String) : DocumentUpdate()
    
    @Serializable
    data class Error(val message: String) : DocumentUpdate()
}

@Serializable
data class GraphData(
    val nodes: MutableList<GraphNode> = mutableListOf(),
    val edges: MutableList<GraphEdge> = mutableListOf(),
    val attributes: Map<String, String> = emptyMap()
) {
    fun addNode(operation: GraphOperation.AddNode): GraphData {
        val newNode = GraphNode(operation.nodeId, operation.label, operation.attributes)
        nodes.add(newNode)
        return this
    }
    
    fun removeNode(nodeId: String): GraphData {
        nodes.removeIf { it.id == nodeId }
        edges.removeIf { it.fromId == nodeId || it.toId == nodeId }
        return this
    }
    
    fun addEdge(operation: GraphOperation.AddEdge): GraphData {
        val newEdge = GraphEdge(operation.fromId, operation.toId, operation.label, operation.attributes)
        edges.add(newEdge)
        return this
    }
    
    fun removeEdge(fromId: String, toId: String): GraphData {
        edges.removeIf { it.fromId == fromId && it.toId == toId }
        return this
    }
    
    fun updateNode(operation: GraphOperation.UpdateNode): GraphData {
        val node = nodes.find { it.id == operation.nodeId } ?: return this
        val updatedNode = node.copy(
            label = operation.newLabel ?: node.label,
            attributes = operation.newAttributes ?: node.attributes
        )
        nodes[nodes.indexOf(node)] = updatedNode
        return this
    }
}

@Serializable
data class GraphNode(
    val id: String,
    val label: String,
    val attributes: Map<String, String> = emptyMap()
)

@Serializable
data class GraphEdge(
    val fromId: String,
    val toId: String,
    val label: String = "",
    val attributes: Map<String, String> = emptyMap()
)

interface GraphParser {
    fun parseGraph(content: String): GraphData?
    fun generateContent(graphData: GraphData): String
} 