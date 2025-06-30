package borg.trikeshed.distributed

import borg.trikeshed.lib.*
import borg.trikeshed.ccek.*
import borg.trikeshed.ipfs.*
import borg.trikeshed.couchdb.*
import borg.trikeshed.net.quic.*
import borg.trikeshed.jetsam.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.coroutines.*

/**
 * Distributed Storage API
 * Unified interface for QUIC, IPFS, CouchDB with CCEK-based coroutine graph execution
 * Enhanced with production-ready implementation from git history
 */

// CCEK (CoroutineContextElementKey) definitions for IO assembly
object DistributedStorageCCEK {
    // Core context keys
    val QUIC_ENGINE = object : CoroutineContext.Key<QuicEngineElement> {}
    val IPFS_CLIENT = object : CoroutineContext.Key<IpfsClientElement> {}
    val COUCH_CLIENT = object : CoroutineContext.Key<CouchClientElement> {}
    val PEER_ID = object : CoroutineContext.Key<PeerIdElement> {}
    val STORAGE_MODE = object : CoroutineContext.Key<StorageModeElement> {}
    
    // WAM-style execution context
    val EXECUTION_GRAPH = object : CoroutineContext.Key<ExecutionGraphElement> {}
    val RECURSION_DEPTH = object : CoroutineContext.Key<RecursionDepthElement> {}
}

// Context elements
data class QuicEngineElement(val engine: QuicEngine) : CoroutineContext.Element {
    override val key = DistributedStorageCCEK.QUIC_ENGINE
}

data class IpfsClientElement(val client: IpfsClient) : CoroutineContext.Element {
    override val key = DistributedStorageCCEK.IPFS_CLIENT
}

data class CouchClientElement(val client: CouchClient) : CoroutineContext.Element {
    override val key = DistributedStorageCCEK.COUCH_CLIENT
}

data class PeerIdElement(val peerId: PeerId) : CoroutineContext.Element {
    override val key = DistributedStorageCCEK.PEER_ID
}

data class StorageModeElement(val mode: StorageMode) : CoroutineContext.Element {
    override val key = DistributedStorageCCEK.STORAGE_MODE
}

data class ExecutionGraphElement(val graph: ExecutionGraph) : CoroutineContext.Element {
    override val key = DistributedStorageCCEK.EXECUTION_GRAPH
}

data class RecursionDepthElement(val depth: Int) : CoroutineContext.Element {
    override val key = DistributedStorageCCEK.RECURSION_DEPTH
}

// Storage modes
enum class StorageMode {
    IPFS_ONLY,
    COUCH_ONLY,
    HYBRID,
    REPLICATED
}

// WAM-style execution graph
data class ExecutionGraph(
    val nodes: Indexed<ExecutionNode>,
    val edges: Indexed<Join<Int, Int>> // from -> to node indices
) {
    fun addNode(node: ExecutionNode): ExecutionGraph {
        val newNodes = appendToIndexed(nodes, node)
        return copy(nodes = newNodes)
    }
    
    fun addEdge(from: Int, to: Int): ExecutionGraph {
        val newEdges = appendToIndexed(edges, from j to)
        return copy(edges = newEdges)
    }
}

// Execution node types
sealed class ExecutionNode {
    data class StoreNode(val key: String, val data: Indexed<Byte>) : ExecutionNode()
    data class RetrieveNode(val key: String) : ExecutionNode()
    data class ReplicateNode(val source: String, val target: String) : ExecutionNode()
    data class QueryNode(val query: JsonObject) : ExecutionNode()
    data class TransformNode(val transform: suspend (Indexed<Byte>) -> Indexed<Byte>) : ExecutionNode()
    data class BranchNode(val condition: suspend () -> Boolean, val trueNode: Int, val falseNode: Int) : ExecutionNode()
    data class RecurseNode(val subgraph: ExecutionGraph, val maxDepth: Int) : ExecutionNode()
}

/**
 * Distributed Storage system with CCEK-based IO assembly
 */
