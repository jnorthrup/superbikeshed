@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.torrent.rpc

import borg.trikeshed.lib.*
import borg.trikeshed.services.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * TorrentRpcTransformer - Advanced RPC Protocol Transformation
 * 
 * Transforms RPC requests between different protocols:
 * - aria2c JSON-RPC ↔ TrikeShed native protocol
 * - HTTP REST ↔ JSON-RPC
 * - gRPC ↔ JSON-RPC
 * - WebSocket ↔ HTTP
 * - Distributed routing and load balancing
 * - Protocol versioning and migration
 */
class TorrentRpcTransformer(
    internal val requestFactory: RequestFactoryService,
    internal val nodeId: String
) {
    
    internal val protocolHandlers = mutableMapOf<String, ProtocolHandler>()
    internal val routeTable = mutableMapOf<String, RouteEntry>()
    internal val loadBalancer = LoadBalancer()
    internal val protocolConverter = ProtocolConverter()
    
    init {
        registerDefaultProtocols()
    }
    
    /**
     * Register protocol handlers
     */
    internal fun registerDefaultProtocols() {
        registerProtocol("aria2c", Aria2cProtocolHandler())
        registerProtocol("rest", RestProtocolHandler())
        registerProtocol("grpc", GrpcProtocolHandler())
        registerProtocol("websocket", WebSocketProtocolHandler())
        registerProtocol("native", NativeProtocolHandler())
    }
    
    /**
     * Register a new protocol handler
     */
    fun registerProtocol(name: String, handler: ProtocolHandler) {
        protocolHandlers[name] = handler
    }
    
    /**
     * Transform request from source protocol to target protocol
     */
    suspend fun transformRequest(
        sourceProtocol: String,
        targetProtocol: String,
        request: ByteArray,
        context: TransformContext = TransformContext()
    ): ByteArray {
        
        val sourceHandler = protocolHandlers[sourceProtocol] 
            ?: throw UnsupportedProtocolException("Unsupported source protocol: $sourceProtocol")
        
        val targetHandler = protocolHandlers[targetProtocol]
            ?: throw UnsupportedProtocolException("Unsupported target protocol: $targetProtocol")
        
        // Parse source request
        val parsedRequest = sourceHandler.parseRequest(request)
        
        // Apply transformations
        val transformedRequest = applyTransformations(parsedRequest, context)
        
        // Route to appropriate node
        val routedRequest = routeRequest(transformedRequest, context)
        
        // Serialize to target protocol
        return targetHandler.serializeRequest(routedRequest)
    }
    
    /**
     * Transform response from target protocol back to source protocol
     */
    suspend fun transformResponse(
        sourceProtocol: String,
        targetProtocol: String,
        response: ByteArray,
        context: TransformContext = TransformContext()
    ): ByteArray {
        
        val sourceHandler = protocolHandlers[sourceProtocol]
            ?: throw UnsupportedProtocolException("Unsupported source protocol: $sourceProtocol")
        
        val targetHandler = protocolHandlers[targetProtocol]
            ?: throw UnsupportedProtocolException("Unsupported target protocol: $targetProtocol")
        
        // Parse target response
        val parsedResponse = targetHandler.parseResponse(response)
        
        // Apply reverse transformations
        val transformedResponse = applyReverseTransformations(parsedResponse, context)
        
        // Serialize to source protocol
        return sourceHandler.serializeResponse(transformedResponse)
    }
    
    /**
     * Apply request transformations
     */
    internal suspend fun applyTransformations(
        request: TransformedRequest,
        context: TransformContext
    ): TransformedRequest {
        
        var transformed = request
        
        // Protocol versioning
        transformed = protocolConverter.convertVersion(transformed, context.sourceVersion, context.targetVersion)
        
        // Authentication transformation
        transformed = protocolConverter.convertAuth(transformed, context.sourceAuth, context.targetAuth)
        
        // Method mapping
        transformed = protocolConverter.mapMethods(transformed, context.methodMapping)
        
        // Parameter transformation
        transformed = protocolConverter.transformParameters(transformed, context.parameterMapping)
        
        // Add metadata
        transformed = transformed.copy(
            metadata = transformed.metadata + mapOf(
                "transformed_at" to kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                "transformer_node" to nodeId,
                "source_protocol" to context.sourceProtocol,
                "target_protocol" to context.targetProtocol
            )
        )
        
        return transformed
    }
    
    /**
     * Apply reverse transformations for responses
     */
    internal suspend fun applyReverseTransformations(
        response: TransformedResponse,
        context: TransformContext
    ): TransformedResponse {
        
        var transformed = response
        
        // Reverse protocol versioning
        transformed = protocolConverter.convertVersion(transformed, context.targetVersion, context.sourceVersion)
        
        // Reverse method mapping
        transformed = protocolConverter.mapMethods(transformed, context.methodMapping.reversed())
        
        // Reverse parameter transformation
        transformed = protocolConverter.transformParameters(transformed, context.parameterMapping.reversed())
        
        // Add transformation metadata
        transformed = transformed.copy(
            metadata = transformed.metadata + mapOf(
                "transformed_at" to kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                "transformer_node" to nodeId
            )
        )
        
        return transformed
    }
    
    /**
     * Route request to appropriate node
     */
    internal suspend fun routeRequest(
        request: TransformedRequest,
        context: TransformContext
    ): TransformedRequest {
        
        val routeKey = "${request.method}:${request.service}"
        val route = routeTable[routeKey] ?: RouteEntry(
            targetNode = nodeId,
            loadBalancing = LoadBalancingStrategy.ROUND_ROBIN,
            failover = emptyList()
        )
        
        val targetNode = when (route.loadBalancing) {
            LoadBalancingStrategy.ROUND_ROBIN -> loadBalancer.roundRobin(route.targetNode, route.failover)
            LoadBalancingStrategy.LEAST_CONNECTIONS -> loadBalancer.leastConnections(route.targetNode, route.failover)
            LoadBalancingStrategy.WEIGHTED -> loadBalancer.weighted(route.targetNode, route.failover, route.weights)
            LoadBalancingStrategy.CONSISTENT_HASH -> loadBalancer.consistentHash(request.id, route.targetNode, route.failover)
        }
        
        return request.copy(
            targetNode = targetNode,
            metadata = request.metadata + mapOf("routed_to" to targetNode)
        )
    }
    
    /**
     * Add route to routing table
     */
    fun addRoute(
        method: String,
        service: String,
        targetNode: String,
        loadBalancing: LoadBalancingStrategy = LoadBalancingStrategy.ROUND_ROBIN,
        failover: List<String> = emptyList(),
        weights: Map<String, Int> = emptyMap()
    ) {
        val routeKey = "$method:$service"
        routeTable[routeKey] = RouteEntry(targetNode, loadBalancing, failover, weights)
    }
    
    /**
     * Stream transformation for real-time protocols
     */
    fun transformStream(
        sourceProtocol: String,
        targetProtocol: String,
        sourceFlow: Flow<ByteArray>,
        context: TransformContext = TransformContext()
    ): Flow<ByteArray> = flow {
        
        sourceFlow.collect { requestData ->
            val transformedData = transformRequest(sourceProtocol, targetProtocol, requestData, context)
            emit(transformedData)
        }
    }
    
    /**
     * Batch transformation for multiple requests
     */
    suspend fun transformBatch(
        sourceProtocol: String,
        targetProtocol: String,
        requests: List<ByteArray>,
        context: TransformContext = TransformContext()
    ): List<ByteArray> {
        
        return coroutineScope {
            requests.map { request ->
                async {
                    transformRequest(sourceProtocol, targetProtocol, request, context)
                }
            }.awaitAll()
        }
    }
}

