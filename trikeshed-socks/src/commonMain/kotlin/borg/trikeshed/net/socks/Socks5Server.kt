@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.socks

import borg.trikeshed.lib.*
import kotlin.coroutines.CoroutineContext
import borg.trikeshed.io.IOContext
import borg.trikeshed.ccek.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.selects.select
import kotlin.jvm.JvmInline

// Import protocol constants from Socks5Protocol
import borg.trikeshed.net.socks.Socks5Protocol.*

/**
 * SOCKS5 CCEK (CoroutineContextElementKey) for channel operations
 */
object Socks5ChannelKey : CoroutineContext.Key<Socks5ChannelElement>

/**
 * SOCKS5 channel element that can be chained in coroutine context
 */
data class Socks5ChannelElement(
    val channel: AsyncChannel,
    val batch: BatchOperation? = null
) : CoroutineContext.Element {
    override val key = Socks5ChannelKey
}

/**
 * Async channel interface with io_uring-first design
 */
interface AsyncChannel {
    val fd: Int

    // Batch operations for io_uring efficiency
    suspend fun readBatch(buffers: Indexed<ByteArray>): Indexed<Int>
    suspend fun writeBatch(buffers: Indexed<ByteArray>): Indexed<Int>
    
    // Single operations (implemented via batch internally)
    suspend fun read(buffer: ByteArray): Int = 
        readBatch(1 j { buffer }).component2()(0)
    
    suspend fun write(buffer: ByteArray): Int = 
        writeBatch(1 j { buffer }).component2()(0)
    
    // Channel info
    val localAddress: String
    val remoteAddress: String
    val isOpen: Boolean
    
    // Close channel
    fun close()

    // For io_uring operations
    suspend fun submitAndWait(sqeOps: Indexed<SqeOp>): Indexed<Int>
    val completions: Channel<Any>
}

/**
 * Batch operation for io_uring submission
 */
data class BatchOperation(
    val ops: Indexed<SqeOp>,
    val completions: Channel<CqeResult>
)

/**
 * io_uring submission queue entry operation
 */
sealed class SqeOp {
    data class Read(val fd: Int, val buffer: ByteArray, val offset: Long) : SqeOp()
    data class Write(val fd: Int, val buffer: ByteArray, val offset: Long) : SqeOp()
    data class Accept(val fd: Int) : SqeOp()
    data class Connect(val fd: Int, val address: String, val port: Int) : SqeOp()
    data class Close(val fd: Int) : SqeOp()
}

/**
 * io_uring completion queue entry result
 */
data class CqeResult(
    val op: SqeOp,
    val result: Int,
    val flags: Int = 0
)

/**
 * SOCKS5 Server implementation with CCEK-based channel handling
 * Implements RFC 1928 SOCKS Protocol Version 5
 * 
 * This server uses direct context-driven patterns for all I/O operations and supports:
 * - No authentication (non-auth mode)
 * - CONNECT command for TCP proxying
 * - io_uring batch operations for performance
 */
