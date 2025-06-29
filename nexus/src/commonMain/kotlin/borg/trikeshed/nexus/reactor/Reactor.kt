package borg.trikeshed.nexus.reactor

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

/**
 * Reactor: Attention Distribution Mechanism for Main()'s Pursuit of Happiness
 * 
 * Reclaimed from superbikeshed TrikeShed with full architectural artistry preserved.
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

    private lateinit var selectorThreads: MetaSeries<Int, SelectorThread>
    private var nextSelectorIndex = 0

    init {
        reactorScope.launch {
            selectorThreads = numSelectorThreads j { i ->
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
        val selectorThread = selectorThreads.b(nextSelectorIndex)
        nextSelectorIndex = (nextSelectorIndex + 1) % numSelectorThreads
        reactorScope.launch {
            selectorThread.registerChannel(channel, operation.interest, operation.action)
        }
    }

    // Start the reactor, launching event loops for each selector thread
    fun start() {
        for (i in 0 until selectorThreads.a) {
            val thread = selectorThreads.b(i)
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
            for (i in 0 until selectorThreads.a) {
                val thread = selectorThreads.b(i)
                thread.selector.close()
            }
        }
    }

    // Check if the reactor is active
    val isActive: Boolean get() = isRunning.value
    
    // Property to hold the server channel if needed
    var serverChannel: ServerChannel? = null
    
    // Implement Reactor interface methods
    fun register(channel: SelectableChannel, ops: Int): SelectionKey {
        // Implementation for registration with ops
        return SelectionKey(channel, ops)
    }
    
    fun stop() {
        shutdown()
    }
    
    val scope: CoroutineScope get() = reactorScope
}

// SelectorThread class for handling selector operations
private class SelectorThread(
    val selector: SelectorInterface,
    private val channelRegistrations: MutableSharedFlow<Join<SelectableChannel, Join<Int, () -> AsyncReaction?>>>,
    private val reactions: MutableSharedFlow<Join<SelectionKey, AsyncReaction>>
) {
    private val keyReactions = mutableMapOf<SelectionKey, () -> AsyncReaction?>()
    private val selectorEvents = Channel<MetaSeries<Int, SelectionKey>>(capacity = Channel.UNLIMITED)

    suspend fun registerChannel(channel: SelectableChannel, interest: Int, reaction: () -> AsyncReaction?) {
        channelRegistrations.emit(channel j (interest j reaction))
    }

    suspend fun runEventLoop(isRunning: MutableStateFlow<Boolean>) = coroutineScope {
        // Launch a separate coroutine to handle blocking selector.select() 
        launch(Dispatchers.IO) {
            while (isRunning.value) {
                try {
                    if (selector.select() > 0) {
                        val readyKeysSet = selector.selectedKeys()
                        val readyKeys: MetaSeries<Int, SelectionKey> = readyKeysSet.size j { i ->
                            readyKeysSet.elementAt(i)
                        }
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
            channelRegistrations.collect { registration ->
                val channel = registration.a
                val interestAndReaction = registration.b
                val interest = interestAndReaction.a
                val reaction = interestAndReaction.b
                
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
                for (i in 0 until readyKeys.a) {
                    val key = readyKeys.b(i)
                    if (key.isValid()) {
                        keyReactions[key]?.let { reaction ->
                            try {
                                reaction()?.let { asyncReaction ->
                                    reactions.emit(key j asyncReaction)
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
                        } ?: run {
                            keyReactions.remove(key)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reactor scope interface
 */
interface ReactorScope {
    val remoteAddress: String
    val reactorScope: CoroutineScope
}

/**
 * Operation data class for reactor registration
 */
data class Operation(
    val interest: Int,
    val action: () -> AsyncReaction?
)