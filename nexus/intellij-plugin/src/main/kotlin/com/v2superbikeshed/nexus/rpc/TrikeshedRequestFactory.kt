package com.v2superbikeshed.nexus.rpc

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.v2superbikeshed.nexus.service.IntelliJAccessService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * TrikeShed RequestFactory RPC implementation that absorbs MCP server functionality
 * into a unified node-based architecture.
 * 
 * Based on GWT RequestFactory patterns:
 * - Service interfaces with @Service annotations
 * - Request contexts for batching operations
 * - Entity proxies for data transfer
 * - Receiver callbacks for async operations
 */
@Service(Service.Level.PROJECT)
class TrikeshedRequestFactory(internal val project: Project) {
    
    internal val serviceLocator = ServiceLocator()
    internal val requestProcessor = RequestProcessor()
    internal val nodeRegistry = NodeRegistry()
    
    /**
     * Create a request context for batching operations
     */
    fun createRequestContext(): RequestContext {
        return RequestContext(this)
    }
    
    /**
     * Register a TrikeShed node as an RPC service
     */
    fun registerNode(nodeId: String, service: TrikeshedNode) {
        nodeRegistry.register(nodeId, service)
        serviceLocator.registerService(nodeId, service)
    }
    
    /**
     * Process a batch of requests
     */
    suspend fun processRequests(context: RequestContext): BatchResponse {
        return requestProcessor.process(context, serviceLocator)
    }
    
    /**
     * Get a service proxy for type-safe RPC calls
     */
    inline fun <reified T : TrikeshedService> getService(): T {
        val serviceClass = T::class
        return serviceLocator.createProxy(serviceClass) as T
    }
}

/**
 * Base interface for all TrikeShed RPC services
 */
interface TrikeshedService {
    val nodeId: String
}

/**
 * TrikeShed node that can handle RPC requests
 */
abstract class TrikeshedNode(
    override val nodeId: String,
    val capabilities: Set<String>
) : TrikeshedService {
    
    internal val _status = MutableStateFlow(NodeStatus.IDLE)
    val status: StateFlow<NodeStatus> = _status.asStateFlow()
    
    /**
     * Handle an RPC request
     */
    abstract suspend fun handleRequest(method: String, params: Map<String, Any?>): Any?
    
    /**
     * Update node status
     */
    internal fun updateStatus(status: NodeStatus) {
        _status.value = status
    }
}

/**
 * RequestFactory-style request context for batching operations
 */
class RequestContext(internal val factory: TrikeshedRequestFactory) {
    
    internal val operations = mutableListOf<Operation>()
    
    /**
     * Add an operation to the batch
     */
    fun <T> invoke(
        serviceId: String,
        method: String,
        params: Map<String, Any?> = emptyMap(),
        receiver: Receiver<T>
    ) {
        operations.add(Operation(serviceId, method, params, receiver))
    }
    
    /**
     * Fire all batched requests
     */
    suspend fun fire() {
        factory.processRequests(this)
    }
    
    internal fun getOperations(): List<Operation> = operations.toList()
}

/**
 * Receiver for async operation results (RequestFactory pattern)
 */
interface Receiver<T> {
    fun onSuccess(response: T)
    fun onFailure(error: ServerFailure)
}

/**
 * Service locator for finding and creating service proxies
 */
class ServiceLocator {
    
    internal val services = ConcurrentHashMap<String, TrikeshedService>()
    internal val proxies = ConcurrentHashMap<String, Any>()
    
    fun registerService(id: String, service: TrikeshedService) {
        services[id] = service
    }
    
    fun findService(id: String): TrikeshedService? = services[id]
    
    fun <T : TrikeshedService> createProxy(serviceClass: kotlin.reflect.KClass<T>): Any {
        return proxies.getOrPut(serviceClass.simpleName ?: "") {
            // Create dynamic proxy for the service interface
            ServiceProxy(this, serviceClass)
        }
    }
}

/**
 * Request processor that handles batched operations
 */
class RequestProcessor {
    
    suspend fun process(context: RequestContext, locator: ServiceLocator): BatchResponse {
        val results = mutableListOf<OperationResult>()
        
        coroutineScope {
            context.getOperations().map { operation ->
                async {
                    processOperation(operation, locator)
                }
            }.awaitAll().forEach { result ->
                results.add(result)
            }
        }
        
        return BatchResponse(results)
    }
    
