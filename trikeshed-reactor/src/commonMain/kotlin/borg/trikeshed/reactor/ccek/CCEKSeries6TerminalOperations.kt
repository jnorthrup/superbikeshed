@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor.ccek

import kotlinx.coroutines.*
import kotlinx.datetime.*
import borg.trikeshed.reactor.*
import borg.trikeshed.lib.*

/**
 * CCEK Series 6: Terminal Operations (Tailcalls)
 * 
 * The endgame - terminal operations that complete the channelization
 * with tail-call optimization, bringing everything full circle.
 * 
 * What makes a series a cluster: Each series represents a layer in the
 * subsumption hierarchy. When composed together, they form a cluster -
 * a complete channelization stack from raw protocols to optimized terminals.
 */

// Terminal operation layers
class TailcallOptimizationLayer(
    internal val underlying: CCEKChannelization
) : TerminalLayer {
    
    override suspend fun <T> dispatch(operation: ChannelOperation<T>): T {
        return when (operation) {
            is FillBufferTailcall -> handleFillBuffer(operation) as T
            is RecursiveTailcall<*> -> handleRecursive(operation) as T
            is TrampolinedOperation<*> -> handleTrampoline(operation) as T
            is StreamTerminalOperation<*> -> handleStreamTerminal(operation) as T
            else -> underlying.dispatch(operation)
        }
    }
    
    internal suspend fun handleFillBuffer(operation: FillBufferTailcall): FillBufferResult {
        // Tail-recursive buffer filling from relaxfactory
        return fillBufferTailcallImpl(
            source = operation.source,
            buffer = operation.buffer,
            offset = operation.offset,
            length = operation.length,
            filled = 0
        )
    }
    
    internal tailrec suspend fun fillBufferTailcallImpl(
        source: suspend (ByteArray, Int, Int) -> Int,
        buffer: ByteArray,
        offset: Int,
        length: Int,
        filled: Int
    ): FillBufferResult {
        if (filled >= length) {
            return FillBufferResult(
                bytesFilled = filled,
                completed = true,
                timestamp = Clock.System.now()
            )
        }
        
        val bytesRead = source(buffer, offset + filled, length - filled)
        
        if (bytesRead <= 0) {
            return FillBufferResult(
                bytesFilled = filled,
                completed = false,
                timestamp = Clock.System.now()
            )
        }
        
        return fillBufferTailcallImpl(source, buffer, offset, length, filled + bytesRead)
    }
    
    internal suspend fun <T> handleRecursive(operation: RecursiveTailcall<T>): T {
        return executeTrampoline(Bounce(operation.computation))
    }
    
    internal suspend fun <T> handleTrampoline(operation: TrampolinedOperation<T>): T {
        return executeTrampoline(operation.trampoline)
    }
    
    internal tailrec suspend fun <T> executeTrampoline(trampoline: Trampoline<T>): T {
        return when (trampoline) {
            is Done -> trampoline.value
            is Bounce -> executeTrampoline(trampoline.computation())
        }
    }
    
    internal suspend fun <T> handleStreamTerminal(operation: StreamTerminalOperation<T>): T {
        return when (operation.terminalOp) {
            is CollectTerminal -> operation.stream.collect { /* process */ } as T
            is FirstTerminal -> operation.stream.first() as T
            is LastTerminal -> operation.stream.last() as T
            is CountTerminal -> operation.stream.count() as T
            is ToListTerminal -> operation.stream.toList() as T
        }
    }
    
    companion object : CoroutineContext.Key<TailcallOptimizationLayer>
}

class CompletionLayer(
    internal val metrics: MetricsCollector = InMemoryMetricsCollector()
) : TerminalLayer {
    
    override suspend fun <T> dispatch(operation: ChannelOperation<T>): T {
        val startTime = Clock.System.now()
        val context = currentCoroutineContext()
        
        try {
            // This is the terminal - we complete the operation
            val result = completeOperation(operation, context)
            
            // Record success metrics
            metrics.recordSuccess(
                operation = operation::class.simpleName ?: "Unknown",
                duration = Clock.System.now() - startTime,
                context = context
            )
            
            return result
        } catch (e: Exception) {
            // Record failure metrics
            metrics.recordFailure(
                operation = operation::class.simpleName ?: "Unknown",
                error = e,
                duration = Clock.System.now() - startTime,
                context = context
            )
            throw e
        }
    }
    
    internal suspend fun <T> completeOperation(
        operation: ChannelOperation<T>,
        context: CoroutineContext
    ): T {
        // Terminal completion - this is where the rubber meets the road
        return when (operation) {
            is TerminalRead -> performTerminalRead(operation) as T
            is TerminalWrite -> performTerminalWrite(operation) as T
            is TerminalCompute -> performTerminalCompute(operation) as T
            else -> throw UnsupportedOperationException("No terminal for $operation")
        }
    }
    
    internal suspend fun performTerminalRead(operation: TerminalRead): ReadCompleted {
        // Simulate actual I/O completion
        delay(10)
        return ReadCompleted(
            data = ByteArray(operation.size) { it.toByte() },
            bytesRead = operation.size,
            timestamp = Clock.System.now()
        )
    }
    
    internal suspend fun performTerminalWrite(operation: TerminalWrite): WriteCompleted {
        // Simulate actual I/O completion
        delay(5)
        return WriteCompleted(
            bytesWritten = operation.data.size,
            timestamp = Clock.System.now()
        )
    }
    
    internal suspend fun performTerminalCompute(operation: TerminalCompute): ComputeCompleted {
        // Execute the computation
        val result = operation.computation()
        return ComputeCompleted(
            result = result,
            computeTime = 1.milliseconds,
            timestamp = Clock.System.now()
        )
    }
    
    companion object : CoroutineContext.Key<CompletionLayer>
}

