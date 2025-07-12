@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.services

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.datetime.*
import kotlinx.serialization.*

/**
 * Modern CRDT-based Request Factory
 * Replaces ancient GWT RequestFactory with modern CRDT collaboration
 */
class CRDTRequestFactory {
    
    // Modern CRDT entity registry
    private val entities = mutableMapOf<String, CRDTEntity<*>>()
    private val collaborationSessions = mutableMapOf<String, Session>()
    
    /**
     * Process request with CRDT merge semantics
     */
    suspend fun processRequest(request: RequestData): ResponseData {
        val entity = entities[request.entityId]
        return if (entity != null) {
            val merged = entity.merge(request.operation)
            entities[request.entityId] = merged
            ResponseData(merged.id, merged.version, "success")
        } else {
            ResponseData("", 0, "entity_not_found")
        }
    }
    
    /**
     * Register CRDT entity
     */
    fun <T> registerEntity(entity: CRDTEntity<T>) {
        entities[entity.id] = entity
    }
    
    /**
     * Start collaboration session
     */
    fun startSession(sessionId: String): Session {
        val session = Session(sessionId, Clock.System.now().toEpochMilliseconds())
        collaborationSessions[sessionId] = session
        return session
    }
}

/**
 * CRDT Entity with merge semantics
 */
interface CRDTEntity<T> {
    val id: String
    val version: Long
    val data: T
    
    fun merge(operation: Operation): CRDTEntity<T>
}

/**
 * Basic CRDT implementation
 */
class BasicCRDTEntity<T>(
    override val id: String,
    override val version: Long,
    override val data: T
) : CRDTEntity<T> {
    
    override fun merge(operation: Operation): CRDTEntity<T> {
        return BasicCRDTEntity(id, version + 1, data)
    }
}

/**
 * Request/Response data structures
 */
data class RequestData(
    val entityId: String,
    val operation: Operation
)

data class ResponseData(
    val entityId: String,
    val version: Long,
    val status: String
)

data class Operation(
    val type: String,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val data: Map<String, Any> = emptyMap()
)

data class Session(
    val id: String,
    val startTime: Long
)