    internal suspend fun processOperation(
        operation: Operation,
        locator: ServiceLocator
    ): OperationResult {
        return try {
            val service = locator.findService(operation.serviceId) as? TrikeshedNode
                ?: return OperationResult.failure(
                    operation,
                    ServerFailure("Service not found: ${operation.serviceId}")
                )
            
            val result = service.handleRequest(operation.method, operation.params)
            OperationResult.success(operation, result)
            
        } catch (e: Exception) {
            OperationResult.failure(operation, ServerFailure(e.message ?: "Unknown error"))
        }
    }
}

/**
 * Node registry for managing TrikeShed nodes
 */
class NodeRegistry {
    
    internal val nodes = ConcurrentHashMap<String, TrikeshedNode>()
    
    fun register(nodeId: String, node: TrikeshedNode) {
        nodes[nodeId] = node
    }
    
    fun unregister(nodeId: String) {
        nodes.remove(nodeId)
    }
    
    fun findNode(nodeId: String): TrikeshedNode? = nodes[nodeId]
    
    fun findNodesByCapability(capability: String): List<TrikeshedNode> {
        return nodes.values.filter { it.capabilities.contains(capability) }
    }
}

// Data classes

@Serializable
data class Operation(
    val serviceId: String,
    val method: String,
    val params: Map<String, Any?>,
    @Transient val receiver: Receiver<*>? = null
)

@Serializable
data class OperationResult(
    val operation: Operation,
    val success: Boolean,
    val result: Any? = null,
    val error: ServerFailure? = null
) {
    companion object {
        fun success(operation: Operation, result: Any?): OperationResult {
            return OperationResult(operation, true, result, null)
        }
        
        fun failure(operation: Operation, error: ServerFailure): OperationResult {
            return OperationResult(operation, false, null, error)
        }
    }
}

@Serializable
data class BatchResponse(
    val results: List<OperationResult>
)

@Serializable
data class ServerFailure(
    val message: String,
    val stackTrace: String? = null
)

enum class NodeStatus {
    IDLE,
    PROCESSING,
    ERROR,
    SHUTDOWN
}

/**
 * Dynamic proxy for service interfaces
 */
class ServiceProxy<T : TrikeshedService>(
    internal val locator: ServiceLocator,
    internal val serviceClass: kotlin.reflect.KClass<T>
) {
    // Proxy implementation would use reflection or code generation
    // to create method implementations that convert calls to RPC operations
}

// Example MCP nodes integrated as TrikeShed nodes

/**
 * PSI MCP functionality as a TrikeShed node
 */
class PsiNode(
    internal val intelliJAccess: IntelliJAccessService
) : TrikeshedNode(
    nodeId = "psi-node",
    capabilities = setOf("find_symbols", "rename_symbols", "get_ast", "modify_ast")
) {
    
    override suspend fun handleRequest(method: String, params: Map<String, Any?>): Any? {
        updateStatus(NodeStatus.PROCESSING)
        
        return try {
            when (method) {
                "find_symbols" -> findSymbols(params)
                "rename_symbols" -> renameSymbols(params)
                "get_ast" -> getAst(params)
                "modify_ast" -> modifyAst(params)
                else -> throw IllegalArgumentException("Unknown method: $method")
            }
        } finally {
            updateStatus(NodeStatus.IDLE)
        }
    }
    
    internal suspend fun findSymbols(params: Map<String, Any?>): List<SymbolInfo> {
        val pattern = params["pattern"] as? String ?: "*"
        val includeLibraries = params["includeLibraries"] as? Boolean ?: false
        
        return intelliJAccess.findSymbolsByPattern(pattern, includeLibraries)
    }
    
    internal suspend fun renameSymbols(params: Map<String, Any?>): RefactorResult {
        val oldName = params["oldName"] as? String 
            ?: throw IllegalArgumentException("Missing oldName")
        val newName = params["newName"] as? String 
            ?: throw IllegalArgumentException("Missing newName")
        
        val element = intelliJAccess.findPsiElement(oldName)
            ?: throw IllegalArgumentException("Element not found: $oldName")
            
        return intelliJAccess.renameSymbol(element, newName)
    }
    
    internal suspend fun getAst(params: Map<String, Any?>): Map<String, Any> {
        val filePath = params["filePath"] as? String 
            ?: throw IllegalArgumentException("Missing filePath")
            
        val psiFile = intelliJAccess.getPsiFile(filePath)
            ?: throw IllegalArgumentException("File not found: $filePath")
            
        return mapOf(
            "filePath" to filePath,
            "content" to psiFile.text,
            "language" to psiFile.language.displayName
        )
    }
    
    internal suspend fun modifyAst(params: Map<String, Any?>): Boolean {
        val filePath = params["filePath"] as? String 
            ?: throw IllegalArgumentException("Missing filePath")
        val edits = params["edits"] as? List<Map<String, Any?>> 
            ?: throw IllegalArgumentException("Missing edits")
            
        val textEdits = edits.map { edit ->
            TextEdit(
                startOffset = (edit["startOffset"] as Number).toInt(),
                endOffset = (edit["endOffset"] as Number).toInt(),
                newText = edit["newText"] as String
            )
        }
        
        return intelliJAccess.applyEdits(filePath, textEdits)
    }
}

