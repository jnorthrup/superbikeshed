package borg.trikeshed.kademlia

import borg.trikeshed.lib.ByteSeries
import borg.trikeshed.net.common.UdpSocketService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap

@Serializable
sealed class KademliaRPCRequest {
    abstract val id: String
    abstract val type: String
}

@Serializable
sealed class KademliaRPCResponse {
    abstract val id: String
    abstract val type: String
}

@Serializable
data class PingRequest(
    override val id: String,
    override val type: String = "ping",
    val sender: NodeInfo
) : KademliaRPCRequest()

@Serializable
data class PingResponse(
    override val id: String,
    override val type: String = "pong",
    val sender: NodeInfo
) : KademliaRPCResponse()

@Serializable
data class FindNodeRequest(
    override val id: String,
    override val type: String = "find_node",
    val sender: NodeInfo,
    val target: NodeId
) : KademliaRPCRequest()

@Serializable
data class FindNodeResponse(
    override val id: String,
    override val type: String = "find_node_response",
    val sender: NodeInfo,
    val nodes: List<NodeInfo>
) : KademliaRPCResponse()

@Serializable
data class FindValueRequest(
    override val id: String,
    override val type: String = "find_value",
    val sender: NodeInfo,
    val key: ByteSeries
) : KademliaRPCRequest()

@Serializable
data class FindValueResponse(
    override val id: String,
    override val type: String = "find_value_response",
    val sender: NodeInfo,
    val value: ByteSeries?,
    val nodes: List<NodeInfo>?
) : KademliaRPCResponse()

@Serializable
data class StoreRequest(
    override val id: String,
    override val type: String = "store",
    val sender: NodeInfo,
    val key: ByteSeries,
    val value: ByteSeries
) : KademliaRPCRequest()

@Serializable
data class StoreResponse(
    override val id: String,
    override val type: String = "store_response",
    val sender: NodeInfo,
    val success: Boolean
) : KademliaRPCResponse()

class KademliaRPCService(
    private val node: KademliaNode,
    private val udpService: UdpSocketService,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val pendingRequests = ConcurrentHashMap<String, RPCResponseCallback>()

    init {
        startListening()
    }

    private fun startListening() {
        udpService.receive { data, address ->
            handleIncomingMessage(data, address)
        }
    }

    private suspend fun handleIncomingMessage(data: ByteSeries, address: String) {
        try {
            val message = json.decodeFromString<KademliaRPCRequest>(data.toString())
            when (message) {
                is PingRequest -> handlePing(message, address)
                is FindNodeRequest -> handleFindNode(message, address)
                is FindValueRequest -> handleFindValue(message, address)
                is StoreRequest -> handleStore(message, address)
            }
        } catch (e: Exception) {
            // Handle error
        }
    }

    private suspend fun handlePing(request: PingRequest, address: String) {
        val response = PingResponse(
            id = request.id,
            sender = node.getNodeInfo()
        )
        sendResponse(response, address)
    }

    private suspend fun handleFindNode(request: FindNodeRequest, address: String) {
        val nodes = node.findNode(request.target, request.sender)
        val response = FindNodeResponse(
            id = request.id,
            sender = node.getNodeInfo(),
            nodes = nodes
        )
        sendResponse(response, address)
    }

    private suspend fun handleFindValue(request: FindValueRequest, address: String) {
        val value = node.findValue(request.key)
        val response = if (value != null) {
            FindValueResponse(
                id = request.id,
                sender = node.getNodeInfo(),
                value = value,
                nodes = null
            )
        } else {
            val nodes = node.findNode(NodeId(BigInteger(request.key.toByteArray())), request.sender)
            FindValueResponse(
                id = request.id,
                sender = node.getNodeInfo(),
                value = null,
                nodes = nodes
            )
        }
        sendResponse(response, address)
    }

    private suspend fun handleStore(request: StoreRequest, address: String) {
        val success = node.store(request.key, request.value)
        val response = StoreResponse(
            id = request.id,
            sender = node.getNodeInfo(),
            success = success
        )
        sendResponse(response, address)
    }

    private suspend fun sendResponse(response: KademliaRPCResponse, address: String) {
        val data = json.encodeToString(KademliaRPCResponse.serializer(), response)
        udpService.send(ByteSeries(data.toByteArray()), address)
    }

    suspend fun sendRequest(request: KademliaRPCRequest, address: String): KademliaRPCResponse {
        val data = json.encodeToString(KademliaRPCRequest.serializer(), request)
        udpService.send(ByteSeries(data.toByteArray()), address)
        
        return suspendCancellableCoroutine { continuation ->
            pendingRequests[request.id] = { response ->
                continuation.resume(response)
            }
        }
    }

    private fun handleResponse(response: KademliaRPCResponse) {
        pendingRequests[response.id]?.invoke(response)
        pendingRequests.remove(response.id)
    }
}

typealias RPCResponseCallback = (KademliaRPCResponse) -> Unit 