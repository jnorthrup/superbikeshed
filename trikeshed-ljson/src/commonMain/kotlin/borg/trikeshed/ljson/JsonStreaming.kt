@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.ljson

import borg.trikeshed.lib.*
import borg.trikeshed.cursor.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

/**
 * Streaming JSON Operations for TrikeShed
 * 
 * High-performance streaming JSON processing integrated with channels and cursors.
 * Supports large JSON documents that don't fit in memory.
 */

/**
 * Streaming JSON parser with backpressure support
 */
class JsonStreamingParser(
    internal val bufferSize: Int = Channel.BUFFERED,
    internal val batchSize: Int = 1000
) {
    
    /**
     * Parse JSON array as a stream of elements
     */
    fun parseArrayStream(json: String): Flow<JsonElement> = flow {
        val provider = BBCursiveJsonProvider()
        val result = provider.parse(json)
        
        when (val element = result.a) {
            is JsonElement.Arr -> {
                for (i in 0 until element.elements.a) {
                    emit(element.elements.b(i))
                    if (i % batchSize == 0) yield() // Allow cancellation
                }
            }
            else -> {
                if (element != null) emit(element)
            }
        }
    }.buffer(bufferSize)
    
    /**
     * Parse large JSON object field by field
     */
    fun parseObjectStream(json: String): Flow<Join<String, JsonElement>> = flow {
        val provider = BBCursiveJsonProvider()
        val result = provider.parse(json)
        
        when (val element = result.a) {
            is JsonElement.Obj -> {
                for (i in 0 until element.fields.a) {
                    emit(element.fields.b(i))
                    if (i % batchSize == 0) yield() // Allow cancellation
                }
            }
            else -> {
                // Not an object, emit as single field
                if (element != null) {
                    emit("value" j element)
                }
            }
        }
    }.buffer(bufferSize)
    
    /**
     * Parse JSON lines format (JSONL/NDJSON)
     */
    fun parseJsonLines(jsonLines: String): Flow<JsonElement> = flow {
        val provider = BBCursiveJsonProvider()
        val lines = jsonLines.lines()
        
        lines.forEachIndexed { index, line ->
            if (line.isNotBlank()) {
                val result = provider.parse(line.trim())
                result.a?.let { emit(it) }
                if (index % batchSize == 0) yield() // Allow cancellation
            }
        }
    }.buffer(bufferSize)
    
    /**
     * Stream JSON to channel
     */
    suspend fun streamToChannel(
        json: String, 
        channel: SendChannel<JsonElement>,
        isArray: Boolean = true
    ) {
        if (isArray) {
            parseArrayStream(json).collect { element ->
                channel.send(element)
            }
        } else {
            parseObjectStream(json).collect { (key, value) ->
                channel.send(JsonElement.Obj(1 j { key j value }))
            }
        }
    }
}

/**
 * JSON streaming operations with CCEK integration
 */
class JsonStreamingCCEK(
    internal val scope: CoroutineScope = GlobalScope
) {
    internal val _events = MutableSharedFlow<JsonStreamEvent>()
    val events: SharedFlow<JsonStreamEvent> = _events.asSharedFlow()
    
    /**
     * Stream JSON with context tracking
     */
    suspend fun streamWithContext(
        json: String,
        contextId: String,
        environment: CursorEnvironment = CursorEnvironment()
    ): Flow<JsonElement> {
        return withContext(CursorContext(
            cursorId = contextId,
            metadata = CursorMetadata(
                rowCount = -1, // Unknown until parsed
                columnCount = 1,
                columnNames = listOf("json_element"),
                columnTypes = listOf(IOMemento.IoString),
                sourceType = CursorSourceType.STREAM,
                sourcePath = null
            ),
            executionPhase = CursorExecutionPhase.STREAMING
        )) {
            val parser = JsonStreamingParser(environment.bufferSize, environment.batchSize)
            
            _events.emit(JsonStreamEvent.StreamStarted(contextId))
            
            var count = 0
            parser.parseArrayStream(json)
                .onEach { 
                    count++
                    if (count % environment.batchSize == 0) {
                        _events.emit(JsonStreamEvent.BatchProcessed(count, contextId))
                    }
                }
                .onCompletion { 
                    _events.emit(JsonStreamEvent.StreamCompleted(count, contextId))
                }
        }
    }
    
    /**
     * Parallel JSON processing
     */
    suspend fun processInParallel(
        jsonDocuments: List<String>,
        concurrency: Int = 4,
        processor: suspend (JsonElement) -> Unit
    ) = coroutineScope {
        val semaphore = Semaphore(concurrency)
        
        jsonDocuments.mapIndexed { index, json ->
            async {
                semaphore.withPermit {
                    val parser = JsonStreamingParser()
                    parser.parseArrayStream(json).collect { element ->
                        processor(element)
                    }
                }
            }
        }.awaitAll()
    }
}

/**
 * JSON streaming events
 */
sealed class JsonStreamEvent {
    data class StreamStarted(val contextId: String) : JsonStreamEvent()
    data class BatchProcessed(val count: Int, val contextId: String) : JsonStreamEvent()
    data class StreamCompleted(val totalCount: Int, val contextId: String) : JsonStreamEvent()
    data class Error(val contextId: String, val throwable: Throwable) : JsonStreamEvent()
}

