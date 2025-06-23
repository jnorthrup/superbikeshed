package borg.trikeshed.spacegraph

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.io.core.toByteArray
import java.security.MessageDigest

@Serializable
data class CloudWord(val word: String, val weight: Double)
@Serializable
data class Evidence(val source: String, val offset: Int? = null, val timestamp: String? = null)
@Serializable
data class EditHistory(val editor: String, val field: String, val old: String, val new: String, val timestamp: Long)
@Serializable
data class TrikeshedFields(val claim: String = "", val form: String = "")
@Serializable
data class TrikeshedNodeDoc(
    @SerialName("_id") val id: String,
    val type: String = "trikeshed",
    val label: String = "",
    val cloudwords: List<CloudWord> = emptyList(),
    val evidence: List<Evidence> = emptyList(),
    val confidence: Double = 0.0,
    val embedding: List<Double> = emptyList(),
    val fields: TrikeshedFields = TrikeshedFields(),
    val history: List<EditHistory> = emptyList(),
    @SerialName("_rev") val rev: String? = null
)

object TrikeshedCouchSync {
    private const val COUCH_URL = "http://localhost:5984/trikeshed_nodes"
    private val client = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true }

    // Content-addressable cache: hash -> node
    private val nodeCache = mutableMapOf<String, TrikeshedNodeDoc>()

    private fun contentHash(doc: TrikeshedNodeDoc): String {
        val canonicalJson = json.encodeToString(doc)
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(canonicalJson.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun fetchAllTrikeshedNodes(): List<TrikeshedNodeDoc> {
        val result: JsonObject = client.get("$COUCH_URL/_all_docs?include_docs=true").body()
        val rows = result["rows"]?.jsonArray ?: return emptyList()
        return rows.mapNotNull { row ->
            row.jsonObject["doc"]?.let { docJson ->
                runCatching { json.decodeFromJsonElement<TrikeshedNodeDoc>(docJson) }.getOrNull()?.also { doc ->
                    val hash = contentHash(doc)
                    nodeCache[hash] = doc
                }
            }
        }
    }

    suspend fun getNodeByIdCached(nodeId: String): TrikeshedNodeDoc? {
        val doc: TrikeshedNodeDoc = client.get("$COUCH_URL/$nodeId").body()
        val hash = contentHash(doc)
        return nodeCache[hash] ?: run {
            nodeCache[hash] = doc
            doc
        }
    }

    fun toSpaceGraphNode(doc: TrikeshedNodeDoc): Map<String, Any?> = mapOf(
        "id" to doc.id,
        "label" to doc.label,
        "type" to doc.type,
        "data" to mapOf(
            "cloudwords" to doc.cloudwords,
            "evidence" to doc.evidence,
            "confidence" to doc.confidence,
            "embedding" to doc.embedding,
            "fields" to doc.fields,
            "history" to doc.history
        )
    )

    suspend fun updateField(
        nodeId: String,
        field: String,
        newValue: String,
        editor: String
    ): Boolean {
        // Fetch current doc
        val doc: TrikeshedNodeDoc = client.get("$COUCH_URL/$nodeId").body()
        val oldValue = when (field) {
            "claim" -> doc.fields.claim
            "form" -> doc.fields.form
            else -> ""
        }
        val newFields = when (field) {
            "claim" -> doc.fields.copy(claim = newValue)
            "form" -> doc.fields.copy(form = newValue)
            else -> doc.fields
        }
        val newHistory = doc.history + EditHistory(
            editor = editor,
            field = field,
            old = oldValue,
            new = newValue,
            timestamp = System.currentTimeMillis()
        )
        val updatedDoc = doc.copy(fields = newFields, history = newHistory)
        val putResult: HttpResponse = client.put("$COUCH_URL/$nodeId") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(updatedDoc))
        }
        // Update cache
        val hash = contentHash(updatedDoc)
        nodeCache[hash] = updatedDoc
        return putResult.status.isSuccess()
    }
} 