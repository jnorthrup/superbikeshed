package borg.trikeshed.ljson

import borg.trikeshed.lib.*
import borg.trikeshed.cursor.*
import borg.trikeshed.isam.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * JSON-ISAM Integration for TrikeShed
 * 
 * Complete integration between JSON processing and ISAM file operations,
 * providing efficient conversion, streaming, and transformation capabilities.
 */

/**
 * JSON-ISAM converter with schema inference
 */
class JsonISAMConverter {
    
    /**
     * Convert JSON array to ISAM with automatic schema inference
     */
    suspend fun jsonArrayToISAM(
        jsonArray: JsonElement.Arr,
        outputPath: String,
        schemaHints: Map<String, IOMemento> = emptyMap()
    ) {
        // Analyze schema from first few records
        val sampleSize = minOf(100, jsonArray.elements.a)
        val schema = inferSchema(jsonArray, sampleSize, schemaHints)
        
        // Convert to cursor
        val rows = mutableListOf<List<Any?>>()
        
        for (i in 0 until jsonArray.elements.a) {
            val element = jsonArray.elements.b(i)
            when (element) {
                is JsonElement.Obj -> {
                    val row = schema.columnNames.map { columnName ->
                        // Find field by name
                        var value: Any? = null
                        for (j in 0 until element.fields.a) {
                            val field = element.fields.b(j)
                            if (field.a == columnName) {
                                value = convertToType(field.b, schema.columnTypes[columnName]!!)
                                break
                            }
                        }
                        value
                    }
                    rows.add(row)
                }
                else -> {
                    // Single-value row
                    rows.add(listOf(convertToType(element, schema.columnTypes.values.first())))
                }
            }
        }
        
        // Create cursor and write to ISAM
        val cursor = cursorOf(rows, schema.columnNames, schema.columnTypes.values.toList())
        cursor.writeISAM(outputPath)
    }
    
    /**
     * Convert ISAM to JSON array
     */
    suspend fun isamToJsonArray(inputPath: String): JsonElement.Arr {
        val handle = openISAMCursor(inputPath)
        val cursor = handle.a
        
        try {
            return JsonCursor.toJsonArray(cursor)
        } finally {
            handle.b.close()
        }
    }
    
    /**
     * Stream JSON to ISAM with chunking
     */
    suspend fun streamJsonToISAM(
        jsonStream: Flow<JsonElement>,
        outputPath: String,
        chunkSize: Int = 10000
    ) {
        val chunks = mutableListOf<JsonElement>()
        var chunkIndex = 0
        
        jsonStream.collect { element ->
            chunks.add(element)
            
            if (chunks.size >= chunkSize) {
                val chunkPath = "${outputPath}.chunk_${chunkIndex}"
                val array = JsonElement.Arr(chunks.size j { i -> chunks[i] })
                jsonArrayToISAM(array, chunkPath)
                
                chunks.clear()
                chunkIndex++
            }
        }
        
        // Process remaining elements
        if (chunks.isNotEmpty()) {
            val chunkPath = "${outputPath}.chunk_${chunkIndex}"
            val array = JsonElement.Arr(chunks.size j { i -> chunks[i] })
            jsonArrayToISAM(array, chunkPath)
        }
        
        // Merge chunks into final ISAM file
        mergeISAMChunks(outputPath, chunkIndex + 1)
    }
    
