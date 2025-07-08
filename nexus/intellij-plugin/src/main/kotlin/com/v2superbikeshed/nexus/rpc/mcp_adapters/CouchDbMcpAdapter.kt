package com.v2superbikeshed.nexus.rpc.mcp_adapters

import com.v2superbikeshed.nexus.rpc.McpError
import com.v2superbikeshed.nexus.rpc.McpRequest
import com.v2superbikeshed.nexus.rpc.McpResponse
import com.v2superbikeshed.nexus.rpc.McpServerInterface
import borg.trikeshed.couchdb.CouchClient
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import mu.KotlinLogging

internal val logger = KotlinLogging.logger {}

import com.superbikeshed.trikeshed.BlobHostingService
import com.superbikeshed.trikeshed.CouchDBBlobHosting
import com.superbikeshed.trikeshed.BlobMetadata
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CouchDbMcpAdapter(
    override val name: String,
    override val version: String,
    override val capabilities: Set<String>,
    internal val couchClient: CouchClient,
    internal val blobHostingService: BlobHostingService
) : McpServerInterface {

    internal val json = Json { ignoreUnknownKeys = true }

    override suspend fun handleRequest(request: McpRequest): McpResponse {
        return try {
            when (request.method) {
                "get" -> {
                    val dbName = request.params?.jsonObject?.get("dbName")?.jsonPrimitive?.content
                    val docId = request.params?.jsonObject?.get("docId")?.jsonPrimitive?.content
                    if (dbName != null && docId != null) {
                        val doc = couchClient.getDocument(dbName, docId)
                        McpResponse(
                            jsonrpc = "2.0",
                            id = request.id,
                            result = doc?.let { json.encodeToJsonElement(it) } ?: JsonPrimitive("null")
                        )
                    } else {
                        McpResponse(
                            jsonrpc = "2.0",
                            id = request.id,
                            error = McpError(-32602, "Invalid 'get' request: missing dbName or docId")
                        )
                    }
                }
                "put" -> {
                    val dbName = request.params?.jsonObject?.get("dbName")?.jsonPrimitive?.content
                    val docId = request.params?.jsonObject?.get("docId")?.jsonPrimitive?.content
                    val docContent = request.params?.jsonObject?.get("docContent") // This is already a JsonElement
                    if (dbName != null && docId != null && docContent != null) {
                        // Assuming putDocument expects a String for docContent, convert JsonElement to String
                        val response = couchClient.putDocument(dbName, docId, json.encodeToString(docContent))
                        McpResponse(
                            jsonrpc = "2.0",
                            id = request.id,
                            result = json.encodeToJsonElement(response)
                        )
                    } else {
                        McpResponse(
                            jsonrpc = "2.0",
                            id = request.id,
                            error = McpError(-32602, "Invalid 'put' request: missing dbName, docId, or docContent")
                        )
                    }
                }
                "listDatabases" -> {
                    val databases = couchClient.listDatabases()
                    McpResponse(
                        jsonrpc = "2.0",
                        id = request.id,
                        result = json.encodeToJsonElement(databases.toList())
                    )
                }
                "createDatabase" -> {
                    val dbName = request.params?.jsonObject?.get("dbName")?.jsonPrimitive?.content
                    if (dbName != null) {
                        val response = couchClient.createDatabase(dbName)
                        McpResponse(
                            jsonrpc = "2.0",
                            id = request.id,
                            result = json.encodeToJsonElement(response)
                        )
                    } else {
                        McpResponse(
                            jsonrpc = "2.0",
                            id = request.id,
                            error = McpError(-32602, "Invalid 'createDatabase' request: missing dbName")
                        )
                    }
                }
                "deleteDatabase" -> {
                    val dbName = request.params?.jsonObject?.get("dbName")?.jsonPrimitive?.content
                    if (dbName != null) {
                        val response = couchClient.deleteDatabase(dbName)
                        McpResponse(
                            jsonrpc = "2.0",
                            id = request.id,
                            result = json.encodeToJsonElement(response)
                        )
                    } else {
                        McpResponse(
                            jsonrpc = "2.0",
                            id = request.id,
                            error = McpError(-32602, "Invalid 'deleteDatabase' request: missing dbName")
                        )
                    }
                }
                "storeBlob" -> {
                    val namespace = request.params?.jsonObject?.get("namespace")?.jsonPrimitive?.content
                    val key = request.params?.jsonObject?.get("key")?.jsonPrimitive?.content
                    val blobData = request.params?.jsonObject?.get("blobData")?.jsonPrimitive?.content
                    if (namespace != null && key != null && blobData != null) {
                        val blobBytes = blobData.decodeBase64Bytes() // Assuming base64 encoded blob
                        val result = blobHostingService.store(namespace, key, blobBytes)
                        result.fold(
                            onSuccess = { metadata ->
                                McpResponse(
                                    jsonrpc = "2.0",
                                    id = request.id,
                                    result = json.encodeToJsonElement(metadata)
                                )
                            },
                            onFailure = { e ->
                                McpResponse(
                                    jsonrpc = "2.0",
                                    id = request.id,
                                    error = McpError(-32000, "Failed to store blob: ${e.message}", JsonPrimitive(e.stackTraceToString()))
                                )
                            }
                        )
                    } else {
                        McpResponse(
                            jsonrpc = "2.0",
                            id = request.id,
                            error = McpError(-32602, "Invalid 'storeBlob' request: missing namespace, key, or blobData")
                        )
                    }
                }
                "retrieveBlob" -> {
                    val namespace = request.params?.jsonObject?.get("namespace")?.jsonPrimitive?.content
                    val key = request.params?.jsonObject?.get("key")?.jsonPrimitive?.content
                    if (namespace != null && key != null) {
                        val result = blobHostingService.retrieve(namespace, key)
                        result.fold(
                            onSuccess = { blobBytes ->
                                McpResponse(
                                    jsonrpc = "2.0",
                                    id = request.id,
                                    result = blobBytes?.encodeBase64()?.let { JsonPrimitive(it) } ?: JsonPrimitive("null")
                                )
                            },
                            onFailure = { e ->
                                McpResponse(
                                    jsonrpc = "2.0",
                                    id = request.id,
                                    error = McpError(-32000, "Failed to retrieve blob: ${e.message}", JsonPrimitive(e.stackTraceToString()))
                                )
                            }
                        )
                    } else {
                        McpResponse(
                            jsonrpc = "2.0",
                            id = request.id,
                            error = McpError(-32602, "Invalid 'retrieveBlob' request: missing namespace or key")
                        )
                    }
                }
                else -> {
                    McpResponse(
                        jsonrpc = "2.0",
                        id = request.id,
                        error = McpError(-32601, "Unknown method: ${request.method}")
                    )
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error handling CouchDB MCP request: ${e.message}" }
            McpResponse(
                jsonrpc = "2.0",
                id = request.id,
                error = McpError(-32603, "Internal server error: ${e.message}", JsonPrimitive(e.stackTraceToString()))
            )
        }
    }
}