/**
 * Analysis MCP functionality as a TrikeShed node
 */
class AnalysisNode(
    internal val intelliJAccess: IntelliJAccessService
) : TrikeshedNode(
    nodeId = "analysis-node",
    capabilities = setOf("analyze_code", "find_issues", "suggest_fixes")
) {
    
    override suspend fun handleRequest(method: String, params: Map<String, Any?>): Any? {
        updateStatus(NodeStatus.PROCESSING)
        
        return try {
            when (method) {
                "analyze_code" -> analyzeCode(params)
                "find_issues" -> findIssues(params)
                "suggest_fixes" -> suggestFixes(params)
                else -> throw IllegalArgumentException("Unknown method: $method")
            }
        } finally {
            updateStatus(NodeStatus.IDLE)
        }
    }
    
    internal suspend fun analyzeCode(params: Map<String, Any?>): List<InspectionInfo> {
        val filePath = params["filePath"] as? String
        return intelliJAccess.getCodeInspections(filePath)
    }
    
    internal suspend fun findIssues(params: Map<String, Any?>): List<CompilationError> {
        return intelliJAccess.getCompilationErrors()
    }
    
    internal suspend fun suggestFixes(params: Map<String, Any?>): List<Map<String, Any>> {
        // Implement quick fix suggestions
        return emptyList()
    }
}

/**
 * Refactoring MCP functionality as a TrikeShed node
 */
class RefactoringNode(
    internal val intelliJAccess: IntelliJAccessService
) : TrikeshedNode(
    nodeId = "refactoring-node",
    capabilities = setOf("rename", "move", "extract", "inline")
) {
    
    override suspend fun handleRequest(method: String, params: Map<String, Any?>): Any? {
        updateStatus(NodeStatus.PROCESSING)
        
        return try {
            when (method) {
                "rename" -> rename(params)
                "move" -> move(params)
                "extract" -> extractMethod(params)
                "inline" -> inlineMethod(params)
                else -> throw IllegalArgumentException("Unknown method: $method")
            }
        } finally {
            updateStatus(NodeStatus.IDLE)
        }
    }
    
    internal suspend fun rename(params: Map<String, Any?>): RefactorResult {
        val elementFqn = params["elementFqn"] as? String 
            ?: throw IllegalArgumentException("Missing elementFqn")
        val newName = params["newName"] as? String 
            ?: throw IllegalArgumentException("Missing newName")
            
        val element = intelliJAccess.findPsiElement(elementFqn)
            ?: throw IllegalArgumentException("Element not found: $elementFqn")
            
        return intelliJAccess.renameSymbol(element, newName)
    }
    
    internal suspend fun move(params: Map<String, Any?>): RefactorResult {
        val elementFqn = params["elementFqn"] as? String 
            ?: throw IllegalArgumentException("Missing elementFqn")
        val targetPackage = params["targetPackage"] as? String 
            ?: throw IllegalArgumentException("Missing targetPackage")
            
        val element = intelliJAccess.findPsiElement(elementFqn)
            ?: throw IllegalArgumentException("Element not found: $elementFqn")
            
        return intelliJAccess.moveElement(element, targetPackage)
    }
    
    internal suspend fun extractMethod(params: Map<String, Any?>): Map<String, Any> {
        // Implement extract method refactoring
        return mapOf("success" to false, "reason" to "Not implemented")
    }
    
    internal suspend fun inlineMethod(params: Map<String, Any?>): Map<String, Any> {
        // Implement inline method refactoring
        return mapOf("success" to false, "reason" to "Not implemented")
    }
}