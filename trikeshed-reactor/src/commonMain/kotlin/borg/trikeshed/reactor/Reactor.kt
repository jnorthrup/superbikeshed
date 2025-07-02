package borg.trikeshed.reactor

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import borg.trikeshed.lib.*
import borg.trikeshed.lib.MetaSeries
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Indexed
import borg.trikeshed.net.socks.socksIngress
import borg.trikeshed.net.socks.socksEgress

// Placeholder IO dispatcher for commonMain - uses Default dispatcher
val PlaceholderIO: CoroutineDispatcher = Dispatchers.Default

/**
 * Reactor: Attention Distribution Mechanism for Main()'s Pursuit of Happiness
 * 
 * Main() is the beneficiary in pursuit of happiness (its original interest).
 * The Reactor is one of the attention distribution mechanisms that main() uses
 * to fulfill its desires across multiple abstractions.
 * 
 * Taxonomic Breakdown of Attention Distribution:
 * - Main() has original interest (e.g., "handle concurrent requests")
 * - Main() distributes attention across abstractions (Reactor, Protocol Stack, Business Logic, etc.)
 * - Each abstraction receives attention and works toward main()'s interest
 * - Attention flows back to main() as progress toward the original goal
 * - Main() continues distributing attention until its interest is fulfilled
 * 
 * The Reactor specifically manages event-driven attention through interestOps,
 * where each handler (UnaryAsyncReaction) can set what the reactor should pay
 * attention to next, enabling WAM-style continuation chains.
 */
class Reactor(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val numSelectorThreads: Int = max(1, 4)
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        println("Unhandled exception in Reactor: ${throwable.message}")
        throwable.printStackTrace()
    }

    internal val reactorScope = CoroutineScope(dispatcher + SupervisorJob() + exceptionHandler)
    private val isRunning = MutableStateFlow(true)

    private lateinit var selectorThreads: List<SelectorThread>
    private var nextSelectorIndex = 0

    init {
        reactorScope.launch {
            selectorThreads = List(numSelectorThreads) {
                SelectorThread(
                    SelectorInterface(),
                    MutableSharedFlow(extraBufferCapacity = Channel.UNLIMITED),
                    MutableSharedFlow(extraBufferCapacity = Channel.UNLIMITED)
                )
            }
        }
    }

    // Register a channel with a specific operation (interest and reaction)
    fun registerChannel(channel: SelectableChannel, operation: Operation) {
        val selectorThread = selectorThreads[nextSelectorIndex]
        nextSelectorIndex = (nextSelectorIndex + 1) % numSelectorThreads
        reactorScope.launch {
            selectorThread.registerChannel(channel, operation.interest, operation.action)
        }
    }

    // Start the reactor, launching event loops for each selector thread
    fun start() {
        selectorThreads.forEach { thread ->
            reactorScope.launch {
                thread.runEventLoop(isRunning)
            }
        }
    }

    // Shutdown the reactor, stopping all selector threads and cleaning up resources
    fun shutdown() {
        isRunning.value = false
        reactorScope.cancel()
        reactorScope.launch {
            selectorThreads.forEach { thread ->
                thread.selector.close()
            }
        }
    }

    // Check if the reactor is active
    val isActive: Boolean get() = isRunning.value
    
    // Property to hold the server channel if needed
    var serverChannel: ServerChannel? = null
}