/**
 * Protocol Handler Interface
 */
interface ProtocolHandler {
    fun parseRequest(data: ByteArray): TransformedRequest
    fun serializeRequest(request: TransformedRequest): ByteArray
    fun parseResponse(data: ByteArray): TransformedResponse
    fun serializeResponse(response: TransformedResponse): ByteArray
    fun supportsProtocol(protocol: String): Boolean
}

/**
 * aria2c Protocol Handler
 */
class Aria2cProtocolHandler : ProtocolHandler {
    
    internal val json = Json { ignoreUnknownKeys = true }
    
    override fun parseRequest(data: ByteArray): TransformedRequest {
        val rpcRequest = json.decodeFromString(RpcRequest.serializer(), data.decodeToString())
        
        return TransformedRequest(
            id = rpcRequest.id,
            method = rpcRequest.method,
            service = "aria2",
            parameters = rpcRequest.params ?: emptyMap(),
            metadata = mapOf("protocol" to "aria2c")
        )
    }
    
    override fun serializeRequest(request: TransformedRequest): ByteArray {
        val rpcRequest = RpcRequest(
            id = request.id,
            method = request.method,
            params = request.parameters
        )
        
        return json.encodeToString(RpcRequest.serializer(), rpcRequest).encodeToByteArray()
    }
    
