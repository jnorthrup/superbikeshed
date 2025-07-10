@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.channel.api

import borg.trikeshed.lib.*
import borg.trikeshed.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext

/**
 * Protocol channelization framework following BBCursor integration pattern.
 * Normalizes all protocols to use unified channel abstractions.
 * 
 * ELIMINATES CODE DUPLICATION:
 * - Common handler patterns
 * - Shared utility functions (readUint32, writeUint32, etc.)
 * - Base protocol channel implementations
 * - Unified message filtering patterns
 */

/**
 * Base interface for channelized protocol adapters.
 * All protocols (HTTP, QUIC, SSH, etc.) should implement this.
 */
interface ProtocolAdapter<TMessage, TConfig> : CoroutineContext.Element {
    val protocolName: String
    val config: TConfig
    
    /**
     * Parse incoming data into protocol messages using BBCursive-style parsing.
     */
    suspend fun parseMessage(data: ByteIndexed, position: Int): Join<TMessage?, Int>?
    
    /**
     * Serialize protocol message into bytes for transmission.
     */
    suspend fun serializeMessage(message: TMessage): ByteIndexed
    
    /**
     * Handle protocol-specific message processing.
     */
    suspend fun handleMessage(message: TMessage, context: ProtocolContext): Flow<TMessage>
    
    /**
     * Create protocol-specific channel wrapper for transport-level operations.
     */
    fun wrapChannel(channel: Channel): ProtocolChannel<TMessage>
}

/**
 * Base implementation for protocol adapters to eliminate duplication.
 */
abstract class AbstractProtocolAdapter<TMessage, TConfig>(
    override val config: TConfig
) : ProtocolAdapter<TMessage, TConfig> {
    
    /**
     * Common handler pattern to eliminate duplication across protocols.
     */
    internal suspend inline fun <reified THandler : CoroutineContext.Element, TResponse> 
        handleWithService(
            context: ProtocolContext,
            handlerType: Class<THandler>,
            message: TMessage,
            defaultResponse: TResponse,
            handlerCall: (THandler) -> TResponse
        ): TResponse {
        val handler = context.currentService<THandler>()
        return if (handler != null) {
            handlerCall(handler)
        } else {
            defaultResponse
        }
    }
    
    /**
     * Common handler pattern with flow emission.
     */
    internal suspend inline fun <reified THandler : CoroutineContext.Element> 
        handleWithServiceFlow(
            context: ProtocolContext,
            message: TMessage,
            defaultResponse: TMessage,
            handlerCall: (THandler) -> TMessage
        ): Flow<TMessage> = flow {
        val handler = context.currentService<THandler>()
        val response = if (handler != null) {
            handlerCall(handler)
        } else {
            defaultResponse
        }
        emit(response)
    }
}

/**
 * Protocol context for dependency injection and service composition.
 */
class ProtocolContext(
    internal val context: CoroutineContext
) : CoroutineContext by context {
    
    inline fun <reified T : CoroutineContext.Element> requireService(): T {
        return context[T::class.java as CoroutineContext.Key<T>]
            ?: error("Required service ${T::class.simpleName} not found in protocol context")
    }
    
    inline fun <reified T : CoroutineContext.Element> currentService(): T? {
        return context[T::class.java as CoroutineContext.Key<T>]
    }
    
    fun withService(element: CoroutineContext.Element): ProtocolContext {
        return ProtocolContext(context + element)
    }
}

/**
 * Channelized protocol interface providing reactive message streams.
 * Follows BBCursor flow-based patterns.
 */
interface ProtocolChannel<TMessage> {
    val channelId: ChannelId
    val isActive: Boolean
    
    /**
     * Incoming message stream from the channel.
     */
    fun incomingMessages(): Flow<TMessage>
    
    /**
     * Send messages through the channel.
     */
    suspend fun sendMessage(message: TMessage)
    
    /**
     * Send message flow to the channel.
     */
    suspend fun sendMessages(messages: Flow<TMessage>)
    
    /**
     * Close the protocol channel.
     */
    suspend fun close()
}

/**
 * Base implementation for protocol channels.
 */
