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
        ipfsStorage: IpfsStorage = IpfsStorage(),
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
    suspend fun retrieve(key: String): Indexed<Byte>? = coroutineScope {
        val mode = coroutineContext[DistributedStorageCCEK.STORAGE_MODE]?.mode ?: StorageMode.HYBRID
        val ipfs = coroutineContext[DistributedStorageCCEK.IPFS_CLIENT]?.client
        val couch = coroutineContext[DistributedStorageCCEK.COUCH_CLIENT]?.client
        
        when (mode) {
            StorageMode.IPFS_ONLY -> {
                requireNotNull(ipfs) { "IPFS client required" }
                // Would need to resolve key to CID first
                null
            }
            
            StorageMode.COUCH_ONLY -> {
                requireNotNull(couch) { "CouchDB client required" }
                try {
                    val doc = couch.getDocument("distributed_storage", key)
                    val dataStr = doc.data["data"]?.jsonPrimitive?.content ?: return@coroutineScope null
                    base64Decode(dataStr)
                } catch (e: Exception) {
                    null
                }
            }
            
            StorageMode.HYBRID, StorageMode.REPLICATED -> {
                // Try CouchDB first (faster)
                couch?.let {
                    try {
                        val doc = it.getDocument("distributed_storage", key)
                        val dataStr = doc.data["data"]?.jsonPrimitive?.content
                        if (dataStr != null) {
                            return@coroutineScope base64Decode(dataStr)
                        }
                    } catch (e: Exception) {
                        // Fall through to IPFS
                    }
                }
                
                // Try IPFS if CouchDB failed
                // Would need to resolve key to CID from gossip or metadata
                null
            }
        }
    }
    
    /**
     * Execute a WAM-style computation graph
     */
    suspend fun executeGraph(graph: ExecutionGraph): Indexed<Any?> = coroutineScope {
        val results = mutableListOf<Any?>()
        val depth = coroutineContext[DistributedStorageCCEK.RECURSION_DEPTH]?.depth ?: 0
        
        // Update context with new graph and depth
        val newContext = coroutineContext + 
            ExecutionGraphElement(graph) +
            RecursionDepthElement(depth + 1)
        
        // Execute nodes in topological order
        val visited = mutableSetOf<Int>()
        val stack = mutableListOf<Int>()
        
        // Find all nodes with no incoming edges (start nodes)
        val incomingCount = IntArray(graph.nodes.a)
        for (i in 0 until graph.edges.a) {
            val edge = graph.edges.b(i)
            incomingCount[edge.b]++
        }
        
        for (i in 0 until graph.nodes.a) {
            if (incomingCount[i] == 0) {
                stack.add(i)
            }
        }
        
        // Process nodes
        while (stack.isNotEmpty()) {
            val nodeIndex = stack.removeAt(0)
            if (visited.contains(nodeIndex)) continue
            
            visited.add(nodeIndex)
            val node = graph.nodes.b(nodeIndex)
            
            // Execute node
            val result = withContext(newContext) {
                executeNode(node)
            }
            results.add(result)
            
            // Add dependent nodes to stack
            for (i in 0 until graph.edges.a) {
                val edge = graph.edges.b(i)
                if (edge.a == nodeIndex) {
                    incomingCount[edge.b]--
                    if (incomingCount[edge.b] == 0) {
                        stack.add(edge.b)
                    }
                }
            }
        }
        
        results.size j { results[it] }
    }
    
    private suspend fun executeNode(node: ExecutionNode): Any? = when (node) {
        is ExecutionNode.StoreNode -> store(node.key, node.data)
        is ExecutionNode.RetrieveNode -> retrieve(node.key)
        is ExecutionNode.ReplicateNode -> replicate(node.source, node.target)
        is ExecutionNode.QueryNode -> query(node.query)
        is ExecutionNode.TransformNode -> null // Would execute transform
        is ExecutionNode.BranchNode -> {
            if (node.condition()) node.trueNode else node.falseNode
        }
        is ExecutionNode.RecurseNode -> {
            val depth = coroutineContext[DistributedStorageCCEK.RECURSION_DEPTH]?.depth ?: 0
            if (depth < node.maxDepth) {
                executeGraph(node.subgraph)
            } else {
                null
            }
        }
    }
    
    private suspend fun replicate(source: String, target: String): Boolean {
        val couch = coroutineContext[DistributedStorageCCEK.COUCH_CLIENT]?.client ?: return false
        
        val request = ReplicationRequest(
            source = source,
            target = target,
            continuous = false
        )
        
        return try {
            val response = couch.replicate(request)
            response.ok
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun query(query: JsonObject): Indexed<JsonElement> {
        // Would execute query against CouchDB or IPFS
        return emptyIndex()
    }
}

// Result types
sealed class StorageResult {
    data class Success(val key: String, val ref: String) : StorageResult()
    data class Failure(val key: String, val error: String) : StorageResult()
}

// Helper functions
private fun <T> appendToIndexed(indexed: Indexed<T>, item: T): Indexed<T> {
    val newSize = indexed.a + 1
    return newSize j { i ->
        if (i < indexed.a) indexed.b(i) else item
    }
}

private fun base64Encode(data: Indexed<Byte>): String {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val sb = StringBuilder()
    
    var i = 0
    while (i < data.a) {
        val b1 = data.b(i++).toInt() and 0xFF
        val b2 = if (i < data.a) data.b(i++).toInt() and 0xFF else 0
        val b3 = if (i < data.a) data.b(i++).toInt() and 0xFF else 0
        
        sb.append(alphabet[(b1 ushr 2) and 0x3F])
        sb.append(alphabet[((b1 and 0x03) shl 4) or ((b2 ushr 4) and 0x0F)])
        sb.append(if (i - 2 < data.a) alphabet[((b2 and 0x0F) shl 2) or ((b3 ushr 6) and 0x03)] else '=')
        sb.append(if (i - 1 < data.a) alphabet[b3 and 0x3F] else '=')
    }
    
    return sb.toString()
}

private fun base64Decode(encoded: String): Indexed<Byte> {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val bytes = mutableListOf<Byte>()
    
    var i = 0
    while (i < encoded.length) {
        val b1 = alphabet.indexOf(encoded[i++])
        val b2 = alphabet.indexOf(encoded[i++])
        val b3 = if (i < encoded.length && encoded[i] != '=') alphabet.indexOf(encoded[i++]) else -1
        val b4 = if (i < encoded.length && encoded[i] != '=') alphabet.indexOf(encoded[i++]) else -1
        
        bytes.add(((b1 shl 2) or (b2 shr 4)).toByte())
        if (b3 >= 0) bytes.add((((b2 and 0x0F) shl 4) or (b3 shr 2)).toByte())
        if (b4 >= 0) bytes.add((((b3 and 0x03) shl 6) or b4).toByte())
    }
    
    return bytes.size j { bytes[it] }
}

private fun <T> emptyIndex(): Indexed<T> = 0 j { throw NoSuchElementException() }