@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.launcher

import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

/**
 * CouchDB Lattice Views Extension
 * 
 * Implements lattice operations through CouchDB views
 * Written AFTER tests (TDD approach)
 */

// Extension functions for UringCouchDBServer to support lattice views
fun UringCouchDBServer.createLatticeView(
    database: String,
    designDocName: String,
    viewDesign: JsonObject
): LatticeViewResult {
    try {
        // Create or update design document
        val designDocId = "_design/$designDocName"
        val response = handleRestRequest(
            method = "PUT",
            path = "/$database/$designDocId",
            body = viewDesign.toString()
        )
        
        if (response.statusCode == 201 || response.statusCode == 200) {
            // Extract view name from design
            val views = viewDesign["views"]?.jsonObject
            val viewName = views?.keys?.firstOrNull()
            
            return LatticeViewResult(
                success = true,
                viewName = viewName
            )
        } else {
            return LatticeViewResult(
                success = false,
                error = "Failed to create view: ${response.body}"
            )
        }
    } catch (e: Exception) {
        return LatticeViewResult(
            success = false,
            error = e.message
        )
    }
}

fun UringCouchDBServer.queryLatticeView(
    database: String,
    designDoc: String,
    viewName: String,
    key: Any? = null,
    startKey: Any? = null,
    endKey: Any? = null,
    descending: Boolean = false
): LatticeQueryResult {
    // Build query parameters
    val params = mutableListOf<String>()
    
    when {
        key != null -> {
            val keyJson = when (key) {
                is String -> JsonPrimitive(key)
                is Number -> JsonPrimitive(key)
                is Boolean -> JsonPrimitive(key)
                is JsonElement -> key
                else -> JsonPrimitive(key.toString())
            }
            params.add("key=${Json.encodeToString(JsonElement.serializer(), keyJson)}")
        }
        startKey != null -> {
            val startKeyJson = when (startKey) {
                is String -> JsonPrimitive(startKey)
                is Number -> JsonPrimitive(startKey)
                is JsonArray -> startKey
                else -> JsonPrimitive(startKey.toString())
            }
            params.add("startkey=${Json.encodeToString(JsonElement.serializer(), startKeyJson)}")
            
            if (endKey != null) {
                val endKeyJson = when (endKey) {
                    is String -> JsonPrimitive(endKey)
                    is Number -> JsonPrimitive(endKey)
                    is JsonArray -> endKey
                    else -> JsonPrimitive(endKey.toString())
                }
                params.add("endkey=${Json.encodeToString(JsonElement.serializer(), endKeyJson)}")
            }
        }
    }
    
    if (descending) {
        params.add("descending=true")
    }
    
    val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
    val path = "/$database/_design/$designDoc/_view/$viewName$queryString"
    
    val response = handleRestRequest("GET", path, null)
    
    if (response.statusCode == 200) {
        val json = Json.parseToJsonElement(response.body).jsonObject
        val rows = json["rows"]?.jsonArray ?: JsonArray(emptyList())
        
        val latticeRows = rows.map { row ->
            val rowObj = row.jsonObject
            LatticeRow(
                id = rowObj["id"]?.jsonPrimitive?.content ?: "",
                key = parseKey(rowObj["key"]),
                value = parseValue(rowObj["value"])
            )
        }
        
        return LatticeQueryResult(
            rows = latticeRows,
            totalRows = json["total_rows"]?.jsonPrimitive?.int ?: latticeRows.size
        )
    } else {
        // Return empty result on error
        return LatticeQueryResult(emptyList(), 0)
    }
}

// Helper functions to parse view results
private fun parseKey(key: JsonElement?): Any {
    return when (key) {
        is JsonPrimitive -> {
            when {
                key.isString -> key.content
                key.booleanOrNull != null -> key.boolean
                key.intOrNull != null -> key.int
                key.doubleOrNull != null -> key.double
                else -> key.content
            }
        }
        is JsonArray -> key
        is JsonObject -> key
        else -> ""
    }
}