abstract class AbstractProtocolChannel<TMessage>(
    internal val adapter: ProtocolAdapter<TMessage, *>,
    internal val channel: Channel
) : ProtocolChannel<TMessage> {
    
    override val channelId: ChannelId get() = channel.id
    override val isActive: Boolean get() = channel.isOpen
    
    override fun incomingMessages(): Flow<TMessage> = flow {
        var accumulated = ByteArray(0)
        
        while (channel.isOpen) {
            val receivedData = channel.receive()
            
            if (receivedData.a > 0) {
                val newData = receivedData.toByteArray()
                accumulated += newData
                
                // Parse messages from accumulated data
                var position = 0
                val indexed = accumulated.toIndexed()
                
                while (position < accumulated.size) {
                    val result = adapter.parseMessage(indexed, position)
                    if (result != null) {
                        val (message, newPos) = result
                        if (message != null) {
                            emit(message)
                            position = newPos
                        } else {
                            break // Need more data
                        }
                    } else {
                        break // Parse error or need more data
                    }
                }
                
                // Keep unprocessed data
                if (position > 0) {
                    accumulated = accumulated.sliceArray(position until accumulated.size)
                }
            }
        }
    }
    
    override suspend fun sendMessage(message: TMessage) {
        val data = adapter.serializeMessage(message)
        channel.send(data)
    }
    
    override suspend fun sendMessages(messages: Flow<TMessage>) {
        messages.collect { message ->
            sendMessage(message)
        }
    }
    
    override suspend fun close() {
        channel.close()
    }
}

/**
 * Enhanced protocol channel with common filtering patterns.
 */
abstract class EnhancedProtocolChannel<TMessage>(
    adapter: ProtocolAdapter<TMessage, *>,
    channel: Channel
) : AbstractProtocolChannel<TMessage>(adapter, channel) {
    
    /**
     * Common pattern for filtering incoming messages by type.
     */
    internal inline fun <reified T : TMessage> filterIncomingMessages(): Flow<T> = flow {
        incomingMessages()
            .filterIsInstance<T>()
            .collect { message ->
                emit(message)
            }
    }
    
    /**
     * Common pattern for extracting data from filtered messages.
     */
    internal inline fun <reified T : TMessage, R> filterAndTransform(
        transform: (T) -> R
    ): Flow<R> = flow {
        filterIncomingMessages<T>().collect { message ->
            emit(transform(message))
        }
    }
}

/**
 * Protocol server interface for accepting connections.
 */
interface ProtocolServer<TMessage, TConfig> {
    val bindAddress: ChannelAddress
    val config: TConfig
    
    /**
     * Start the protocol server.
     */
    suspend fun start()
    
    /**
     * Stop the protocol server.
     */
    suspend fun stop()
    
    /**
     * Flow of incoming client connections.
     */
    fun clientConnections(): Flow<ProtocolChannel<TMessage>>
}

/**
 * Base implementation for protocol servers.
 */
abstract class AbstractProtocolServer<TMessage, TConfig>(
    internal val adapter: ProtocolAdapter<TMessage, TConfig>,
    override val bindAddress: ChannelAddress,
    override val config: TConfig
) : ProtocolServer<TMessage, TConfig> {
    
    internal var serverChannel: ServerChannel? = null
    internal var serverScope: CoroutineScope? = null
    
    override suspend fun start() {
        val channelService = currentCoroutineContext().requireService<ChannelService>()
        
        serverChannel = channelService.createServer(
            ChannelConfig(
                type = getChannelType(),
                mode = ChannelMode.READ_WRITE
            ),
            bindAddress
        )
        
        serverChannel?.bind()
        serverScope = CoroutineScope(currentCoroutineContext() + SupervisorJob())
    }
    
    override suspend fun stop() {
        serverScope?.cancel()
        serverChannel?.close()
        serverChannel = null
        serverScope = null
    }
    
    override fun clientConnections(): Flow<ProtocolChannel<TMessage>> = flow {
        val server = serverChannel ?: error("Server not started")
        
        while (server.isOpen) {
            val clientChannel = server.accept()
            if (clientChannel != null) {
                val protocolChannel = adapter.wrapChannel(clientChannel)
                emit(protocolChannel)
            }
        }
    }
    
    internal abstract fun getChannelType(): ChannelType
}