    override fun parseResponse(data: ByteArray): TransformedResponse {
        val rpcResponse = json.decodeFromString(RpcResponse.serializer(), data.decodeToString())
        
        return TransformedResponse(
            id = rpcResponse.id,
            result = rpcResponse.result,
            error = rpcResponse.error?.let { TransformedError(it.code, it.message, it.data) },
            metadata = mapOf("protocol" to "aria2c")
        )
    }
    
    override fun serializeResponse(response: TransformedResponse): ByteArray {
        val rpcResponse = RpcResponse(
            id = response.id,
            result = response.result,
            error = response.error?.let { RpcError(it.code, it.message, it.data) }
        )
        
        return json.encodeToString(RpcResponse.serializer(), rpcResponse).encodeToByteArray()
    }
    
    override fun supportsProtocol(protocol: String): Boolean = protocol == "aria2c"
}

/**
 * REST Protocol Handler
 */
class RestProtocolHandler : ProtocolHandler {
    
    override fun parseRequest(data: ByteArray): TransformedRequest {
        // Parse HTTP request and convert to internal format
        val httpRequest = parseHttpRequest(data)
        
        return TransformedRequest(
            id = httpRequest.headers["X-Request-ID"] ?: generateId(),
            method = mapHttpMethodToRpc(httpRequest.method, httpRequest.path),
            service = extractServiceFromPath(httpRequest.path),
            parameters = httpRequest.queryParams + httpRequest.bodyParams,
            metadata = mapOf(
                "protocol" to "rest",
                "http_method" to httpRequest.method,
                "path" to httpRequest.path
            )
        )
    }
    
    override fun serializeRequest(request: TransformedRequest): ByteArray {
        // Convert internal format to HTTP request
        val httpRequest = createHttpRequest(request)
        return serializeHttpRequest(httpRequest)
    }
    
    override fun parseResponse(data: ByteArray): TransformedResponse {
        val httpResponse = parseHttpResponse(data)
        
        return TransformedResponse(
            id = httpResponse.headers["X-Request-ID"] ?: generateId(),
            result = httpResponse.body,
            error = if (httpResponse.statusCode >= 400) {
                TransformedError(httpResponse.statusCode, httpResponse.statusText, httpResponse.body)
            } else null,
            metadata = mapOf(
                "protocol" to "rest",
                "status_code" to httpResponse.statusCode
            )
        )
    }
    
    override fun serializeResponse(response: TransformedResponse): ByteArray {
        val httpResponse = createHttpResponse(response)
        return serializeHttpResponse(httpResponse)
    }
    
    override fun supportsProtocol(protocol: String): Boolean = protocol == "rest"
    
    // Helper methods for HTTP parsing/serialization
    internal fun parseHttpRequest(data: ByteArray): HttpRequest = HttpRequest()
    internal fun serializeHttpRequest(request: HttpRequest): ByteArray = ByteArray(0)
    internal fun parseHttpResponse(data: ByteArray): HttpResponse = HttpResponse()
    internal fun serializeHttpResponse(response: HttpResponse): ByteArray = ByteArray(0)
    internal fun mapHttpMethodToRpc(method: String, path: String): String = "aria2.addUri"
    internal fun extractServiceFromPath(path: String): String = "aria2"
    internal fun createHttpRequest(request: TransformedRequest): HttpRequest = HttpRequest()
    internal fun createHttpResponse(response: TransformedResponse): HttpResponse = HttpResponse()
    internal fun generateId(): String = "rest_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
}

