@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.ljson

import borg.trikeshed.lib.*
import borg.trikeshed.cursor.*
import borg.trikeshed.isam.*
import kotlinx.serialization.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

/**
 * Bitmap Streaming Conversions for TrikeShed
 * 
 * High-performance streaming JSON conversions using bitmap scanning,
 * integrated with cursors, ISAM, and reactive processing patterns.
 */

/**
 * Bitmap-powered streaming JSON processor
 */
class BitmapStreamingProcessor(
    internal val chunkSize: Int = 64 * 1024,
    internal val bufferSize: Int = Channel.BUFFERED,
    internal val enableParallel: Boolean = true
) {
    
    /**
     * Stream JSON array using bitmap scanning with backpressure
     */
    fun streamJsonArray(json: String): Flow<JsonElement> = flow {
        if (json.length > chunkSize && enableParallel) {
            streamLargeArrayBitmap(json).collect { emit(it) }
        } else {
            streamSmallArrayBitmap(json).collect { emit(it) }
        }
    }.buffer(bufferSize)
    
    /**
     * Stream JSON object fields using bitmap indices
     */
    fun streamJsonObject(json: String): Flow<Join<String, JsonElement>> = flow {
        val provider = BitmapJsonProvider(enableParallel = false)
        val result = provider.parse(json)
        
        when (val element = result.a) {
            is JsonElement.Obj -> {
                for (i in 0 until element.fields.a) {
                    emit(element.fields.b(i))
                    yield() // Allow cancellation
                }
            }
            else -> {
                if (element != null) {
                    emit("value" j element)
                }
            }
        }
    }.buffer(bufferSize)
    
    /**
     * Stream JSON lines (JSONL/NDJSON) with bitmap optimization
     */
    fun streamJsonLines(jsonLines: String): Flow<JsonElement> = flow {
        val lines = jsonLines.lines()
        val provider = BitmapJsonProvider(enableParallel = false)
        
        lines.forEachIndexed { index, line ->
            if (line.isNotBlank()) {
                val result = provider.parse(line.trim())
                result.a?.let { emit(it) }
                
                if (index % 100 == 0) yield() // Periodic yield
            }
        }
    }.buffer(bufferSize)
    
    internal fun streamSmallArrayBitmap(json: String): Flow<JsonElement> = flow {
        val provider = BitmapJsonProvider(enableParallel = false)
        val result = provider.parse(json)
        
        when (val element = result.a) {
            is JsonElement.Arr -> {
                for (i in 0 until element.elements.a) {
                    emit(element.elements.b(i))
                }
            }
            else -> {
                element?.let { emit(it) }
            }
        }
    }
    
    internal fun streamLargeArrayBitmap(json: String): Flow<JsonElement> = channelFlow {
        val chunks = json.chunked(chunkSize)
        
        chunks.forEach { chunk ->
            launch(Dispatchers.Default) {
                val provider = BitmapJsonProvider(enableParallel = true)
                val result = provider.parse(chunk)
                
                when (val element = result.a) {
                    is JsonElement.Arr -> {
                        for (i in 0 until element.elements.a) {
                            send(element.elements.b(i))
                        }
                    }
                    else -> {
                        element?.let { send(it) }
                    }
                }
            }
        }
    }
}

/**
 * Bitmap streaming cursor converter
 */
class BitmapStreamingCursor(
    internal val processor: BitmapStreamingProcessor = BitmapStreamingProcessor()
) {
    
    /**
     * Convert streaming JSON to cursor using bitmap scanning
     */
    suspend fun jsonStreamToCursor(
        jsonStream: Flow<String>,
        contextId: String = "bitmap_stream_cursor"
    ): Cursor = withContext(CursorContext(
        cursorId = contextId,
        metadata = CursorMetadata(
            rowCount = -1,
            columnCount = -1,
            columnNames = emptyList(),
            columnTypes = emptyList(),
            sourceType = CursorSourceType.STREAM,
            sourcePath = null
        ),
        executionPhase = CursorExecutionPhase.PROCESSING
    )) {
        val allRows = mutableListOf<List<Any?>>()
        var maxColumns = 0
        
        jsonStream.collect { jsonChunk ->
            processor.streamJsonArray(jsonChunk).collect { element ->
                val row = elementToRow(element)
                allRows.add(row)
                maxColumns = maxOf(maxColumns, row.size)
            }
        }
        
        val columnNames = (0 until maxColumns).map { "col_$it" }
        cursorOf(allRows, columnNames)
    }
    
    /**
     * Convert cursor to streaming JSON using bitmap optimization
     */
    fun cursorToJsonStream(cursor: Cursor): Flow<String> = flow {
        val batchSize = 1000
        var processedRows = 0
        
        while (processedRows < cursor.a) {
            val endIndex = minOf(processedRows + batchSize, cursor.a)
            val batch = cursor.at(processedRows until endIndex)
            
            val jsonArray = JsonCursor.toJsonArray(batch)
            val jsonString = Json.stringify(jsonArray)
            emit(jsonString)
            
            processedRows = endIndex
        }
    }
    
    internal fun elementToRow(element: JsonElement): List<Any?> = when (element) {
        is JsonElement.Obj -> {
            (0 until element.fields.a).map { i ->
                element.fields.b(i).b.toNativeValue()
            }
        }
        else -> listOf(element.toNativeValue())
    }
}

