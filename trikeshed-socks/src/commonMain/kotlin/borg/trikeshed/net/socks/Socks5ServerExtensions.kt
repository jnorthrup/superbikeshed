@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.socks

import borg.trikeshed.lib.*
import borg.trikeshed.io.IOContext
import borg.trikeshed.reactor.SelectableChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Extension functions for SOCKS5 server channel operations
 */

// Extension for SelectableChannel to read with timeout
suspend fun SelectableChannel.read(buffer: ByteArray, offset: Int, length: Int): Int {
    return withTimeout(30.seconds) {
        val slice = buffer.sliceArray(offset until offset + length)
        val read = read(slice)
        if (read > 0) {
            System.arraycopy(slice, 0, buffer, offset, read)
        }
        read
    }
}

// Extension for SelectableChannel to write with timeout
suspend fun SelectableChannel.write(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
    return withTimeout(30.seconds) {
        write(data.sliceArray(offset until offset + length))
    }
}

// Extension to get remote address as string
fun SelectableChannel.remoteAddress(): String {
    // Platform-specific implementation would go here
    return "unknown"
}

// Extension to get local address as string
fun SelectableChannel.localAddress(): String {
    // Platform-specific implementation would go here
    return "0.0.0.0:0"
}

/**
 * SOCKS5 server builder with fluent API
 */
class Socks5ServerBuilder {
    internal var bindAddress: String = "0.0.0.0"
    internal var bindPort: Int = 1080
    internal var ioContext: IOContext? = null
    internal var scope: CoroutineScope? = null
    internal var maxConnections: Int = 1000
    internal var connectionTimeout: Duration = 30.seconds
    
    fun bind(address: String, port: Int = 1080) = apply {
        this.bindAddress = address
        this.bindPort = port
    }
    
    fun ioContext(context: IOContext) = apply { this.ioContext = context }
    fun scope(scope: CoroutineScope) = apply { this.scope = scope }
    fun maxConnections(max: Int) = apply { this.maxConnections = max }
    fun connectionTimeout(timeout: Duration) = apply { this.connectionTimeout = timeout }
    
    fun build(): Socks5Server {
        val context = ioContext ?: IOContext.NioContext("socks5-server")
        val coroutineScope = scope ?: GlobalScope
        
        return Socks5Server(bindAddress, bindPort, context, coroutineScope)
    }
}

/**
 * Coroutine context for SOCKS5 operations
 */
class Socks5Context(
    val ioContext: IOContext,
    val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : CoroutineScope {
    internal val job = SupervisorJob()
    override val coroutineContext = dispatcher + job
    
    fun cancel() = job.cancel()
}

/**
 * Channel-based SOCKS5 relay
 */
class Socks5Relay(
    internal val clientChannel: Channel<ByteArray>,
    internal val targetChannel: Channel<ByteArray>
) {
    suspend fun start() = coroutineScope {
        val clientToTarget = launch {
            for (data in clientChannel) {
                targetChannel.send(data)
            }
        }
        
        val targetToClient = launch {
            for (data in targetChannel) {
                clientChannel.send(data)
            }
        }
        
        // Wait for either to complete
        select<Unit> {
            clientToTarget.onJoin { }
            targetToClient.onJoin { }
        }
        
        // Cancel both
        clientToTarget.cancel()
        targetToClient.cancel()
    }
}

/**
 * SOCKS5 authentication handler
 */
interface Socks5AuthHandler {
    suspend fun authenticate(username: String, password: String): Boolean
}

/**
 * No-op auth handler for non-auth mode
 */
object NoAuthHandler : Socks5AuthHandler {
    override suspend fun authenticate(username: String, password: String) = true
}

/**
 * SOCKS5 statistics
 */
data class Socks5Stats(
    val activeConnections: Int,
    val totalConnections: Long,
    val bytesRelayed: Long,
    val connectRequests: Long,
    val bindRequests: Long,
    val udpRequests: Long,
    val failures: Long
)

/**
 * Create a basic SOCKS5 server
 */
suspend fun createSocks5Server(
    port: Int = 1080,
    context: IOContext = IOContext.NioContext("socks5")
): Socks5Server {
    return Socks5ServerBuilder()
        .bind("0.0.0.0", port)
        .ioContext(context)
        .build()
}

/**
 * Run SOCKS5 server with automatic cleanup
 */
suspend fun runSocks5Server(
    port: Int = 1080,
    block: suspend Socks5Server.() -> Unit
) {
    val server = createSocks5Server(port)
    try {
        server.start()
        server.block()
    } finally {
        server.stop()
    }
}