// Trampoline for tail recursion
sealed class Trampoline<out T>
data class Done<T>(val value: T) : Trampoline<T>()
data class Bounce<T>(val computation: suspend () -> Trampoline<T>) : Trampoline<T>()

// Terminal operations
data class FillBufferTailcall(
    val source: suspend (ByteArray, Int, Int) -> Int,
    val buffer: ByteArray,
    val offset: Int = 0,
    val length: Int = buffer.size
) : ChannelOperation<FillBufferResult>

data class FillBufferResult(
    val bytesFilled: Int,
    val completed: Boolean,
    val timestamp: Instant
)

data class RecursiveTailcall<T>(
    val computation: suspend () -> Trampoline<T>
) : ChannelOperation<T>

data class TrampolinedOperation<T>(
    val trampoline: Trampoline<T>
) : ChannelOperation<T>

data class StreamTerminalOperation<T>(
    val stream: kotlinx.coroutines.flow.Flow<*>,
    val terminalOp: TerminalOp
) : ChannelOperation<T>

// Stream terminal operations
sealed class TerminalOp
object CollectTerminal : TerminalOp()
object FirstTerminal : TerminalOp()
object LastTerminal : TerminalOp()
object CountTerminal : TerminalOp()
object ToListTerminal : TerminalOp()

// Final terminal operations
data class TerminalRead(
    val size: Int
) : ChannelOperation<ReadCompleted>

data class TerminalWrite(
    val data: ByteArray
) : ChannelOperation<WriteCompleted>

data class TerminalCompute(
    val computation: suspend () -> Any
) : ChannelOperation<ComputeCompleted>

// Completion results
data class ReadCompleted(
    val data: ByteArray,
    val bytesRead: Int,
    val timestamp: Instant
)

data class WriteCompleted(
    val bytesWritten: Int,
    val timestamp: Instant
)

data class ComputeCompleted(
    val result: Any,
    val computeTime: Duration,
    val timestamp: Instant
)

// Metrics collection
interface MetricsCollector {
    suspend fun recordSuccess(
        operation: String,
        duration: Duration,
        context: CoroutineContext
    )
    
    suspend fun recordFailure(
        operation: String,
        error: Exception,
        duration: Duration,
        context: CoroutineContext
    )
    
    suspend fun getMetrics(): ChannelMetrics
}

class InMemoryMetricsCollector : MetricsCollector {
    internal val successCounts = mutableMapOf<String, Long>()
    internal val failureCounts = mutableMapOf<String, Long>()
    internal val totalDurations = mutableMapOf<String, Duration>()
    
    override suspend fun recordSuccess(
        operation: String,
        duration: Duration,
        context: CoroutineContext
    ) {
        successCounts[operation] = (successCounts[operation] ?: 0) + 1
        totalDurations[operation] = (totalDurations[operation] ?: Duration.ZERO) + duration
    }
    
    override suspend fun recordFailure(
        operation: String,
        error: Exception,
        duration: Duration,
        context: CoroutineContext
    ) {
        failureCounts[operation] = (failureCounts[operation] ?: 0) + 1
        totalDurations[operation] = (totalDurations[operation] ?: Duration.ZERO) + duration
    }
    
    override suspend fun getMetrics(): ChannelMetrics {
        return ChannelMetrics(
            successCounts = successCounts.toMap(),
            failureCounts = failureCounts.toMap(),
            averageDurations = totalDurations.mapValues { (op, total) ->
                val count = (successCounts[op] ?: 0) + (failureCounts[op] ?: 0)
                if (count > 0) total / count else Duration.ZERO
            },
            timestamp = Clock.System.now()
        )
    }
}

data class ChannelMetrics(
    val successCounts: Map<String, Long>,
    val failureCounts: Map<String, Long>,
    val averageDurations: Map<String, Duration>,
    val timestamp: Instant
)

// Extension for creating terminal operations
fun <T> kotlinx.coroutines.flow.Flow<T>.terminal(op: TerminalOp): StreamTerminalOperation<*> =
    StreamTerminalOperation(this, op)

// Helper for creating tailcall operations
suspend fun <T> tailcall(computation: suspend () -> T): T {
    var current: Any? = computation
    while (current is Function0<*>) {
        current = (current as suspend () -> Any?)()
    }
    @Suppress("UNCHECKED_CAST")
    return current as T
}

/**
 * Complete CCEK Cluster Assembly
 * 
 * This brings all 6 series together into a complete channelization cluster:
 * 
 * Series 0: Protocol Adapters (TCP, UDP)
 * Series 1: Protocol Stacks (HTTP, WebSocket)
 * Series 2: Service Integration (Load Balancing, Circuit Breaking)
 * Series 3: Distributed Coordination (Consensus, Locking)
 * Series 4: Reactive Streams (Backpressure, Flow Control)
 * Series 5: Kernel Integration (io_uring, eBPF)
 * Series 6: Terminal Operations (Tailcalls, Completion)
 * 
 * Together they form a complete cluster for channelized I/O.
 */
fun createCompleteChannelizationCluster(): CCEKChannelization {
    // Start with terminal completion
    val completion = CompletionLayer()
    
    // Add tail-call optimization
    val tailcalls = TailcallOptimizationLayer(completion)
    
    // Add kernel integration
    val kernel = IoUringLayer(tailcalls)
    
    // Add reactive streams
    val reactive = BackpressureLayer(kernel)
    
    // Add distributed coordination
    val distributed = ConsensusLayer(listOf(reactive))
    
    // Add service integration
    val services = LoadBalancingService(listOf(distributed))
    
    // Add protocol stacks
    val tcp = TcpAdapter()
    val http = HttpOverTcpStack(tcp)
    
    // Return the complete cluster
    return services
}