/**
 * Bitmap ISAM streaming integration
 */
class BitmapISAMStreaming(
    internal val processor: BitmapStreamingProcessor = BitmapStreamingProcessor()
) {
    
    /**
     * Stream JSON to ISAM using bitmap processing
     */
    suspend fun streamJsonToISAM(
        jsonStream: Flow<String>,
        outputPath: String,
        schemaHints: Map<String, IOMemento> = emptyMap()
    ) {
        val tempRows = mutableListOf<List<Any?>>()
        var maxColumns = 0
        val batchSize = 10000
        
        jsonStream.collect { jsonChunk ->
            processor.streamJsonArray(jsonChunk).collect { element ->
                val row = when (element) {
                    is JsonElement.Obj -> {
                        (0 until element.fields.a).map { i ->
                            element.fields.b(i).b.toNativeValue()
                        }
                    }
                    else -> listOf(element.toNativeValue())
                }
                
                tempRows.add(row)
                maxColumns = maxOf(maxColumns, row.size)
                
                // Write batch when full
                if (tempRows.size >= batchSize) {
                    writeBatchToISAM(tempRows, maxColumns, outputPath, schemaHints)
                    tempRows.clear()
                }
            }
        }
        
        // Write remaining rows
        if (tempRows.isNotEmpty()) {
            writeBatchToISAM(tempRows, maxColumns, outputPath, schemaHints)
        }
    }
    
    /**
     * Stream ISAM to JSON using bitmap optimization
     */
    fun streamISAMToJson(inputPath: String): Flow<String> = flow {
        val handle = openISAMCursor(inputPath)
        val cursor = handle.a
        
        try {
            val converter = BitmapStreamingCursor()
            converter.cursorToJsonStream(cursor).collect { jsonString ->
                emit(jsonString)
            }
        } finally {
            handle.b.close()
        }
    }
    
    internal suspend fun writeBatchToISAM(
        rows: List<List<Any?>>,
        maxColumns: Int,
        outputPath: String,
        schemaHints: Map<String, IOMemento>
    ) {
        val columnNames = (0 until maxColumns).map { "col_$it" }
        val columnTypes = inferColumnTypes(rows, schemaHints)
        
        val cursor = cursorOf(rows, columnNames, columnTypes)
        cursor.writeISAM("${outputPath}_batch_${System.currentTimeMillis()}")
    }
    
    internal fun inferColumnTypes(
        rows: List<List<Any?>>,
        hints: Map<String, IOMemento>
    ): List<IOMemento> {
        if (rows.isEmpty()) return emptyList()
        
        val maxColumns = rows.maxOfOrNull { it.size } ?: 0
        return (0 until maxColumns).map { colIndex ->
            val columnName = "col_$colIndex"
            hints[columnName] ?: inferTypeFromValues(rows, colIndex)
        }
    }
    
    internal fun inferTypeFromValues(rows: List<List<Any?>>, colIndex: Int): IOMemento {
        val sampleValues = rows.take(100).mapNotNull { row ->
            row.getOrNull(colIndex)
        }
        
        return when {
            sampleValues.all { it is Int || (it is Number && it.toDouble() == it.toInt().toDouble()) } -> IOMemento.IoInt
            sampleValues.all { it is Number } -> IOMemento.IoDouble
            sampleValues.all { it is Boolean } -> IOMemento.IoString // Store as string
            else -> IOMemento.IoString
        }
    }
}

/**
 * Reactive bitmap streaming with events
 */
class ReactiveBitmapStreaming(
    internal val scope: CoroutineScope = GlobalScope
) {
    internal val _events = MutableSharedFlow<BitmapStreamEvent>()
    val events: SharedFlow<BitmapStreamEvent> = _events.asSharedFlow()
    
    /**
     * Stream with reactive event emission
     */
    suspend fun streamWithEvents(
        json: String,
        contextId: String
    ): Flow<JsonElement> = withContext(CursorContext(
        cursorId = contextId,
        metadata = CursorMetadata(
            rowCount = -1,
            columnCount = -1,
            columnNames = emptyList(),
            columnTypes = emptyList(),
            sourceType = CursorSourceType.STREAM,
            sourcePath = null
        ),
        executionPhase = CursorExecutionPhase.STREAMING
    )) {
        _events.emit(BitmapStreamEvent.StreamStarted(contextId))
        
        val processor = BitmapStreamingProcessor()
        var elementCount = 0
        
        processor.streamJsonArray(json)
            .onEach { 
                elementCount++
                if (elementCount % 1000 == 0) {
                    _events.emit(BitmapStreamEvent.BatchProcessed(elementCount, contextId))
                }
            }
            .onCompletion { 
                _events.emit(BitmapStreamEvent.StreamCompleted(elementCount, contextId))
            }
    }
    
    /**
     * Monitor streaming performance
     */
    suspend fun monitorStreaming(
        json: String,
        contextId: String
    ): Flow<BitmapPerformanceMetrics> = flow {
        val startTime = System.currentTimeMillis()
        var elementsProcessed = 0
        
        streamWithEvents(json, contextId).collect { element ->
            elementsProcessed++
            
            if (elementsProcessed % 5000 == 0) {
                val currentTime = System.currentTimeMillis()
                val duration = currentTime - startTime
                val throughput = if (duration > 0) {
                    (elementsProcessed * 1000.0) / duration
                } else 0.0
                
                emit(BitmapPerformanceMetrics(
                    elementsProcessed = elementsProcessed,
                    durationMs = duration,
                    elementsPerSecond = throughput,
                    contextId = contextId
                ))
            }
        }
    }
}

