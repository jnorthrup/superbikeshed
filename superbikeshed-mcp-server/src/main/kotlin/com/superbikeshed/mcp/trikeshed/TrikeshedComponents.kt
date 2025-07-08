package com.superbikeshed.mcp.trikeshed

import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import mu.KotlinLogging
import com.v2superbikeshed.nexus.rpc.McpError
import com.v2superbikeshed.nexus.rpc.McpRequest
import com.v2superbikeshed.nexus.rpc.McpResponse
import com.v2superbikeshed.nexus.rpc.McpServerInterface
import com.v2superbikeshed.nexus.rpc.TrikeshedMcpBridge

internal val logger = KotlinLogging.logger {}

// Common interfaces
interface TrikeshedComponent {
    fun getName(): String
    fun getVersion(): String
}

interface Lifecycle {
    fun start()
    fun stop()
    fun isRunning(): Boolean
}

// Service Registry
interface ServiceRegistry : TrikeshedComponent, Lifecycle {
    fun register(name: String, service: Any)
    fun get(name: String): Any?
    fun unregister(name: String)
    fun listServices(): List<String>
}

class TrikeshedServiceRegistry : ServiceRegistry {
    internal val services = mutableMapOf<String, Any>()
    internal var running = false
    
    override fun getName(): String = "TrikeshedServiceRegistry"
    override fun getVersion(): String = "1.0.0"
    
    override fun start() {
        running = true
    }
    
    override fun stop() {
        running = false
        services.clear()
    }
    
    override fun isRunning(): Boolean = running
    
    override fun register(name: String, service: Any) {
        services[name] = service
    }
    
    override fun get(name: String): Any? {
        return services[name]
    }
    
    override fun unregister(name: String) {
        services.remove(name)
    }
    
    override fun listServices(): List<String> {
        return services.keys.toList()
    }
}

// Reactor
interface Reactor : TrikeshedComponent, Lifecycle {
    fun submit(task: suspend () -> Unit)
    fun shutdown()
}

class TrikeshedReactor : Reactor {
    internal var running = false
    internal val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    override fun getName(): String = "TrikeshedReactor"
    override fun getVersion(): String = "1.0.0"
    
    override fun start() {
        running = true
    }
    
    override fun stop() {
        running = false
        shutdown()
    }
    
    override fun isRunning(): Boolean = running
    
    override fun submit(task: suspend () -> Unit) {
        scope.launch {
            task()
        }
    }
    
    override fun shutdown() {
        scope.cancel()
    }
}

// Trikeshed MCP Server
class TrikeshedMcpServer(
    override val name: String,
    override val version: String,
    override val capabilities: Set<String>,
    internal val serviceRegistry: ServiceRegistry,
    internal val reactor: Reactor
) : McpServerInterface, Lifecycle {
    
    internal var running = false
    
    override fun start() {
        serviceRegistry.start()
        reactor.start()
        running = true
        logger.info { "Trikeshed MCP Server '$name' started" }
    }
    
    override fun stop() {
        running = false
        serviceRegistry.stop()
        reactor.shutdown()
        logger.info { "Trikeshed MCP Server '$name' stopped" }
    }
    
    override fun isRunning(): Boolean = running
    
    override suspend fun handleRequest(request: McpRequest): McpResponse {
        return try {
            // Extract the actual method name from the request (e.g., "register", "invoke", "list")
            val method = request.method
            val params = request.params
            
            when (method) {
                "register" -> {
                    val serviceName = params?.jsonObject?.get("name")?.jsonPrimitive?.content
                    val serviceVersion = params?.jsonObject?.get("version")?.jsonPrimitive?.content
                    if (serviceName != null && serviceVersion != null) {
                        serviceRegistry.register(serviceName, object : TrikeshedComponent {
                            override fun getName(): String = serviceName
                            override fun getVersion(): String = serviceVersion
                        })
                        McpResponse(
                            jsonrpc = "2.0",
                            id = request.id,
                            result = JsonPrimitive("Service $serviceName registered")
                        )
                    } else {
                        McpResponse(
                            jsonrpc = "2.0",
                            id = request.id,
                            error = McpError(-32602, "Invalid registration request: missing name or version")
                        )
                    }
                }
                "invoke" -> {
                    val serviceName = params?.jsonObject?.get("service")?.jsonPrimitive?.content
                    val methodName = params?.jsonObject?.get("method")?.jsonPrimitive?.content
                    val invokeParams = params?.jsonObject?.get("params")
                    
                    if (serviceName != null && methodName != null) {
                        val service = serviceRegistry.get(serviceName)
                        if (service != null) {
                            // In a real scenario, use reflection to invoke the method
                            McpResponse(
                                jsonrpc = "2.0",
                                id = request.id,
                                result = JsonPrimitive("Invoked $methodName on $serviceName with params $invokeParams")
                            )
                        } else {
                            McpResponse(
                                jsonrpc = "2.0",
                                id = request.id,
                                error = McpError(-32601, "Service $serviceName not found")
                            )
                        }
                    } else {
                        McpResponse(
                            jsonrpc = "2.0",
                            id = request.id,
                            error = McpError(-32602, "Invalid invoke request: missing service or method")
                        )
                    }
                }
                "list" -> {
                    val services = serviceRegistry.listServices()
                    McpResponse(
                        jsonrpc = "2.0",
                        id = request.id,
                        result = JsonArray(services.map { JsonPrimitive(it) })
                    )
                }
                else -> {
                    McpResponse(
                        jsonrpc = "2.0",
                        id = request.id,
                        error = McpError(-32601, "Unknown method: ${request.method}")
                    )
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error handling MCP request: ${e.message}" }
            McpResponse(
                jsonrpc = "2.0",
                id = request.id,
                error = McpError(-32603, "Internal server error: ${e.message}", JsonPrimitive(e.stackTraceToString()))
            )
        }
    }
}