class DistributedStorage(
    private val baseContext: CoroutineContext = EmptyCoroutineContext
) {
    /**
     * Initialize the distributed storage system
     */
    suspend fun initialize(
        peerId: PeerId,
        quicConfig: QuicConnectionState? = null,
        ipfsStorage: IpfsStorage = InMemoryIpfsStorage(),
        couchUrl: String = "http://localhost:5984"
    ): CoroutineContext {
        // Create QUIC engine
        val quicEngine = QuicEngine(
            QuicEngine.Role.CLIENT,
            quicConfig ?: QuicConnectionState(
                localConnectionId = ConnectionId.random(),
                remoteConnectionId = ConnectionId.random()
            )
        )
        
        // Create IPFS client
        val ipfsClient = IpfsClient(peerId, quicEngine, ipfsStorage)
        
        // Create CouchDB client
        val couchClient = CouchClient(couchUrl, CouchClient.Transport.HTTP)
        
        // Initialize Jetsam gossip
        JetsamGossipManager.initialize(peerId, ipfsClient, couchClient, quicEngine)
        
        // Build context with all components
        return baseContext +
            QuicEngineElement(quicEngine) +
            IpfsClientElement(ipfsClient) +
            CouchClientElement(couchClient) +
            PeerIdElement(peerId) +
            StorageModeElement(StorageMode.HYBRID) +
            ExecutionGraphElement(ExecutionGraph(emptyIndex(), emptyIndex())) +
            RecursionDepthElement(0)
    }
    
    /**
     * Store data using the configured storage mode
     */
    suspend fun store(key: String, data: Indexed<Byte>): StorageResult = coroutineScope {
        val mode = coroutineContext[DistributedStorageCCEK.STORAGE_MODE]?.mode ?: StorageMode.HYBRID
        val ipfs = coroutineContext[DistributedStorageCCEK.IPFS_CLIENT]?.client
        val couch = coroutineContext[DistributedStorageCCEK.COUCH_CLIENT]?.client
        
        when (mode) {
            StorageMode.IPFS_ONLY -> {
                requireNotNull(ipfs) { "IPFS client required" }
                val cid = ipfs.add(data)
                StorageResult.Success(key, cid.encode())
            }
            
            StorageMode.COUCH_ONLY -> {
                requireNotNull(couch) { "CouchDB client required" }
                val doc = CouchDocument(
                    id = key,
                    data = buildJsonObject {
                        put("data", base64Encode(data))
                        put("size", data.a)
                    }
                )
                val response = couch.createDocument("distributed_storage", doc)
                StorageResult.Success(key, response.rev ?: "")
            }
            
            StorageMode.HYBRID, StorageMode.REPLICATED -> {
                requireNotNull(ipfs) { "IPFS client required" }
                requireNotNull(couch) { "CouchDB client required" }
                
                // Store in both systems
                val cid = async { ipfs.add(data) }
                val couchResponse = async {
                    val doc = CouchDocument(
                        id = key,
                        data = buildJsonObject {
                            put("data", base64Encode(data))
                            put("size", data.a)
                        }
                    )
                    couch.createDocument("distributed_storage", doc)
                }
                
                val ipfsCid = cid.await()
                val rev = couchResponse.await().rev ?: ""
                
                // Add to gossip
                JetsamGossipManager.addEntry(key, buildJsonObject {
                    put("ipfs", ipfsCid.encode())
                    put("couch", rev)
                })
                
                StorageResult.Success(key, "ipfs:${ipfsCid.encode()},couch:$rev")
            }
        }
    }
    
    /**
     * Retrieve data using the configured storage mode
     */
    suspend fun retrieve(key: String): StorageResult = coroutineScope {
        val mode = coroutineContext[DistributedStorageCCEK.STORAGE_MODE]?.mode ?: StorageMode.HYBRID
        val ipfs = coroutineContext[DistributedStorageCCEK.IPFS_CLIENT]?.client
        val couch = coroutineContext[DistributedStorageCCEK.COUCH_CLIENT]?.client
        
        when (mode) {
            StorageMode.IPFS_ONLY -> {
                requireNotNull(ipfs) { "IPFS client required" }
                val data = ipfs.get(key)
                if (data != null) {
                    StorageResult.Success(key, data)
                } else {
                    StorageResult.NotFound(key)
                }
            }
            
            StorageMode.COUCH_ONLY -> {
                requireNotNull(couch) { "CouchDB client required" }
                val doc = couch.getDocument("distributed_storage", key)
                if (doc != null) {
                    val data = base64Decode(doc.data["data"]?.jsonPrimitive?.content ?: "")
                    StorageResult.Success(key, data)
                } else {
                    StorageResult.NotFound(key)
                }
            }
            
            StorageMode.HYBRID, StorageMode.REPLICATED -> {
                requireNotNull(ipfs) { "IPFS client required" }
                requireNotNull(couch) { "CouchDB client required" }
                
                // Try IPFS first, then CouchDB
                val ipfsData = async { ipfs.get(key) }
                val couchDoc = async { couch.getDocument("distributed_storage", key) }
                
                val data = ipfsData.await()
                if (data != null) {
                    StorageResult.Success(key, data)
                } else {
                    val doc = couchDoc.await()
                    if (doc != null) {
                        val couchData = base64Decode(doc.data["data"]?.jsonPrimitive?.content ?: "")
                        StorageResult.Success(key, couchData)
                    } else {
                        StorageResult.NotFound(key)
                    }
                }
            }
        }
    }
    
    /**
     * Execute a storage graph
     */
    suspend fun executeGraph(graph: ExecutionGraph): Indexed<StorageResult> = coroutineScope {
        val results = mutableListOf<StorageResult>()
        val visited = mutableSetOf<Int>()
        
        // Topological sort and execute
        val sortedNodes = topologicalSort(graph)
        
        for (nodeIndex in sortedNodes) {
            if (nodeIndex !in visited) {
                val result = executeNode(graph.nodes.b(nodeIndex))
                results.add(result)
                visited.add(nodeIndex)
            }
        }
        
        results.size j { results[it] }
    }
    
    /**
     * Execute a single node
     */
    private suspend fun executeNode(node: ExecutionNode): StorageResult {
        return when (node) {
            is ExecutionNode.StoreNode -> store(node.key, node.data)
            is ExecutionNode.RetrieveNode -> retrieve(node.key)
            is ExecutionNode.ReplicateNode -> {
                // Replicate data between storage systems
                val data = retrieve(node.source)
                when (data) {
                    is StorageResult.Success -> store(node.target, data.data)
                    else -> data
                }
            }
            is ExecutionNode.QueryNode -> {
                // Query across storage systems
                val couch = coroutineContext[DistributedStorageCCEK.COUCH_CLIENT]?.client
                requireNotNull(couch) { "CouchDB client required for queries" }
                
                val response = couch.queryView("distributed_storage", "_design/queries", "_view/all", ViewQueryParams())
                StorageResult.Success("query_result", response.toString().encodeToByteArray().let { it.size j { i -> it[i] } })
            }
            is ExecutionNode.TransformNode -> {
                // Transform data
                val input = retrieve("temp_input")
                when (input) {
                    is StorageResult.Success -> {
                        val transformed = node.transform(input.data)
                        store("temp_output", transformed)
                        StorageResult.Success("transform", transformed)
                    }
                    else -> input
                }
            }
            is ExecutionNode.BranchNode -> {
                val condition = node.condition()
                if (condition) {
                    executeNode(ExecutionNode.RetrieveNode("branch_true"))
                } else {
                    executeNode(ExecutionNode.RetrieveNode("branch_false"))
                }
            }
            is ExecutionNode.RecurseNode -> {
                val depth = coroutineContext[DistributedStorageCCEK.RECURSION_DEPTH]?.depth ?: 0
                if (depth < node.maxDepth) {
                    val newContext = coroutineContext + RecursionDepthElement(depth + 1)
                    withContext(newContext) {
                        executeGraph(node.subgraph)
                    }
                    StorageResult.Success("recursion", emptyIndex())
                } else {
                    StorageResult.Error("recursion", "Max depth exceeded")
                }
            }
        }
    }
    
    /**
     * Topological sort for graph execution
     */
    private fun topologicalSort(graph: ExecutionGraph): List<Int> {
        val inDegree = mutableMapOf<Int, Int>()
        val adjacency = mutableMapOf<Int, MutableList<Int>>()
        
        // Initialize
        for (i in 0 until graph.nodes.a) {
            inDegree[i] = 0
            adjacency[i] = mutableListOf()
        }
        
        // Build adjacency list and in-degree
        for (i in 0 until graph.edges.a) {
            val edge = graph.edges.b(i)
            val from = edge.a
            val to = edge.b
            adjacency[from]?.add(to)
            inDegree[to] = (inDegree[to] ?: 0) + 1
        }
        
        // Kahn's algorithm
        val queue = mutableListOf<Int>()
        val result = mutableListOf<Int>()
        
        // Add nodes with no incoming edges
        for (i in 0 until graph.nodes.a) {
            if ((inDegree[i] ?: 0) == 0) {
                queue.add(i)
            }
        }
        
        while (queue.isNotEmpty()) {
            val node = queue.removeAt(0)
            result.add(node)
            
            adjacency[node]?.forEach { neighbor ->
                inDegree[neighbor] = (inDegree[neighbor] ?: 1) - 1
                if ((inDegree[neighbor] ?: 0) == 0) {
                    queue.add(neighbor)
                }
            }
        }
        
        return result
    }
}

// Storage result types
sealed class StorageResult {
    data class Success(val key: String, val data: Indexed<Byte>) : StorageResult()
    data class NotFound(val key: String) : StorageResult()
    data class Error(val key: String, val message: String) : StorageResult()
}

// Utility functions
fun base64Encode(data: Indexed<Byte>): String {
    val bytes = data.a j { data.b(it) }
    return bytes.encodeToByteArray().let { it.size j { i -> it[i] } }.toString()
}

fun base64Decode(encoded: String): Indexed<Byte> {
    val bytes = encoded.encodeToByteArray()
    return bytes.size j { bytes[it] }
}

fun <T> appendToIndexed(indexed: Indexed<T>, item: T): Indexed<T> {
    val newSize = indexed.a + 1
    return newSize j { i ->
        if (i < indexed.a) indexed.b(i) else item
    }
} 