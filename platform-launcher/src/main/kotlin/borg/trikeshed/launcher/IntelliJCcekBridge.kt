@file:Suppress("UNCHECKED_CAST")
package borg.trikeshed.launcher

import com.sun.jna.*
import com.sun.jna.ptr.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.util.concurrent.ConcurrentHashMap
import javax.naming.Context
import javax.naming.InitialContext

/**
 * IntelliJ CCEK Bridge
 * 
 * This runs inside IntelliJ's JVM and communicates with the native
 * io_uring MCP host via CCEK messages over shared memory.
 */
class IntelliJCcekBridge(
    private val pluginContext: Any? = null
) : CoroutineScope {
    
    override val coroutineContext = SupervisorJob() + Dispatchers.IO
    
    // Shared memory mapping
    private var shmChannel: FileChannel? = null
    private var shmBuffer: ByteBuffer? = null
    
    // CCEK channels
    private val toNative = Channel<CCEKMessage>(Channel.UNLIMITED)
    private val fromNative = Channel<CCEKMessage>(Channel.UNLIMITED)
    
    // Active MCP servers
    private val mcpServers = ConcurrentHashMap<String, McpServerProxy>()
    
    // JNDI context for service discovery
    private var namingContext: Context? = null
    
    fun initialize() {
        println("🔌 IntelliJ CCEK Bridge Initializing...")
        
        // Map shared memory
        mapSharedMemory()
        
        // Initialize JNDI with io_uring provider
        initializeNaming()
        
        // Start message processors
        launch { processOutgoingMessages() }
        launch { processIncomingMessages() }
        launch { monitorSharedMemory() }
        
        println("✅ IntelliJ CCEK Bridge Ready")
    }
    
    private fun mapSharedMemory() {
        try {
            // Open shared memory file
            val shmPath = Paths.get("/dev/shm/uring_mcp_ccek")
            shmChannel = FileChannel.open(shmPath, 
                StandardOpenOption.READ, 
                StandardOpenOption.WRITE)
            
            // Map to ByteBuffer
            shmBuffer = shmChannel!!.map(
                FileChannel.MapMode.READ_WRITE, 
                0, 
                16 * 1024 * 1024 // 16MB
            )
            
            println("✅ Shared memory mapped: 16MB")
            
        } catch (e: Exception) {
            println("⚠️ Failed to map shared memory: ${e.message}")
            // Fallback to socket communication
            useSocketFallback()
        }
    }
    
    private fun initializeNaming() {
        System.setProperty(Context.INITIAL_CONTEXT_FACTORY,
            "borg.trikeshed.launcher.UringNamingService")
        
        val env = java.util.Hashtable<String, String>()
        env[Context.PROVIDER_URL] = "uring://native-host"
        
        namingContext = InitialContext(env)
        
        // Register IntelliJ services
        namingContext?.bind("intellij/bridge", this)
        namingContext?.bind("intellij/project", pluginContext ?: "default")
    }
    
    private suspend fun processOutgoingMessages() {
        for (msg in toNative) {
            writeToSharedMemory(msg)
        }
    }
    
    private suspend fun processIncomingMessages() {
        for (msg in fromNative) {
            when (msg.type) {
                CCEKMessageType.MCP_SERVER_CREATED -> {
                    val name = msg.payload as String
                    onMcpServerCreated(name)
                }
                
                CCEKMessageType.MCP_RESPONSE -> {
                    val response = msg.payload as McpResponse
                    onMcpResponse(response)
                }
                
                else -> {
                    println("Received: ${msg.type}")
                }
            }
        }
    }
    
    private suspend fun monitorSharedMemory() {
        val ringBuffer = RingBufferView(shmBuffer!!)
        
        while (isActive) {
            // Check for new messages
            while (ringBuffer.hasMessages()) {
                val msg = ringBuffer.readMessage()
                if (msg != null) {
                    fromNative.send(msg)
                }
            }
            
            delay(10) // Poll every 10ms
        }
    }
    
    private fun writeToSharedMemory(msg: CCEKMessage) {
        val ringBuffer = RingBufferView(shmBuffer!!)
        ringBuffer.writeMessage(msg)
        
        // Notify native process (could use eventfd or signal)
        notifyNativeProcess()
    }
    
    private fun notifyNativeProcess() {
        // Send signal or write to eventfd
        // For now, native process polls
    }
    
    /**
     * Create a new MCP server
     */
    suspend fun createMcpServer(
        name: String, 
        port: Int = 0,
        handler: McpRequestHandler
    ): McpServerProxy {
        
        val config = McpServerConfig(
            name = name,
            port = if (port == 0) findAvailablePort() else port
        )
        
        // Request native host to create server
        toNative.send(CCEKMessage(
            type = CCEKMessageType.CREATE_MCP_SERVER,
            payload = config
        ))
        
        // Create proxy
        val proxy = McpServerProxy(name, config.port, handler)
        mcpServers[name] = proxy
        
        // Register in JNDI
        namingContext?.bind("mcp/$name", proxy)
        
        return proxy
    }
    
    /**
     * Destroy an MCP server
     */
    suspend fun destroyMcpServer(name: String) {
        toNative.send(CCEKMessage(
            type = CCEKMessageType.DESTROY_MCP_SERVER,
            payload = name
        ))
        
        mcpServers.remove(name)
        namingContext?.unbind("mcp/$name")
    }
    
    private fun onMcpServerCreated(name: String) {
        val proxy = mcpServers[name]
        proxy?.onCreated()
    }
    
    private fun onMcpResponse(response: McpResponse) {
        // Route response to appropriate handler
        val requestId = response.id
        // TODO: Implement request tracking
    }
    
    private fun findAvailablePort(): Int {
        // Find available port
        return (8000..9000).random()
    }
    
    private fun useSocketFallback() {
        // Implement socket-based communication as fallback
        println("📡 Using socket fallback for CCEK communication")
    }
    
    fun shutdown() {
        cancel()
        shmChannel?.close()
        namingContext?.close()
    }
}

