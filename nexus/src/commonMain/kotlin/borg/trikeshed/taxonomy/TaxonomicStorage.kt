package borg.trikeshed.taxonomy

import com.rtsgame.storage.StratifiedJsonStorage
import com.rtsgame.storage.JsonSlab
import com.rtsgame.storage.CompressionAlgorithm
import com.rtsgame.storage.StorageTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import borg.trikeshed.parse.json.JsonParser
import borg.trikeshed.lib.*
import borg.trikeshed.lib.toSeries
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray

/**
 * Storage adapter for taxonomic entities
 */
class TaxonomicStorage(
    private val storage: StratifiedJsonStorage
) {
    /**
     * Store a taxonomic entity
     */
    suspend fun storeEntity(entity: TaxonomicEntity) {
        val jsonStr = entityToJson(entity)
        val parsed = JsonParser.reify(jsonStr.toSeries())
        val jsonElement = convertToJsonElement(parsed)
        val slab = JsonSlab(
            id = entity.id,
            data = jsonElement,
            metadata = com.rtsgame.storage.SlabMetadata(
                size = 0, // Will be calculated by storage
                lastAccess = System.currentTimeMillis(),
                accessCount = 0,
                tier = StorageTier.HOT_RAM,
                compression = CompressionAlgorithm.NONE
            )
        )
        storage.putSlab(slab)
    }

    /**
     * Store a taxonomic graph
     */
    suspend fun storeGraph(graph: TaxonomicGraph) {
        val jsonStr = graphToJson(graph)
        val parsed = JsonParser.reify(jsonStr.toSeries())
        val jsonElement = convertToJsonElement(parsed)
        val slab = JsonSlab(
            id = "graph_${System.currentTimeMillis()}",
            data = jsonElement,
            metadata = com.rtsgame.storage.SlabMetadata(
                size = 0, // Will be calculated by storage
                lastAccess = System.currentTimeMillis(),
                accessCount = 0,
                tier = StorageTier.HOT_RAM,
                compression = CompressionAlgorithm.NONE
            )
        )
        storage.putSlab(slab)
    }

    /**
     * Retrieve a taxonomic entity
     */
    suspend fun getEntity(id: SemanticId): TaxonomicEntity? {
        val slab = storage.getSlab(id) ?: return null
        return jsonToEntity(slab.data as JsonObject)
    }

    /**
     * Retrieve a taxonomic graph
     */
    suspend fun getGraph(id: String): TaxonomicGraph? {
        val slab = storage.getSlab(id) ?: return null
        return jsonToGraph(slab.data as JsonObject)
    }

    /**
     * Watch for changes to a taxonomic entity
     */
    fun watchEntity(id: SemanticId): Flow<TaxonomicEntity> {
        return storage.watchSlab(id).map { slab ->
            jsonToEntity(slab.data as JsonObject)
        }
    }

    /**
     * Watch for changes to a taxonomic graph
     */
    fun watchGraph(id: String): Flow<TaxonomicGraph> {
        return storage.watchSlab(id).map { slab ->
            jsonToGraph(slab.data as JsonObject)
        }
    }

    /**
     * Find related entities with attention scores
     */
    suspend fun findRelatedEntities(
        entityId: SemanticId,
        threshold: AttentionScore = 0.5
    ): List<Pair<SemanticId, AttentionScore>> {
        val entity = getEntity(entityId) ?: return emptyList()
        val graph = getGraph("graph_${entity.version}") ?: return emptyList()
        return graph.findRelatedEntities(entityId, threshold)
    }

    /**
     * Update entity attention scores
     */
    suspend fun updateAttention(entityId: SemanticId, newAttention: AttentionScore) {
        val entity = getEntity(entityId) ?: return
        val updatedEntity = (entity as DefaultTaxonomicEntity).copy(attention = newAttention)
        storeEntity(updatedEntity)
    }

    /**
     * Update concept vectors
     */
    suspend fun updateConcepts(entityId: SemanticId, newConcepts: ConceptVector) {
        val entity = getEntity(entityId) ?: return
        val updatedEntity = (entity as DefaultTaxonomicEntity).copy(concepts = newConcepts)
        storeEntity(updatedEntity)
    }

    /**
     * Convert entity to JSON string
     */
    private fun entityToJson(entity: TaxonomicEntity): String {
        val props = (entity as DefaultTaxonomicEntity).properties
        return buildString {
            append("""{"id":"${entity.id}","version":"${entity.version}","attention":${entity.attention},"concepts":""")
            append(entity.concepts.joinToString(",", "[", "]"))
            append("""","properties":""")
            append(props.toString())
            append("}")
        }
    }

    /**
     * Convert JSON object to entity
     */
    private fun jsonToEntity(json: JsonObject): DefaultTaxonomicEntity {
        return DefaultTaxonomicEntity(
            id = (json["id"] as? JsonPrimitive)?.content ?: "",
            version = (json["version"] as? JsonPrimitive)?.content ?: "",
            attention = (json["attention"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0,
            concepts = (json["concepts"] as? JsonArray)?.map { (it as JsonPrimitive).content.toFloatOrNull() ?: 0f } ?: emptyList(),
            properties = json["properties"] as? JsonObject ?: JsonObject(emptyMap())
        )
    }

    /**
     * Convert graph to JSON string
     */
    private fun graphToJson(graph: TaxonomicGraph): String {
        return buildString {
            append("""{"entities":{""")
            append(graph.entities.entries.joinToString(",") { (id, entity) ->
                """"$id":${entityToJson(entity)}"""
            })
            append("""},"relationships":[""")
            append(graph.relationships.joinToString(",") { rel ->
                """{"source":"${rel.source}","target":"${rel.target}","type":"${rel.type}","weight":${rel.weight},"bidirectional":${rel.bidirectional}}"""
            })
            append("""],"conceptSpace":{""")
            append(graph.conceptSpace.entries.joinToString(",") { (id, vector) ->
                """"$id":${vector.joinToString(",", "[", "]")}"""
            })
            append("}}")
        }
    }

    /**
     * Convert JSON object to graph
     */
    private fun jsonToGraph(json: JsonObject): TaxonomicGraph {
        val entities = (json["entities"] as? JsonObject)?.mapValues { (_, entityJson) ->
            jsonToEntity(entityJson as JsonObject)
        } ?: emptyMap()
        
        val relationships = (json["relationships"] as? JsonArray)?.map { rel ->
            val obj = rel as JsonObject
            TaxonomicRelationship(
                source = (obj["source"] as? JsonPrimitive)?.content ?: "",
                target = (obj["target"] as? JsonPrimitive)?.content ?: "",
                type = (obj["type"] as? JsonPrimitive)?.content ?: "",
                weight = (obj["weight"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0,
                bidirectional = (obj["bidirectional"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
            )
        } ?: emptyList()
        
        val conceptSpace = (json["conceptSpace"] as? JsonObject)?.mapValues { (_, vector) ->
            (vector as? JsonArray)?.map { (it as JsonPrimitive).content.toFloatOrNull() ?: 0f } ?: emptyList()
        } ?: emptyMap()
        
        return TaxonomicGraph(
            entities = entities,
            relationships = relationships,
            conceptSpace = conceptSpace,
            attentionModel = { a, b -> 
                // Default cosine similarity
                val dotProduct = a.zip(b) { x, y -> x.toDouble() * y.toDouble() }.sum()
                val normA = kotlin.math.sqrt(a.map { it.toDouble() * it.toDouble() }.sum())
                val normB = kotlin.math.sqrt(b.map { it.toDouble() * it.toDouble() }.sum())
                if (normA == 0.0 || normB == 0.0) 0.0 else dotProduct / (normA * normB)
            }
        )
    }

    /**
     * Convert parsed data to JsonElement
     */
    private fun convertToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonPrimitive(null)
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is List<*> -> JsonArray(value.map { convertToJsonElement(it) })
            is Map<*, *> -> JsonObject(value.mapKeys { it.key.toString() }.mapValues { convertToJsonElement(it.value) })
            else -> JsonPrimitive(value.toString())
        }
    }
} 