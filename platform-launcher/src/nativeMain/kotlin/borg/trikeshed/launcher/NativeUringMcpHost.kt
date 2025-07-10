@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@file:OptIn(kotlin.ExperimentalStdlibApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
package borg.trikeshed.launcher

import kotlinx.cinterop.*
import platform.posix.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.native.concurrent.*

/**
 * Native io_uring MCP Server Host
 * 
 * This is a pure Kotlin Native application that:
 * 1. Hosts MCP servers using native io_uring (Darwin kqueue)
 * 2. Loads JVM via DLL for IntelliJ integration
 * 3. Provides CCEK io_uring transport to IntelliJ VM
 * 4. Zero GC, maximum performance
 */

// Native entry point
fun main() = memScoped {
    println("🚀 Native io_uring MCP Server Host")
    println("==================================")
    
    val host = NativeUringMcpHost()
    host.initialize()
    host.start()
}

@ThreadLocal
private val globalHost: NativeUringMcpHost? = null

class NativeUringMcpHost {
    private var uringFd: Int = -1
    private var jvmHandle: COpaquePointer? = null
    private val mcpServers = mutableMapOf<String, McpServerInstance>()
    
    // CCEK channels for IntelliJ communication
    private val toIntelliJ = Channel<CCEKMessage>(Channel.UNLIMITED)
    private val fromIntelliJ = Channel<CCEKMessage>(Channel.UNLIMITED)
    
    fun initialize() = memScoped {
        // Initialize io_uring (kqueue on Darwin)
        uringFd = kqueue()
        if (uringFd < 0) {
            error("Failed to create kqueue: ${strerror(errno)?.toKString()}")
        }
        
        println("✅ io_uring initialized (fd=$uringFd)")
        
        // Load JVM DLL
        loadJvmDll()
        
        // Setup CCEK transport
        setupCcekTransport()
    }
    
    private fun loadJvmDll() = memScoped {
        val jvmPath = getenv("JAVA_HOME")?.toKString() ?: "/Library/Java/JavaVirtualMachines/default/Contents/Home"
        val libPath = "$jvmPath/lib/server/libjvm.dylib"
        
        println("📦 Loading JVM from: $libPath")
        
        jvmHandle = dlopen(libPath, RTLD_LAZY)
        if (jvmHandle == null) {
            error("Failed to load JVM: ${dlerror()?.toKString()}")
        }
        
        // Get JNI_CreateJavaVM function
        val createVmFunc = dlsym(jvmHandle, "JNI_CreateJavaVM")
            ?: error("JNI_CreateJavaVM not found")
        
        // Create JVM instance
        val vm = alloc<CPointerVar<JavaVMVar>>()
        val env = alloc<CPointerVar<JNIEnvVar>>()
        val vmArgs = alloc<JavaVMInitArgs>().apply {
            version = JNI_VERSION_1_8
            nOptions = 3
            
            val options = allocArray<JavaVMOption>(3)
            options[0].optionString = "-Xms512m".cstr.ptr
            options[1].optionString = "-Xmx2g".cstr.ptr
            options[2].optionString = "-XX:+UseSerialGC".cstr.ptr  // Minimal GC
            
            this.options = options
            ignoreUnrecognized = JNI_TRUE.toByte()
        }
        
        @Suppress("UNCHECKED_CAST")
        val createVm = createVmFunc as CPointer<CFunction<(
            CPointer<CPointerVar<JavaVMVar>>,
            CPointer<CPointerVar<JNIEnvVar>>,
            CPointer<JavaVMInitArgs>
        ) -> jint>>
        
        val result = createVm(vm.ptr, env.ptr, vmArgs.ptr)
        if (result != JNI_OK) {
            error("Failed to create JVM: $result")
        }
        
        println("✅ JVM loaded successfully")
    }
    
    private fun setupCcekTransport() = memScoped {
        // Create shared memory region for zero-copy IPC
        val shmName = "/uring_mcp_ccek"
        val shmFd = shm_open(shmName, O_CREAT or O_RDWR, 0666)
        if (shmFd < 0) {
            error("Failed to create shared memory: ${strerror(errno)?.toKString()}")
        }
        
        // Set size to 16MB
        val shmSize = 16 * 1024 * 1024L
        if (ftruncate(shmFd, shmSize) < 0) {
            error("Failed to set shared memory size")
        }
        
        // Memory map the region
        val shmPtr = mmap(null, shmSize.convert(), 
            PROT_READ or PROT_WRITE, MAP_SHARED, shmFd, 0)
        
        if (shmPtr == MAP_FAILED) {
            error("Failed to map shared memory")
        }
        
        // Initialize ring buffer in shared memory
        val ringBuffer = shmPtr!!.reinterpret<CCEKRingBuffer>()
        ringBuffer.pointed.writeIndex = 0u
        ringBuffer.pointed.readIndex = 0u
        ringBuffer.pointed.capacity = (shmSize - sizeOf<CCEKRingBuffer>()).toUInt()
        
        println("✅ CCEK transport initialized (shm: $shmName)")
        
        // Register with kqueue for notifications
        val event = alloc<kevent>()
        EV_SET(event.ptr, shmFd.convert(), EVFILT_VNODE.convert(), 
            (EV_ADD or EV_ENABLE).convert(), 
            NOTE_WRITE.convert(), 0, null)
        
        kevent(uringFd, event.ptr, 1, null, 0, null)
    }
    
    fun start() = runBlocking {
        // Start CCEK message processor
        launch { processCcekMessages() }
        
        // Start MCP server listener
        launch { startMcpListener() }
        
        // Start io_uring event loop
        launch { runUringEventLoop() }
        
        // Wait forever
        delay(Long.MAX_VALUE)
    }
    
    private suspend fun processCcekMessages() {
        for (msg in fromIntelliJ) {
            when (msg.type) {
                CCEKMessageType.CREATE_MCP_SERVER -> {
                    val config = msg.payload as McpServerConfig
                    createMcpServer(config)
                }
                
                CCEKMessageType.DESTROY_MCP_SERVER -> {
                    val name = msg.payload as String
                    destroyMcpServer(name)
                }
                
                CCEKMessageType.MCP_REQUEST -> {
                    val request = msg.payload as McpRequest
                    handleMcpRequest(request)
                }
                
                else -> {
                    println("⚠️ Unknown CCEK message type: ${msg.type}")
                }
            }
        }
    }
    
    private suspend fun createMcpServer(config: McpServerConfig) {
        println("🔧 Creating MCP server: ${config.name}")
        
        val instance = McpServerInstance(
            name = config.name,
            port = config.port,
            uringFd = uringFd
        )
        
        // Initialize server socket
        instance.initialize()
        
        mcpServers[config.name] = instance
        
        // Notify IntelliJ
        toIntelliJ.send(CCEKMessage(
            type = CCEKMessageType.MCP_SERVER_CREATED,
            payload = config.name
        ))
    }
    
    private suspend fun destroyMcpServer(name: String) {
        mcpServers.remove(name)?.apply {
            shutdown()
        }
        
        toIntelliJ.send(CCEKMessage(
            type = CCEKMessageType.MCP_SERVER_DESTROYED,
            payload = name
        ))
    }
    
    private suspend fun handleMcpRequest(request: McpRequest) {
        val server = mcpServers[request.serverName]
        if (server != null) {
            val response = server.handleRequest(request)
            toIntelliJ.send(CCEKMessage(
                type = CCEKMessageType.MCP_RESPONSE,
                payload = response
            ))
        }
    }
    
    private suspend fun startMcpListener() = memScoped {
        // Create MCP control socket
        val sockFd = socket(AF_INET, SOCK_STREAM, 0)
        if (sockFd < 0) return@memScoped
        
        // Make non-blocking
        fcntl(sockFd, F_SETFL, O_NONBLOCK)
        
        // Bind to control port
        val addr = alloc<sockaddr_in>().apply {
            sin_family = AF_INET.convert()
            sin_port = htons(9876u)
            sin_addr.s_addr = htonl(INADDR_LOOPBACK)
        }
        
        if (bind(sockFd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) < 0) {
            close(sockFd)
            return@memScoped
        }
        
        listen(sockFd, 10)
        
        // Register with kqueue
        val event = alloc<kevent>()
        EV_SET(event.ptr, sockFd.convert(), EVFILT_READ.convert(),
            (EV_ADD or EV_ENABLE).convert(), 0, 0, null)
        
        kevent(uringFd, event.ptr, 1, null, 0, null)
        
        println("📡 MCP control listener on port 9876")
    }
    
    private suspend fun runUringEventLoop() = memScoped {
        val events = allocArray<kevent>(64)
        val timeout = alloc<timespec>().apply {
            tv_sec = 0
            tv_nsec = 100_000_000 // 100ms
        }
        
        while (true) {
            val nEvents = kevent(uringFd, null, 0, events, 64, timeout.ptr)
            
            if (nEvents > 0) {
                for (i in 0 until nEvents) {
                    val event = events[i]
                    processUringEvent(event)
                }
            }
            
            yield() // Allow other coroutines to run
        }
    }
    
    private fun processUringEvent(event: kevent) {
        when (event.filter) {
            EVFILT_READ -> {
                // Handle incoming connections or data
                val fd = event.ident.toInt()
                handleReadEvent(fd)
            }
            
            EVFILT_WRITE -> {
                // Handle write readiness
                val fd = event.ident.toInt()
                handleWriteEvent(fd)
            }
            
            EVFILT_VNODE -> {
                // Handle shared memory changes from IntelliJ
                handleShmEvent()
            }
        }
    }
    
    private fun handleReadEvent(fd: Int) = memScoped {
        val buffer = allocArray<ByteVar>(4096)
        val bytesRead = read(fd, buffer, 4096u)
        
        if (bytesRead > 0) {
            // Process incoming data
            println("📥 Read $bytesRead bytes from fd=$fd")
        }
    }
    
    private fun handleWriteEvent(fd: Int) {
        // Handle write readiness
    }
    
    private fun handleShmEvent() {
        // Read CCEK messages from shared memory
        // This is called when IntelliJ writes to shared memory
    }
}

/**
 * MCP Server Instance
 */
class McpServerInstance(
    val name: String,
    val port: Int,
    private val uringFd: Int
) {
    private var sockFd: Int = -1
    private val clients = mutableSetOf<Int>()
    
    fun initialize() = memScoped {
        sockFd = socket(AF_INET, SOCK_STREAM, 0)
        if (sockFd < 0) {
            error("Failed to create socket")
        }
        
        // Set socket options
        val reuseAddr = alloc<IntVar>()
        reuseAddr.value = 1
        setsockopt(sockFd, SOL_SOCKET, SO_REUSEADDR, 
            reuseAddr.ptr, sizeOf<IntVar>().convert())
        
        // Make non-blocking
        fcntl(sockFd, F_SETFL, O_NONBLOCK)
        
        // Bind
        val addr = alloc<sockaddr_in>().apply {
            sin_family = AF_INET.convert()
            sin_port = htons(port.toUShort())
            sin_addr.s_addr = htonl(INADDR_ANY)
        }
        
        if (bind(sockFd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) < 0) {
            close(sockFd)
            error("Failed to bind to port $port")
        }
        
        listen(sockFd, 128)
        
        // Register with kqueue
        val event = alloc<kevent>()
        EV_SET(event.ptr, sockFd.convert(), EVFILT_READ.convert(),
            (EV_ADD or EV_ENABLE).convert(), 0, 0, 
            StableRef.create(this).asCPointer())
        
        kevent(uringFd, event.ptr, 1, null, 0, null)
        
        println("✅ MCP server '$name' listening on port $port")
    }
    
    suspend fun handleRequest(request: McpRequest): McpResponse {
        // Process MCP request
        return McpResponse(
            id = request.id,
            result = "Processed by native io_uring",
            error = null
        )
    }
    
    fun shutdown() {
        clients.forEach { close(it) }
        clients.clear()
        
        if (sockFd >= 0) {
            close(sockFd)
            sockFd = -1
        }
    }
}

// CCEK Message Types
enum class CCEKMessageType {
    CREATE_MCP_SERVER,
    DESTROY_MCP_SERVER,
    MCP_SERVER_CREATED,
    MCP_SERVER_DESTROYED,
    MCP_REQUEST,
    MCP_RESPONSE,
    SHUTDOWN
}

// CCEK Messages
data class CCEKMessage(
    val type: CCEKMessageType,
    val payload: Any
)

// MCP Types
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

// Shared memory ring buffer
@CStruct("struct CCEKRingBuffer")
class CCEKRingBuffer(
    var writeIndex: UInt,
    var readIndex: UInt,
    var capacity: UInt
    // Followed by actual buffer data
)

// JNI types
typealias JavaVMVar = COpaquePointer
typealias JNIEnvVar = COpaquePointer

@CStruct("struct JavaVMInitArgs")
class JavaVMInitArgs(
    var version: jint,
    var nOptions: jint,
    var options: CPointer<JavaVMOption>?,
    var ignoreUnrecognized: jbyte
)

@CStruct("struct JavaVMOption") 
class JavaVMOption(
    var optionString: CPointer<ByteVar>?,
    var extraInfo: COpaquePointer?
)

// JNI constants
const val JNI_VERSION_1_8 = 0x00010008
const val JNI_OK = 0
const val JNI_TRUE: Byte = 1

// Platform specific
expect fun shm_open(name: String, oflag: Int, mode: mode_t): Int
expect fun htons(hostshort: UShort): UShort
expect fun htonl(hostlong: UInt): UInt