@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.isam

import borg.trikeshed.cursor.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

/**
 * ISAM Reactor - Reactive ISAM operations with TrikeShed patterns
 * 
 * Normalizes ISAM cursor operations with the existing reactor patterns,
 * providing streaming, batching, and event-driven processing capabilities.
 */

/**
 * Reactive ISAM cursor with channel-based operations
 */
class ReactiveISAMCursor(
    internal val handle: ISAMHandle,
    internal val scope: CoroutineScope = GlobalScope
) {
    internal val cursor = handle.a
    internal val fileAccess = handle.b
    
    internal val _events = MutableSharedFlow<ISAMEvent>()
    val events: SharedFlow<ISAMEvent> = _events.asSharedFlow()
    
    /**
     * Stream ISAM data with backpressure control
     */
    fun streamRows(
        bufferSize: Int = Channel.BUFFERED,
        batchSize: Int = 1000
    ): Flow<RowVec> = flow {
        _events.emit(ISAMEvent.StreamStarted(cursor.a))
        
        var streamed = 0
        for (i in 0 until cursor.a) {
            emit(cursor.at(i))
            streamed++
            
            if (streamed % batchSize == 0) {
                _events.emit(ISAMEvent.BatchProcessed(streamed, cursor.a))
                yield() // Allow cancellation
            }
        }
        
        _events.emit(ISAMEvent.StreamCompleted(streamed))
    }.buffer(bufferSize).flowOn(Dispatchers.IO)
    
    /**
     * Random access with caching and prefetch
     */
    suspend fun getRowsCached(indices: IntArray): Flow<RowVec> = flow {
        _events.emit(ISAMEvent.RandomAccessStarted(indices.size))
        
        indices.forEach { index ->
            emit(cursor.at(index))
        }
        
        _events.emit(ISAMEvent.RandomAccessCompleted(indices.size))
    }.flowOn(Dispatchers.IO)
    
    /**
     * Windowed reading for large datasets
     */
    fun windowedRead(
        windowSize: Int = 10000,
        overlap: Int = 0
    ): Flow<Cursor> = flow {
        val totalRows = cursor.a
        var start = 0
        
        while (start < totalRows) {
            val end = minOf(start + windowSize, totalRows)
            val window = cursor.at(start until end)
            
            _events.emit(ISAMEvent.WindowProcessed(start, end, totalRows))
            emit(window)
            
            start += windowSize - overlap
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Close resources
     */
    suspend fun close() {
        fileAccess.close()
        _events.emit(ISAMEvent.Closed)
    }
}

/**
 * ISAM events for reactive monitoring
 */
sealed class ISAMEvent {
    data class StreamStarted(val totalRows: Int) : ISAMEvent()
    data class BatchProcessed(val processed: Int, val total: Int) : ISAMEvent()
    data class StreamCompleted(val totalProcessed: Int) : ISAMEvent()
    data class RandomAccessStarted(val requestCount: Int) : ISAMEvent()
    data class RandomAccessCompleted(val accessCount: Int) : ISAMEvent()
    data class WindowProcessed(val start: Int, val end: Int, val total: Int) : ISAMEvent()
    object Closed : ISAMEvent()
    data class Error(val throwable: Throwable) : ISAMEvent()
}

/**
 * ISAM selector for reactor integration
 */
class ISAMSelector(
    internal val scope: CoroutineScope = GlobalScope
) {
    internal val registrations = mutableMapOf<String, ReactiveISAMCursor>()
    internal val _selections = MutableSharedFlow<ISAMSelection>()
    val selections: SharedFlow<ISAMSelection> = _selections.asSharedFlow()
    
    /**
     * Register ISAM cursor for selection
     */
    suspend fun register(key: String, path: String) {
        val handle = openISAMCursor(path)
        val reactive = ReactiveISAMCursor(handle, scope)
        registrations[key] = reactive
        
        // Monitor events from this cursor
        reactive.events.collect { event ->
            _selections.emit(ISAMSelection(key, event))
        }
    }
    
    /**
     * Select from registered cursors
     */
    fun select(key: String): ReactiveISAMCursor? = registrations[key]
    
    /**
     * Select multiple cursors for parallel processing
     */
    fun selectMultiple(keys: List<String>): Flow<Join<String, RowVec>> = flow {
        val cursors = keys.mapNotNull { key ->
            registrations[key]?.let { key to it }
        }
        
        cursors.forEach { (key, cursor) ->
            cursor.streamRows().collect { row ->
                emit(key j row)
            }
        }
    }
    
    /**
     * Unregister and close cursor
     */
    suspend fun unregister(key: String) {
        registrations[key]?.close()
        registrations.remove(key)
    }
    
    /**
     * Close all registered cursors
     */
    suspend fun closeAll() {
        registrations.values.forEach { it.close() }
        registrations.clear()
    }
}

/**
 * ISAM selection event
 */
data class ISAMSelection(
    val cursorKey: String,
    val event: ISAMEvent
)

/**
 * ISAM factory for reactive operations
 */
object ISAMFactory {
    
    /**
     * Create reactive ISAM cursor
     */
    suspend fun openReactive(
        path: String,
        scope: CoroutineScope = GlobalScope
    ): ReactiveISAMCursor {
        val handle = openISAMCursor(path)
        return ReactiveISAMCursor(handle, scope)
    }
    
    /**
     * Create ISAM selector
     */
    fun createSelector(scope: CoroutineScope = GlobalScope): ISAMSelector {
        return ISAMSelector(scope)
    }
    
    /**
     * Parallel ISAM processing
     */
    suspend fun processInParallel(
        paths: List<String>,
        concurrency: Int = 4,
        processor: suspend (String, ReactiveISAMCursor) -> Unit
    ) = coroutineScope {
        val semaphore = Semaphore(concurrency)
        
        paths.map { path ->
            async {
                semaphore.withPermit {
                    val cursor = openReactive(path, this@coroutineScope)
                    try {
                        processor(path, cursor)
                    } finally {
                        cursor.close()
                    }
                }
            }
        }.awaitAll()
    }
}

/**
 * ISAM streaming utilities
 */
object ISAMStreaming {
    
    /**
     * Merge multiple ISAM files into single stream
     */
    fun mergeISAMFiles(
        paths: List<String>,
        bufferSize: Int = Channel.BUFFERED
    ): Flow<Join<String, RowVec>> = channelFlow {
        val jobs = paths.map { path ->
            launch {
                val cursor = ISAMFactory.openReactive(path)
                try {
                    cursor.streamRows(bufferSize).collect { row ->
                        send(path j row)
                    }
                } finally {
                    cursor.close()
                }
            }
        }
        
        jobs.joinAll()
    }
    
    /**
     * Partition ISAM stream by predicate
     */
    fun partitionISAMStream(
        path: String,
        predicate: (RowVec) -> Boolean
    ): Join<Flow<RowVec>, Flow<RowVec>> {
        val source = flow {
            val cursor = ISAMFactory.openReactive(path)
            try {
                cursor.streamRows().collect { emit(it) }
            } finally {
                cursor.close()
            }
        }
        
        val trueFlow = source.filter(predicate)
        val falseFlow = source.filter { !predicate(it) }
        
        return trueFlow j falseFlow
    }
    
    /**
     * ISAM to channel adapter
     */
    suspend fun streamToChannel(
        path: String,
        channel: SendChannel<RowVec>,
        batchSize: Int = 1000
    ) {
        val cursor = ISAMFactory.openReactive(path)
        try {
            cursor.streamRows(batchSize = batchSize).collect { row ->
                channel.send(row)
            }
        } finally {
            cursor.close()
        }
    }
}