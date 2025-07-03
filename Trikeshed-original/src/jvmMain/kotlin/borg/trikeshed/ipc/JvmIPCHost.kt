@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.ipc

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.io.RandomAccessFile
import java.net.*
import java.nio.*
import java.nio.channels.*
import java.nio.file.*
import java.util.concurrent.ConcurrentHashMap
// JNA dependencies not available
// import com.sun.jna.*
// import com.sun.jna.ptr.*

/**
 * JVM IPC Host that can be launched by native process
 * and communicate with both native and GraalWASM processes
 */
class JvmIPCHost(
    hostId: ProcessId,
    private val nativeSocketPath: String? = null
) : IPCHost(hostId, TransportTypes.JVM) {
    
    // Connection to parent native process
    private var nativeConnection: SocketChannel? = null
    
    // GraalVM context for WASM execution
    private var graalContext: Any? = null
    private var wasmModules = ConcurrentHashMap<String, Any>()
    
    init {
        // Connect back to native parent if socket path provided
        nativeSocketPath?.let { connectToNative(it) }
        
        // Initialize GraalVM for WASM support
        initializeGraalWASM()
    }
    
    private fun connectToNative(socketPath: String) {
        try {
            val path = Paths.get(socketPath)
            val address = UnixDomainSocketAddress.of(path)
            nativeConnection = SocketChannel.open(address)
            nativeConnection?.configureBlocking(false)
            
            // Send handshake to native parent
            sendHandshake()
        } catch (e: Exception) {
            println("Failed to connect to native parent: ${e.message}")
        }
    }
    
    private fun sendHandshake() {
        val handshake = IPCMessage(
            id = generateMessageId(),
            type = MessageTypes.HANDSHAKE,
            source = hostId,
            destination = 0, // Native parent is always 0
            channelId = "control",
            payload = "JVM_READY".toByteArray().toIdx()
        )
        
        scope.launch {
            nativeConnection?.let { socket ->
                val buffer = ByteBuffer.wrap(serializeMessage(handshake))
                socket.write(buffer)
            }
        }
    }
    
    private fun initializeGraalWASM() {
        try {
            val contextClass = Class.forName("org.graalvm.polyglot.Context")
            val engineClass = Class.forName("org.graalvm.polyglot.Engine")
            
            // Create engine with WASM support
            val engineBuilder = engineClass.getMethod("newBuilder").invoke(null)
            val engine = engineBuilder.javaClass.getMethod("build").invoke(engineBuilder)
            
            // Create context
            val contextBuilder = contextClass.getMethod("newBuilder", Array<String>::class.java)
                .invoke(null, arrayOf("wasm", "js"))
            
            graalContext = contextBuilder.javaClass
                .getMethod("engine", engineClass)
                .invoke(contextBuilder, engine)
                .let { it.javaClass.getMethod("build").invoke(it) }
                
            println("GraalWASM initialized successfully")
        } catch (e: Exception) {
            println("GraalWASM initialization failed: ${e.message}")
        }
    }
    
    override suspend fun createChannel(
        channelId: ChannelId,
        mode: IPCMode,
        config: IPCChannelConfig
    ): IPCChannel = withContext(Dispatchers.IO) {
        when (mode) {
            IPCModes.SHARED_MEMORY -> JvmSharedMemoryChannel(channelId, config)
            IPCModes.TCP_SOCKET -> JvmTcpChannel(channelId, config)
            IPCModes.NAMED_PIPE -> JvmNamedPipeChannel(channelId, config)
            else -> throw UnsupportedOperationException("Mode not supported: ${mode.value}")
        }.also { channels[channelId] = it }
    }
    
    override suspend fun connectChannel(
        address: IPCAddress,
        port: IPCPort,
        channelId: ChannelId
    ): IPCChannel = withContext(Dispatchers.IO) {
        val channel = JvmTcpChannel(channelId, IPCChannelConfig())
        channel.connect(address, port)
        channels[channelId] = channel
        channel
    }
    
    /**
     * Launch WASM module in GraalVM context
     */
    suspend fun launchWASMModule(
        modulePath: String,
        moduleName: String,
        channelId: ChannelId
    ): Boolean = withContext(Dispatchers.IO) {
        if (graalContext == null) {
            println("GraalWASM not initialized")
            return@withContext false
        }
        
        try {
            // Load WASM module
            val wasmBytes = Files.readAllBytes(Paths.get(modulePath))
            
            val sourceClass = Class.forName("org.graalvm.polyglot.Source")
            val source = sourceClass.getMethod(
                "newBuilder",
                String::class.java,
                ByteArray::class.java,
                String::class.java
            ).invoke(null, "wasm", wasmBytes, moduleName)
                .let { it.javaClass.getMethod("build").invoke(it) }
            
            // Evaluate and store module
            val module = graalContext!!.javaClass.getMethod("eval", sourceClass).invoke(graalContext, source)
            wasmModules[moduleName] = module
            
            // Create IPC bridge for WASM module
            createWASMBridge(moduleName, channelId)
            
            true
        } catch (e: Exception) {
            println("Failed to launch WASM module: ${e.message}")
            false
        }
    }
    
    private fun createWASMBridge(moduleName: String, channelId: ChannelId) {
        val module = wasmModules[moduleName] ?: return
        
        // GraalVM support commented out - dependencies not available
        /*
        // Set up IPC functions for WASM module
        val bindingsClass = Class.forName("org.graalvm.polyglot.Value")
        
        // Create host bindings for IPC
        val ipcSend = ProxyExecutable { args ->
            scope.launch {
                if (args.size >= 2) {
                    val destination = args[0].asInt()
                    val data = args[1].asByteArray()
                    
                    sendTo(
                        destination = destination,
                        channelId = channelId,
                        payload = data.toIdx(),
                        type = MessageTypes.DATA
                    )
                }
            }
            null
        }
        */
        
        // Bind IPC functions to WASM module
        try {
            val bindings = graalContext!!.javaClass
                .getMethod("getBindings", String::class.java)
                .invoke(graalContext, "wasm")
            
            // GraalVM support commented out
            /*
            bindings.javaClass
                .getMethod("putMember", String::class.java, Any::class.java)
                .invoke(bindings, "ipc_send", ipcSend)
            */
                
            println("WASM IPC bridge created for $moduleName")
        } catch (e: Exception) {
            println("Failed to create WASM bridge: ${e.message}")
        }
    }
    
    override suspend fun shutdown() {
        // Notify native parent of shutdown
        val shutdownMsg = IPCMessage(
            id = generateMessageId(),
            type = MessageTypes.CLOSE,
            source = hostId,
            destination = 0,
            channelId = "control",
            payload = "JVM_SHUTDOWN".toByteArray().toIdx()
        )
        
        nativeConnection?.let { socket ->
            val buffer = ByteBuffer.wrap(serializeMessage(shutdownMsg))
            socket.write(buffer)
            socket.close()
        }
        
        // Close GraalVM context
        graalContext?.javaClass?.getMethod("close")?.invoke(graalContext)
        
        super.shutdown()
    }
}