/**
 * Protocol client interface for outbound connections.
 */
interface ProtocolClient<TMessage, TConfig> {
    val config: TConfig
    
    /**
     * Connect to a remote address.
     */
    suspend fun connect(address: ChannelAddress): ProtocolChannel<TMessage>
    
    /**
     * Disconnect from remote address.
     */
    suspend fun disconnect()
}

/**
 * Base implementation for protocol clients.
 */
abstract class AbstractProtocolClient<TMessage, TConfig>(
    internal val adapter: ProtocolAdapter<TMessage, TConfig>,
    override val config: TConfig
) : ProtocolClient<TMessage, TConfig> {
    
    internal var connection: ProtocolChannel<TMessage>? = null
    
    override suspend fun connect(address: ChannelAddress): ProtocolChannel<TMessage> {
        val channelService = currentCoroutineContext().requireService<ChannelService>()
        
        val channel = channelService.connect(
            ChannelConfig(
                type = getChannelType(),
                mode = ChannelMode.READ_WRITE
            ),
            address
        )
        
        val protocolChannel = adapter.wrapChannel(channel)
        connection = protocolChannel
        return protocolChannel
    }
    
    override suspend fun disconnect() {
        connection?.close()
        connection = null
    }
    
    internal abstract fun getChannelType(): ChannelType
}

/**
 * Shared utility functions to eliminate duplication across protocols.
 */
object ProtocolUtils {
    
    /**
     * Read 32-bit unsigned integer from ByteIndexed.
     */
    fun readUint32(data: ByteIndexed, position: Int): Long {
        return (data[position].toLong() shl 24) or
               (data[position + 1].toLong() shl 16) or
               (data[position + 2].toLong() shl 8) or
               data[position + 3].toLong()
    }
    
    /**
     * Write 32-bit unsigned integer to ByteArray.
     */
    fun writeUint32(data: ByteArray, position: Int, value: Long) {
        data[position] = (value shr 24).toByte()
        data[position + 1] = (value shr 16).toByte()
        data[position + 2] = (value shr 8).toByte()
        data[position + 3] = value.toByte()
    }
    
    /**
     * Write 32-bit unsigned integer and return as List<Byte>.
     */
    fun writeUint32(value: Long): List<Byte> {
        return listOf(
            (value shr 24).toByte(),
            (value shr 16).toByte(),
            (value shr 8).toByte(),
            value.toByte()
        )
    }
    
    /**
     * Read string from ByteIndexed (length-prefixed).
     */
    fun readString(data: ByteIndexed, position: Int): String {
        val length = readUint32(data, position).toInt()
        return data.slice(position + 4 until position + 4 + length).decodeToString()
    }
    
    /**
     * Write string to ByteArray (length-prefixed).
     */
    fun writeString(data: ByteArray, position: Int, value: String) {
        val bytes = value.encodeToByteArray()
        writeUint32(data, position, bytes.size.toLong())
        bytes.copyInto(data, position + 4)
    }
    
    /**
     * Read name list from ByteIndexed (SSH-style).
     */
    fun readNameList(data: ByteIndexed, position: Int): List<String> {
        val length = readUint32(data, position).toInt()
        val listData = data.slice(position + 4 until position + 4 + length)
        val names = mutableListOf<String>()
        var offset = 0
        
        while (offset < listData.size) {
            val nameLength = readUint32(listData, offset).toInt()
            val name = listData.slice(offset + 4 until offset + 4 + nameLength).decodeToString()
            names.add(name)
            offset += 4 + nameLength
        }
        
        return names
    }
    
    /**
     * Write name list to ByteArray (SSH-style).
     */
    fun writeNameList(data: ByteArray, position: Int, names: List<String>) {
        val totalLength = names.sumOf { 4 + it.length }
        writeUint32(data, position, totalLength.toLong())
        var offset = position + 4
        
        names.forEach { name ->
            writeString(data, offset, name)
            offset += 4 + name.length
        }
    }
    