/**
 * JSON to Cursor streaming converter
 */
object JsonCursorStreaming {
    
    /**
     * Convert streaming JSON array to cursor
     */
    suspend fun fromJsonArrayStream(jsonStream: Flow<JsonElement>): Cursor {
        val rows = mutableListOf<List<Any?>>()
        var maxColumns = 0
        
        jsonStream.collect { element ->
            when (element) {
                is JsonElement.Obj -> {
                    val row = mutableListOf<Any?>()
                    for (i in 0 until element.fields.a) {
                        val field = element.fields.b(i)
                        row.add(field.b.toNativeValue())
                    }
                    rows.add(row)
                    maxColumns = maxOf(maxColumns, row.size)
                }
                else -> {
                    rows.add(listOf(element.toNativeValue()))
                    maxColumns = maxOf(maxColumns, 1)
                }
            }
        }
        
        val columnNames = (0 until maxColumns).map { "col_$it" }
        return cursorOf(rows, columnNames)
    }
    
    /**
     * Convert cursor to streaming JSON array
     */
    fun toJsonArrayStream(cursor: Cursor): Flow<JsonElement> = flow {
        for (i in 0 until cursor.a) {
            val row = cursor.at(i)
            val fields = mutableListOf<Join<String, JsonElement>>()
            
            for (j in 0 until row.a) {
                val cell = row.b(j)
                val columnName = cursor.columnNames.b(j)
                val value = cell.a.toJsonElement()
                fields.add(columnName j value)
            }
            
            val obj = JsonElement.Obj(fields.size j { k -> fields[k] })
            emit(obj)
        }
    }
    
    /**
     * Stream JSON to ISAM format
     */
    suspend fun streamJsonToISAM(
        jsonStream: Flow<JsonElement>,
        outputPath: String,
        fieldExtractor: (JsonElement) -> List<Any?> = { element ->
            when (element) {
                is JsonElement.Obj -> {
                    (0 until element.fields.a).map { i ->
                        element.fields.b(i).b.toNativeValue()
                    }
                }
                else -> listOf(element.toNativeValue())
            }
        }
    ) {
        val rows = mutableListOf<List<Any?>>()
        var maxColumns = 0
        
        jsonStream.collect { element ->
            val fields = fieldExtractor(element)
            rows.add(fields)
            maxColumns = maxOf(maxColumns, fields.size)
        }
        
        if (rows.isNotEmpty()) {
            val columnNames = (0 until maxColumns).map { "col_$it" }
            val cursor = cursorOf(rows, columnNames)
            cursor.writeISAM(outputPath)
        }
    }
    
    /**
     * Stream ISAM to JSON format
     */
    suspend fun streamISAMToJson(
        isamlPath: String,
        jsonArrayOutput: Boolean = true
    ): Flow<JsonElement> = flow {
        val handle = openISAMCursor(isamlPath)
        val cursor = handle.a
        
        try {
            if (jsonArrayOutput) {
                // Emit entire array as single element
                val jsonArray = JsonCursor.toJsonArray(cursor)
                emit(jsonArray)
            } else {
                // Emit each row as separate element
                for (i in 0 until cursor.a) {
                    val row = cursor.at(i)
                    val fields = mutableListOf<Join<String, JsonElement>>()
                    
                    for (j in 0 until row.a) {
                        val cell = row.b(j)
                        val columnName = cursor.columnNames.b(j)
                        val value = cell.a.toJsonElement()
                        fields.add(columnName j value)
                    }
                    
                    val obj = JsonElement.Obj(fields.size j { k -> fields[k] })
                    emit(obj)
                }
            }
        } finally {
            handle.b.close()
        }
    }
}

/**
 * Batch JSON operations
 */
object JsonBatching {
    
    /**
     * Process JSON in batches
     */
    suspend fun processBatched(
        json: String,
        batchSize: Int = 1000,
        processor: suspend (List<JsonElement>) -> Unit
    ) {
        val parser = JsonStreamingParser(batchSize = batchSize)
        val batch = mutableListOf<JsonElement>()
        
        parser.parseArrayStream(json).collect { element ->
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
     * Aggregate JSON stream
     */
    suspend fun aggregate(
        jsonStream: Flow<JsonElement>,
        aggregator: (JsonElement, JsonElement) -> JsonElement
    ): JsonElement? {
        var result: JsonElement? = null
        
        jsonStream.collect { element ->
            result = if (result == null) {
                element
            } else {
                aggregator(result!!, element)
            }
        }
        
        return result
    }
}

/**
 * Extension functions for JsonElement
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

internal fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonElement.Null
    is Boolean -> JsonElement.Bool(this)
    is Number -> JsonElement.Num(this.toDouble())
    is String -> JsonElement.Str(this)
    is List<*> -> {
        val elements = size j { i -> this[i].toJsonElement() }
        JsonElement.Arr(elements)
    }
    is Map<*, *> -> {
        val fields = mutableListOf<Join<String, JsonElement>>()
        forEach { (key, value) ->
            fields.add((key?.toString() ?: "") j value.toJsonElement())
        }
        JsonElement.Obj(fields.size j { i -> fields[i] })
    }
    else -> JsonElement.Str(toString())
}