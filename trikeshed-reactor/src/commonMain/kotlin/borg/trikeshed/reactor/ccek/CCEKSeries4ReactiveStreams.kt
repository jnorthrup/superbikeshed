@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor.ccek

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.*
import kotlinx.datetime.*
import borg.trikeshed.reactor.*
import borg.trikeshed.lib.*

/**
 * CCEK Series 4: Reactive Streams
 * 
 * Implements reactive patterns with backpressure, flow control,
 * and stream processing capabilities.
 */

// Reactive stream layers
class BackpressureLayer(
    internal val underlying: CCEKChannelization,
    internal val bufferSize: Int = 64,
    internal val strategy: BackpressureStrategy = BackpressureStrategy.BUFFER
) : ReactiveLayer {
    
    internal val buffer = Channel<ChannelOperation<*>>(
        capacity = when (strategy) {
            BackpressureStrategy.BUFFER -> bufferSize
            BackpressureStrategy.DROP_OLDEST -> Channel.CONFLATED
            BackpressureStrategy.DROP_LATEST -> Channel.RENDEZVOUS
            BackpressureStrategy.ERROR -> Channel.RENDEZVOUS
        }
    )
    
    init {
        // Start processing buffer
        GlobalScope.launch {
            for (operation in buffer) {
                processBuffered(operation)
            }
        }
    }
    
    override suspend fun <T> dispatch(operation: ChannelOperation<T>): T {
        return when (operation) {
            is StreamOperation -> handleStream(operation) as T
            else -> {
                when (strategy) {
                    BackpressureStrategy.BUFFER -> {
                        buffer.send(operation)
                        awaitResult(operation)
                    }
                    BackpressureStrategy.DROP_OLDEST -> {
                        buffer.trySend(operation)
                        awaitResult(operation)
                    }
                    BackpressureStrategy.DROP_LATEST -> {
                        if (!buffer.trySend(operation).isSuccess) {
                            throw BackpressureException("Buffer full, dropping operation")
                        }
                        awaitResult(operation)
                    }
                    BackpressureStrategy.ERROR -> {
                        if (!buffer.trySend(operation).isSuccess) {
                            throw BackpressureException("Buffer full")
                        }
                        awaitResult(operation)
                    }
                }
            }
        }
    }
    
    internal suspend fun <T> handleStream(operation: StreamOperation<T>): Flow<T> {
        return operation.source
            .buffer(bufferSize)
            .map { item ->
                underlying.dispatch(TransformOperation(item, operation.transform))
            }
    }
    
    internal suspend fun processBuffered(operation: ChannelOperation<*>) {
        try {
            underlying.dispatch(operation)
        } catch (e: Exception) {
            // Log error
        }
    }
    
    internal suspend fun <T> awaitResult(operation: ChannelOperation<T>): T {
        // In real implementation, would track and return results
        @Suppress("UNCHECKED_CAST")
        return Unit as T
    }
    
    companion object : CoroutineContext.Key<BackpressureLayer>
}

class StreamProcessingLayer(
    internal val underlying: CCEKChannelization
) : ReactiveLayer {
    
    override suspend fun <T> dispatch(operation: ChannelOperation<T>): T {
        return when (operation) {
            is MapOperation<*, *> -> handleMap(operation) as T
            is FilterOperation<*> -> handleFilter(operation) as T
            is ReduceOperation<*, *> -> handleReduce(operation) as T
            is WindowOperation<*> -> handleWindow(operation) as T
            is MergeOperation<*> -> handleMerge(operation) as T
            else -> underlying.dispatch(operation)
        }
    }
    
    internal suspend fun <S, T> handleMap(operation: MapOperation<S, T>): Flow<T> {
        return operation.source.map { item ->
            operation.transform(item)
        }
    }
    
    internal suspend fun <T> handleFilter(operation: FilterOperation<T>): Flow<T> {
        return operation.source.filter { item ->
            operation.predicate(item)
        }
    }
    
    internal suspend fun <T, R> handleReduce(operation: ReduceOperation<T, R>): R {
        return operation.source.fold(operation.initial) { acc, item ->
            operation.accumulator(acc, item)
        }
    }
    
    internal suspend fun <T> handleWindow(operation: WindowOperation<T>): Flow<List<T>> {
        return operation.source
            .windowed(operation.size, operation.step, operation.partialWindows)
            .asFlow()
    }
    
    internal suspend fun <T> handleMerge(operation: MergeOperation<T>): Flow<T> {
        return merge(*operation.sources.toTypedArray())
    }
    
    companion object : CoroutineContext.Key<StreamProcessingLayer>
}

