package fiduciary.attention

import kotlinx.coroutines.flow.*

/**
 * Generic navigation abstraction for fiduciary data sources
 * 
 * Treats all sources (Divine indexes, OCR, CouchDB, etc) as a navigable hierarchy
 */
interface AttentionNavigator {
    suspend fun listRoots(): List<NavigableNode>
    suspend fun getChildren(node: NavigableNode): List<NavigableNode>
    suspend fun getContent(node: NavigableNode): NavigableContent?
    suspend fun search(query: String, scope: NavigableNode? = null): Flow<NavigableNode>
    
    // Attention tracking
    fun recordAttention(node: NavigableNode, action: AttentionAction)
    fun getAttentionHistory(): Flow<AttentionEvent>
}

/**
 * Unified node representation for any data source
 */
data class NavigableNode(
    val id: String,
    val name: String,
    val type: NodeType,
    val parent: String? = null,
    val hasChildren: Boolean = false,
    val metadata: Map<String, Any> = emptyMap()
)

enum class NodeType {
    // Container types
    SOURCE,      // Top-level source (Divine, OCR, etc)
    DIRECTORY,   // Logical grouping
    VIEW,        // CouchDB view
    COLLECTION,  // Document collection
    
    // Content types
    DOCUMENT,    // Actual content
    INDEX,       // Index entry
    ATTACHMENT,  // Binary attachment
    STREAM       // Real-time stream
}

data class NavigableContent(
    val node: NavigableNode,
    val data: Any,  // Could be String, ByteArray, or domain object
    val mimeType: String = "text/plain"
)

data class AttentionEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val nodeId: String,
    val action: AttentionAction,
    val duration: Long? = null
)

enum class AttentionAction {
    VIEW, SELECT, EXPAND, COLLAPSE, SEARCH, EXPORT
}

/**
 * Composite navigator that unifies multiple sources
 */
class CompositeAttentionNavigator(
    private val navigators: Map<String, AttentionNavigator>
) : AttentionNavigator {
    
    override suspend fun listRoots(): List<NavigableNode> {
        return navigators.map { (id, nav) ->
            NavigableNode(
                id = id,
                name = id.replace("-", " ").capitalize(),
                type = NodeType.SOURCE,
                hasChildren = true
            )
        }
    }
    
    override suspend fun getChildren(node: NavigableNode): List<NavigableNode> {
        return when (node.type) {
            NodeType.SOURCE -> {
                navigators[node.id]?.listRoots() ?: emptyList()
            }
            else -> {
                val sourceId = findSourceId(node)
                navigators[sourceId]?.getChildren(node) ?: emptyList()
            }
        }
    }
    
    override suspend fun getContent(node: NavigableNode): NavigableContent? {
        val sourceId = findSourceId(node)
        return navigators[sourceId]?.getContent(node)
    }
    
    override suspend fun search(query: String, scope: NavigableNode?): Flow<NavigableNode> {
        return if (scope == null) {
            // Search all sources
            navigators.values.asFlow()
                .flatMapMerge { it.search(query) }
        } else {
            // Search specific source
            val sourceId = findSourceId(scope)
            navigators[sourceId]?.search(query, scope) ?: emptyFlow()
        }
    }
    
    override fun recordAttention(node: NavigableNode, action: AttentionAction) {
        // Record in appropriate navigator
        val sourceId = findSourceId(node)
        navigators[sourceId]?.recordAttention(node, action)
    }
    
    override fun getAttentionHistory(): Flow<AttentionEvent> {
        return navigators.values.asFlow()
            .flatMapMerge { it.getAttentionHistory() }
    }
    
    private fun findSourceId(node: NavigableNode): String {
        var current = node
        while (current.parent != null) {
            current = NavigableNode(id = current.parent!!, name = "", type = NodeType.SOURCE)
        }
        return current.id
    }
}

/**
 * Example navigators for specific sources
 */
class CouchDBNavigator(private val baseUrl: String) : AttentionNavigator {
    override suspend fun listRoots(): List<NavigableNode> {
        // List databases
        return listOf(
            NavigableNode("_all_dbs", "All Databases", NodeType.DIRECTORY, hasChildren = true),
            NavigableNode("_design", "Design Documents", NodeType.DIRECTORY, hasChildren = true)
        )
    }
    
    override suspend fun getChildren(node: NavigableNode): List<NavigableNode> {
        // Get docs, views, etc
        return emptyList()
    }
    
    override suspend fun getContent(node: NavigableNode): NavigableContent? {
        // Fetch document
        return null
    }
    
    override suspend fun search(query: String, scope: NavigableNode?): Flow<NavigableNode> {
        // Use CouchDB search
        return emptyFlow()
    }
    
    override fun recordAttention(node: NavigableNode, action: AttentionAction) {
        // Track what's being accessed
    }
    
    override fun getAttentionHistory(): Flow<AttentionEvent> = emptyFlow()
}

class FileSystemNavigator(private val rootPath: String) : AttentionNavigator {
    override suspend fun listRoots(): List<NavigableNode> {
        // List directories
        return emptyList()
    }
    
    override suspend fun getChildren(node: NavigableNode): List<NavigableNode> {
        // List files/dirs
        return emptyList()
    }
    
    override suspend fun getContent(node: NavigableNode): NavigableContent? {
        // Read file
        return null
    }
    
    override suspend fun search(query: String, scope: NavigableNode?): Flow<NavigableNode> {
        // File search
        return emptyFlow()
    }
    
    override fun recordAttention(node: NavigableNode, action: AttentionAction) {}
    override fun getAttentionHistory(): Flow<AttentionEvent> = emptyFlow()
}

/**
 * Factory for creating navigators
 */
object NavigatorFactory {
    fun createFiduciaryNavigator(): AttentionNavigator {
        return CompositeAttentionNavigator(
            mapOf(
                "divine" to FileSystemNavigator("/data/divine-indexes"),
                "ocr" to FileSystemNavigator("/data/ocr-documents"),
                "couchdb" to CouchDBNavigator("http://localhost:5984"),
                "blackboard" to createBlackboardNavigator(),
                "telemetry" to createTelemetryNavigator()
            )
        )
    }
    
    private fun createBlackboardNavigator(): AttentionNavigator {
        // Return navigator for blackboard lattice
        return object : AttentionNavigator {
            override suspend fun listRoots() = emptyList<NavigableNode>()
            override suspend fun getChildren(node: NavigableNode) = emptyList<NavigableNode>()
            override suspend fun getContent(node: NavigableNode) = null
            override suspend fun search(query: String, scope: NavigableNode?) = emptyFlow<NavigableNode>()
            override fun recordAttention(node: NavigableNode, action: AttentionAction) {}
            override fun getAttentionHistory() = emptyFlow<AttentionEvent>()
        }
    }
    
    private fun createTelemetryNavigator(): AttentionNavigator {
        // Return navigator for telemetry data
        return object : AttentionNavigator {
            override suspend fun listRoots() = emptyList<NavigableNode>()
            override suspend fun getChildren(node: NavigableNode) = emptyList<NavigableNode>()
            override suspend fun getContent(node: NavigableNode) = null
            override suspend fun search(query: String, scope: NavigableNode?) = emptyFlow<NavigableNode>()
            override fun recordAttention(node: NavigableNode, action: AttentionAction) {}
            override fun getAttentionHistory() = emptyFlow<AttentionEvent>()
        }
    }
}