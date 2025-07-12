package fiduciary.metaverse

import borg.trikeshed.lib.*
import borg.trikeshed.services.*
import borg.trikeshed.couchdb.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.random.Random
import java.time.Instant

/**
 * Fiduciary Blackboard Metaverse - Neal Stephenson's True Vision
 * 
 * Integrates:
 * - Apache Wave CRDT for real-time collaboration
 * - No-GWQT RequestFactory with autobeans delegates
 * - JPA integration for persistence
 * - CouchDB for realtime document recording
 * - Git commit history from wavelets
 * - Realtime projection to other routers
 */
class FiduciaryBlackboardMetaverse(
    internal val waveEngine: WaveCRDTEngine = WaveCRDTEngine(),
    internal val requestFactory: EvolvedRequestFactory,
    internal val couchService: CouchDBService = CouchDBService(),
    internal val gitHistoryService: GitHistoryService = GitHistoryService(),
    internal val projectionRouter: RealtimeProjectionRouter = RealtimeProjectionRouter(),
    internal val autobeansRegistry: AutobeansDelegateRegistry = AutobeansDelegateRegistry()
) {
    
    // Active collaboration sessions
    internal val activeSessions = mutableMapOf<String, FiduciarySession>()
    
    // Blackboard subspaces
    internal val blackboardSubspaces = mutableMapOf<String, BlackboardSubspace>()
    
    // Git commit history from wavelets
    internal val waveletGitHistory = mutableMapOf<String, GitCommitHistory>()
    
    // Realtime projection targets
    internal val projectionTargets = mutableMapOf<String, ProjectionTarget>()

    /**
     * Create a new fiduciary collaboration session
     */
    suspend fun createSession(
        sessionId: String,
        subspaceId: String,
        initialDocument: String = ""
    ): FiduciarySession {
        
        // Create Wave CRDT session
        val waveSession = waveEngine.createSession(sessionId)
        
        // Create CouchDB document for realtime recording
        val couchDoc = CouchDocument(
            id = "session-$sessionId",
            data = mapOf(
                "sessionId" to JsonPrimitive(sessionId),
                "subspaceId" to JsonPrimitive(subspaceId),
                "createdAt" to JsonPrimitive(Instant.now().toString()),
                "content" to JsonPrimitive(initialDocument),
                "participants" to JsonArray(emptyList()),
                "operations" to JsonArray(emptyList())
            )
        )
        
        val savedDoc = couchService.saveDocument("fiduciary_sessions", couchDoc)
        
        // Initialize git history for this session
        val gitHistory = gitHistoryService.initializeHistory(sessionId, initialDocument)
        waveletGitHistory[sessionId] = gitHistory
        
        // Create fiduciary session
        val session = FiduciarySession(
            id = sessionId,
            subspaceId = subspaceId,
            waveSession = waveSession,
            couchDocument = savedDoc,
            gitHistory = gitHistory,
            autobeansRegistry = autobeansRegistry
        )
        
        activeSessions[sessionId] = session
        
        // Subscribe to realtime updates
        subscribeToRealtimeUpdates(session)
        
        return session
    }

    /**
     * Join an existing fiduciary session
     */
    suspend fun joinSession(
        sessionId: String,
        participantId: String,
        pseudonym: String? = null
    ): FiduciaryParticipant {
        
        val session = activeSessions[sessionId]
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        
        // Join Wave session
        val waveParticipant = waveEngine.joinSession(sessionId, participantId)
        
        // Create participant with autobeans delegate
        val participant = FiduciaryParticipant(
            id = participantId,
            pseudonym = pseudonym ?: generatePseudonym(),
            session = session,
            waveParticipant = waveParticipant,
            autobeansDelegate = autobeansRegistry.createDelegate(participantId)
        )
        
        // Update CouchDB document
        updateParticipantInCouch(session, participant)
        
        // Project participant join to other routers
        projectionRouter.projectParticipantsessionId j participant
        
        return participant
    }

    /**
     * Contribute knowledge to the blackboard
     */
    suspend fun contributeKnowledge(
        sessionId: String,
        participantId: String,
        knowledge: KnowledgeFragment,
        operationType: WaveOperationType = WaveOperationType.INSERT
    ): ContributionResult {
        
        val session = activeSessions[sessionId]
            ?: return ContributionResult.Failure("Session not found")
        
        val participant = session.getParticipant(participantId)
            ?: return ContributionResult.Failure("Participant not found")
        
        // Create Wave operation
        val waveOperation = WaveOperation(
            operationId = generateOperationId(),
            type = operationType,
            participantId = participantId,
            content = knowledge.content,
            position = knowledge.position,
            timestamp = Instant.now().toEpochMilliseconds()
        )
        
        // Apply to Wave CRDT
        val waveResult = waveEngine.applyOperation(sessionId, waveOperation)
        
        // Update CouchDB document
        val updatedDoc = updateDocumentInCouch(session, waveOperation)
        
        // Create git commit from wavelet
        val gitCommit = gitHistoryService.createCommitFromWavelet(
            sessionId = sessionId,
            operation = waveOperation,
            documentState = updatedDoc
        )
        
        // Update git history
        session.gitHistory.addCommit(gitCommit)
        
        // Project to other routers
        projectionRouter.projectOperation(sessionId, waveOperation, gitCommit)
        
        // Apply autobeans delegate processing
        val delegateResult = participant.autobeansDelegate.processKnowledge(knowledge)
        
        return ContributionResult.Success(
            operationId = waveOperation.operationId,
            gitCommitId = gitCommit.commitId,
            delegateResult = delegateResult
        )
    }

    /**
     * Query knowledge from the blackboard
     */
    suspend fun queryKnowledge(
        sessionId: String,
        query: KnowledgeQuery
    ): QueryResult {
        
        val session = activeSessions[sessionId]
            ?: return QueryResult.Failure("Session not found")
        
        // Query Wave CRDT state
        val waveState = waveEngine.getDocumentState(sessionId)
        
        // Query CouchDB for historical data
        val couchResults = couchService.queryDocuments(
            database = "fiduciary_sessions",
            query = mapOf(
                "sessionId" to sessionId,
                "timestamp" to mapOf(
                    "\$gte" to (Instant.now().toEpochMilliseconds() - query.timeWindow)
                )
            )
        )
        
        // Query git history
        val gitHistory = session.gitHistory.queryHistory(query)
        
        // Combine results
        val combinedResults = combineQueryResults(waveState, couchResults, gitHistory)
        
        return QueryResult.Success(combinedResults)
    }

    /**
     * Create a blackboard subspace
     */
    suspend fun createSubspace(
        subspaceId: String,
        name: String,
        description: String,
        creatorId: String,
        privacyLevel: PrivacyLevel = PrivacyLevel.PSEUDONYMOUS
    ): SubspaceResult {
        
        // Create CouchDB database for subspace
        val databaseCreated = couchService.createDatabase("subspace_$subspaceId")
        
        if (!databaseCreated) {
            return SubspaceResult.Failure("Failed to create database")
        }
        
        // Create subspace document
        val subspaceDoc = CouchDocument(
            id = subspaceId,
            data = mapOf(
                "name" to JsonPrimitive(name),
                "description" to JsonPrimitive(description),
                "creatorId" to JsonPrimitive(creatorId),
                "privacyLevel" to JsonPrimitive(privacyLevel.name),
                "createdAt" to JsonPrimitive(Instant.now().toString()),
                "activeSessions" to JsonArray(emptyList()),
                "participants" to JsonArray(emptyList())
            )
        )
        
        val savedDoc = couchService.saveDocument("subspace_$subspaceId", subspaceDoc)
        
        // Create subspace
        val subspace = BlackboardSubspace(
            id = subspaceId,
            name = name,
            description = description,
            creatorId = creatorId,
            privacyLevel = privacyLevel,
            couchDatabase = "subspace_$subspaceId",
            waveEngine = waveEngine,
            gitHistoryService = gitHistoryService
        )
        
        blackboardSubspaces[subspaceId] = subspace
        
        return SubspaceResult.Success(subspace)
    }

    /**
     * Get git commit history for a session
     */
    suspend fun getGitHistory(sessionId: String): GitCommitHistory? {
        return waveletGitHistory[sessionId]
    }

    /**
     * Project realtime updates to external routers
     */
    suspend fun projectToRouter(
        sessionId: String,
        routerId: String,
        projectionType: ProjectionType
    ) {
        val session = activeSessions[sessionId] ?: return
        
        val projection = Projection(
            sessionId = sessionId,
            routerId = routerId,
            type = projectionType,
            data = when (projectionType) {
                ProjectionType.DOCUMENT_STATE -> session.getCurrentState()
                ProjectionType.OPERATION_HISTORY -> session.getOperationHistory()
                ProjectionType.GIT_HISTORY -> session.gitHistory.getRecentCommits(10)
                ProjectionType.PARTICIPANT_LIST -> session.getParticipants()
            },
            timestamp = Instant.now().toEpochMilliseconds()
        )
        
        projectionRouter.sendProjection(projection)
    }

    // Private helper methods

    internal suspend fun subscribeToRealtimeUpdates(session: FiduciarySession) {
        // Subscribe to CouchDB changes feed
        couchService.subscribeToChanges("fiduciary_sessions") { change ->
            if (change.id == session.couchDocument.id) {
                // Update session state
                session.updateFromCouchChange(change)
                
                // Project to routers
                projectionRouter.projectDocumentChange(session.id, change)
            }
        }
    }

    internal suspend fun updateParticipantInCouch(
        session: FiduciarySession,
        participant: FiduciaryParticipant
    ) {
        val currentData = session.couchDocument.data.toMutableMap()
        val participants = (currentData["participants"] as? JsonArray)?.toMutableList() ?: mutableListOf()
        
        participants.add(JsonObject(mapOf(
            "id" to JsonPrimitive(participant.id),
            "pseudonym" to JsonPrimitive(participant.pseudonym),
            "joinedAt" to JsonPrimitive(Instant.now().toString())
        )))
        
        currentData["participants"] = JsonArray(participants)
        
        val updatedDoc = session.couchDocument.copy(data = currentData)
        couchService.saveDocument("fiduciary_sessions", updatedDoc)
    }

    internal suspend fun updateDocumentInCouch(
        session: FiduciarySession,
        operation: WaveOperation
    ): CouchDocument {
        val currentData = session.couchDocument.data.toMutableMap()
        val operations = (currentData["operations"] as? JsonArray)?.toMutableList() ?: mutableListOf()
        
        operations.add(JsonObject(mapOf(
            "operationId" to JsonPrimitive(operation.operationId),
            "type" to JsonPrimitive(operation.type.name),
            "participantId" to JsonPrimitive(operation.participantId),
            "content" to JsonPrimitive(operation.content),
            "position" to JsonPrimitive(operation.position),
            "timestamp" to JsonPrimitive(operation.timestamp)
        )))
        
        currentData["operations"] = JsonArray(operations)
        currentData["content"] = JsonPrimitive(operation.content) // Simplified for demo
        
        val updatedDoc = session.couchDocument.copy(data = currentData)
        return couchService.saveDocument("fiduciary_sessions", updatedDoc)
    }

    internal fun combineQueryResults(
        waveState: WaveDocumentState?,
        couchResults: List<CouchDocument>,
        gitHistory: List<GitCommit>
    ): List<KnowledgeFragment> {
        val results = mutableListOf<KnowledgeFragment>()
        
        // Add current Wave state
        waveState?.let { state ->
            results.add(KnowledgeFragment(
                id = "wave-current",
                content = state.content,
                type = KnowledgeType.FACT,
                confidence = 1.0,
                position = 0
            ))
        }
        
        // Add CouchDB historical data
        couchResults.forEach { doc ->
            val content = doc.data["content"]?.jsonPrimitive?.content ?: ""
            if (content.isNotEmpty()) {
                results.add(KnowledgeFragment(
                    id = "couch-${doc.id}",
                    content = content,
                    type = KnowledgeType.FACT,
                    confidence = 0.9,
                    position = 0
                ))
            }
        }
        
        // Add git history
        gitHistory.forEach { commit ->
            results.add(KnowledgeFragment(
                id = "git-${commit.commitId}",
                content = commit.message,
                type = KnowledgeType.SYNTHESIZED,
                confidence = 0.8,
                position = 0
            ))
        }
        
        return results
    }

    internal fun generateOperationId(): String = 
        "op-${System.currentTimeMillis()}-${Random.nextInt()}"
    
    internal fun generatePseudonym(): String = 
        "anon-${Random.nextInt(1000, 9999)}"
}