class Socks5Server(
    internal val bindAddress: String,
    internal val bindPort: Int,
    internal val ioContext: IOContext.UringContext, // Now a direct dependency, not just a config object
    internal val scope: CoroutineScope = GlobalScope
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<Socks5Server>
    override val key: CoroutineContext.Key<*> get() = Key
    // Server state
    internal var serverChannel: AsyncChannel? = null
    internal var serverJob: Job? = null
    internal val activeConnections = mutableMapOf<String, SocksConnection>()
    
    // Channels for connection management
    internal val acceptChannel = Channel<AsyncChannel>(Channel.UNLIMITED)
    internal val commandChannel = Channel<SocksCommand>(Channel.UNLIMITED)
    
    // io_uring batch processor
    internal val batchProcessor = Channel<BatchOperation>(Channel.UNLIMITED)
    
    /**
     * Connection state for each SOCKS client with CCEK context
     */
    internal data class SocksConnection(
        val id: String,
        val clientChannel: AsyncChannel,
        val clientAddress: String,
        var state: ConnectionState = ConnectionState.INIT,
        var targetChannel: AsyncChannel? = null,
        var udpChannel: AsyncChannel? = null,
        val context: CoroutineContext = EmptyCoroutineContext
    ) {
        // Add channel to coroutine context
        fun withChannel(channel: AsyncChannel): CoroutineContext =
            context + Socks5ChannelElement(channel)
    }
    
    internal enum class ConnectionState {
        INIT,
        AUTH_NEGOTIATED,
        CONNECTED,
        BINDING,
        UDP_ASSOCIATED,
        CLOSED
    }
    
    internal sealed class SocksCommand {
        data class Connect(val connectionId: String, val host: String, val port: Int) : SocksCommand()
        data class Bind(val connectionId: String, val host: String, val port: Int) : SocksCommand()
        data class UdpAssociate(val connectionId: String, val host: String, val port: Int) : SocksCommand()
        data class Close(val connectionId: String) : SocksCommand()
    }
    
    /**
     * Start the SOCKS5 server with io_uring batch processing
     */
    suspend fun start() {
        if (serverJob?.isActive == true) {
            println("SOCKS5 Server already running on $bindAddress:$bindPort")
            return
        }
        
        println("Starting SOCKS5 Server on $bindAddress:$bindPort with io_uring...")
        
        // Create server socket with io_uring
        serverChannel = createUringServerChannel(bindAddress, bindPort)
        
        // Start server coroutines
        serverJob = scope.launch {
            acceptLoop()
        }
    }
    
    /**
     * Stop the SOCKS5 server
     */
    suspend fun stop() {
        println("Stopping SOCKS5 Server...")
        
        // Close all active connections
        activeConnections.values.forEach { conn ->
            conn.clientChannel.close()
            conn.targetChannel?.close()
            conn.udpChannel?.close()
        }
        activeConnections.clear()
        
        // Stop server
        serverJob?.cancelAndJoin()
        serverChannel?.close()
        acceptChannel.close()
        commandChannel.close()
        
        println("SOCKS5 Server stopped")
    }
    
    /**
     * Accept loop with io_uring batch accepts
     */
    internal suspend fun acceptLoop() {
        val acceptBatch = mutableListOf<SqeOp.Accept>()
        val batchSize = 32 // Accept up to 32 connections at once
        
        while (isActive) {
            try {
                // Prepare batch accept operations
                repeat(batchSize) {
                    acceptBatch.add(SqeOp.Accept(serverChannel?.fd ?: -1))
                }
                
                // Submit batch to io_uring
                val batch = BatchOperation(
                    ops = acceptBatch.size j { acceptBatch[it] },
                    completions = Channel(batchSize)
                )
                batchProcessor.send(batch)
                
                // Process completions
                for (i in 0 until batchSize) {
                    val result = batch.completions.receive()
                    if (result.result > 0) {
                        val clientFd = result.result
                        val clientChannel = createUringChannel(clientFd)
                        val connectionId = generateConnectionId()
                        
                        println("SOCKS5: New connection ${clientChannel.remoteAddress} (ID: $connectionId)")
                        
                        val connection = SocksConnection(
                            id = connectionId,
                            clientChannel = clientChannel,
                            clientAddress = clientChannel.remoteAddress,
                            context = Socks5ChannelElement(clientChannel)
                        )
                        
                        activeConnections[connectionId] = connection
                        
                        // Launch the handler in the connection's own coroutine context
                        scope.launch(connection.context) {
                            handleConnection(connection)
                        }
                    }
                }
                
                acceptBatch.clear()
                
            } catch (e: Exception) {
                if (isActive) {
                    println("SOCKS5: Accept error: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Handle individual SOCKS5 connection
     */
    internal suspend fun handleConnection(connection: SocksConnection) {
        try {
            // Phase 1: Version and method negotiation
            if (!negotiateAuth(connection)) {
                return
            }
            
            // Phase 2: Process client request
            processRequest(connection)
            
            // Phase 3: Relay data (for CONNECT command)
            if (connection.state == ConnectionState.CONNECTED) {
                relayData(connection)
            }
            
        } catch (e: Exception) {
            println("SOCKS5: Connection error for ${connection.id}: ${e.message}")
        } finally {
            cleanupConnection(connection)
        }
    }
    
    /**
     * Negotiate authentication method with CCEK context
     */
    internal suspend fun negotiateAuth(connection: SocksConnection): Boolean = 
        withContext(connection.context) {
            val buffer = ByteArray(257) // Max possible: version + nmethods + 255 methods
            
            // Use channel from context
            val channel = coroutineContext[Socks5ChannelKey]?.channel 
                ?: connection.clientChannel
            
            // Read version and number of methods
            val read = channel.read(buffer)
            if (read < 2) {
                println("SOCKS5: Invalid greeting from ${connection.id}")
                return@withContext false
            }
        
        val version = buffer[0]
        val nmethods = buffer[1].toInt() and 0xFF
        
        if (version != VERSION) {
            println("SOCKS5: Unsupported version $version from ${connection.id}")
            sendAuthResponse(connection, Methods.NO_ACCEPTABLE_METHODS)
            return false
        }
        
        // Read methods
        val methodsRead = connection.clientChannel.read(buffer, 2, nmethods)
        if (methodsRead < nmethods) {
            println("SOCKS5: Incomplete methods from ${connection.id}")
            return false
        }
        
        // Check for no-auth method
        var hasNoAuth = false
        for (i in 0 until nmethods) {
            if (buffer[2 + i] == Methods.NO_AUTHENTICATION_REQUIRED) {
                hasNoAuth = true
                break
            }
        }
        
        if (!hasNoAuth) {
            println("SOCKS5: No acceptable methods from ${connection.id}")
            sendAuthResponse(connection, Methods.NO_ACCEPTABLE_METHODS)
            return false
        }
        
        // Accept no-auth
        sendAuthResponse(connection, Methods.NO_AUTHENTICATION_REQUIRED)
        connection.state = ConnectionState.AUTH_NEGOTIATED
        return true
    }
    
    /**
     * Send authentication response
     */
    internal suspend fun sendAuthResponse(connection: SocksConnection, method: Byte) {
        val response = byteArrayOf(VERSION, method)
        connection.clientChannel.write(response)
    }
    
    /**
     * Process SOCKS5 request
     */
    internal suspend fun processRequest(connection: SocksConnection) {
        val buffer = ByteArray(512)
        
        // Read request header (minimum 10 bytes)
        val read = connection.clientChannel.read(buffer, 0, 10)
        if (read < 10) {
            println("SOCKS5: Invalid request from ${connection.id}")
            sendReply(connection, ReplyCodes.SOCKS_SERVER_FAILURE, "0.0.0.0", 0)
            return
        }
        
        val version = buffer[0]
        val command = buffer[1]
        val reserved = buffer[2]
        val addressType = buffer[3]
        
        if (version != VERSION) {
            println("SOCKS5: Wrong version in request from ${connection.id}")
            sendReply(connection, ReplyCodes.SOCKS_SERVER_FAILURE, "0.0.0.0", 0)
            return
        }
        
        // Parse target address
        val (targetHost, targetPort, bytesRead) = parseAddress(buffer, 3, addressType)
        if (targetHost == null || targetPort == null) {
            println("SOCKS5: Invalid address in request from ${connection.id}")
            sendReply(connection, ReplyCodes.ADDRESS_TYPE_NOT_SUPPORTED, "0.0.0.0", 0)
            return
        }
        
        // Handle command
        when (command) {
            Commands.CONNECT -> handleConnect(connection, targetHost, targetPort)
            Commands.BIND -> handleBind(connection, targetHost, targetPort)
            Commands.UDP_ASSOCIATE -> handleUdpAssociate(connection, targetHost, targetPort)
            else -> {
                println("SOCKS5: Unsupported command $command from ${connection.id}")
                sendReply(connection, ReplyCodes.COMMAND_NOT_SUPPORTED, "0.0.0.0", 0)
            }
        }
    }
    
    /**
     * Handle CONNECT command
     */
    internal suspend fun handleConnect(
        connection: SocksConnection,
        targetHost: String,
        targetPort: Int
    ) {
        println("SOCKS5: CONNECT request from ${connection.id} to $targetHost:$targetPort")
        
        try {
            // Create connection to target
            val targetChannel = createClientChannel(targetHost, targetPort)
            connection.targetChannel = targetChannel
            connection.state = ConnectionState.CONNECTED
            
            // Send success reply
            val localAddr = targetChannel.localAddress()
            val (host, port) = parseLocalAddress(localAddr)
            sendReply(connection, ReplyCodes.SUCCEEDED, host, port)
            
            println("SOCKS5: Connected ${connection.id} to $targetHost:$targetPort")
            
        } catch (e: Exception) {
            println("SOCKS5: Connect failed for ${connection.id}: ${e.message}")
            val replyCode = when {
                e.message?.contains("unreachable", ignoreCase = true) == true -> 
                    ReplyCodes.NETWORK_UNREACHABLE
                e.message?.contains("refused", ignoreCase = true) == true -> 
                    ReplyCodes.CONNECTION_REFUSED
                else -> ReplyCodes.SOCKS_SERVER_FAILURE
            }
            sendReply(connection, replyCode, "0.0.0.0", 0)
        }
    }
    
    /**
     * Handle BIND command
     */
    internal suspend fun handleBind(
        connection: SocksConnection,
        targetHost: String,
        targetPort: Int
    ) {
        println("SOCKS5: BIND request from ${connection.id} for $targetHost:$targetPort")
        
        try {
            // Create listening socket
            val bindChannel = createServerChannel("0.0.0.0", 0) // Random port
            connection.targetChannel = bindChannel
            connection.state = ConnectionState.BINDING
            
            val bindAddr = bindChannel.localAddress()
            val (host, port) = parseLocalAddress(bindAddr)
            
            // Send first reply with bind address
            sendReply(connection, ReplyCodes.SUCCEEDED, host, port)
            
            // Wait for incoming connection
            scope.launch {
                try {
                    val incomingChannel = bindChannel.accept()
                    connection.targetChannel = incomingChannel
                    connection.state = ConnectionState.CONNECTED
                    
                    val remoteAddr = incomingChannel.remoteAddress()
                    val (remoteHost, remotePort) = parseLocalAddress(remoteAddr)
                    
                    // Send second reply with incoming connection info
                    sendReply(connection, ReplyCodes.SUCCEEDED, remoteHost, remotePort)
                    
                    // Start relaying
                    relayData(connection)
                    
                } catch (e: Exception) {
                    println("SOCKS5: Bind accept failed for ${connection.id}: ${e.message}")
                    sendReply(connection, ReplyCodes.SOCKS_SERVER_FAILURE, "0.0.0.0", 0)
                }
            }
            
        } catch (e: Exception) {
            println("SOCKS5: Bind failed for ${connection.id}: ${e.message}")
            sendReply(connection, ReplyCodes.SOCKS_SERVER_FAILURE, "0.0.0.0", 0)
        }
    }
    
    /**
     * Handle UDP ASSOCIATE command
     */
    internal suspend fun handleUdpAssociate(
        connection: SocksConnection,
        targetHost: String,
        targetPort: Int
    ) {
        println("SOCKS5: UDP ASSOCIATE request from ${connection.id}")
        
        try {
            // Create UDP socket
            val udpChannel = createUdpChannel()
            connection.udpChannel = udpChannel
            connection.state = ConnectionState.UDP_ASSOCIATED
            
            val udpAddr = udpChannel.localAddress()
            val (host, port) = parseLocalAddress(udpAddr)
            
            // Send reply with UDP relay address
            sendReply(connection, ReplyCodes.SUCCEEDED, host, port)
            
            // Start UDP relay
            scope.launch {
                relayUdp(connection)
            }
            
        } catch (e: Exception) {
            println("SOCKS5: UDP associate failed for ${connection.id}: ${e.message}")
            sendReply(connection, ReplyCodes.SOCKS_SERVER_FAILURE, "0.0.0.0", 0)
        }
    }
    
    /**
     * Relay data with io_uring batch operations
     */
    internal suspend fun relayData(connection: SocksConnection) = coroutineScope {
        val batchSize = 16
        val bufferSize = 65536
        
        // Create buffer pools for zero-copy
        val clientBuffers = Array(batchSize) { ByteArray(bufferSize) }
        val targetBuffers = Array(batchSize) { ByteArray(bufferSize) }
        
        val clientToTarget = launch(connection.withChannel(connection.clientChannel)) {
            relayBatch(
                from = connection.clientChannel,
                to = connection.targetChannel!!,
                buffers = clientBuffers.size j clientBuffers::get,
                direction = "client->target"
            )
        }
        
        val targetToClient = launch(connection.withChannel(connection.targetChannel!!)) {
            relayBatch(
                from = connection.targetChannel!!,
                to = connection.clientChannel,
                buffers = targetBuffers.size j targetBuffers::get,
                direction = "target->client"
            )
        }
        
        // Wait for either direction to finish
        select<Unit> {
            clientToTarget.onJoin { }
            targetToClient.onJoin { }
        }
        
        // Cancel the other direction
        clientToTarget.cancel()
        targetToClient.cancel()
    }
    
    /**
     * Relay data using io_uring batch operations
     */
    internal suspend fun relayBatch(
        from: AsyncChannel,
        to: AsyncChannel,
        buffers: Indexed<ByteArray>,
        direction: String
    ) {
        try {
            while (isActive && from.isOpen && to.isOpen) {
                // Batch read
                val readResults = from.readBatch(buffers)
                
                // Prepare write batch for successful reads
                val writeBuffers = mutableListOf<ByteArray>()
                for (i in 0 until readResults.component1()) {
                    val bytesRead = readResults.component2()(i)
                    if (bytesRead > 0) {
                        writeBuffers.add(buffers.component2()(i).sliceArray(0 until bytesRead))
                    }
                }
                
                if (writeBuffers.isEmpty()) break
                
                // Batch write
                val writeResults = to.writeBatch(
                    writeBuffers.size j writeBuffers::get
                )
                
                // Check for write errors
                for (i in 0 until writeResults.component1()) {
                    if (writeResults.component2()(i) < 0) {
                        throw Exception("Write failed in $direction")
                    }
                }
            }
        } catch (e: Exception) {
            // Connection closed or error
        }
    }
    
    /**
     * Relay UDP packets
     */
    internal suspend fun relayUdp(connection: SocksConnection) {
        // TODO: Implement UDP relay with SOCKS5 UDP encapsulation
        // This requires parsing SOCKS5 UDP request headers and forwarding
    }
    
    /**
     * Send SOCKS5 reply
     */
    internal suspend fun sendReply(
        connection: SocksConnection,
        replyCode: Byte,
        bindHost: String,
        bindPort: Int
    ) {
        val response = mutableListOf<Byte>()
        response.add(VERSION)
        response.add(replyCode)
        response.add(0x00) // Reserved
        
        // Add address
        when {
            bindHost.contains(':') -> {
                // IPv6
                response.add(AddressTypes.IPV6)
                // TODO: Parse IPv6 address
                response.addAll(ByteArray(16).toList())
            }
            bindHost.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) -> {
                // IPv4
                response.add(AddressTypes.IPV4)
                bindHost.split('.').forEach { octet ->
                    response.add(octet.toInt().toByte())
                }
            }
            else -> {
                // Domain name
                response.add(AddressTypes.DOMAINNAME)
                val hostBytes = bindHost.toByteArray()
                response.add(hostBytes.size.toByte())
                response.addAll(hostBytes.toList())
            }
        }
        
        // Add port
        response.add((bindPort shr 8).toByte())
        response.add((bindPort and 0xFF).toByte())
        
        connection.clientChannel.write(response.toByteArray())
    }
    
    /**
     * Parse address from SOCKS5 request
     */
    internal suspend fun parseAddress(
        buffer: ByteArray,
        offset: Int,
        addressType: Byte
    ): Triple<String?, Int?, Int> {
        var pos = offset + 1
        
        val host = when (addressType) {
            AddressTypes.IPV4 -> {
                if (buffer.size < pos + 4) return Triple(null, null, 0)
                val addr = "${buffer[pos].toInt() and 0xFF}." +
                          "${buffer[pos + 1].toInt() and 0xFF}." +
                          "${buffer[pos + 2].toInt() and 0xFF}." +
                          "${buffer[pos + 3].toInt() and 0xFF}"
                pos += 4
                addr
            }
            AddressTypes.DOMAINNAME -> {
                if (buffer.size < pos + 1) return Triple(null, null, 0)
                val len = buffer[pos].toInt() and 0xFF
                pos += 1
                if (buffer.size < pos + len) return Triple(null, null, 0)
                val addr = String(buffer, pos, len)
                pos += len
                addr
            }
            AddressTypes.IPV6 -> {
                if (buffer.size < pos + 16) return Triple(null, null, 0)
                // TODO: Format IPv6 address
                pos += 16
                "[IPv6]"
            }
            else -> return Triple(null, null, 0)
        }
        
        if (buffer.size < pos + 2) return Triple(null, null, 0)
        val port = ((buffer[pos].toInt() and 0xFF) shl 8) or (buffer[pos + 1].toInt() and 0xFF)
        
        return Triple(host, port, pos + 2 - offset)
    }
    
    /**
     * Parse local address string
     */
    internal fun parseLocalAddress(addr: String): Pair<String, Int> {
        val lastColon = addr.lastIndexOf(':')
        return if (lastColon != -1) {
            addr.substring(0, lastColon) to addr.substring(lastColon + 1).toInt()
        } else {
            addr to 0
        }
    }
    
    /**
     * Process commands from command channel
     */
    internal suspend fun commandProcessor() {
        for (command in commandChannel) {
            when (command) {
                is SocksCommand.Connect -> {
                    // Handle dynamic connects if needed
                }
                is SocksCommand.Close -> {
                    activeConnections[command.connectionId]?.let { conn ->
                        cleanupConnection(conn)
                    }
                }
                else -> {}
            }
        }
    }
    
    /**
     * Cleanup connection resources
     */
    internal fun cleanupConnection(connection: SocksConnection) {
        connection.clientChannel.close()
        connection.targetChannel?.close()
        connection.udpChannel?.close()
        activeConnections.remove(connection.id)
        println("SOCKS5: Closed connection ${connection.id}")
    }
    
    /**
     * Generate unique connection ID
     */
    internal fun generateConnectionId(): String {
        return "socks5-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}-${(Math.random() * 10000).toInt()}"
    }
    
    /**
     * io_uring batch processor coroutine
     */
    internal suspend fun uringBatchProcessor() {
        for (batch in batchProcessor) {
            // Submit batch to io_uring
            ioContext.submitBatch(batch.ops)
            
            // Wait for completions
            val results = ioContext.waitCompletions(batch.ops.component1())
            
            // Send results back
            for (i in 0 until results.component1()) {
                batch.completions.send(results.component2()(i))
            }
        }
    }
    
    /**
     * Create io_uring channels
     */
    internal suspend fun createUringServerChannel(host: String, port: Int): AsyncChannel {
        return UringServerChannel(host, port, ioContext)
    }
    
    internal suspend fun createUringChannel(fd: Int): AsyncChannel {
        return UringChannel(fd, ioContext)
    }
    
    internal suspend fun createUringClientChannel(host: String, port: Int): AsyncChannel {
        return UringClientChannel(host, port, ioContext)
    }
    
    internal suspend fun createUringUdpChannel(): AsyncChannel {
        return UringUdpChannel(ioContext)
    }

    // CCEK-based connection handler using channel chain context
    internal suspend fun handleConnectionWithCCEK(connection: SocksConnection, ccek: CcekContext) {
        val channelContext = AsyncChannelContext(
            channelId = connection.id,
            fd = connection.clientChannel.fd,
            type = AsyncChannelContext.ChannelType.TCP_CLIENT,
            localAddr = connection.clientChannel.localAddress,
            remoteAddr = connection.clientChannel.remoteAddress
        )
        val chain = ChannelChainContext(1 j { channelContext })
        withContext(ccek + chain) {
            // Use relayBatch or channel-based relay as appropriate
            if (connection.targetChannel != null) {
                relayBatch(connection.clientChannel, connection.targetChannel)
            }
        }
    }

    // io_uring-first async batch relay
    internal suspend fun relayBatch(
        from: AsyncChannel,
        to: AsyncChannel,
        bufferPool: Array<ByteArray> = Array(16) { ByteArray(65536) }
    ) {
        val batchSize = bufferPool.size
        while (from.isOpen && to.isOpen) {
            val readOps = Array(batchSize) { i -> SqeOp.Read(from.fd, bufferPool[i], 0) }
            val readResults = from.context.submitAndWait(batchSize j readOps::get)
            for (i in 0 until batchSize) {
                val read = readResults.component2()(i)
                if (read <= 0) continue
                val writeOp = SqeOp.Write(to.fd, bufferPool[i], 0, read)
                to.context.submitAndWait(1 j { writeOp })
            }
        }
    }
}

/**
 * io_uring channel implementations
 */
class UringChannel(
    internal val fd: Int,
    internal val context: IOContext.UringContext
) : AsyncChannel {
    override suspend fun readBatch(buffers: Indexed<ByteArray>): Indexed<Int> {
        val ops = Array(buffers.component1()) { i ->
            SqeOp.Read(fd, buffers.component2()(i), 0)
        }
        return context.submitAndWait(ops.size j ops::get)
    }
    
    override suspend fun writeBatch(buffers: Indexed<ByteArray>): Indexed<Int> {
        val ops = Array(buffers.component1()) { i ->
            SqeOp.Write(fd, buffers.component2()(i), 0)
        }
        return context.submitAndWait(ops.size j ops::get)
    }
    
    override val localAddress: String get() = context.getLocalAddress(fd)
    override val remoteAddress: String get() = context.getRemoteAddress(fd)
    override val isOpen: Boolean get() = context.isOpen(fd)
    
    override fun close() = context.close(fd)
    
    val fd: Int get() = fd
}

class UringServerChannel(
    host: String,
    port: Int,
    context: IOContext.UringContext
) : UringChannel(context.createServerSocket(host, port), context) {
    suspend fun accept(): AsyncChannel {
        val op = SqeOp.Accept(fd)
        val result = context.submitAndWait(1 j { op })
        return UringChannel(result.component2()(0), context)
    }
}

class UringClientChannel(
    host: String,
    port: Int,
    context: IOContext.UringContext
) : UringChannel(context.createSocket(), context) {
    init {
        // Connect in constructor
        runBlocking {
            val op = SqeOp.Connect(fd, host, port)
            context.submitAndWait(1 j { op })
        }
    }
}

class UringUdpChannel(
    context: IOContext.UringContext
) : UringChannel(context.createUdpSocket(), context)

// CCEK Key-based API extensions for SOCKS5 Server
/**
 * Start SOCKS5 server using Socks5Server from context
 */
suspend fun Socks5Server.Key.start(): Socks5Server {
    val server = coroutineContext[this] 
        ?: throw IllegalStateException("Socks5Server not found in context")
    server.start()
    return server
}

/**
 * Stop SOCKS5 server using Socks5Server from context
 */
suspend fun Socks5Server.Key.stop() {
    val server = coroutineContext[this] 
        ?: throw IllegalStateException("Socks5Server not found in context")
    server.stop()
}

/**
 * Get server stats using Socks5Server from context
 */
fun Socks5Server.Key.getStats(): Socks5Stats {
    val server = coroutineContext[this] 
        ?: throw IllegalStateException("Socks5Server not found in context")
    return Socks5Stats(
        isRunning = server.serverJob?.isActive == true,
        bindAddress = server.bindAddress,
        bindPort = server.bindPort,
        activeConnections = server.activeConnections.size
    )
}

/**
 * Create SOCKS5 server in context
 */
fun Socks5Server.Key.create(
    bindAddress: String = "0.0.0.0",
    bindPort: Int = 1080,
    ioContext: IOContext.UringContext,
    scope: CoroutineScope = GlobalScope,
    configure: Socks5Server.() -> Unit = {}
): Socks5Server {
    return Socks5Server(bindAddress, bindPort, ioContext, scope).apply(configure)
}

/**
 * Server statistics
 */
data class Socks5Stats(
    val isRunning: Boolean,
    val bindAddress: String,
    val bindPort: Int,
    val activeConnections: Int
)