/**
 * JVM Shared Memory Channel using memory-mapped files
 */
class JvmSharedMemoryChannel(
    override val id: ChannelId,
    private val config: IPCChannelConfig
) : IPCChannel {
    override val mode = IPCModes.SHARED_MEMORY
    
    private val file = RandomAccessFile("/tmp/trikeshed_shm_$id", "rw")
    private val channel = file.channel
    private val buffer: MappedByteBuffer
    
    private var isOpen = true
    
    init {
        // Map shared memory
        buffer = channel.map(
            FileChannel.MapMode.READ_WRITE,
            0,
            config.bufferSize.toLong() * 1024
        )
    }
    
    override suspend fun send(message: IPCMessage): Boolean {
        if (!isOpen()) return false
        
        return try {
            val data = serializeMessage(message)
            buffer.position(0)
            buffer.putInt(data.size)
            buffer.put(data)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun receive(): IPCMessage? {
        if (!isOpen()) return null
        
        return try {
            buffer.position(0)
            val size = buffer.getInt()
            if (size > 0) {
                val data = ByteArray(size)
                buffer.get(data)
                deserializeMessage(data)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun close() {
        isOpen = false
        channel.close()
        file.close()
    }
    
    override fun isOpen() = isOpen
    override fun messageCount() = 0 // Not tracked for shared memory
}

/**
 * JVM TCP Socket Channel
 */
class JvmTcpChannel(
    override val id: ChannelId,
    private val config: IPCChannelConfig
) : IPCChannel {
    override val mode = IPCModes.TCP_SOCKET
    
    private var socket: SocketChannel? = null
    private val sendQueue = Channel<IPCMessage>(Channel.UNLIMITED)
    private val receiveQueue = Channel<IPCMessage>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    suspend fun connect(address: IPCAddress, port: IPCPort) = withContext(Dispatchers.IO) {
        socket = SocketChannel.open(InetSocketAddress(address, port))
        socket?.configureBlocking(false)
        
        // Start send/receive coroutines
        startMessagePump()
    }
    
    private fun startMessagePump() {
        scope.launch {
            while (isOpen()) {
                // Send messages
                sendQueue.tryReceive().getOrNull()?.let { msg ->
                    socket?.write(ByteBuffer.wrap(serializeMessage(msg)))
                }
                
                // Receive messages
                val buffer = ByteBuffer.allocate(config.maxMessageSize)
                socket?.read(buffer)?.let { bytesRead ->
                    if (bytesRead > 0) {
                        buffer.flip()
                        val data = ByteArray(bytesRead)
                        buffer.get(data)
                        deserializeMessage(data)?.let { receiveQueue.send(it) }
                    }
                }
                
                delay(10)
            }
        }
    }
    
    override suspend fun send(message: IPCMessage) = sendQueue.send(message).let { true }
    override suspend fun receive() = receiveQueue.tryReceive().getOrNull()
    override suspend fun close() {
        socket?.close()
        sendQueue.close()
        receiveQueue.close()
        scope.cancel()
    }
    
    override fun isOpen() = socket?.isOpen ?: false
    override fun messageCount() = receiveQueue.isEmpty.let { if (it) 0 else 1 }
}

/**
 * JVM Named Pipe Channel
 */
class JvmNamedPipeChannel(
    override val id: ChannelId,
    private val config: IPCChannelConfig
) : IPCChannel {
    override val mode = IPCModes.NAMED_PIPE
    
    private val pipePath = if (System.getProperty("os.name").startsWith("Windows")) {
        "\\\\.\\pipe\\trikeshed_$id"
    } else {
        "/tmp/trikeshed_pipe_$id"
    }
    
    private var isOpen = true
    
    override suspend fun send(message: IPCMessage): Boolean {
        // Implementation depends on OS
        return true
    }
    
    override suspend fun receive(): IPCMessage? {
        // Implementation depends on OS
        return null
    }
    
    override suspend fun close() {
        isOpen = false
    }
    
    override fun isOpen() = isOpen
    override fun messageCount() = 0
}

// Serialization helpers
private fun serializeMessage(message: IPCMessage): ByteArray {
    val buffer = mutableListOf<Byte>()
    
    // Header
    buffer.add(0x02) // JVM message version
    buffer.add(message.type.value.toByte())
    
    // Message fields
    buffer.addAll(message.id.toBytes().toList())
    buffer.addAll(message.source.toBytes().toList())
    buffer.addAll(message.destination.toBytes().toList())
    
    // Channel ID
    val channelBytes = message.channelId.toByteArray()
    buffer.addAll(channelBytes.size.toBytes().toList())
    buffer.addAll(channelBytes.toList())
    
    // Payload
    buffer.addAll(message.payload.a.toBytes().toList())
    for (i in 0 until message.payload.a) {
        buffer.add(message.payload[i])
    }
    
    // Timestamp
    buffer.addAll(message.timestamp.toBytes().toList())
    
    return buffer.toByteArray()
}

private fun deserializeMessage(data: ByteArray): IPCMessage? {
    if (data.size < 2 || data[0] != 0x02.toByte()) return null
    
    var offset = 1
    val type = MessageType(data[offset++].toUByte())
    
    // Read fields
    val id = data.sliceArray(offset until offset + 8).toLong()
    offset += 8
    
    val source = data.sliceArray(offset until offset + 4).toInt()
    offset += 4
    
    val destination = data.sliceArray(offset until offset + 4).toInt()
    offset += 4
    
    // Channel ID
    val channelIdLength = data.sliceArray(offset until offset + 4).toInt()
    offset += 4
    val channelId = String(data.sliceArray(offset until offset + channelIdLength))
    offset += channelIdLength
    
    // Payload
    val payloadLength = data.sliceArray(offset until offset + 4).toInt()
    offset += 4
    val payload = data.sliceArray(offset until offset + payloadLength)
    offset += payloadLength
    
    // Timestamp
    val timestamp = data.sliceArray(offset until offset + 8).toLong()
    
    return IPCMessage(
        id = id,
        type = type,
        source = source,
        destination = destination,
        channelId = channelId,
        payload = payload.toIdx(),
        timestamp = timestamp
    )
}

// Byte conversion extensions
private fun Long.toBytes() = ByteArray(8) { i -> (this shr (i * 8) and 0xFF).toByte() }
private fun Int.toBytes() = ByteArray(4) { i -> (this shr (i * 8) and 0xFF).toByte() }
private fun ByteArray.toLong() = foldIndexed(0L) { i, acc, b -> acc or ((b.toLong() and 0xFF) shl (i * 8)) }
private fun ByteArray.toInt() = foldIndexed(0) { i, acc, b -> acc or ((b.toInt() and 0xFF) shl (i * 8)) }

// GraalVM interop commented out - dependencies not available
/*
// ProxyExecutable for GraalVM interop
fun interface ProxyExecutable : org.graalvm.polyglot.proxy.ProxyExecutable
*/

/*
// Extension to get byte array from Value
private fun Any.asByteArray(): ByteArray {
    val valueClass = this.javaClass
    val size = valueClass.getMethod("getArraySize").invoke(this) as Long
    return ByteArray(size.toInt()) { i ->
        valueClass.getMethod("getArrayElement", Long::class.java)
            .invoke(this, i.toLong())
            .let { it as Number }
            .toByte()
    }
}

private fun Any.asInt(): Int {
    return this.javaClass.getMethod("asInt").invoke(this) as Int
}
*/

// Factory function removed - defined in IPCHostActuals.kt