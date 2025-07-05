package com.superbikeshed.trikeshed

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

// RequestFactory - The polymorphic HTTP factory that grows legs
expect interface RequestFactory {
    suspend fun spawn(protocol: HTTPProtocol): Result<RequestHandle>
    suspend fun clone(): RequestFactory
    suspend fun growLegs(): RequestFactory // Enable mobility/migration
    suspend fun shed(): RequestFactory // Shed old protocols, birth new ones
}

// HTTP 0.9 through HTTP/3 QUIC
enum class HTTPProtocol {
    HTTP_0_9,   // The ancient one-liner
    HTTP_1_0,   // Connection: close
    HTTP_1_1,   // Connection: keep-alive, chunked
    HTTP_2,     // Binary framing, multiplexing
    HTTP_3      // QUIC transport
}

expect interface RequestHandle {
    val protocol: HTTPProtocol
    suspend fun execute(request: HTTPRequest): Result<HTTPResponse>
    suspend fun stream(request: HTTPRequest): Flow<HTTPChunk>
    suspend fun upgrade(to: UpgradeProtocol): Result<UpgradedConnection>
}

data class HTTPRequest(
    val method: String,
    val path: String,
    val headers: Map<String, List<String>> = emptyMap(),
    val body: ByteArray? = null,
    // HTTP/0.9 has no headers, just GET path
    val protocolHint: HTTPProtocol? = null
)

data class HTTPResponse(
    val status: Int,
    val headers: Map<String, List<String>>,
    val body: ByteArray? = null,
    val protocol: HTTPProtocol
)

data class HTTPChunk(
    val data: ByteArray,
    val extensions: Map<String, String> = emptyMap(),
    val final: Boolean = false
)

enum class UpgradeProtocol {
    WEBSOCKET,
    H2C,        // HTTP/2 cleartext
    CONNECT,    // TCP tunnel
    COUCHDB,    // Native CouchDB protocol
    TRIKESHED   // Native Trikeshed protocol
}

expect interface UpgradedConnection {
    val protocol: UpgradeProtocol
    suspend fun send(data: ByteArray): Result<Unit>
    suspend fun receive(): Result<ByteArray>
    suspend fun close()
}

// RelaxFactory - The CouchDB middleware that knows how to relax
expect interface RelaxFactory : RequestFactory {
    suspend fun relax(): CouchDBProxy
    suspend fun tense(): Result<Unit> // Opposite of relax - strict mode
    suspend fun equilibrium(): RelaxationState // Find balance
}

data class RelaxationState(
    val tensionLevel: Float, // 0.0 = fully relaxed, 1.0 = fully tense
    val activeConnections: Int,
    val pendingRequests: Int,
    val replicationLag: Long
)

expect interface CouchDBProxy {
    // Classic CouchDB REST API
    suspend fun handleRequest(request: CouchRequest): Result<CouchResponse>
    
    // Replication protocol
    suspend fun replicate(peer: CouchDBProxy): Flow<ReplicationEvent>
    
    // View engine
    suspend fun mapReduce(
        map: String,
        reduce: String?,
        options: ViewOptions
    ): Result<ViewResult>
    
    // Changes feed with long polling
    suspend fun changes(
        since: String?,
        options: ChangesOptions
    ): Flow<ChangesFeedEntry>
}

data class CouchRequest(
    val method: String,
    val database: String?,
    val document: String?,
    val path: List<String>,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: ByteArray?
)

data class CouchResponse(
    val status: Int,
    val json: Map<String, Any?>?,
    val headers: Map<String, String>
)

data class ViewOptions(
    val startKey: Any? = null,
    val endKey: Any? = null,
    val limit: Int? = null,
    val skip: Int? = null,
    val descending: Boolean = false,
    val includeDocs: Boolean = false,
    val reduce: Boolean = true,
    val group: Boolean = false,
    val groupLevel: Int? = null
)

data class ViewResult(
    val totalRows: Int,
    val offset: Int,
    val rows: List<ViewRow>
)

data class ViewRow(
    val id: String,
    val key: Any?,
    val value: Any?,
    val doc: Map<String, Any?>?
)

data class ChangesOptions(
    val feed: ChangesFeed = ChangesFeed.NORMAL,
    val style: ChangesStyle = ChangesStyle.MAIN_ONLY,
    val heartbeat: Long? = null,
    val timeout: Long? = null,
    val filter: String? = null,
    val includeDocs: Boolean = false
)

enum class ChangesFeed {
    NORMAL,
    LONGPOLL,
    CONTINUOUS,
    EVENTSOURCE
}

enum class ChangesStyle {
    MAIN_ONLY,
    ALL_DOCS
}

data class ChangesFeedEntry(
    val seq: String,
    val id: String,
    val changes: List<RevisionChange>,
    val deleted: Boolean = false,
    val doc: Map<String, Any?>? = null
)

data class RevisionChange(
    val rev: String
)

data class ReplicationEvent(
    val type: ReplicationType,
    val seq: String,
    val docs: List<String>
)

enum class ReplicationType {
    CHECKPOINT,
    DOCUMENT,
    ATTACHMENT,
    VIEW_UPDATE,
    CONFLICT,
    ERROR
}

// Legacy GWT RequestFactory compatibility
expect interface GWTRequestFactory : RequestFactory {
    suspend fun createProxy(token: String): Result<ServiceProxy>
    suspend fun makeRequest(): GWTRequest
}

expect interface ServiceProxy {
    suspend fun invoke(method: String, args: List<Any?>): Result<Any?>
}

expect interface GWTRequest {
    fun <T> call(method: String): GWTInvocation<T>
    suspend fun fire(): Result<Unit>
}

value class GWTInvocation<T>(val method: String)

// The Factory Factory - because we need to manufacture factories
expect interface FactoryFactory {
    suspend fun createRequestFactory(config: FactoryConfig): RequestFactory
    suspend fun createRelaxFactory(config: RelaxConfig): RelaxFactory
    suspend fun merge(vararg factories: RequestFactory): RequestFactory
}

data class FactoryConfig(
    val protocols: Set<HTTPProtocol>,
    val maxConnections: Int = 100,
    val growthEnabled: Boolean = true,
    val cloneDepth: Int = 3
)

data class RelaxConfig(
    val couchDBUrl: String,
    val replicationEnabled: Boolean = true,
    val viewEngineEnabled: Boolean = true,
    val conflictResolution: ConflictStrategy = ConflictStrategy.LATEST_WINS
)

enum class ConflictStrategy {
    LATEST_WINS,
    MERGE_ALL,
    MANUAL,
    TRIKESHED_CONSENSUS
}