private fun parseValue(value: JsonElement?): Map<String, Any> {
    val result = mutableMapOf<String, Any>()
    
    when (value) {
        is JsonObject -> {
            value.forEach { (k, v) ->
                result[k] = when (v) {
                    is JsonPrimitive -> {
                        when {
                            v.isString -> v.content
                            v.booleanOrNull != null -> v.boolean
                            v.intOrNull != null -> v.int
                            v.doubleOrNull != null -> v.double
                            else -> v.content
                        }
                    }
                    is JsonArray -> v
                    is JsonObject -> parseValue(v)
                    else -> v.toString()
                }
            }
        }
        is JsonPrimitive -> {
            result["value"] = when {
                value.isString -> value.content
                value.booleanOrNull != null -> value.boolean
                value.intOrNull != null -> value.int
                value.doubleOrNull != null -> value.double
                else -> value.content
            }
        }
        else -> {
            result["value"] = value.toString()
        }
    }
    
    return result
}

/**
 * Lattice-specific view templates
 */
object LatticeViewTemplates {
    
    /**
     * Create a standard concept hierarchy view
     */
    fun conceptHierarchyView(): JsonObject = buildJsonObject {
        put("_id", "_design/lattice")
        put("views", buildJsonObject {
            put("concept_hierarchy", buildJsonObject {
                put("map", """
                    function(doc) {
                        if (doc.type === 'concept' && doc.parent) {
                            emit(doc.parent, {
                                child: doc._id,
                                name: doc.name,
                                level: doc.level || 0
                            });
                        }
                    }
                """.trimIndent())
                put("reduce", "_count")
            })
        })
    }
    
    /**
     * Create a lattice join detection view
     */
    fun latticeJoinView(): JsonObject = buildJsonObject {
        put("_id", "_design/lattice_ops")
        put("views", buildJsonObject {
            put("find_join", buildJsonObject {
                put("map", """
                    function(doc) {
                        if (doc.type === 'concept' && doc.parents && doc.parents.length > 1) {
                            // Emit all parent combinations
                            for (var i = 0; i < doc.parents.length; i++) {
                                for (var j = i + 1; j < doc.parents.length; j++) {
                                    emit([doc.parents[i], doc.parents[j]], {
                                        join: doc._id,
                                        name: doc.name
                                    });
                                }
                            }
                        }
                    }
                """.trimIndent())
            })
        })
    }
    
    /**
     * Create a fiduciary attention view
     */
    fun fiduciaryAttentionView(): JsonObject = buildJsonObject {
        put("_id", "_design/fiduciary_lattice")
        put("views", buildJsonObject {
            put("by_attention", buildJsonObject {
                put("map", """
                    function(doc) {
                        if (doc.type === 'concept' && doc.attention_required) {
                            emit(doc.attention_level || 0, {
                                id: doc._id,
                                name: doc.name,
                                interest_score: doc.interest_score || 0,
                                last_accessed: doc.last_accessed
                            });
                        }
                    }
                """.trimIndent())
            })
        })
    }
}

/**
 * Lattice operations helper
 */
class LatticeOperations(private val server: UringCouchDBServer) {
    
    /**
     * Find the join (least upper bound) of two concepts
     */
    suspend fun finddatabase: String j concept1: String, concept2: String: String? {
        val result = server.queryLatticeView(
            database = database,
            designDoc = "lattice_ops",
            viewName = "find_join",
            key = JsonArray(listOf(JsonPrimitive(concept1), JsonPrimitive(concept2)))
        )
        
        return result.rows.firstOrNull()?.value?.get("join") as? String
    }
    
    /**
     * Find all children of a concept
     */
    suspend fun findChildren(database: String, parentConcept: String): List<String> {
        val result = server.queryLatticeView(
            database = database,
            designDoc = "lattice",
            viewName = "concept_hierarchy",
            key = parentConcept
        )
        
        return result.rows.mapNotNull { row ->
            row.value["child"] as? String
        }
    }
    
    /**
     * Get concepts by attention level (fiduciary focus)
     */
    suspend fun getHighAttentionConcepts(database: String, minLevel: Int = 5): List<Map<String, Any>> {
        val result = server.queryLatticeView(
            database = database,
            designDoc = "fiduciary_lattice",
            viewName = "by_attention",
            startKey = minLevel,
            descending = true
        )
        
        return result.rows.map { it.value }
    }
}