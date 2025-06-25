package borg.trikeshed.reactor

import borg.trikeshed.lib.*

// Platform-agnostic time function
expect fun getCurrentTimeMillis(): Long

/**
 * Reactor Pattern Implementation
 * 
 * Event-driven architecture using the unary operator j for composition
 */
open class Reactor<T>(
    private val name: String = "reactor",
    private val maxEvents: Int = 10000
) {
    private val eventQueue = mutableListOf<Event<T>>()
    private val handlers = mutableMapOf<EventType, MutableList<EventHandler<T>>>()
    private val reactors = mutableMapOf<String, Reactor<*>>()
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
        emit(Event(type, data, getCurrentTimeMillis()))
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
        types.a j { i -> on(types.b(i), handler) }
    }
    
    /**
     * Add child reactor
     */
    fun addReactor(name: String, reactor: Reactor<*>) {
        reactors[name] = reactor
    }
    
    /**
     * Remove child reactor
     */
    fun removeReactor(name: String) {
        reactors.remove(name)
    }
    
    /**
     * Get reactor statistics
     */
    fun getStats(): ReactorStats {
        return ReactorStats(
            name = name,
            isRunning = isRunning,
            eventQueueSize = eventQueue.size,
            totalEventsProcessed = eventCount,
            handlerCount = handlers.values.sumOf { it.size },
            childReactorCount = reactors.size
        )
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
 * Reactor statistics
 */
data class ReactorStats(
    val name: String,
    val isRunning: Boolean,
    val eventQueueSize: Int,
    val totalEventsProcessed: Long,
    val handlerCount: Int,
    val childReactorCount: Int
)

/**
 * Reactor Network - Manages multiple reactors
 */
class ReactorNetwork {
    private val reactors = mutableMapOf<String, Reactor<*>>()
    private val connections = mutableMapOf<String, MutableList<String>>()
    
    /**
     * Add reactor to network
     */
    fun addReactor(name: String, reactor: Reactor<*>) {
        reactors[name] = reactor
        connections[name] = mutableListOf()
    }
    
    /**
     * Connect two reactors
     */
    fun connect(from: String, to: String) {
        connections.getOrPut(from) { mutableListOf() }.add(to)
    }
    
    /**
     * Disconnect reactors
     */
    fun disconnect(from: String, to: String) {
        connections[from]?.remove(to)
    }
    
    /**
     * Start all reactors
     */
    suspend fun start() {
        reactors.values.forEach { reactor ->
            // Start each reactor in its own coroutine
            reactor.start()
        }
    }
    
    /**
     * Stop all reactors
     */
    fun stop() {
        reactors.values.forEach { it.stop() }
    }
    
    /**
     * Get network statistics
     */
    fun getStats(): NetworkStats {
        return NetworkStats(
            reactorCount = reactors.size,
            connectionCount = connections.values.sumOf { it.size },
            reactors = reactors.keys.toList()
        )
    }
    
    /**
     * Check if network is active
     */
    fun isActive(): Boolean {
        return reactors.values.any { it.getStats().isRunning }
    }
    
    /**
     * Emit event to specific reactor
     */
    fun emit(reactorName: String, event: Any) {
        val reactor = reactors[reactorName]
        if (reactor != null) {
            // Cast to appropriate type and emit
            when (reactor) {
                is Reactor<*> -> {
                    // This is a simplified emit - in a real implementation you'd need proper type handling
                    println("Emitting event to reactor $reactorName")
                }
            }
        }
    }
}

/**
 * Network statistics
 */
data class NetworkStats(
    val reactorCount: Int,
    val connectionCount: Int,
    val reactors: List<String>
)

/**
 * HTTP Reactor - Specialized reactor for HTTP events
 */
class HttpReactor : Reactor<HttpEvent>("http-reactor") {
    
    init {
        // Register default handlers
        on(EventType.REQUEST) { event ->
            val httpEvent = event.data as HttpEvent
            handleRequest(httpEvent)
        }
        
        on(EventType.RESPONSE) { event ->
            val httpEvent = event.data as HttpEvent
            handleResponse(httpEvent)
        }
        
        on(EventType.ERROR) { event ->
            val httpEvent = event.data as HttpEvent
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
class QuicReactor : Reactor<QuicEvent>("quic-reactor") {
    
    init {
        // Register default handlers
        on(EventType.CONNECT) { event ->
            val quicEvent = event.data as QuicEvent
            handleConnect(quicEvent)
        }
        
        on(EventType.DISCONNECT) { event ->
            val quicEvent = event.data as QuicEvent
            handleDisconnect(quicEvent)
        }
        
        on(EventType.MESSAGE) { event ->
            val quicEvent = event.data as QuicEvent
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
    val data: Indexed<Byte> = 0 j { 0.toByte() },
    val error: String? = null
)

/**
 * Database Reactor - Specialized reactor for database events
 */
class DatabaseReactor : Reactor<DatabaseEvent>("database-reactor") {
    
    init {
        // Register default handlers
        on(EventType.DATA) { event ->
            val dbEvent = event.data as DatabaseEvent
            handleData(dbEvent)
        }
        
        on(EventType.ERROR) { event ->
            val dbEvent = event.data as DatabaseEvent
            handleError(dbEvent)
        }
    }
    
    private suspend fun handleData(event: DatabaseEvent) {
        // Process database operation
        println("Database Reactor: ${event.operation} on ${event.collection}")
    }
    
    private suspend fun handleError(event: DatabaseEvent) {
        // Process database error
        println("Database Reactor: Error ${event.error}")
    }
}

/**
 * Database event data
 */
data class DatabaseEvent(
    val operation: String = "",
    val collection: String = "",
    val document: String = "",
    val data: Map<String, Any> = emptyMap(),
    val error: String? = null
)

/**
 * Reactor Builder - DSL for building reactors
 */
class ReactorBuilder<T>(private val name: String) {
    private val handlers = mutableMapOf<EventType, MutableList<EventHandler<T>>>()
    private val childReactors = mutableMapOf<String, Reactor<*>>()
    
    fun on(type: EventType, handler: EventHandler<T>) {
        handlers.getOrPut(type) { mutableListOf() }.add(handler)
    }
    
    fun reactor(name: String, block: ReactorBuilder<*>.() -> Unit): Reactor<*> {
        val builder = ReactorBuilder<Any>(name)
        block(builder)
        return builder.build()
    }
    
    fun build(): Reactor<T> {
        val reactor = Reactor<T>(name)
        
        // Register all handlers
        for ((type, handlerList) in handlers) {
            for (handler in handlerList) {
                reactor.on(type, handler)
            }
        }
        
        // Add child reactors
        for ((name, childReactor) in childReactors) {
            reactor.addReactor(name, childReactor)
        }
        
        return reactor
    }
}

/**
 * Reactor DSL function
 */
fun <T> reactor(name: String, block: ReactorBuilder<T>.() -> Unit): Reactor<T> {
    val builder = ReactorBuilder<T>(name)
    block(builder)
    return builder.build()
} 