class ReactiveEventBus(
    internal val underlying: CCEKChannelization
) : ReactiveLayer {
    
    internal val eventFlows = mutableMapOf<String, MutableSharedFlow<Event>>()
    internal val subscribers = mutableMapOf<String, MutableList<EventSubscriber>>()
    
    override suspend fun <T> dispatch(operation: ChannelOperation<T>): T {
        return when (operation) {
            is PublishEvent -> handlePublish(operation) as T
            is SubscribeToEvents -> handleSubscribe(operation) as T
            is UnsubscribeFromEvents -> handleUnsubscribe(operation) as T
            else -> underlying.dispatch(operation)
        }
    }
    
    internal suspend fun handlePublish(operation: PublishEvent): PublishResult {
        val timestamp = Clock.System.now()
        val event = Event(
            id = generateEventId(),
            topic = operation.topic,
            payload = operation.payload,
            timestamp = timestamp,
            metadata = operation.metadata
        )
        
        // Get or create flow for topic
        val flow = eventFlows.getOrPut(operation.topic) {
            MutableSharedFlow(
                replay = 10,
                extraBufferCapacity = 100,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        }
        
        flow.emit(event)
        
        // Notify subscribers
        subscribers[operation.topic]?.forEach { subscriber ->
            try {
                subscriber.onEvent(event)
            } catch (e: Exception) {
                // Handle subscriber error
            }
        }
        
        return PublishResult(
            eventId = event.id,
            timestamp = timestamp,
            subscriberCount = subscribers[operation.topic]?.size ?: 0
        )
    }
    
    internal suspend fun handleSubscribe(operation: SubscribeToEvents): Flow<Event> {
        val flow = eventFlows.getOrPut(operation.topic) {
            MutableSharedFlow(
                replay = 10,
                extraBufferCapacity = 100,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        }
        
        operation.subscriber?.let { subscriber ->
            subscribers.getOrPut(operation.topic) { mutableListOf() }.add(subscriber)
        }
        
        return flow.asSharedFlow()
    }
    
    internal suspend fun handleUnsubscribe(operation: UnsubscribeFromEvents): UnsubscribeResult {
        val removed = operation.subscriber?.let { subscriber ->
            subscribers[operation.topic]?.remove(subscriber) ?: false
        } ?: false
        
        return UnsubscribeResult(removed)
    }
    
    internal fun generateEventId(): String = 
        "${Clock.System.now().toEpochMilliseconds()}_${(0..999999).random()}"
    
    companion object : CoroutineContext.Key<ReactiveEventBus>
}

class FlowControlLayer(
    internal val underlying: CCEKChannelization,
    internal val rateLimiter: RateLimiter = TokenBucketRateLimiter()
) : ReactiveLayer {
    
    override suspend fun <T> dispatch(operation: ChannelOperation<T>): T {
        // Apply rate limiting
        rateLimiter.acquire()
        
        return when (operation) {
            is ThrottledOperation<*> -> handleThrottled(operation) as T
            is DebounceOperation<*> -> handleDebounce(operation) as T
            is SampleOperation<*> -> handleSample(operation) as T
            else -> underlying.dispatch(operation)
        }
    }
    
    internal suspend fun <T> handleThrottled(operation: ThrottledOperation<T>): Flow<T> {
        return operation.source
            .throttleLatest(operation.periodMillis)
    }
    
    internal suspend fun <T> handleDebounce(operation: DebounceOperation<T>): Flow<T> {
        return operation.source
            .debounce(operation.timeoutMillis)
    }
    
    internal suspend fun <T> handleSample(operation: SampleOperation<T>): Flow<T> {
        return operation.source
            .sample(operation.periodMillis)
    }
    
    companion object : CoroutineContext.Key<FlowControlLayer>
}

// Backpressure strategies
enum class BackpressureStrategy {
    BUFFER,      // Buffer until capacity
    DROP_OLDEST, // Drop oldest when full
    DROP_LATEST, // Drop newest when full
    ERROR        // Error when full
}

// Stream operations
data class StreamOperation<T>(
    val source: Flow<T>,
    val transform: suspend (T) -> T
) : ChannelOperation<Flow<T>>

data class MapOperation<S, T>(
    val source: Flow<S>,
    val transform: suspend (S) -> T
) : ChannelOperation<Flow<T>>

data class FilterOperation<T>(
    val source: Flow<T>,
    val predicate: suspend (T) -> Boolean
) : ChannelOperation<Flow<T>>

data class ReduceOperation<T, R>(
    val source: Flow<T>,
    val initial: R,
    val accumulator: suspend (R, T) -> R
) : ChannelOperation<R>

data class WindowOperation<T>(
    val source: Flow<T>,
    val size: Int,
    val step: Int = size,
    val partialWindows: Boolean = false
) : ChannelOperation<Flow<List<T>>>

data class MergeOperation<T>(
    val sources: List<Flow<T>>
) : ChannelOperation<Flow<T>>

data class TransformOperation<S, T>(
    val item: S,
    val transform: suspend (S) -> T
) : ChannelOperation<T>

// Event bus operations
data class Event(
    val id: String,
    val topic: String,
    val payload: Any,
    val timestamp: Instant,
    val metadata: Map<String, Any> = emptyMap()
)

interface EventSubscriber {
    suspend fun onEvent(event: Event)
}

data class PublishEvent(
    val topic: String,
    val payload: Any,
    val metadata: Map<String, Any> = emptyMap()
) : ChannelOperation<PublishResult>

data class PublishResult(
    val eventId: String,
    val timestamp: Instant,
    val subscriberCount: Int
)

data class SubscribeToEvents(
    val topic: String,
    val subscriber: EventSubscriber? = null
) : ChannelOperation<Flow<Event>>

data class UnsubscribeFromEvents(
    val topic: String,
    val subscriber: EventSubscriber
) : ChannelOperation<UnsubscribeResult>

data class UnsubscribeResult(
    val removed: Boolean
)

// Flow control operations
data class ThrottledOperation<T>(
    val source: Flow<T>,
    val periodMillis: Long
) : ChannelOperation<Flow<T>>

data class DebounceOperation<T>(
    val source: Flow<T>,
    val timeoutMillis: Long
) : ChannelOperation<Flow<T>>

data class SampleOperation<T>(
    val source: Flow<T>,
    val periodMillis: Long
) : ChannelOperation<Flow<T>>

// Rate limiting
interface RateLimiter {
    suspend fun acquire()
    fun tryAcquire(): Boolean
}

class TokenBucketRateLimiter(
    internal val capacity: Int = 100,
    internal val refillRate: Int = 10, // tokens per second
) : RateLimiter {
    
    internal var tokens = capacity
    internal var lastRefill = Clock.System.now()
    
    override suspend fun acquire() {
        while (!tryAcquire()) {
            delay(100)
        }
    }
    
    override fun tryAcquire(): Boolean {
        refill()
        
        return if (tokens > 0) {
            tokens--
            true
        } else {
            false
        }
    }
    
    internal fun refill() {
        val now = Clock.System.now()
        val elapsed = now - lastRefill
        val tokensToAdd = (elapsed.inWholeMilliseconds * refillRate / 1000).toInt()
        
        if (tokensToAdd > 0) {
            tokens = minOf(capacity, tokens + tokensToAdd)
            lastRefill = now
        }
    }
}

// Exceptions
class BackpressureException(message: String) : Exception(message)

// Extension functions for Flow operations
fun <T> Flow<T>.windowed(size: Int, step: Int, partialWindows: Boolean): Flow<List<T>> = flow {
    val buffer = mutableListOf<T>()
    var count = 0
    
    collect { value ->
        buffer.add(value)
        count++
        
        if (count == size) {
            emit(buffer.toList())
            repeat(step) {
                if (buffer.isNotEmpty()) buffer.removeAt(0)
            }
            count = buffer.size
        }
    }
    
    if (partialWindows && buffer.isNotEmpty()) {
        emit(buffer.toList())
    }
}