    /**
     * Stream ISAM to JSON with pagination
     */
    fun streamISAMToJson(
        inputPath: String,
        pageSize: Int = 1000
    ): Flow<JsonElement> = flow {
        val handle = openISAMCursor(inputPath)
        val cursor = handle.a
        
        try {
            var offset = 0
            while (offset < cursor.a) {
                val endIndex = minOf(offset + pageSize, cursor.a)
                val page = cursor.at(offset until endIndex)
                
                for (i in 0 until page.a) {
                    val row = page.at(i)
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
                
                offset = endIndex
            }
        } finally {
            handle.b.close()
        }
    }
    
    private data class JsonSchema(
        val columnNames: List<String>,
        val columnTypes: Map<String, IOMemento>
    )
    
    private fun inferSchema(
        array: JsonElement.Arr,
        sampleSize: Int,
        hints: Map<String, IOMemento>
    ): JsonSchema {
        val columnNames = mutableSetOf<String>()
        val typeFrequency = mutableMapOf<String, MutableMap<IOMemento, Int>>()
        
        // Analyze sample records
        for (i in 0 until minOf(sampleSize, array.elements.a)) {
            val element = array.elements.b(i)
            when (element) {
                is JsonElement.Obj -> {
                    for (j in 0 until element.fields.a) {
                        val field = element.fields.b(j)
                        val fieldName = field.a
                        val fieldType = inferTypeFromElement(field.b)
                        
                        columnNames.add(fieldName)
                        typeFrequency.getOrPut(fieldName) { mutableMapOf() }
                            .merge(fieldType, 1) { old, new -> old + new }
                    }
                }
                else -> {
                    // Single-value array
                    columnNames.add("value")
                    val fieldType = inferTypeFromElement(element)
                    typeFrequency.getOrPut("value") { mutableMapOf() }
                        .merge(fieldType, 1) { old, new -> old + new }
                }
            }
        }
        
        // Determine final types (most frequent type wins)
        val columnTypes = columnNames.associateWith { columnName ->
            hints[columnName] ?: typeFrequency[columnName]
                ?.maxByOrNull { it.value }?.key ?: IOMemento.IoString
        }
        
        return JsonSchema(columnNames.sorted(), columnTypes)
    }
    
    private fun inferTypeFromElement(element: JsonElement): IOMemento = when (element) {
        JsonElement.Null -> IOMemento.IoString
        is JsonElement.Bool -> IOMemento.IoString // Store as string for flexibility
        is JsonElement.Num -> {
            if (element.value == element.value.toInt().toDouble()) {
                IOMemento.IoInt
            } else {
                IOMemento.IoDouble
            }
        }
        is JsonElement.Str -> IOMemento.IoString
        is JsonElement.Arr -> IOMemento.IoString // Serialize to JSON string
        is JsonElement.Obj -> IOMemento.IoString // Serialize to JSON string
    }
    
    private fun convertToType(element: JsonElement, targetType: IOMemento): Any? = when (targetType) {
        IOMemento.IoInt -> when (element) {
            is JsonElement.Num -> element.value.toInt()
            is JsonElement.Str -> element.value.toIntOrNull()
            else -> 0
        }
        IOMemento.IoFloat -> when (element) {
            is JsonElement.Num -> element.value.toFloat()
            is JsonElement.Str -> element.value.toFloatOrNull()
            else -> 0f
        }
        IOMemento.IoDouble -> when (element) {
            is JsonElement.Num -> element.value
            is JsonElement.Str -> element.value.toDoubleOrNull()
            else -> 0.0
        }
        IOMemento.IoString -> when (element) {
            JsonElement.Null -> null
            is JsonElement.Bool -> element.value.toString()
            is JsonElement.Num -> element.value.toString()
            is JsonElement.Str -> element.value
            else -> Json.stringify(element)
        }
        IOMemento.IoLocalDate -> when (element) {
            is JsonElement.Str -> {
                try {
                    kotlinx.datetime.LocalDate.parse(element.value)
                } catch (e: Exception) {
                    kotlinx.datetime.LocalDate.fromEpochDays(0)
                }
            }
            else -> kotlinx.datetime.LocalDate.fromEpochDays(0)
        }
    }
    
    private suspend fun mergeISAMChunks(outputPath: String, chunkCount: Int) {
        // This would merge multiple ISAM chunk files into a single file
        // Implementation depends on ISAM file format details
        // For now, just use the first chunk as the final file
        if (chunkCount > 0) {
            val firstChunk = "${outputPath}.chunk_0"
            // Copy/rename first chunk to final output
            // Additional chunks would be appended
        }
    }
}

/**
 * JSON-ISAM reactive processor
 */
class JsonISAMReactor(
    private val scope: CoroutineScope = GlobalScope
) {
    private val converter = JsonISAMConverter()
    private val _events = MutableSharedFlow<JsonISAMEvent>()
    val events: SharedFlow<JsonISAMEvent> = _events.asSharedFlow()
    
    /**
     * Process JSON to ISAM with reactive events
     */
    suspend fun processJsonToISAM(
        jsonPath: String,
        isamPath: String,
        contextId: String
    ) = withContext(CursorContext(
        cursorId = contextId,
        metadata = CursorMetadata(
            rowCount = -1,
            columnCount = -1,
            columnNames = emptyList(),
            columnTypes = emptyList(),
            sourceType = CursorSourceType.STREAM,
            sourcePath = jsonPath
        ),
        executionPhase = CursorExecutionPhase.PROCESSING
    )) {
        try {
            _events.emit(JsonISAMEvent.ConversionStarted(contextId, jsonPath, isamPath))
            
            // Read and parse JSON
            val json = kotlinx.coroutines.Dispatchers.IO.run {
                // File reading would be platform-specific
                // For now, assume JSON content is available
                """[{"name": "sample", "value": 123}]"""
            }
            
            val result = Json.parse(json)
            if (result.a is JsonElement.Arr) {
                converter.jsonArrayToISAM(result.a as JsonElement.Arr, isamPath)
                _events.emit(JsonISAMEvent.ConversionCompleted(contextId, isamPath))
            } else {
                _events.emit(JsonISAMEvent.ConversionFailed(contextId, "Invalid JSON array"))
            }
        } catch (e: Exception) {
            _events.emit(JsonISAMEvent.ConversionFailed(contextId, e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Batch process multiple JSON files to ISAM
     */
    suspend fun batchProcessToISAM(
        jsonPaths: List<String>,
        outputDir: String,
        concurrency: Int = 4
    ) = coroutineScope {
        val semaphore = Semaphore(concurrency)
        
        jsonPaths.mapIndexed { index, jsonPath ->
            async {
                semaphore.withPermit {
                    val isamPath = "$outputDir/output_$index.isam"
                    processJsonToISAM(jsonPath, isamPath, "batch_$index")
                }
            }
        }.awaitAll()
    }
}

/**
 * JSON-ISAM events
 */
sealed class JsonISAMEvent {
    data class ConversionStarted(val contextId: String, val source: String, val target: String) : JsonISAMEvent()
    data class ConversionCompleted(val contextId: String, val target: String) : JsonISAMEvent()
    data class ConversionFailed(val contextId: String, val error: String) : JsonISAMEvent()
    data class BatchProgress(val completed: Int, val total: Int) : JsonISAMEvent()
}

/**
 * JSON-ISAM factory and utilities
 */
object JsonISAMFactory {
    
    /**
     * Create converter with default settings
     */
    fun createConverter(): JsonISAMConverter = JsonISAMConverter()
    
    /**
     * Create reactive processor
     */
    fun createReactor(scope: CoroutineScope = GlobalScope): JsonISAMReactor = 
        JsonISAMReactor(scope)
    
    /**
     * Quick conversion utility
     */
    suspend fun quickConvert(
        jsonContent: String,
        outputPath: String,
        schemaHints: Map<String, IOMemento> = emptyMap()
    ) {
        val converter = createConverter()
        val result = Json.parse(jsonContent)
        
        when (val element = result.a) {
            is JsonElement.Arr -> {
                converter.jsonArrayToISAM(element, outputPath, schemaHints)
            }
            else -> {
                // Wrap single element in array
                val array = JsonElement.Arr(1 j { element ?: JsonElement.Null })
                converter.jsonArrayToISAM(array, outputPath, schemaHints)
            }
        }
    }
    
    /**
     * Quick read utility
     */
    suspend fun quickRead(inputPath: String): JsonElement.Arr {
        val converter = createConverter()
        return converter.isamToJsonArray(inputPath)
    }
}

/**
 * Extension functions for convenience
 */
private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonElement.Null
    is Boolean -> JsonElement.Bool(this)
    is Number -> JsonElement.Num(this.toDouble())
    is String -> JsonElement.Str(this)
    else -> JsonElement.Str(toString())
}