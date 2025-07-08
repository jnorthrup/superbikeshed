@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.cursor

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.CoroutineContext
import kotlinx.datetime.Clock

/**
 * Cursor-Channel Integration Layer
 * 
 * Normalizes cursor operations with TrikeShed's reactor patterns and channelization.
 * Provides streaming, batching, and reactive processing capabilities for cursors.
 */

/**
 * Channel-based cursor streaming with backpressure
 */
fun Cursor.asFlow(
    bufferSize: Int = Channel.BUFFERED,
    context: CoroutineContext = Dispatchers.Default
): Flow<RowVec> = flow {
    for (i in 0 until size) {
        emit(at(i))
    }
}.buffer(bufferSize).flowOn(context)

/**
 * Stream cursor rows to a channel
 */
suspend fun Cursor.streamTo(
    channel: SendChannel<RowVec>,
    batchSize: Int = 1000
) {
    var sent = 0
    for (i in 0 until size) {
        channel.send(at(i))
        sent++
        
        // Yield control periodically for cooperative cancellation
        if (sent % batchSize == 0) {
            yield()
        }
    }
}

/**
 * Create cursor from channel of rows
 */
suspend fun fromChannel(
    channel: ReceiveChannel<RowVec>,
    maxRows: Int = Int.MAX_VALUE
): Cursor {
    val rows = mutableListOf<RowVec>()
    var count = 0
    
    for (row in channel) {
        rows.add(row)
        count++
        if (count >= maxRows) break
    }
    
    return Cursor(CursorRowIndex(rows.size) j { i: CursorRowIndex -> rows[i.value] })
}

/**
 * Parallel cursor processing with channels
 */
fun <T> Cursor.mapParallel(
    parallelism: Int = 4,
    bufferSize: Int = Channel.BUFFERED,
    transform: suspend (RowVec) -> T
): Flow<T> = channelFlow {
    val semaphore = Semaphore(parallelism)
    asFlow(bufferSize).collect { row ->
        launch {
            semaphore.withPermit {
                val result = transform(row)
                send(result)
            }
        }
    }
}

/**
 * Cursor windowing for streaming operations
 */
fun Cursor.windowed(
    windowSize: Int,
    step: Int = windowSize
): Flow<Cursor> = flow {
    var start = 0
    while (start < size) {
        val end = minOf(start + windowSize, size)
        val window = at(start until end)
        emit(window)
        start += step
    }
}

/**
 * Channel-based cursor merging
 */
suspend fun mergeCursors(
    cursors: Array<Cursor>,
    bufferSize: Int = Channel.BUFFERED
): Cursor = coroutineScope {
    val channel = Channel<RowVec>(bufferSize)
    
    // Launch producers for each cursor
    cursors.forEach { cursor ->
        launch {
            cursor.streamTo(channel)
        }
    }
    
    // Wait for all producers and close channel
    launch {
        cursors.forEach { cursor ->
            cursor.asFlow().collect { /* consume to completion */ }
        }
        channel.close()
    }
    
    fromChannel(channel)
}

/**
 * Reactive cursor operations
 */
class CursorReactor(
    internal val scope: CoroutineScope = GlobalScope
) {
    internal val _events = MutableSharedFlow<CursorEvent>()
    val events: SharedFlow<CursorEvent> = _events.asSharedFlow()
    
    /**
     * Process cursor with event emission
     */
    suspend fun processCursor(
        cursor: Cursor,
        processor: suspend (RowVec) -> Unit
    ) {
        _events.emit(CursorEvent.ProcessingStarted(cursor.size))
        
        var processed = 0
        cursor.asFlow().collect { row ->
            processor(row)
            processed++
            
            if (processed % 1000 == 0) {
                _events.emit(CursorEvent.ProgressUpdate(processed, cursor.size))
            }
        }
        
        _events.emit(CursorEvent.ProcessingCompleted(processed))
    }
    
    /**
     * Monitor cursor operations
     */
    fun monitorCursor(cursor: Cursor): Flow<CursorMetrics> = flow {
        val startTime = Clock.System.now().toEpochMilliseconds()
        var rowsProcessed = 0
        
        cursor.asFlow()
            .onEach { rowsProcessed++ }
            .collect()
        
        val endTime = Clock.System.now().toEpochMilliseconds()
        val duration = endTime - startTime
        
        emit(CursorMetrics(
            rowCount = cursor.size,
            processingTimeMs = duration,
            rowsPerSecond = if (duration > 0) (rowsProcessed * 1000.0 / duration) else 0.0
        ))
    }
}

/**
 * Cursor events for reactive processing
 */
sealed class CursorEvent {
    data class ProcessingStarted(val totalRows: Int) : CursorEvent()
    data class ProgressUpdate(val processed: Int, val total: Int) : CursorEvent()
    data class ProcessingCompleted(val totalProcessed: Int) : CursorEvent()
    data class Error(val throwable: Throwable) : CursorEvent()
}

/**
 * Cursor processing metrics
 */
data class CursorMetrics(
    val rowCount: Int,
    val processingTimeMs: Long,
    val rowsPerSecond: Double
)

/**
 * Channel-based cursor aggregations
 */
suspend fun Cursor.aggregateToChannel(
    outputChannel: SendChannel<AggregateResult>,
    aggregations: List<ColumnAggregation>
) {
    val results = mutableMapOf<String, Any>()
    
    asFlow().collect { row ->
        aggregations.forEach { agg ->
            val currentValue = results[agg.name] ?: agg.initialValue
            val cellValue = row.b(agg.columnIndex).a
            results[agg.name] = agg.accumulator(currentValue, cellValue)
        }
    }
    
    aggregations.forEach { agg ->
        val finalValue = agg.finalizer(results[agg.name] ?: agg.initialValue)
        outputChannel.send(AggregateResult(agg.name, finalValue))
    }
}

/**
 * Column aggregation specification
 */
data class ColumnAggregation(
    val name: String,
    val columnIndex: Int,
    val initialValue: Any,
    val accumulator: (current: Any, new: Any?) -> Any,
    val finalizer: (accumulated: Any) -> Any = { it }
)

/**
 * Aggregation result
 */
data class AggregateResult(
    val name: String,
    val value: Any
)

/**
 * Cursor streaming utilities
 */
object CursorStreaming {
    
    /**
     * Create sum aggregation
     */
    fun sum(name: String, columnIndex: Int) = ColumnAggregation(
        name = name,
        columnIndex = columnIndex,
        initialValue = 0.0,
        accumulator = { current, new ->
            val sum = current as Double
            val newValue = when (new) {
                is Number -> new.toDouble()
                else -> 0.0
            }
            sum + newValue
        }
    )
    
    /**
     * Create count aggregation
     */
    fun count(name: String, columnIndex: Int) = ColumnAggregation(
        name = name,
        columnIndex = columnIndex,
        initialValue = 0,
        accumulator = { current, new ->
            val count = current as Int
            if (new != null) count + 1 else count
        }
    )
    
    /**
     * Create average aggregation
     */
    fun average(name: String, columnIndex: Int) = ColumnAggregation(
        name = name,
        columnIndex = columnIndex,
        initialValue = 0.0 to 0,
        accumulator = { current, new ->
            val (sum, count) = current as Pair<Double, Int>
            val newValue = when (new) {
                is Number -> new.toDouble()
                else -> 0.0
            }
            if (new != null) {
                (sum + newValue) to (count + 1)
            } else {
                sum to count
            }
        },
        finalizer = { accumulated ->
            val (sum, count) = accumulated as Pair<Double, Int>
            if (count > 0) sum / count else 0.0
        }
    )
}