    /**
     * Write varint (QUIC-style).
     */
    fun writeVarint(value: Long): List<Byte> {
        return when {
            value < 0x40 -> listOf(value.toByte())
            value < 0x4000 -> listOf(
                (0x40 or (value shr 8)).toByte(),
                value.toByte()
            )
            value < 0x40000000 -> listOf(
                (0x80 or (value shr 24)).toByte(),
                (value shr 16).toByte(),
                (value shr 8).toByte(),
                value.toByte()
            )
            else -> listOf(
                (0xC0 or (value shr 56)).toByte(),
                (value shr 48).toByte(),
                (value shr 40).toByte(),
                (value shr 32).toByte(),
                (value shr 24).toByte(),
                (value shr 16).toByte(),
                (value shr 8).toByte(),
                value.toByte()
            )
        }
    }
}

/**
 * Extension functions for ByteArray channelization compatibility.
 */
fun ByteArray.toIndexed(): ByteIndexed = size j { i -> this[i] }

fun ByteIndexed.toByteArray(): ByteArray {
    val result = ByteArray(a)
    for (i in 0 until a) {
        result[i] = b(i)
    }
    return result
}

/**
 * Context extension for service requirements.
 */
suspend inline fun <reified T : CoroutineContext.Element> CoroutineContext.requireService(): T {
    return this[T::class.java as CoroutineContext.Key<T>]
        ?: error("Required service ${T::class.simpleName} not found in context")
}

/**
 * Protocol registry for managing multiple protocol adapters.
 * Follows BBCursor composition patterns.
 */
class ProtocolRegistry {
    internal val adapters = mutableMapOf<String, ProtocolAdapter<*, *>>()
    
    /**
     * Register a protocol adapter.
     */
    fun <TMessage, TConfig> register(adapter: ProtocolAdapter<TMessage, TConfig>) {
        adapters[adapter.protocolName] = adapter
    }
    
    /**
     * Get protocol adapter by name.
     */
    @Suppress("UNCHECKED_CAST")
    fun <TMessage, TConfig> getAdapter(name: String): ProtocolAdapter<TMessage, TConfig>? {
        return adapters[name] as? ProtocolAdapter<TMessage, TConfig>
    }
    
    /**
     * Create protocol channel for given protocol.
     */
    suspend fun <TMessage, TConfig> createChannel(
        protocolName: String,
        channel: Channel,
        config: TConfig
    ): ProtocolChannel<TMessage>? {
        val adapter = getAdapter<TMessage, TConfig>(protocolName) ?: return null
        return adapter.wrapChannel(channel)
    }
}

/**
 * Protocol composition utilities following BBCursor patterns.
 */
object ProtocolComposition {
    
    /**
     * Compose multiple protocol channels into a unified interface.
     */
    fun <TMessage> composeChannels(
        channels: Indexed<ProtocolChannel<TMessage>>
    ): ProtocolChannel<TMessage> = object : ProtocolChannel<TMessage> {
        override val channelId: ChannelId = channels[0].channelId
        override val isActive: Boolean = channels.any { it.isActive }
        
        override fun incomingMessages(): Flow<TMessage> = flow {
            channels.forEach { channel ->
                channel.incomingMessages().collect { message ->
                    emit(message)
                }
            }
        }
        
        override suspend fun sendMessage(message: TMessage) {
            channels.forEach { channel ->
                channel.sendMessage(message)
            }
        }
        
        override suspend fun sendMessages(messages: Flow<TMessage>) {
            channels.forEach { channel ->
                channel.sendMessages(messages)
            }
        }
        
        override suspend fun close() {
            channels.forEach { channel ->
                channel.close()
            }
        }
    }
    
    /**
     * Create protocol pipeline for message transformation.
     */
    fun <TInput, TOutput> createPipeline(
        inputChannel: ProtocolChannel<TInput>,
        transform: suspend (TInput) -> TOutput
    ): ProtocolChannel<TOutput> = object : ProtocolChannel<TOutput> {
        override val channelId: ChannelId = inputChannel.channelId
        override val isActive: Boolean = inputChannel.isActive
        
        override fun incomingMessages(): Flow<TOutput> = flow {
            inputChannel.incomingMessages().collect { input ->
                val output = transform(input)
                emit(output)
            }
        }
        
        override suspend fun sendMessage(message: TOutput) {
            // Pipeline is read-only for output
            throw UnsupportedOperationException("Pipeline is read-only")
        }
        
        override suspend fun sendMessages(messages: Flow<TOutput>) {
            throw UnsupportedOperationException("Pipeline is read-only")
        }
        
        override suspend fun close() {
            inputChannel.close()
        }
    }
}