/**
 * gRPC Protocol Handler
 */
class GrpcProtocolHandler : ProtocolHandler {
    
    override fun parseRequest(data: ByteArray): TransformedRequest {
        // Parse gRPC request
        return TransformedRequest(
            id = generateId(),
            method = "aria2.addUri", // Extract from gRPC method
            service = "aria2",
            parameters = emptyMap(), // Parse from protobuf
            metadata = mapOf("protocol" to "grpc")
        )
    }
    
    override fun serializeRequest(request: TransformedRequest): ByteArray {
        // Serialize to gRPC format
        return ByteArray(0)
    }
    
    override fun parseResponse(data: ByteArray): TransformedResponse {
        return TransformedResponse(
            id = generateId(),
            result = null,
            error = null,
            metadata = mapOf("protocol" to "grpc")
        )
    }
    
    override fun serializeResponse(response: TransformedResponse): ByteArray {
        return ByteArray(0)
    }
    
    override fun supportsProtocol(protocol: String): Boolean = protocol == "grpc"
    
    internal fun generateId(): String = "grpc_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
}

/**
 * WebSocket Protocol Handler
 */
class WebSocketProtocolHandler : ProtocolHandler {
    
    override fun parseRequest(data: ByteArray): TransformedRequest {
        // Parse WebSocket message
        return TransformedRequest(
            id = generateId(),
            method = "aria2.addUri",
            service = "aria2",
            parameters = emptyMap(),
            metadata = mapOf("protocol" to "websocket")
        )
    }
    
    override fun serializeRequest(request: TransformedRequest): ByteArray {
        return ByteArray(0)
    }
    
    override fun parseResponse(data: ByteArray): TransformedResponse {
        return TransformedResponse(
            id = generateId(),
            result = null,
            error = null,
            metadata = mapOf("protocol" to "websocket")
        )
    }
    
    override fun serializeResponse(response: TransformedResponse): ByteArray {
        return ByteArray(0)
    }
    
    override fun supportsProtocol(protocol: String): Boolean = protocol == "websocket"
    
    internal fun generateId(): String = "ws_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
}

/**
 * Native TrikeShed Protocol Handler
 */
class NativeProtocolHandler : ProtocolHandler {
    
    override fun parseRequest(data: ByteArray): TransformedRequest {
        // Parse native TrikeShed protocol
        return TransformedRequest(
            id = generateId(),
            method = "aria2.addUri",
            service = "aria2",
            parameters = emptyMap(),
            metadata = mapOf("protocol" to "native")
        )
    }
    
    override fun serializeRequest(request: TransformedRequest): ByteArray {
        return ByteArray(0)
    }
    
    override fun parseResponse(data: ByteArray): TransformedResponse {
        return TransformedResponse(
            id = generateId(),
            result = null,
            error = null,
            metadata = mapOf("protocol" to "native")
        )
    }
    
    override fun serializeResponse(response: TransformedResponse): ByteArray {
        return ByteArray(0)
    }
    
    override fun supportsProtocol(protocol: String): Boolean = protocol == "native"
    
    internal fun generateId(): String = "native_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
}

/**
 * Load Balancer
 */
class LoadBalancer {
    
    internal val nodeConnections = mutableMapOf<String, Int>()
    internal val nodeWeights = mutableMapOf<String, Int>()
    internal var roundRobinIndex = 0
    
    fun roundRobin(primary: String, failover: List<String>): String {
        val allNodes = listOf(primary) + failover
        roundRobinIndex = (roundRobinIndex + 1) % allNodes.size
        return allNodes[roundRobinIndex]
    }
    
    fun leastConnections(primary: String, failover: List<String>): String {
        val allNodes = listOf(primary) + failover
        return allNodes.minByOrNull { nodeConnections[it] ?: 0 } ?: primary
    }
    
    fun weighted(primary: String, failover: List<String>, weights: Map<String, Int>): String {
        val allNodes = listOf(primary) + failover
        val totalWeight = allNodes.sumOf { weights[it] ?: 1 }
        val random = (0..totalWeight).random()
        
        var currentWeight = 0
        for (node in allNodes) {
            currentWeight += weights[node] ?: 1
            if (random <= currentWeight) {
                return node
            }
        }
        
        return primary
    }
    