/**
 * MCP Server Proxy
 */
class McpServerProxy(
    val name: String,
    val port: Int,
    private val handler: McpRequestHandler
) {
    private val created = CompletableDeferred<Unit>()
    
    suspend fun awaitCreation() = created.await()
    
    internal fun onCreated() {
        created.complete(Unit)
        println("✅ MCP server '$name' created on port $port")
    }
    
    suspend fun handleRequest(request: McpRequest): McpResponse {
        return handler.handle(request)
    }
}

/**
 * MCP Request Handler
 */
fun interface McpRequestHandler {
    suspend fun handle(request: McpRequest): McpResponse
}

/**
 * Ring Buffer View for shared memory
 */
class RingBufferView(private val buffer: ByteBuffer) {
    
    private val headerSize = 12 // 3 * sizeof(uint32)
    
    fun hasMessages(): Boolean {
        val writeIndex = buffer.getInt(0)
        val readIndex = buffer.getInt(4)
        return writeIndex != readIndex
    }
    
    fun readMessage(): CCEKMessage? {
        val writeIndex = buffer.getInt(0)
        val readIndex = buffer.getInt(4)
        val capacity = buffer.getInt(8)
        
        if (writeIndex == readIndex) return null
        
        // Read message from ring buffer
        val dataStart = headerSize + readIndex
        buffer.position(dataStart)
        
        // Read message size
        val msgSize = buffer.getInt()
        if (msgSize <= 0) return null
        
        // Read message type
        val msgType = CCEKMessageType.values()[buffer.getInt()]
        
        // Read payload
        val payloadBytes = ByteArray(msgSize - 8)
        buffer.get(payloadBytes)
        
        // Update read index
        val newReadIndex = (readIndex + msgSize + 4) % capacity
        buffer.putInt(4, newReadIndex)
        
        // Deserialize payload based on type
        val payload = deserializePayload(msgType, payloadBytes)
        
        return CCEKMessage(msgType, payload)
    }
    
