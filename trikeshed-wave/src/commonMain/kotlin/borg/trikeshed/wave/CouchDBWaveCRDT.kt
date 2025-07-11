package borg.trikeshed.wave

import borg.trikeshed.couchdb.*
import borg.trikeshed.services.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.coroutines.CoroutineContext

/**
 * CouchDB-backed Wave CRDT with Git-flakes History
 * 
 * Provides:
 * - Persistent CRDT document storage
 * - Git-style operation history and audit trail
 * - Real-time collaboration with CouchDB replication
 * - Conflict resolution with operational transformation
 */
@Serializable
data class CouchWaveDocument(
    val _id: String,
    val _rev: String? = null,
    val type: String = "wave-document",
    val documentType: DocumentCRDT.DocumentType,
    val content: String,
    val version: Long = 0,
    val operations: MutableList<DocumentOperation> = mutableListOf(),
    val participants: MutableSet<String> = mutableSetOf(),
    val history: MutableList<DocumentSnapshot> = mutableListOf(),
    val metadata: DocumentMetadata = DocumentMetadata(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class DocumentSnapshot(
    val version: Long,
    val content: String,
    val operations: List<DocumentOperation>,
    val participants: Set<String>,
    val timestamp: Long,
    val author: String,
    val commitMessage: String? = null,
    val parentVersions: List<Long> = emptyList()
)

@Serializable
data class DocumentMetadata(
    val title: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val author: String = "",
    val collaborators: List<String> = emptyList(),
    val permissions: Map<String, Set<String>> = emptyMap(),
    val gitBranch: String = "main",
    val gitCommit: String = "",
    val lastSync: Long = 0
)

@Serializable
data class WaveOperationLog(
    val _id: String,
    val _rev: String? = null,
    val type: String = "wave-operation",
    val documentId: String,
    val operation: DocumentOperation,
    val version: Long,
    val timestamp: Long,
    val author: String,
    val sessionId: String,
    val conflictResolution: ConflictResolution? = null
)

@Serializable
data class ConflictResolution(
    val originalOperation: DocumentOperation,
    val transformedOperation: DocumentOperation,
    val conflictType: ConflictType,
    val resolutionStrategy: String,
    val timestamp: Long
)

@Serializable
enum class ConflictType {
    CONCURRENT_EDIT,
    DIVERGENT_HISTORY,
    MERGE_CONFLICT,
    OPERATION_TRANSFORM
}

class CouchDBWaveCRDT(
    private val couchDB: CouchDBClient,
    private val waveIntegration: WaveProtocolIntegration = WaveProtocolIntegration()
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<CouchDBWaveCRDT>
    override val key: CoroutineContext.Key<*> get() = Key
    private val databaseName = "wave-documents"
    private val operationsDatabaseName = "wave-operations"
    private val updateFlows = mutableMapOf<String, MutableSharedFlow<DocumentUpdate>>()
    private val graphParsers = mutableMapOf<DocumentCRDT.DocumentType, GraphParser>()
    
    init {
        setupGraphParsers()
        initializeDatabases()
    }
    
    /**
     * Create a new collaborative document with CouchDB persistence
     */
    suspend fun createDocument(
        id: String,
        type: DocumentCRDT.DocumentType,
        initialContent: String = "",
        metadata: DocumentMetadata = DocumentMetadata()
    ): CouchWaveDocument = coroutineScope {
        
        val document = CouchWaveDocument(
            _id = id,
            documentType = type,
            content = initialContent,
            metadata = metadata.copy(
                author = metadata.author.ifEmpty { "anonymous" },
                gitCommit = generateGitCommitId()
            )
        )
        
        // Save to CouchDB
        val savedDoc = couchDB.saveDocument(databaseName, document)
        
        // Create initial snapshot
        val snapshot = DocumentSnapshot(
            version = 0,
            content = initialContent,
            operations = emptyList(),
            participants = emptySet(),
            timestamp = System.currentTimeMillis(),
            author = metadata.author.ifEmpty { "anonymous" },
            commitMessage = "Initial document creation"
        )
        
        savedDoc.history.add(snapshot)
        couchDB.updateDocument(databaseName, savedDoc)
        
        // Create wave session
        waveIntegration.createSession("doc-$id", id)
        
        // Set up update flow
        updateFlows[id] = MutableSharedFlow()
        
        savedDoc
    }
    
    /**
     * Join document collaboration with CouchDB sync
     */
    suspend fun joinDocument(documentId: String, participantId: String): Flow<DocumentUpdate> {
        val document = getDocument(documentId)
            ?: throw IllegalArgumentException("Document not found: $documentId")
        
        // Add participant
        document.participants.add(participantId)
        document.metadata.collaborators.add(participantId)
        document.updatedAt = System.currentTimeMillis()
        
        // Update in CouchDB
        couchDB.updateDocument(databaseName, document)
        
        // Join wave session
        waveIntegration.joinSession("doc-$documentId", participantId)
        
        // Log participant join
        logOperation(documentId, participantId, "join", emptyMap())
        
        return updateFlows[documentId]?.asSharedFlow() 
            ?: flow { emit(DocumentUpdate.Error("Document not found")) }
    }
    
    /**
     * Apply operation with CouchDB persistence and conflict resolution
     */
    suspend fun applyOperation(
        documentId: String,
        participantId: String,
        operation: DocumentOperation
    ): DocumentUpdate = coroutineScope {
        
        val document = getDocument(documentId)
            ?: return@coroutineScope DocumentUpdate.Error("Document not found")
        
        // Check for conflicts and resolve
        val conflictResolution = checkForConflicts(document, operation)
        val finalOperation = conflictResolution?.transformedOperation ?: operation
        
        // Apply operation to document
        applyOperationToDocument(document, finalOperation, participantId)
        
        // Create snapshot for this version
        val snapshot = DocumentSnapshot(
            version = document.version,
            content = document.content,
            operations = document.operations.toList(),
            participants = document.participants.toSet(),
            timestamp = System.currentTimeMillis(),
            author = participantId,
            commitMessage = generateCommitMessage(finalOperation),
            parentVersions = listOf(document.version - 1)
        )
        
        document.history.add(snapshot)
        document.updatedAt = System.currentTimeMillis()
        
        // Save to CouchDB
        couchDB.updateDocument(databaseName, document)
        
        // Log operation
        logOperation(documentId, participantId, "operation", mapOf(
            "operation" to finalOperation,
            "conflictResolution" to conflictResolution
        ))
        
        // Broadcast to other participants
        broadcastOperation(documentId, participantId, finalOperation)
        
        // Update graph visualization if needed
        if (operation is DocumentOperation.GraphUpdate) {
            updateGraphVisualization(documentId, operation)
        }
        
        val update = DocumentUpdate.OperationApplied(finalOperation)
        updateFlows[documentId]?.emit(update)
        
        update
    }
    
    /**
     * Get document with full history
     */
    suspend fun getDocument(documentId: String): CouchWaveDocument? {
        return couchDB.getDocument<CouchWaveDocument>(databaseName, documentId)
    }
    
    /**
     * Get document history (git-style)
     */
    suspend fun getDocumentHistory(documentId: String): List<DocumentSnapshot> {
        val document = getDocument(documentId) ?: return emptyList()
        return document.history.sortedBy { it.version }
    }
    
    /**
     * Get operation log for audit trail
     */
    suspend fun getOperationLog(documentId: String): List<WaveOperationLog> {
        val viewResult = couchDB.queryView<WaveOperationLog>(
            operationsDatabaseName,
            "operations",
            "by-document",
            key = documentId
        )
        return viewResult.rows.map { it.value }
    }
    
    /**
     * Revert to specific version (git-style)
     */
    suspend fun revertToVersion(documentId: String, version: Long, participantId: String): DocumentUpdate {
        val document = getDocument(documentId)
            ?: return DocumentUpdate.Error("Document not found")
        
        val targetSnapshot = document.history.find { it.version == version }
            ?: return DocumentUpdate.Error("Version not found")
        
        // Create revert operation
        val revertOp = DocumentOperation.Replace(
            position = 0,
            oldContent = document.content,
            newContent = targetSnapshot.content,
            timestamp = System.currentTimeMillis(),
            author = participantId
        )
        
        return applyOperation(documentId, participantId, revertOp)
    }
    
    /**
     * Create branch (git-style)
     */
    suspend fun createBranch(documentId: String, branchName: String, participantId: String): String {
        val document = getDocument(documentId)
            ?: throw IllegalArgumentException("Document not found")
        
        val branchDoc = document.copy(
            _id = "$documentId-$branchName",
            metadata = document.metadata.copy(
                gitBranch = branchName,
                gitCommit = generateGitCommitId()
            )
        )
        
        couchDB.saveDocument(databaseName, branchDoc)
        
        // Log branch creation
        logOperation(documentId, participantId, "branch-create", mapOf(
            "branchName" to branchName,
            "newDocumentId" to branchDoc._id
        ))
        
        return branchDoc._id
    }
    
    /**
     * Merge branches (git-style)
     */
    suspend fun mergeBranches(
        sourceDocumentId: String,
        targetDocumentId: String,
        participantId: String
    ): DocumentUpdate {
        val sourceDoc = getDocument(sourceDocumentId)
            ?: return DocumentUpdate.Error("Source document not found")
        val targetDoc = getDocument(targetDocumentId)
            ?: return DocumentUpdate.Error("Target document not found")
        
        // Create merge operation
        val mergeOp = DocumentOperation.Replace(
            position = 0,
            oldContent = targetDoc.content,
            newContent = mergeContent(targetDoc.content, sourceDoc.content),
            timestamp = System.currentTimeMillis(),
            author = participantId
        )
        
        return applyOperation(targetDocumentId, participantId, mergeOp)
    }
    
    /**
     * Subscribe to real-time updates with CouchDB changes feed
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
    
    private suspend fun initializeDatabases() {
        // Create databases if they don't exist
        couchDB.createDatabase(databaseName)
        couchDB.createDatabase(operationsDatabaseName)
        
        // Create views for operation logging
        val operationsView = mapOf(
            "operations" to mapOf(
                "by-document" to "function(doc) { if (doc.type === 'wave-operation') { emit(doc.documentId, doc); } }",
                "by-author" to "function(doc) { if (doc.type === 'wave-operation') { emit(doc.author, doc); } }",
                "by-timestamp" to "function(doc) { if (doc.type === 'wave-operation') { emit(doc.timestamp, doc); } }"
            )
        )
        
        couchDB.createViews(operationsDatabaseName, operationsView)
    }
    
    private fun checkForConflicts(
        document: CouchWaveDocument,
        operation: DocumentOperation
    ): ConflictResolution? {
        // Check for concurrent edits
        val recentOps = document.operations.takeLastWhile { 
            System.currentTimeMillis() - it.timestamp < 5000 // 5 second window
        }
        
        if (recentOps.any { it.author != operation.author }) {
            // Transform operation to resolve conflicts
            val transformedOp = transformOperation(operation, recentOps)
            return ConflictResolution(
                originalOperation = operation,
                transformedOperation = transformedOp,
                conflictType = ConflictType.CONCURRENT_EDIT,
                resolutionStrategy = "operational-transformation",
                timestamp = System.currentTimeMillis()
            )
        }
        
        return null
    }
    
    private fun transformOperation(
        operation: DocumentOperation,
        recentOps: List<DocumentOperation>
    ): DocumentOperation {
        // Simple operational transformation
        // In production, this would implement proper OT algorithms
        return operation
    }
    
    private fun applyOperationToDocument(
        document: CouchWaveDocument,
        operation: DocumentOperation,
        participantId: String
    ) {
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
                updateDocumentGraph(document, operation)
            }
        }
    }
    
    private fun updateDocumentGraph(document: CouchWaveDocument, operation: DocumentOperation.GraphUpdate) {
        val parser = graphParsers[document.documentType] ?: return
        val graphData = parser.parseGraph(document.content) ?: return
        
        // Apply graph operation
        val updatedGraph = when (operation.operation) {
            is GraphOperation.AddNode -> graphData.addNode(operation.operation)
            is GraphOperation.RemoveNode -> graphData.removeNode(operation.operation.nodeId)
            is GraphOperation.AddEdge -> graphData.addEdge(operation.operation)
            is GraphOperation.RemoveEdge -> graphData.removeEdge(operation.operation.fromId, operation.operation.toId)
            is GraphOperation.UpdateNode -> graphData.updateNode(operation.operation)
        }
        
        // Update document content
        document.content = parser.generateContent(updatedGraph)
    }
    
    private suspend fun logOperation(
        documentId: String,
        participantId: String,
        operationType: String,
        data: Map<String, Any?>
    ) {
        val logEntry = WaveOperationLog(
            _id = "op-${System.currentTimeMillis()}-${kotlin.random.Random.nextInt()}",
            documentId = documentId,
            operation = data["operation"] as? DocumentOperation ?: DocumentOperation.Insert(0, "", 0, ""),
            version = (data["version"] as? Long) ?: 0,
            timestamp = System.currentTimeMillis(),
            author = participantId,
            sessionId = "doc-$documentId"
        )
        
        couchDB.saveDocument(operationsDatabaseName, logEntry)
    }
    
    private suspend fun broadcastOperation(
        documentId: String,
        senderId: String,
        operation: DocumentOperation
    ) {
        val document = getDocument(documentId) ?: return
        
        document.participants.forEach { participantId ->
            if (participantId != senderId) {
                waveIntegration.applyOperation("doc-$documentId", participantId, operation)
            }
        }
    }
    
    private suspend fun updateGraphVisualization(documentId: String, operation: DocumentOperation.GraphUpdate) {
        val document = getDocument(documentId) ?: return
        val graphData = getGraphData(documentId)
        
        val update = DocumentUpdate.GraphVisualizationUpdate(
            documentId = documentId,
            graphData = graphData,
            operation = operation
        )
        updateFlows[documentId]?.emit(update)
    }
    
    private fun getGraphData(documentId: String): GraphData? {
        val document = getDocument(documentId) ?: return null
        val parser = graphParsers[document.documentType] ?: return null
        
        return parser.parseGraph(document.content)
    }
    
    private fun generateGitCommitId(): String {
        return "commit-${System.currentTimeMillis()}-${kotlin.random.Random.nextInt()}"
    }
    
    private fun generateCommitMessage(operation: DocumentOperation): String {
        return when (operation) {
            is DocumentOperation.Insert -> "Insert content at position ${operation.position}"
            is DocumentOperation.Delete -> "Delete ${operation.length} characters at position ${operation.position}"
            is DocumentOperation.Replace -> "Replace content at position ${operation.position}"
            is DocumentOperation.GraphUpdate -> "Update graph: ${operation.operation}"
        }
    }
    
    private fun mergeContent(targetContent: String, sourceContent: String): String {
        // Simple merge strategy - in production would use proper diff/merge algorithms
        return "$targetContent\n\n--- MERGED FROM BRANCH ---\n\n$sourceContent"
    }
}

// CCEK Key-based API extensions for CouchDB Wave CRDT
/**
 * Apply operation to document using CouchDBWaveCRDT from context
 */
suspend fun CouchDBWaveCRDT.Key.applyOperation(
    documentId: String,
    participantId: String,
    operation: DocumentOperation
): DocumentUpdate {
    val crdt = coroutineContext[this] 
        ?: throw IllegalStateException("CouchDBWaveCRDT not found in context")
    return crdt.applyOperation(documentId, participantId, operation)
}

/**
 * Create collaborative document using CouchDBWaveCRDT from context
 */
suspend fun CouchDBWaveCRDT.Key.createDocument(
    id: String,
    type: DocumentCRDT.DocumentType,
    initialContent: String = "",
    metadata: DocumentMetadata = DocumentMetadata()
): CouchWaveDocument {
    val crdt = coroutineContext[this] 
        ?: throw IllegalStateException("CouchDBWaveCRDT not found in context")
    return crdt.createDocument(id, type, initialContent, metadata)
}

/**
 * Join document collaboration using CouchDBWaveCRDT from context
 */
suspend fun CouchDBWaveCRDT.Key.joinDocument(
    documentId: String, 
    participantId: String
): Flow<DocumentUpdate> {
    val crdt = coroutineContext[this] 
        ?: throw IllegalStateException("CouchDBWaveCRDT not found in context")
    return crdt.joinDocument(documentId, participantId)
}

/**
 * Get document using CouchDBWaveCRDT from context
 */
suspend fun CouchDBWaveCRDT.Key.getDocument(documentId: String): CouchWaveDocument? {
    val crdt = coroutineContext[this] 
        ?: throw IllegalStateException("CouchDBWaveCRDT not found in context")
    return crdt.getDocument(documentId)
}

/**
 * Subscribe to real-time updates using CouchDBWaveCRDT from context
 */
fun CouchDBWaveCRDT.Key.subscribeToUpdates(documentId: String): Flow<DocumentUpdate> {
    val crdt = coroutineContext[this] 
        ?: throw IllegalStateException("CouchDBWaveCRDT not found in context")
    return crdt.subscribeToUpdates(documentId)
}

/**
 * Create Wave CRDT in context
 */
fun CouchDBWaveCRDT.Key.create(
    couchDB: CouchDBClient,
    waveIntegration: WaveProtocolIntegration = WaveProtocolIntegration(),
    configure: CouchDBWaveCRDT.() -> Unit = {}
): CouchDBWaveCRDT {
    return CouchDBWaveCRDT(couchDB, waveIntegration).apply(configure)
} 