// Core data types

data class FiduciarySession(
    val id: String,
    val subspaceId: String,
    val waveSession: WaveSession,
    var couchDocument: CouchDocument,
    val gitHistory: GitCommitHistory,
    val autobeansRegistry: AutobeansDelegateRegistry
) {
    internal val participants = mutableMapOf<String, FiduciaryParticipant>()
    
    fun getParticipant(participantId: String): FiduciaryParticipant? = 
        participants[participantId]
    
    fun addParticipant(participant: FiduciaryParticipant) {
        participants[participant.id] = participant
    }
    
    fun getParticipants(): List<FiduciaryParticipant> = participants.values.toList()
    
    fun getCurrentState(): Map<String, Any> = mapOf(
        "sessionId" to id,
        "content" to couchDocument.data["content"]?.jsonPrimitive?.content ?: "",
        "participantCount" to participants.size
    )
    
    fun getOperationHistory(): List<Map<String, Any>> {
        val operations = couchDocument.data["operations"] as? JsonArray ?: JsonArray(emptyList())
        return operations.map { op ->
            val obj = op as JsonObject
            mapOf(
                "operationId" to (obj["operationId"]?.jsonPrimitive?.content ?: ""),
                "type" to (obj["type"]?.jsonPrimitive?.content ?: ""),
                "timestamp" to (obj["timestamp"]?.jsonPrimitive?.long ?: 0L)
            )
        }
    }
    
    fun updateFromCouchChange(change: CouchChange) {
        // Update session state from CouchDB change
        couchDocument = change.document ?: couchDocument
    }
}