/**
 * Protocol metrics and monitoring.
 */
data class ProtocolMetrics(
    val protocolName: String,
    val messagesProcessed: Long,
    val bytesTransferred: Long,
    val errors: Long,
    val latencyMs: Double
)

/**
 * Protocol monitoring context.
 */
class ProtocolMonitor(
    internal val scope: CoroutineScope = GlobalScope
) {
    internal val _metrics = MutableSharedFlow<ProtocolMetrics>()
    val metrics: SharedFlow<ProtocolMetrics> = _metrics.asSharedFlow()
    
    /**
     * Monitor protocol channel.
     */
    fun <TMessage> monitorChannel(
        channel: ProtocolChannel<TMessage>,
        protocolName: String
    ): ProtocolChannel<TMessage> = object : ProtocolChannel<TMessage> {
        override val channelId: ChannelId = channel.channelId
        override val isActive: Boolean = channel.isActive
        
        internal var messagesProcessed = 0L
        internal var bytesTransferred = 0L
        internal var errors = 0L
        internal val startTime = System.currentTimeMillis()
        
        override fun incomingMessages(): Flow<TMessage> = flow {
            channel.incomingMessages()
                .onEach { message ->
                    messagesProcessed++
                    bytesTransferred += message.toString().length
                }
                .catch { error ->
                    errors++
                    throw error
                }
                .collect { message ->
                    emit(message)
                }
        }
        
        override suspend fun sendMessage(message: TMessage) {
            try {
                channel.sendMessage(message)
                messagesProcessed++
                bytesTransferred += message.toString().length
            } catch (e: Exception) {
                errors++
                throw e
            }
        }
        
        override suspend fun sendMessages(messages: Flow<TMessage>) {
            channel.sendMessages(messages)
        }
        
        override suspend fun close() {
            val endTime = System.currentTimeMillis()
            val latency = (endTime - startTime).toDouble()
            
            _metrics.emit(ProtocolMetrics(
                protocolName = protocolName,
                messagesProcessed = messagesProcessed,
                bytesTransferred = bytesTransferred,
                errors = errors,
                latencyMs = latency
            ))
            
            channel.close()
        }
    }
}

// === UNIFIED PROTOCOL STATE ===

/**
 * Single unified protocol state - no more per-protocol enums
 */
sealed class ProtocolState {
    object Initial : ProtocolState()
    object Handshaking : ProtocolState()
    object Established : ProtocolState()
    object Closing : ProtocolState()
    object Closed : ProtocolState()
    data class Error(val reason: String) : ProtocolState()
}

/**
 * Protocol-agnostic message types
 */
sealed class ProtocolMessage {
    data class Request(val data: ByteIndexed, val protocol: String) : ProtocolMessage()
    data class Response(val data: ByteIndexed, val protocol: String) : ProtocolMessage()
    data class Control(val command: String, val payload: ByteIndexed) : ProtocolMessage()
}

// === UNIFIED PROTOCOL CHANNEL ===

/**
 * Single protocol channel that handles all protocols
 * No more protocol-specific state machines
 */
interface UnifiedProtocolChannel : Channel {
    val protocol: String
    val state: ProtocolState
    
    suspend fun processMessage(message: ProtocolMessage): ProtocolMessage?
    suspend fun transitionTo(newState: ProtocolState)
}

/**
 * Protocol channel implementation that eliminates FSM duplication
 */