/**
 * Bitmap streaming events
 */
sealed class BitmapStreamEvent {
    data class StreamStarted(val contextId: String) : BitmapStreamEvent()
    data class BatchProcessed(val count: Int, val contextId: String) : BitmapStreamEvent()
    data class StreamCompleted(val totalCount: Int, val contextId: String) : BitmapStreamEvent()
    data class Error(val contextId: String, val throwable: Throwable) : BitmapStreamEvent()
}

/**
 * Bitmap performance metrics
 */
data class BitmapPerformanceMetrics(
    val elementsProcessed: Int,
    val durationMs: Long,
    val elementsPerSecond: Double,
    val contextId: String
)

/**
 * Bitmap batch operations
 */
object BitmapBatching {
    
    /**
     * Process JSON in batches using bitmap optimization
     */
    suspend fun processBatchedBitmap(
        json: String,
        batchSize: Int = 1000,
        processor: suspend (List<JsonElement>) -> Unit
    ) {
        val streamingProcessor = BitmapStreamingProcessor()
        val batch = mutableListOf<JsonElement>()
        
        streamingProcessor.streamJsonArray(json).collect { element ->
            batch.add(element)
            
            if (batch.size >= batchSize) {
                processor(batch.toList())
                batch.clear()
            }
        }
        
        // Process remaining elements
        if (batch.isNotEmpty()) {
            processor(batch.toList())
        }
    }
    
    /**
     * Aggregate streaming JSON using bitmap parsing
     */
    suspend fun aggregateBitmap(
        jsonStream: Flow<String>,
        aggregator: (JsonElement, JsonElement) -> JsonElement
    ): JsonElement? {
        var result: JsonElement? = null
        val processor = BitmapStreamingProcessor()
        
        jsonStream.collect { jsonChunk ->
            processor.streamJsonArray(jsonChunk).collect { element ->
                result = if (result == null) {
                    element
                } else {
                    aggregator(result!!, element)
                }
            }
        }
        
        return result
    }
}

/**
 * Factory for bitmap streaming operations
 */
object BitmapStreamingFactory {
    
    /**
     * Create optimized streaming processor
     */
    fun createProcessor(
        chunkSize: Int = 64 * 1024,
        enableParallel: Boolean = true
    ): BitmapStreamingProcessor = BitmapStreamingProcessor(chunkSize, enableParallel = enableParallel)
    
    /**
     * Create streaming cursor converter
     */
    fun createCursorConverter(): BitmapStreamingCursor = BitmapStreamingCursor()
    
    /**
     * Create ISAM streaming integration
     */
    fun createISAMStreaming(): BitmapISAMStreaming = BitmapISAMStreaming()
    
    /**
     * Create reactive streaming with events
     */
    fun createReactiveStreaming(scope: CoroutineScope = GlobalScope): ReactiveBitmapStreaming = 
        ReactiveBitmapStreaming(scope)
}

/**
 * Extension functions for convenience
 */

/** Stream JSON using bitmap optimization */
fun String.streamBitmapJson(): Flow<JsonElement> = 
    BitmapStreamingFactory.createProcessor().streamJsonArray(this)

/** Convert JSON stream to cursor using bitmap processing */
suspend fun Flow<String>.toBitmapCursor(contextId: String = "bitmap_stream"): Cursor =
    BitmapStreamingFactory.createCursorConverter().jsonStreamToCursor(this, contextId)

/**
 * Helper conversion functions
 */
internal fun JsonElement.toNativeValue(): Any? = when (this) {
    JsonElement.Null -> null
    is JsonElement.Bool -> value
    is JsonElement.Num -> value
    is JsonElement.Str -> value
    is JsonElement.Arr -> (0 until elements.a).map { elements.b(it).toNativeValue() }
    is JsonElement.Obj -> {
        val map = mutableMapOf<String, Any?>()
        for (i in 0 until fields.a) {
            val field = fields.b(i)
            map[field.a] = field.b.toNativeValue()
        }
        map
    }
}