data class FiduciaryParticipant(
    val id: String,
    val pseudonym: String,
    val session: FiduciarySession,
    val waveParticipant: WaveParticipant,
    val autobeansDelegate: AutobeansDelegate
)

data class KnowledgeFragment(
    val id: String,
    val content: String,
    val type: KnowledgeType,
    val confidence: Double,
    val position: Int,
    val metadata: Map<String, String> = emptyMap()
)

enum class KnowledgeType {
    FACT, OPINION, QUESTION, SYNTHESIZED, EMERGENT
}

enum class WaveOperationType {
    INSERT, DELETE, ANNOTATE, MOVE_CURSOR, SELECTION_CHANGE
}

enum class PrivacyLevel {
    ANONYMOUS, PSEUDONYMOUS, PRIVATE, PUBLIC
}

enum class ProjectionType {
    DOCUMENT_STATE, OPERATION_HISTORY, GIT_HISTORY, PARTICIPANT_LIST,
    PARTICIPANT_JOIN, OPERATION_APPLIED, DOCUMENT_CHANGED, GIT_COMMIT, HEARTBEAT
}

// Result types

sealed class ContributionResult {
    data class Success(
        val operationId: String,
        val gitCommitId: String,
        val delegateResult: AutobeansDelegateResult
    ) : ContributionResult()
    data class Failure(val error: String) : ContributionResult()
}

sealed class QueryResult {
    data class Success(val results: List<KnowledgeFragment>) : QueryResult()
    data class Failure(val error: String) : QueryResult()
}

sealed class SubspaceResult {
    data class Success(val subspace: BlackboardSubspace) : SubspaceResult()
    data class Failure(val error: String) : SubspaceResult()
}

data class Projection(
    val sessionId: String,
    val routerId: String,
    val type: ProjectionType,
    val data: Any,
    val timestamp: Long
) 