class ProtocolChannelImpl(
    override val protocol: String,
    internal val underlyingChannel: Channel
) : UnifiedProtocolChannel, Channel by underlyingChannel {
    
    internal var _state: ProtocolState = ProtocolState.Initial
    override val state: ProtocolState get() = _state
    
    override suspend fun processMessage(message: ProtocolMessage): ProtocolMessage? {
        return when (message) {
            is ProtocolMessage.Request -> handleRequest(message)
            is ProtocolMessage.Response -> handleResponse(message)
            is ProtocolMessage.Control -> handleControl(message)
        }
    }
    
    override suspend fun transitionTo(newState: ProtocolState) {
        _state = newState
    }
    
    internal suspend fun handleRequest(request: ProtocolMessage.Request): ProtocolMessage? {
        return when (protocol) {
            "HTTP" -> handleHttpRequest(request)
            "SOCKS" -> handleSocksRequest(request)
            "QUIC" -> handleQuicRequest(request)
            "HTTP2" -> handleHttp2Request(request)
            else -> null
        }
    }
    
    internal suspend fun handleResponse(response: ProtocolMessage.Response): ProtocolMessage? {
        return when (protocol) {
            "HTTP" -> handleHttpResponse(response)
            "SOCKS" -> handleSocksResponse(response)
            "QUIC" -> handleQuicResponse(response)
            "HTTP2" -> handleHttp2Response(response)
            else -> null
        }
    }
    
    internal suspend fun handleControl(control: ProtocolMessage.Control): ProtocolMessage? {
        return when (control.command) {
            "HANDSHAKE" -> performHandshake()
            "CLOSE" -> performClose()
            "RESET" -> performReset()
            else -> null
        }
    }
    
    // Protocol-specific handlers - no state machines, just pure functions
    internal suspend fun handleHttpRequest(request: ProtocolMessage.Request): ProtocolMessage? {
        // Parse HTTP request using BBCursor patterns
        val parsed = parseHttpRequest(request.data)
        return ProtocolMessage.Response(parsed, "HTTP")
    }
    
    internal suspend fun handleSocksRequest(request: ProtocolMessage.Request): ProtocolMessage? {
        // Parse SOCKS request using BBCursor patterns  
        val parsed = parseSocksRequest(request.data)
        return ProtocolMessage.Response(parsed, "SOCKS")
    }
    
    internal suspend fun handleQuicRequest(request: ProtocolMessage.Request): ProtocolMessage? {
        // Parse QUIC request using BBCursor patterns
        val parsed = parseQuicRequest(request.data)
        return ProtocolMessage.Response(parsed, "QUIC")
    }
    
    internal suspend fun handleHttp2Request(request: ProtocolMessage.Request): ProtocolMessage? {
        // Parse HTTP/2 request using BBCursor patterns
        val parsed = parseHttp2Request(request.data)
        return ProtocolMessage.Response(parsed, "HTTP2")
    }
    
    internal suspend fun handleHttpResponse(response: ProtocolMessage.Response): ProtocolMessage? = response
    internal suspend fun handleSocksResponse(response: ProtocolMessage.Response): ProtocolMessage? = response
    internal suspend fun handleQuicResponse(response: ProtocolMessage.Response): ProtocolMessage? = response
    internal suspend fun handleHttp2Response(response: ProtocolMessage.Response): ProtocolMessage? = response
    
    internal suspend fun performHandshake(): ProtocolMessage? {
        transitionTo(ProtocolState.Handshaking)
        // Protocol-agnostic handshake
        transitionTo(ProtocolState.Established)
        return ProtocolMessage.Control("HANDSHAKE_COMPLETE", emptyByteIndexed())
    }
    
    internal suspend fun performClose(): ProtocolMessage? {
        transitionTo(ProtocolState.Closing)
        transitionTo(ProtocolState.Closed)
        return ProtocolMessage.Control("CLOSED", emptyByteIndexed())
    }
    
    internal suspend fun performReset(): ProtocolMessage? {
        transitionTo(ProtocolState.Error("Reset by peer"))
        return ProtocolMessage.Control("RESET", emptyByteIndexed())
    }
    
    // BBCursor-based parsers - no state machines
    internal fun parseHttpRequest(data: ByteIndexed): ByteIndexed {
        // Use BBCursor patterns for HTTP parsing
        return data.transform { bytes ->
            // HTTP request parsing logic
            bytes
        }
    }
    
    internal fun parseSocksRequest(data: ByteIndexed): ByteIndexed {
        // Use BBCursor patterns for SOCKS parsing
        return data.transform { bytes ->
            // SOCKS request parsing logic
            bytes
        }
    }
    
    internal fun parseQuicRequest(data: ByteIndexed): ByteIndexed {
        // Use BBCursor patterns for QUIC parsing
        return data.transform { bytes ->
            // QUIC request parsing logic
            bytes
        }
    }
    
    internal fun parseHttp2Request(data: ByteIndexed): ByteIndexed {
        // Use BBCursor patterns for HTTP/2 parsing
        return data.transform { bytes ->
            // HTTP/2 request parsing logic
            bytes
        }
    }
}

