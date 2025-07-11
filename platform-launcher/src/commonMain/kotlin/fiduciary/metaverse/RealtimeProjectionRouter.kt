package fiduciary.metaverse

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.datetime.Clock

/**
 * Realtime Projection Router
 * 
 * Handles projecting updates to other routers in the metaverse:
 * - Real-time projection of operations
 * - Git commit broadcasting
 * - Participant join/leave notifications
 * - Document change propagation
 * - Cross-router synchronization
 */
class RealtimeProjectionRouter {
    
    // Active projection targets
    internal val projectionTargets = mutableMapOf<String, ProjectionTarget>()
    
    // Projection subscriptions
    internal val subscriptions = mutableMapOf<String, MutableList<(Projection) -> Unit>>()
    
    // Router connections
    internal val routerConnections = mutableMapOf<String, RouterConnection>()
    
    // Projection history
    internal val projectionHistory = mutableMapOf<String, MutableList<Projection>>()

    /**
     * Register a projection target
     */
    fun registerTarget(target: ProjectionTarget) {
        projectionTargets[target.routerId] = target
    }

    /**
     * Unregister a projection target
     */
    fun unregisterTarget(routerId: String) {
        projectionTargets.remove(routerId)
        routerConnections.remove(routerId)
    }

    /**
     * Subscribe to projections
     */
    fun subscribeToProjections(
        routerId: String,
        callback: (Projection) -> Unit
    ) {
        subscriptions.getOrPut(routerId) { mutableListOf() }.add(callback)
    }

    /**
     * Unsubscribe from projections
     */
    fun unsubscribeFromProjections(
        routerId: String,
        callback: (Projection) -> Unit
    ) {
        subscriptions[routerId]?.remove(callback)
    }

    /**
     * Project participant join
     */
    suspend fun projectParticipantJoin(
        sessionId: String,
        participant: FiduciaryParticipant
    ) {
        val projection = Projection(
            sessionId = sessionId,
            routerId = "all",
            type = ProjectionType.PARTICIPANT_JOIN,
            data = mapOf(
                "participantId" to participant.id,
                "pseudonym" to participant.pseudonym,
                "timestamp" to Clock.System.now().toEpochMilliseconds()
            ),
            timestamp = Clock.System.now().toEpochMilliseconds()
        )
        
        sendProjection(projection)
    }

    /**
     * Project operation
     */
    suspend fun projectOperation(
        sessionId: String,
        operation: WaveOperation,
        gitCommit: GitCommit
    ) {
        val projection = Projection(
            sessionId = sessionId,
            routerId = "all",
            type = ProjectionType.OPERATION_APPLIED,
            data = mapOf(
                "operationId" to operation.operationId,
                "operationType" to operation.type.name,
                "participantId" to operation.participantId,
                "content" to operation.content,
                "position" to operation.position,
                "gitCommitId" to gitCommit.commitId,
                "gitMessage" to gitCommit.message,
                "timestamp" to operation.timestamp
            ),
            timestamp = Clock.System.now().toEpochMilliseconds()
        )
        
        sendProjection(projection)
    }

    /**
     * Project document change
     */
    suspend fun projectDocumentChange(
        sessionId: String,
        change: CouchChange
    ) {
        val projection = Projection(
            sessionId = sessionId,
            routerId = "all",
            type = ProjectionType.DOCUMENT_CHANGED,
            data = mapOf<String, Any>(
                "documentId" to change.id,
                "sequence" to change.seq,
                "deleted" to change.deleted,
                "revision" to (change.changes.firstOrNull()?.rev ?: ""),
                "timestamp" to Clock.System.now().toEpochMilliseconds()
            ),
            timestamp = Clock.System.now().toEpochMilliseconds()
        )
        
        sendProjection(projection)
    }

    /**
     * Project git commit
     */
    suspend fun projectGitCommit(
        sessionId: String,
        commit: GitCommit
    ) {
        val projection = Projection(
            sessionId = sessionId,
            routerId = "all",
            type = ProjectionType.GIT_COMMIT,
            data = mapOf(
                "commitId" to commit.commitId,
                "parentCommitId" to (commit.parentCommitId ?: ""),
                "author" to commit.author,
                "message" to commit.message,
                "waveletId" to commit.waveletId,
                "timestamp" to commit.timestamp
            ),
            timestamp = Clock.System.now().toEpochMilliseconds()
        )
        
        sendProjection(projection)
    }

