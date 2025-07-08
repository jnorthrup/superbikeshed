package com.v2superbikeshed.nexus.rpc.mcp_adapters

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Blob hosting support for Viola, GWT, and other systems
 * These systems are hosted as-is without integration requirements
 */
interface BlobHostingService {
    suspend fun store(namespace: String, key: String, blob: ByteArray): Result<BlobMetadata>
    suspend fun retrieve(namespace: String, key: String): Result<ByteArray?>
    suspend fun list(namespace: String): Flow<BlobMetadata>
    suspend fun delete(namespace: String, key: String): Result<Boolean>
    suspend fun getMetadata(namespace: String, key: String): Result<BlobMetadata?>
}

data class BlobMetadata(
    val namespace: String,
    val key: String,
    val size: Long,
    val contentType: String,
    val hash: String,
    val created: Long,
    val modified: Long,
    val attributes: Map<String, String> = emptyMap()
)

/**
 * Namespaces for different hosted systems
 */
object BlobNamespaces {
    const val VIOLA = "viola"
    const val GWT = "gwt"
    const val REQUEST_FACTORY = "requestfactory"
    const val LEGACY_SYSTEMS = "legacy"
}

/**
 * CouchDB-backed blob storage
 */
class CouchDBBlobHosting(
    internal val couchDB: CouchDBConnection
) : BlobHostingService {
    
    override suspend fun store(namespace: String, key: String, blob: ByteArray): Result<BlobMetadata> {
        val metadata = BlobMetadata(
            namespace = namespace,
            key = key,
            size = blob.size.toLong(),
            contentType = detectContentType(blob),
            hash = computeHash(blob),
            created = System.kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            modified = System.kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )
        
        // Store in CouchDB with attachments
        val doc = Document(
            id = "$namespace:$key",
            rev = null,
            data = mapOf(
                "type" to "blob",
                "namespace" to namespace,
                "metadata" to metadata
            ),
            attachments = mapOf(
                "blob" to Attachment(
                    contentType = metadata.contentType,
                    length = metadata.size,
                    digest = metadata.hash,
                    data = blob
                )
            )
        )
        
        return couchDB.putDocument("blobs", doc).map { metadata }
    }
    
    override suspend fun retrieve(namespace: String, key: String): Result<ByteArray?> {
        return couchDB.getDocument("blobs", "$namespace:$key").mapCatching { doc ->
            doc.attachments?.get("blob")?.data
        }
    }
    
    override suspend fun list(namespace: String): Flow<BlobMetadata> {
        // Use CouchDB view to list blobs by namespace
        TODO("Implement CouchDB view query")
    }
    
    override suspend fun delete(namespace: String, key: String): Result<Boolean> {
        return couchDB.getDocument("blobs", "$namespace:$key").mapCatching { doc ->
            doc.rev?.let { rev ->
                couchDB.deleteDocument("blobs", doc.id, rev)
                true
            } ?: false
        }.getOrElse { false }.let { Result.success(it) }
    }
    
    override suspend fun getMetadata(namespace: String, key: String): Result<BlobMetadata?> {
        return couchDB.getDocument("blobs", "$namespace:$key").mapCatching { doc ->
            @Suppress("UNCHECKED_CAST")
            (doc.data["metadata"] as? Map<String, Any>)?.let { metadata ->
                BlobMetadata(
                    namespace = metadata["namespace"] as String,
                    key = metadata["key"] as String,
                    size = (metadata["size"] as Number).toLong(),
                    contentType = metadata["contentType"] as String,
                    hash = metadata["hash"] as String,
                    created = (metadata["created"] as Number).toLong(),
                    modified = (metadata["modified"] as Number).toLong(),
                    attributes = (metadata["attributes"] as? Map<String, String>) ?: emptyMap()
                )
            }
        }
    }
    
    internal fun detectContentType(blob: ByteArray): String {
        // Simple content type detection
        return when {
            blob.size >= 4 && blob[0] == 0x50.toByte() && blob[1] == 0x4B.toByte() -> "application/zip"
            blob.size >= 2 && blob[0] == 0xFF.toByte() && blob[1] == 0xD8.toByte() -> "image/jpeg"
            blob.size >= 8 && blob.sliceArray(0..7).contentEquals("<!DOCTYPE".toByteArray()) -> "text/html"
            blob.size >= 15 && blob.sliceArray(0..14).decodeToString().contains("javascript", true) -> "application/javascript"
            else -> "application/octet-stream"
        }
    }
    
    internal fun computeHash(blob: ByteArray): String {
        // Simple hash for now - would use proper crypto
        var hash = 0L
        for (byte in blob) {
            hash = hash * 31 + byte
        }
        return hash.toString(16)
    }
}

/**
 * RequestFactory support - maintains compatibility with GWT RequestFactory
 * but implemented in pure KMP
 */
interface RequestFactoryService {
    suspend fun processRequest(request: RequestFactoryRequest): Result<RequestFactoryResponse>
}

data class RequestFactoryRequest(
    val version: String,
    val operations: List<Operation>
) {
    @Serializable
    data class Operation(
        val type: String,
        val method: String,
        val parameters: Map<String, Any?>
    )
}

data class RequestFactoryResponse(
    val version: String,
    val results: List<OperationResult>
) {
    @Serializable
    data class OperationResult(
        val value: Any?,
        val error: String? = null
    )
}

/**
 * Bridge to support Viola playground without integration
 */
class ViolaBlobSupport(
    internal val blobHosting: BlobHostingService
) {
    suspend fun storeCompiledGWT(projectId: String, artifact: ByteArray): Result<BlobMetadata> {
        return blobHosting.store(BlobNamespaces.VIOLA, "compiled/$projectId", artifact)
    }
    
    suspend fun retrieveCompiledGWT(projectId: String): Result<ByteArray?> {
        return blobHosting.retrieve(BlobNamespaces.VIOLA, "compiled/$projectId")
    }
    
    suspend fun storeSourceCode(projectId: String, source: String): Result<BlobMetadata> {
        return blobHosting.store(
            BlobNamespaces.VIOLA, 
            "source/$projectId", 
            source.encodeToByteArray()
        )
    }
}

/**
 * A/B test support - routes between blob-hosted systems
 */
class ABTestRouter(
    internal val blobHosting: BlobHostingService,
    internal val dht: ConcentricDHT
) {
    suspend fun route(request: PlaygroundRequest): ABCohort {
        // Use DHT for consistent hashing
        val key = DynamicKey(request.sessionId.encodeToByteArray(), 160)
        val nodeId = dht.route(key).getOrNull()?.firstOrNull()?.id
        
        return when (nodeId?.key?.bits?.get(0)?.toInt()?.and(1)) {
            0 -> ABCohort.CONTROL  // Viola
            else -> ABCohort.TREATMENT  // Kotlin Playground
        }
    }
}

data class PlaygroundRequest(
    val sessionId: String,
    val sourceCode: String,
    val language: String
)

enum class ABCohort {
    CONTROL,    // Viola/GWT
    TREATMENT   // Kotlin Playground
}