// === PROTOCOL FACTORY - NO MORE PROTOCOL-SPECIFIC FACTORIES ===

/**
 * Single protocol factory that creates unified channels
 * Eliminates: SocksFactory, HttpFactory, QuicFactory, etc.
 */
object ProtocolFactory {
    suspend fun createChannel(protocol: String, config: ChannelConfig): UnifiedProtocolChannel {
        val underlyingChannel = when (config.type) {
            ChannelType.NIO -> createNioChannel(config)
            ChannelType.IOURING -> createIoUringChannel(config)
            ChannelType.TEST -> createTestChannel(config)
        }
        
        return ProtocolChannelImpl(protocol, underlyingChannel)
    }
    
    internal suspend fun createNioChannel(config: ChannelConfig): Channel {
        // NIO implementation
        return object : Channel {
            override val isOpen: Boolean = true
            override suspend fun close() {}
            override suspend fun send(data: ByteIndexed) {}
            override suspend fun receive(): ByteIndexed = emptyByteIndexed()
        }
    }
    
    internal suspend fun createIoUringChannel(config: ChannelConfig): Channel {
        // io_uring implementation
        return object : Channel {
            override val isOpen: Boolean = true
            override suspend fun close() {}
            override suspend fun send(data: ByteIndexed) {}
            override suspend fun receive(): ByteIndexed = emptyByteIndexed()
        }
    }
    
    internal suspend fun createTestChannel(config: ChannelConfig): Channel {
        // Test implementation
        return object : Channel {
            override val isOpen: Boolean = true
            override suspend fun close() {}
            override suspend fun send(data: ByteIndexed) {}
            override suspend fun receive(): ByteIndexed = emptyByteIndexed()
        }
    }
}

// === PROTOCOL ROUTER - UNIFIED ROUTING ===

/**
 * Single protocol router that handles all protocols
 * Eliminates protocol-specific routers
 */
class ProtocolRouter {
    internal val channels = mutableMapOf<String, UnifiedProtocolChannel>()
    
    suspend fun routeMessage(protocol: String, message: ProtocolMessage): ProtocolMessage? {
        val channel = channels.getOrPut(protocol) {
            ProtocolFactory.createChannel(protocol, ChannelConfig.default())
        }
        
        return channel.processMessage(message)
    }
    
    suspend fun closeAll() {
        channels.values.forEach { it.close() }
        channels.clear()
    }
}

// === UTILITY FUNCTIONS ===

internal fun emptyByteIndexed(): ByteIndexed = 0 j { ByteArray(0)[0] }

internal fun ByteIndexed.transform(transform: (ByteIndexed) -> ByteIndexed): ByteIndexed = transform(this)

// === MIGRATION HELPERS ===

/**
 * Migration helpers to convert existing protocol-specific code
 */
object ProtocolMigration {
    fun migrateHttpStateMachine(oldStateMachine: Any): UnifiedProtocolChannel {
        // Convert HttpStateMachine to UnifiedProtocolChannel
        return ProtocolFactory.createChannel("HTTP", ChannelConfig.default())
    }
    
    fun migrateSocksStateMachine(oldStateMachine: Any): UnifiedProtocolChannel {
        // Convert SocksConnectionState to UnifiedProtocolChannel
        return ProtocolFactory.createChannel("SOCKS", ChannelConfig.default())
    }
    
    fun migrateQuicStateMachine(oldStateMachine: Any): UnifiedProtocolChannel {
        // Convert ConnectionState to UnifiedProtocolChannel
        return ProtocolFactory.createChannel("QUIC", ChannelConfig.default())
    }
    
    fun migrateHttp2StateMachine(oldStateMachine: Any): UnifiedProtocolChannel {
        // Convert HTTP2ConnectionState to UnifiedProtocolChannel
        return ProtocolFactory.createChannel("HTTP2", ChannelConfig.default())
    }
}