// SelectorThread class for handling selector operations
private class SelectorThread(
    val selector: SelectorInterface,
    private val channelRegistrations: MutableSharedFlow<Triple<SelectableChannel, Int, () -> AsyncReaction?>>,
    private val reactions: MutableSharedFlow<Pair<SelectionKey, AsyncReaction>>
) {
    private val keyReactions = mutableMapOf<SelectionKey, () -> AsyncReaction?>()
    private val selectorEvents = Channel<Set<SelectionKey>>(capacity = Channel.UNLIMITED)

    suspend fun registerChannel(channel: SelectableChannel, interest: Int, reaction: () -> AsyncReaction?) {
        channelRegistrations.emit(Triple(channel, interest, reaction))
    }

    suspend fun runEventLoop(isRunning: MutableStateFlow<Boolean>) = coroutineScope {
        // Launch a separate coroutine to handle blocking selector.select() on placeholder IO dispatcher
        launch(PlaceholderIO) {
            while (isRunning.value && isActive) {
                try {
                    if (selector.select() > 0) {
                        val readyKeys = selector.selectedKeys()
                        selectorEvents.send(readyKeys)
                    }
                } catch (e: Exception) {
                    println("Error in selector loop: ${e.message}")
                    isRunning.value = false // Stop the loop on error
                }
            }
        }

        // Handle channel registrations
        launch {
            channelRegistrations.collect { (channel, interest, reaction) ->
                try {
                    val key = selector.register(channel, interest, null)
                    keyReactions[key] = reaction
                } catch (e: Exception) {
                    println("Error registering channel: ${e.message}")
                    launch { try { channel.close() } catch (_: Exception) {} }
                }
            }
        }

        // Handle selector events
        launch {
            selectorEvents.receiveAsFlow().collect { readyKeys ->
                readyKeys.forEach { key ->
                    if (key.isValid()) {
                        keyReactions[key]?.let { reaction ->
                            try {
                                reaction()?.let { asyncReaction ->
                                    reactions.emit(key to asyncReaction)
                                } ?: run {
                                    keyReactions.remove(key)
                                    key.cancel()
                                    try { key.channel().close() } catch (_: Exception) {}
                                }
                            } catch (e: Exception) {
                                println("Error during reaction for key $key: ${e.message}")
                                keyReactions.remove(key)
                                key.cancel()
                                try { key.channel().close() } catch (_: Exception) {}
                            }
                        } else {
                            keyReactions.remove(key)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Legacy Event-Driven Reactor for backward compatibility
 * This provides the simpler event-based API from the HEAD branch
 */
open class EventReactor<T>(
    private val name: String = "reactor",
    private val maxEvents: Int = 10000
) {
    private val eventQueue = mutableListOf<Event<T>>()
    private val handlers = mutableMapOf<EventType, MutableList<EventHandler<T>>>()
    private val reactors = mutableMapOf<String, EventReactor<*>>()
    private var isRunning = false
    private var eventCount = 0L
    
    /**
     * Start the reactor
     */
    suspend fun start() {
        if (isRunning) return
        
        isRunning = true
        println("Reactor '$name' started")
        
        while (isRunning) {
            try {
                processEvents()
            } catch (e: Exception) {
                println("Reactor '$name' error: ${e.message}")
            }
        }
    }
    
    /**
     * Stop the reactor
     */
    fun stop() {
        isRunning = false
        println("Reactor '$name' stopped")
    }
    
    /**
     * Emit an event
     */
    fun emit(event: Event<T>) {
        if (eventQueue.size < maxEvents) {
            eventQueue.add(event)
            eventCount++
        } else {
            println("Reactor '$name' event queue full, dropping event")
        }
    }
    
    /**
     * Emit event with current timestamp
     */
    fun emit(type: EventType, data: T) {
        emit(Event(type, data, kotlinx.datetime.Clock.System.now().toEpochMilliseconds()))
    }
    
    /**
     * Register event handler
     */
    fun on(type: EventType, handler: EventHandler<T>) {
        handlers.getOrPut(type) { mutableListOf() }.add(handler)
    }
    
    /**
     * Register multiple event handlers
     */
    fun on(types: Indexed<EventType>, handler: EventHandler<T>) {
        for (i in 0 until types.size) {
            on(types[i], handler)
        }
    }
    
    /**
     * Process all pending events
     */
    private suspend fun processEvents() {
        val eventsToProcess = eventQueue.toList()
        eventQueue.clear()
        
        for (event in eventsToProcess) {
            processEvent(event)
        }
    }
    
    /**
     * Process a single event
     */
    private suspend fun processEvent(event: Event<T>) {
        val eventHandlers = handlers[event.type] ?: return
        
        for (handler in eventHandlers) {
            try {
                handler(event)
            } catch (e: Exception) {
                println("Reactor '$name' handler error: ${e.message}")
            }
        }
    }
}

/**
 * Event types
 */
enum class EventType {
    DATA,
    CONTROL,
    ERROR,
    TIMEOUT,
    CONNECT,
    DISCONNECT,
    MESSAGE,
    REQUEST,
    RESPONSE,
    BROADCAST
}

/**
 * Event data structure
 */
data class Event<T>(
    val type: EventType,
    val data: T,
    val timestamp: Long
)

/**
 * Event handler interface
 */
typealias EventHandler<T> = suspend (Event<T>) -> Unit

/**
 * HTTP Reactor - Specialized reactor for HTTP events
 */
class HttpReactor : EventReactor<HttpEvent>("http-reactor") {
    
    init {
        // Register default handlers
        on(EventType.REQUEST) { event ->
            val httpEvent = event.data
            handleRequest(httpEvent)
        }
        
        on(EventType.RESPONSE) { event ->
            val httpEvent = event.data
            handleResponse(httpEvent)
        }
        
        on(EventType.ERROR) { event ->
            val httpEvent = event.data
            handleError(httpEvent)
        }
    }
    
    private suspend fun handleRequest(event: HttpEvent) {
        // Process HTTP request
        println("HTTP Reactor: Processing request ${event.method} ${event.path}")
    }
    
    private suspend fun handleResponse(event: HttpEvent) {
        // Process HTTP response
        println("HTTP Reactor: Processing response ${event.status}")
    }
    
    private suspend fun handleError(event: HttpEvent) {
        // Process HTTP error
        println("HTTP Reactor: Processing error ${event.error}")
    }
}

/**
 * HTTP event data
 */
data class HttpEvent(
    val method: String = "",
    val path: String = "",
    val status: Int = 0,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
    val error: String? = null
)

/**
 * QUIC Reactor - Specialized reactor for QUIC events
 */
class QuicReactor : EventReactor<QuicEvent>("quic-reactor") {
    
    init {
        // Register default handlers
        on(EventType.CONNECT) { event ->
            val quicEvent = event.data
            handleConnect(quicEvent)
        }
        
        on(EventType.DISCONNECT) { event ->
            val quicEvent = event.data
            handleDisconnect(quicEvent)
        }
        
        on(EventType.MESSAGE) { event ->
            val quicEvent = event.data
            handleMessage(quicEvent)
        }
    }
    
    private suspend fun handleConnect(event: QuicEvent) {
        // Process QUIC connection
        println("QUIC Reactor: New connection from ${event.connectionId}")
    }
    
    private suspend fun handleDisconnect(event: QuicEvent) {
        // Process QUIC disconnection
        println("QUIC Reactor: Connection closed ${event.connectionId}")
    }
    
    private suspend fun handleMessage(event: QuicEvent) {
        // Process QUIC message
        println("QUIC Reactor: Message on stream ${event.streamId}")
    }
}

/**
 * QUIC event data
 */
data class QuicEvent(
    val connectionId: String = "",
    val streamId: Long = 0,
    val data: Indexed<Byte> = emptyIndexed(),
    val error: String? = null
)

// === PROTOCOL CHANNEL INGRESS/EGRESS CONTEXT SELECTION CHORD SHEET ===

// Channel type selection chord - maps channel types to context selection functions
private val channelTypeSelectionChord: MetaSeries<SelectableChannel, () -> CoroutineContext> =
    object : SelectableChannel {} j { channel ->
        when (channel) {
            is borg.trikeshed.reactor.socks.SocksChannel -> { 
                { channel.createSocksContext() }
            }
            is borg.trikeshed.reactor.http.HttpChannel -> { 
                { channel.createHttpContext() }
            }
            is borg.trikeshed.reactor.quic.QuicChannel -> { 
                { channel.createQuicContext() }
            }
            is borg.trikeshed.reactor.ipc.IpcChannel -> { 
                { channel.createIpcContext() }
            }
            else -> { 
                { Dispatchers.IO }
            }
        }
    }

// Protocol routing chord - maps protocol types to routing functions
private val protocolRoutingChord: MetaSeries<EventType, (SelectionKey, CoroutineContext) -> AsyncReaction?> =
    EventType.DATA j { eventType ->
        when (eventType) {
            EventType.DATA -> { key, context ->
                // Route data events based on context
                when {
                    context.socksIngress != null -> createSocksIngressReaction(key, context)
                    context.socksEgress != null -> createSocksEgressReaction(key, context)
                    else -> createDefaultDataReaction(key, context)
                }
            }
            EventType.CONTROL -> { key, context ->
                createControlReaction(key, context)
            }
            EventType.ERROR -> { key, context ->
                createErrorReaction(key, context)
            }
            EventType.CONNECT -> { key, context ->
                createConnectReaction(key, context)
            }
            EventType.DISCONNECT -> { key, context ->
                createDisconnectReaction(key, context)
            }
            EventType.MESSAGE -> { key, context ->
                createMessageReaction(key, context)
            }
            EventType.REQUEST -> { key, context ->
                createRequestReaction(key, context)
            }
            EventType.RESPONSE -> { key, context ->
                createResponseReaction(key, context)
            }
            EventType.BROADCAST -> { key, context ->
                createBroadcastReaction(key, context)
            }
            EventType.TIMEOUT -> { key, context ->
                createTimeoutReaction(key, context)
            }
        }
    }

// Context composition chord - maps context elements to composition functions
private val contextCompositionChord: MetaSeries<Join<CoroutineContext, CoroutineContext>, () -> CoroutineContext> =
    (Dispatchers.IO j Dispatchers.Default) j { (base, additional) ->
        { base + additional }
    }

// Ingress channel selection chord - maps ingress types to channel selection functions
private val ingressChannelSelectionChord: MetaSeries<String, (CoroutineContext) -> borg.trikeshed.reactor.socks.SocksIngressChannel?> =
    "socks" j { protocol ->
        when (protocol) {
            "socks" -> { context -> context.socksIngress }
            "http" -> { context -> context.socksIngress } // HTTP can use SOCKS ingress
            "quic" -> { context -> context.socksIngress } // QUIC can use SOCKS ingress
            else -> { _ -> null }
        }
    }

// Egress channel selection chord - maps egress types to channel selection functions
private val egressChannelSelectionChord: MetaSeries<String, (CoroutineContext) -> borg.trikeshed.reactor.socks.SocksEgressChannel?> =
    "socks" j { protocol ->
        when (protocol) {
            "socks" -> { context -> context.socksEgress }
            "http" -> { context -> context.socksEgress } // HTTP can use SOCKS egress
            "quic" -> { context -> context.socksEgress } // QUIC can use SOCKS egress
            else -> { _ -> null }
        }
    }

// === REFACTORED CONTEXT SELECTION USING CHORD SHEET ===

private fun selectChannelContext(channel: SelectableChannel): CoroutineContext {
    return channelTypeSelectionChord.b(channel)()
}

private fun routeProtocolEvent(eventType: EventType, key: SelectionKey, context: CoroutineContext): AsyncReaction? {
    return protocolRoutingChord.b(eventType)(key, context)
}

private fun composeContexts(base: CoroutineContext, additional: CoroutineContext): CoroutineContext {
    return contextCompositionChord.b(base j additional)()
}

private fun selectIngressChannel(protocol: String, context: CoroutineContext): borg.trikeshed.reactor.socks.SocksIngressChannel? {
    return ingressChannelSelectionChord.b(protocol)(context)
}

private fun selectEgressChannel(protocol: String, context: CoroutineContext): borg.trikeshed.reactor.socks.SocksEgressChannel? {
    return egressChannelSelectionChord.b(protocol)(context)
}

// === REACTION CREATION FUNCTIONS ===

private fun createSocksIngressReaction(key: SelectionKey, context: CoroutineContext): AsyncReaction {
    return object : AsyncReaction {
        override suspend fun execute() {
            val ingress = context.socksIngress
            if (ingress != null) {
                val data = ingress.receive()
                // Process SOCKS ingress data
            }
        }
    }
}

private fun createSocksEgressReaction(key: SelectionKey, context: CoroutineContext): AsyncReaction {
    return object : AsyncReaction {
        override suspend fun execute() {
            val egress = context.socksEgress
            if (egress != null) {
                // Send data through SOCKS egress
            }
        }
    }
}

private fun createDefaultDataReaction(key: SelectionKey, context: CoroutineContext): AsyncReaction {
    return object : AsyncReaction {
        override suspend fun execute() {
            // Default data processing
        }
    }
}

private fun createControlReaction(key: SelectionKey, context: CoroutineContext): AsyncReaction {
    return object : AsyncReaction {
        override suspend fun execute() {
            // Control event processing
        }
    }
}

private fun createErrorReaction(key: SelectionKey, context: CoroutineContext): AsyncReaction {
    return object : AsyncReaction {
        override suspend fun execute() {
            // Error event processing
        }
    }
}

private fun createConnectReaction(key: SelectionKey, context: CoroutineContext): AsyncReaction {
    return object : AsyncReaction {
        override suspend fun execute() {
            // Connection event processing
        }
    }
}

private fun createDisconnectReaction(key: SelectionKey, context: CoroutineContext): AsyncReaction {
    return object : AsyncReaction {
        override suspend fun execute() {
            // Disconnection event processing
        }
    }
}

private fun createMessageReaction(key: SelectionKey, context: CoroutineContext): AsyncReaction {
    return object : AsyncReaction {
        override suspend fun execute() {
            // Message event processing
        }
    }
}

private fun createRequestReaction(key: SelectionKey, context: CoroutineContext): AsyncReaction {
    return object : AsyncReaction {
        override suspend fun execute() {
            // Request event processing
        }
    }
}

private fun createResponseReaction(key: SelectionKey, context: CoroutineContext): AsyncReaction {
    return object : AsyncReaction {
        override suspend fun execute() {
            // Response event processing
        }
    }
}

private fun createBroadcastReaction(key: SelectionKey, context: CoroutineContext): AsyncReaction {
    return object : AsyncReaction {
        override suspend fun execute() {
            // Broadcast event processing
        }
    }
}

private fun createTimeoutReaction(key: SelectionKey, context: CoroutineContext): AsyncReaction {
    return object : AsyncReaction {
        override suspend fun execute() {
            // Timeout event processing
        }
    }
}