    /**
     * Send projection to all targets
     */
    suspend fun sendProjection(projection: Projection) {
        // Store in history
        projectionHistory.getOrPut(projection.sessionId) { mutableListOf() }.add(projection)
        
        // Send to all targets
        projectionTargets.values.forEach { target ->
            try {
                target.receiveProjection(projection)
            } catch (e: Exception) {
                println("Error sending projection to ${target.routerId}: ${e.message}")
            }
        }
        
        // Notify subscribers
        subscriptions[projection.routerId]?.forEach { callback ->
            try {
                callback(projection)
            } catch (e: Exception) {
                println("Error in projection callback: ${e.message}")
            }
        }
    }

    /**
     * Get projection history for a session
     */
    suspend fun getProjectionHistory(
        sessionId: String,
        limit: Int = 100
    ): List<Projection> {
        return projectionHistory[sessionId]?.takeLast(limit) ?: emptyList()
    }

    /**
     * Connect to another router
     */
    suspend fun connectToRouter(
        routerId: String,
        connectionInfo: RouterConnectionInfo
    ): ConnectionResult {
        return try {
            val connection = RouterConnection(
                routerId = routerId,
                connectionInfo = connectionInfo,
                isConnected = true,
                lastHeartbeat = Clock.System.now().toEpochMilliseconds()
            )
            
            routerConnections[routerId] = connection
            
            // Register as projection target
            val target = ProjectionTarget(
                routerId = routerId,
                connectionInfo = connectionInfo,
                router = this
            )
            registerTarget(target)
            
            ConnectionResult.Success(connection)
            
        } catch (e: Exception) {
            ConnectionResult.Failure("Failed to connect to router: ${e.message}")
        }
    }

    /**
     * Disconnect from a router
     */
    suspend fun disconnectFromRouter(routerId: String): Boolean {
        val connection = routerConnections[routerId] ?: return false
        
        connection.isConnected = false
        routerConnections.remove(routerId)
        unregisterTarget(routerId)
        
        return true
    }

    /**
     * Get connected routers
     */
    suspend fun getConnectedRouters(): List<RouterConnection> {
        return routerConnections.values.filter { it.isConnected }.toList()
    }

    /**
     * Send heartbeat to connected routers
     */
    suspend fun sendHeartbeat() {
        val heartbeat = Projection(
            sessionId = "system",
            routerId = "all",
            type = ProjectionType.HEARTBEAT,
            data = mapOf(
                "timestamp" to Clock.System.now().toEpochMilliseconds()
            ),
            timestamp = Clock.System.now().toEpochMilliseconds()
        )
        
        sendProjection(heartbeat)
        
        // Update last heartbeat for connected routers
        routerConnections.values.forEach { connection ->
            connection.lastHeartbeat = Clock.System.now().toEpochMilliseconds()
        }
    }

    /**
     * Get projection statistics
     */
    suspend fun getProjectionStats(): ProjectionStats {
        val totalProjections = projectionHistory.values.sumOf { it.size }
        val activeTargets = projectionTargets.size
        val connectedRouters = routerConnections.values.count { it.isConnected }
        
        return ProjectionStats(
            totalProjections = totalProjections,
            activeTargets = activeTargets,
            connectedRouters = connectedRouters,
            lastHeartbeat = Clock.System.now().toEpochMilliseconds()
        )
    }
}

/**
 * Projection Target
 */
class ProjectionTarget(
    val routerId: String,
    val connectionInfo: RouterConnectionInfo,
    internal val router: RealtimeProjectionRouter
) {
    
    suspend fun receiveProjection(projection: Projection) {
        // In real implementation, would send to actual router
        // For demo, we'll just log the projection
        println("Router $routerId received projection: ${projection.type}")
    }
}

/**
 * Router Connection
 */
data class RouterConnection(
    val routerId: String,
    val connectionInfo: RouterConnectionInfo,
    var isConnected: Boolean,
    var lastHeartbeat: Long
)

/**
 * Router Connection Info
 */
data class RouterConnectionInfo(
    val host: String,
    val port: Int,
    val protocol: String = "http",
    val credentials: Map<String, String> = emptyMap()
)

/**
 * Projection Stats
 */
data class ProjectionStats(
    val totalProjections: Int,
    val activeTargets: Int,
    val connectedRouters: Int,
    val lastHeartbeat: Long
)

// Result types

sealed class ConnectionResult {
    data class Success(val connection: RouterConnection) : ConnectionResult()
    data class Failure(val error: String) : ConnectionResult()
} 