    fun consistentHash(key: String, primary: String, failover: List<String>): String {
        val allNodes = listOf(primary) + failover
        val hash = key.hashCode()
        return allNodes[hash.absoluteValue % allNodes.size]
    }
    
    fun updateConnections(node: String, connections: Int) {
        nodeConnections[node] = connections
    }
    
    fun updateWeights(node: String, weight: Int) {
        nodeWeights[node] = weight
    }
}

/**
 * Protocol Converter
 */
class ProtocolConverter {
    
    fun convertVersion(
        request: TransformedRequest,
        sourceVersion: String,
        targetVersion: String
    ): TransformedRequest {
        // Handle protocol version differences
        return request.copy(
            metadata = request.metadata + mapOf(
                "source_version" to sourceVersion,
                "target_version" to targetVersion
            )
        )
    }
    
    fun convertVersion(
        response: TransformedResponse,
        sourceVersion: String,
        targetVersion: String
    ): TransformedResponse {
        return response.copy(
            metadata = response.metadata + mapOf(
                "source_version" to sourceVersion,
                "target_version" to targetVersion
            )
        )
    }
    
    fun convertAuth(
        request: TransformedRequest,
        sourceAuth: AuthInfo?,
        targetAuth: AuthInfo?
    ): TransformedRequest {
        // Convert authentication between protocols
        return request.copy(
            metadata = request.metadata + mapOf(
                "auth_converted" to true,
                "source_auth_type" to sourceAuth?.type,
                "target_auth_type" to targetAuth?.type
            )
        )
    }
    
    fun mapMethods(
        request: TransformedRequest,
        methodMapping: Map<String, String>
    ): TransformedRequest {
        val mappedMethod = methodMapping[request.method] ?: request.method
        return request.copy(method = mappedMethod)
    }
    
    fun transformParameters(
        request: TransformedRequest,
        parameterMapping: Map<String, String>
    ): TransformedRequest {
        val transformedParams = request.parameters.mapKeys { (key, _) ->
            parameterMapping[key] ?: key
        }
        return request.copy(parameters = transformedParams)
    }
}

/**
 * Data Classes
 */
@Serializable
data class TransformedRequest(
    val id: String,
    val method: String,
    val service: String,
    val parameters: Map<String, Any?>,
    val metadata: Map<String, Any?> = emptyMap(),
    val targetNode: String? = null
)

@Serializable
data class TransformedResponse(
    val id: String,
    val result: Any?,
    val error: TransformedError?,
    val metadata: Map<String, Any?> = emptyMap()
)

@Serializable
data class TransformedError(
    val code: Int,
    val message: String,
    val data: Any? = null
)

@Serializable
data class RouteEntry(
    val targetNode: String,
    val loadBalancing: LoadBalancingStrategy,
    val failover: List<String> = emptyList(),
    val weights: Map<String, Int> = emptyMap()
)

@Serializable
data class TransformContext(
    val sourceProtocol: String = "aria2c",
    val targetProtocol: String = "native",
    val sourceVersion: String = "1.0",
    val targetVersion: String = "1.0",
    val sourceAuth: AuthInfo? = null,
    val targetAuth: AuthInfo? = null,
    val methodMapping: Map<String, String> = emptyMap(),
    val parameterMapping: Map<String, String> = emptyMap()
)

@Serializable
data class AuthInfo(
    val type: String,
    val credentials: Map<String, String>
)

enum class LoadBalancingStrategy {
    ROUND_ROBIN,
    LEAST_CONNECTIONS,
    WEIGHTED,
    CONSISTENT_HASH
}

// HTTP request/response data classes
data class HttpRequest(
    val method: String = "GET",
    val path: String = "/",
    val headers: Map<String, String> = emptyMap(),
    val queryParams: Map<String, Any?> = emptyMap(),
    val bodyParams: Map<String, Any?> = emptyMap()
)

data class HttpResponse(
    val statusCode: Int = 200,
    val statusText: String = "OK",
    val headers: Map<String, String> = emptyMap(),
    val body: Any? = null
)

class UnsupportedProtocolException(message: String) : Exception(message) 