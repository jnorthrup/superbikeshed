package org.flatton.service

import org.flatton.client.CouchClient
import org.flatton.parse.JsonWireProtoAdapter
import org.flatton.parse.JsonObjectCursor
import org.flatton.types.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield

/**
 * The main service layer for the Flatton library.
 * It provides high-level operations and orchestrates the client and parsing layers.
 * 
 * This follows the relaxfactory/1xio visitor pattern with Trikeshed's canonical types.
 */
class FlattonService(private val client: CouchClient) {

    /**
     * Queries a CouchDB view and returns the result as a cursor-like `Indexed`.
     * This allows for efficient, on-demand processing of large result sets without
     * loading everything into memory. This is the "cursor element usecase" analog.
     *
     * @param dbName The name of the database.
     * @param designDocId The ID of the design document.
     * @param viewName The name of the view.
     * @param params Query parameters for the view.
     * @return A `Indexed` of `JsonObjectCursor` objects, where each cursor points to a row object in the JSON response.
     */
    suspend fun queryViewAsCursor(
        dbName: DatabaseName,
        designDocId: DocumentId,
        viewName: ViewName,
        params: ViewQueryParams = ViewQueryParams()
    ): Indexed<JsonObjectCursor> {
        // Get the raw response as a Indexed<Byte> for efficient processing
        val responseJson = client.queryView<Any, Any>(dbName, designDocId, viewName, params).toString()
        val responseBytes = responseJson.encodeToByteArray().toSeries()
        
        // Use the robust JsonWireProtoAdapter with BitmapJsonDecoder
        val wireAdapter = JsonWireProtoAdapter()
        return wireAdapter.toCursor(responseBytes)
    }
    
    /**
     * Queries a CouchDB view and returns the result as a Indexed of parsed objects.
     * This provides a higher-level API for common use cases.
     */
    suspend fun <T> queryViewAsObjects(
        dbName: DatabaseName,
        designDocId: DocumentId,
        viewName: ViewName,
        params: ViewQueryParams = ViewQueryParams(),
        transform: (JsonObjectCursor) -> T
    ): Indexed<T> {
        val cursors = queryViewAsCursor(dbName, designDocId, viewName, params)
        return cursors.map(transform)
    }
    
    /**
     * Streams a CouchDB view result as a flow of cursors.
     * This follows the relaxfactory visitor pattern for reactive processing.
     */
    suspend fun queryViewAsFlow(
        dbName: DatabaseName,
        designDocId: DocumentId,
        viewName: ViewName,
        params: ViewQueryParams = ViewQueryParams(),
        batchSize: Int = 100
    ): Flow<JsonObjectCursor> = flow {
        val cursors = queryViewAsCursor(dbName, designDocId, viewName, params)
        
        // Process in batches following the 1xio pattern
        var currentIndex = 0
        while (currentIndex < cursors.size) {
            val batch = mutableListOf<JsonObjectCursor>()
            val endIndex = minOf(currentIndex + batchSize, cursors.size)
            
            for (i in currentIndex until endIndex) {
                batch.add(cursors[i])
            }
            
            // Emit the batch as individual items
            batch.forEach { emit(it) }
            currentIndex = endIndex
            
            // Yield to prevent blocking (1xio pattern)
            yield()
        }
    }
    
    /**
     * Performs a bulk operation on documents using efficient cursor processing.
     */
    suspend fun bulkProcessDocuments(
        dbName: DatabaseName,
        designDocId: DocumentId,
        viewName: ViewName,
        params: ViewQueryParams = ViewQueryParams(),
        processor: suspend (JsonObjectCursor) -> Boolean
    ): Join<Int, Int> {
        val cursors = queryViewAsCursor(dbName, designDocId, viewName, params)
        var processed = 0
        var successful = 0
        
        for (i in 0 until cursors.size) {
            processed++
            if (processor(cursors[i])) {
                successful++
            }
            
            // Yield every 100 items to prevent blocking (1xio pattern)
            if (processed % 100 == 0) {
                yield()
            }
        }
        
        return Join(processed, successful)
    }
    
    /**
     * Creates a materialized view of the query results.
     * This caches the results for repeated access.
     */
    suspend fun createMaterializedView(
        dbName: DatabaseName,
        designDocId: DocumentId,
        viewName: ViewName,
        params: ViewQueryParams = ViewQueryParams()
    ): MaterializedView {
        val cursors = queryViewAsCursor(dbName, designDocId, viewName, params)
        return MaterializedView(cursors)
    }
}

/**
 * A materialized view that caches cursor results for efficient repeated access.
 * This follows the relaxfactory pattern for state management.
 */
class MaterializedView(private val cursors: Indexed<JsonObjectCursor>) {
    
    /**
     * Gets the total number of items in the view.
     */
    val size: Int get() = cursors.size
    
    /**
     * Gets a cursor at the specified index.
     */
    fun get(index: Int): JsonObjectCursor = cursors[index]
    
    /**
     * Maps the cursors to a new Indexed using the provided transform function.
     */
    fun <T> map(transform: (JsonObjectCursor) -> T): Indexed<T> {
        return cursors.map(transform)
    }
    
    /**
     * Filters the cursors using the provided predicate.
     */
    fun filter(predicate: (JsonObjectCursor) -> Boolean): Indexed<JsonObjectCursor> {
        return cursors.filter(predicate)
    }
    
    /**
     * Reduces the cursors to a single value.
     */
    fun <T> reduce(initial: T, operation: (T, JsonObjectCursor) -> T): T {
        return cursors.play.fold(initial, operation)
    }
    
    /**
     * Converts the materialized view to a list.
     */
    fun toList(): List<JsonObjectCursor> = cursors.play.toList()
}