    fun writeMessage(msg: CCEKMessage) {
        val writeIndex = buffer.getInt(0)
        val capacity = buffer.getInt(8)
        
        // Serialize message
        val payloadBytes = serializePayload(msg.payload)
        val msgSize = 8 + payloadBytes.size // type(4) + size(4) + payload
        
        // Write to ring buffer
        val dataStart = headerSize + writeIndex
        buffer.position(dataStart)
        
        buffer.putInt(msgSize)
        buffer.putInt(msg.type.ordinal)
        buffer.put(payloadBytes)
        
        // Update write index
        val newWriteIndex = (writeIndex + msgSize + 4) % capacity
        buffer.putInt(0, newWriteIndex)
    }
    
    private fun serializePayload(payload: Any): ByteArray {
        // Simple serialization - in production use protobuf or similar
        return when (payload) {
            is String -> payload.toByteArray()
            is McpServerConfig -> {
                "${payload.name}|${payload.port}|${payload.maxConnections}".toByteArray()
            }
            is McpRequest -> {
                "${payload.id}|${payload.serverName}|${payload.method}".toByteArray()
            }
            is McpResponse -> {
                "${payload.id}|${payload.result}|${payload.error}".toByteArray()
            }
            else -> ByteArray(0)
        }
    }
    
    private fun deserializePayload(type: CCEKMessageType, bytes: ByteArray): Any {
        val str = String(bytes)
        return when (type) {
            CCEKMessageType.MCP_SERVER_CREATED,
            CCEKMessageType.MCP_SERVER_DESTROYED -> str
            
            CCEKMessageType.CREATE_MCP_SERVER -> {
                val parts = str.split("|")
                McpServerConfig(parts[0], parts[1].toInt(), parts[2].toInt())
            }
            
            CCEKMessageType.MCP_REQUEST -> {
                val parts = str.split("|")
                McpRequest(parts[0], parts[1], parts[2], null)
            }
            
            CCEKMessageType.MCP_RESPONSE -> {
                val parts = str.split("|")
                McpResponse(parts[0], parts.getOrNull(1), parts.getOrNull(2))
            }
            
            else -> str
        }
    }
}

/**
 * IntelliJ Plugin Integration Example
 */
class IntelliJMcpPlugin {
    private lateinit var bridge: IntelliJCcekBridge
    
    fun initialize(project: Any) = runBlocking {
        bridge = IntelliJCcekBridge(project)
        bridge.initialize()
        
        // Create MCP servers for IntelliJ features
        createIntelliJMcpServers()
    }
    
    private suspend fun createIntelliJMcpServers() {
        // Code completion MCP server
        bridge.createMcpServer("code-completion", 8001) { request ->
            McpResponse(
                id = request.id,
                result = "Completion results from IntelliJ",
                error = null
            )
        }
        
        // Refactoring MCP server
        bridge.createMcpServer("refactoring", 8002) { request ->
            McpResponse(
                id = request.id,
                result = "Refactoring suggestions",
                error = null
            )
        }
        
        // Debugging MCP server
        bridge.createMcpServer("debugging", 8003) { request ->
            McpResponse(
                id = request.id,
                result = "Debug information",
                error = null
            )
        }
        
        println("✅ IntelliJ MCP servers created")
    }
    
    fun shutdown() {
        bridge.shutdown()
    }
}

// Shared types (duplicated from native for JVM)
enum class CCEKMessageType {
    CREATE_MCP_SERVER,
    DESTROY_MCP_SERVER,
    MCP_SERVER_CREATED,
    MCP_SERVER_DESTROYED,
    MCP_REQUEST,
    MCP_RESPONSE,
    SHUTDOWN
}

data class CCEKMessage(
    val type: CCEKMessageType,
    val payload: Any
)

data class McpServerConfig(
    val name: String,
    val port: Int,
    val maxConnections: Int = 100
)

data class McpRequest(
    val id: String,
    val serverName: String,
    val method: String,
    val params: Any?
)

data class McpResponse(
    val id: String,
    val result: Any?,
    val error: String?
)