package org.flatton.service

import org.flatton.client.CouchClient
import org.flatton.parse.JsonWireProtoAdapter
import org.flatton.parse.JsonObjectCursor
import org.flatton.types.*
import borg.trikeshed.lib.Series

/**
 * The main service layer for the Flatton library.
 * It provides high-level operations and orchestrates the client and parsing layers.
 */
class FlattonService(private val client: CouchClient) {

    /**
     * Queries a CouchDB view and returns the result as a cursor-like `Series`.
     * This allows for efficient, on-demand processing of large result sets without
     * loading everything into memory. This is the "cursor element usecase" analog.
     *
     * @param dbName The name of the database.
     * @param designDocId The ID of the design document.
     * @param viewName The name of the view.
     * @param params Query parameters for the view.
     * @return A `Series` of `JsonObjectCursor` objects, where each cursor points to a row object in the JSON response.
     */
    suspend fun queryViewAsCursor(
        dbName: DatabaseName,
        designDocId: DocumentId,
        viewName: ViewName,
        params: ViewQueryParams = ViewQueryParams()
    ): Series<JsonObjectCursor> {
        // In a real implementation, the client would support streaming the raw response body.
        // Here, we simulate this by getting the full response and then creating a cursor over it.
        val responseJson = client.queryView<Any, Any>(dbName, designDocId, viewName, params).toString() // Simplified
        val wireAdapter = JsonWireProtoAdapter()
        return wireAdapter.toCursor(responseJson.toByteArray())
    }
}