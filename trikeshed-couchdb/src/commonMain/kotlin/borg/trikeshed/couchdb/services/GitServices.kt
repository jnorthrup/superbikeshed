package borg.trikeshed.couchdb.services

import borg.trikeshed.couchdb.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.datetime.Clock

/**
 * Git Services Implementation
 * 
 * TDD Implementation of GitForensicsService and GitHistoryService for
 * git analysis and forensics with CouchDB integration.
 */

// ===== GIT FORENSICS SERVICE =====

/**
 * Git Forensics Service for analyzing git repositories and objects
 */
class GitForensicsService(
    private val couchService: CouchDBService,
    private val gitHistoryService: GitHistoryService
) {
    private val forensicSessions = mutableMapOf<String, GitForensicSession>()
    private val attentionModels = mutableMapOf<String, GitAttentionModel>()
    private val patternDetectors = mutableMapOf<String, GitPatternDetector>()

    /**
     * Initialize forensics for a repository
     */
    suspend fun initializeForensics(repoId: String): GitForensicSession {
        val session = GitForensicSession(
            id = repoId,
            databaseName = "git_forensics_$repoId",
            startTime = Clock.System.now().toEpochMilliseconds(),
            attentionModel = createAttentionModel(),
            patternDetector = createPatternDetector()
        )

        // Create forensic database
        couchService.createDatabase(session.databaseName)
        forensicSessions[repoId] = session

        return session
    }

    /**
     * Analyze git object
     */
    suspend fun analyzeGitObject(
        repoId: String,
        objectId: String,
        objectType: GitObjectType,
        objectData: ByteArray
    ): GitObjectAnalysis {
        val session = forensicSessions[repoId] ?: throw GitForensicsException("Forensic session not found: $repoId")

        val analysis = GitObjectAnalysis(
            objectId = objectId,
            objectType = objectType,
            analysisTime = Clock.System.now().toEpochMilliseconds(),
            attentionScore = calculateAttentionScore(objectData),
            patterns = detectPatterns(objectData),
            metadata = extractMetadata(objectData, objectType)
        )

        // Store analysis in CouchDB
        val document = CouchDocument(
            _id = "analysis_${objectId}",
            _rev = "",
            data = JsonObject(mapOf(
                "objectId" to JsonPrimitive(objectId),
                "objectType" to JsonPrimitive(objectType.name),
                "analysisTime" to JsonPrimitive(analysis.analysisTime),
                "attentionScore" to JsonPrimitive(analysis.attentionScore),
                "patterns" to JsonPrimitive(analysis.patterns.joinToString(",")),
                "metadata" to JsonPrimitive(analysis.metadata.toString())
            ))
        )

        couchService.createDocument(session.databaseName, document)

        return analysis
    }

    /**
     * Create forensic event
     */
    suspend fun createForensicEvent(
        repoId: String,
        eventType: GitForensicEventType,
        eventData: Map<String, Any>
    ): GitForensicEvent {
        val session = forensicSessions[repoId] ?: throw GitForensicsException("Forensic session not found: $repoId")

        val event = GitForensicEvent(
            id = "event_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}",
            eventType = eventType,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            data = eventData,
            attentionScore = calculateEventAttentionScore(eventType, eventData)
        )

        // Store event in CouchDB
        val document = CouchDocument(
            _id = event.id,
            _rev = "",
            data = JsonObject(mapOf(
                "eventType" to JsonPrimitive(eventType.name),
                "timestamp" to JsonPrimitive(event.timestamp),
                "attentionScore" to JsonPrimitive(event.attentionScore),
                "data" to JsonPrimitive(event.data.toString())
            ))
        )

        couchService.createDocument(session.databaseName, document)

        return event
    }

    /**
     * Get forensic events
     */
    suspend fun getForensicEvents(repoId: String, since: Long? = null): List<GitForensicEvent> {
        val session = forensicSessions[repoId] ?: throw GitForensicsException("Forensic session not found: $repoId")

        // In a real implementation, this would query CouchDB for events
        // For now, return empty list
        return emptyList()
    }

    /**
     * Create scene replay
     */
    suspend fun createSceneReplay(
        repoId: String,
        startTime: Long,
        endTime: Long
    ): GitSceneReplay {
        val session = forensicSessions[repoId] ?: throw GitForensicsException("Forensic session not found: $repoId")

        val replay = GitSceneReplay(
            id = "replay_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}",
            startTime = startTime,
            endTime = endTime,
            events = getForensicEvents(repoId, startTime),
            attentionScore = calculateReplayAttentionScore(startTime, endTime)
        )

        // Store replay in CouchDB
        val document = CouchDocument(
            _id = replay.id,
            _rev = "",
            data = JsonObject(mapOf(
                "startTime" to JsonPrimitive(replay.startTime),
                "endTime" to JsonPrimitive(replay.endTime),
                "attentionScore" to JsonPrimitive(replay.attentionScore),
                "eventCount" to JsonPrimitive(replay.events.size)
            ))
        )

        couchService.createDocument(session.databaseName, document)

        return replay
    }

    /**
     * Analyze realtime changes
     */
    suspend fun analyzeRealtimeChanges(repoId: String): Flow<GitRealtimeAnalysis> {
        val session = forensicSessions[repoId] ?: throw GitForensicsException("Forensic session not found: $repoId")

        // Subscribe to CouchDB changes
        val changes = mutableListOf<GitRealtimeAnalysis>()
        
        couchService.subscribeToChanges(session.databaseName) { change ->
            val analysis = GitRealtimeAnalysis(
                timestamp = Clock.System.now().toEpochMilliseconds(),
                documentId = change.documentId,
                changeType = if (change.deleted) "deleted" else "modified",
                attentionScore = calculateChangeAttentionScore(change)
            )
            changes.add(analysis)
        }

        return flowOf(*changes.toTypedArray())
    }

    // Private helper methods

    private fun createAttentionModel(): GitAttentionModel {
        return GitAttentionModel(
            id = "attention_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}",
            weights = mapOf(
                "commit_frequency" to 0.3,
                "file_changes" to 0.25,
                "author_activity" to 0.2,
                "branch_activity" to 0.15,
                "merge_activity" to 0.1
            )
        )
    }

    private fun createPatternDetector(): GitPatternDetector {
        return GitPatternDetector(
            id = "pattern_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}",
            patterns = listOf(
                "rapid_commits",
                "large_files",
                "suspicious_timing",
                "author_changes",
                "branch_manipulation"
            )
        )
    }

    private fun calculateAttentionScore(data: ByteArray): Double {
        // Simple attention score calculation based on data size and content
        val sizeScore = (data.size / 1024.0).coerceAtMost(1.0)
        val contentScore = if (data.contains("suspicious".toByteArray())) 0.8 else 0.2
        return (sizeScore + contentScore) / 2.0
    }

    private fun detectPatterns(data: ByteArray): List<String> {
        val patterns = mutableListOf<String>()
        
        if (data.size > 1024 * 1024) patterns.add("large_file")
        if (data.contains("password".toByteArray())) patterns.add("suspicious_content")
        if (data.contains("key".toByteArray())) patterns.add("key_material")
        
        return patterns
    }

    private fun extractMetadata(data: ByteArray, objectType: GitObjectType): Map<String, Any> {
        return mapOf(
            "size" to data.size,
            "type" to objectType.name,
            "hash" to data.contentHashCode().toString(),
            "timestamp" to Clock.System.now().toEpochMilliseconds()
        )
    }

    private fun calculateEventAttentionScore(eventType: GitForensicEventType, eventData: Map<String, Any>): Double {
        val baseScore = when (eventType) {
            GitForensicEventType.COMMIT -> 0.3
            GitForensicEventType.BRANCH_CREATE -> 0.4
            GitForensicEventType.BRANCH_DELETE -> 0.6
            GitForensicEventType.MERGE -> 0.5
            GitForensicEventType.FILE_MODIFY -> 0.2
            GitForensicEventType.SUSPICIOUS_ACTIVITY -> 0.9
        }
        
        val dataScore = if (eventData.containsKey("suspicious")) 0.8 else 0.1
        return (baseScore + dataScore) / 2.0
    }

    private fun calculateReplayAttentionScore(startTime: Long, endTime: Long): Double {
        val duration = endTime - startTime
        return (duration / 1000.0).coerceAtMost(1.0) // Normalize to 0-1
    }

    private fun calculateChangeAttentionScore(change: CouchChange): Double {
        return if (change.deleted) 0.7 else 0.3
    }
}

// ===== GIT HISTORY SERVICE =====

/**
 * Git History Service for managing git repository history
 */
class GitHistoryService {
    private val repositories = mutableMapOf<String, GitRepository>()
    private val commitHistory = mutableMapOf<String, MutableList<GitCommit>>()
    private val branchHistory = mutableMapOf<String, MutableList<GitBranch>>()

    /**
     * Initialize repository
     */
    suspend fun initializeRepository(repoId: String, repoPath: String): GitRepository {
        val repository = GitRepository(
            id = repoId,
            path = repoPath,
            createdAt = Clock.System.now().toEpochMilliseconds(),
            isActive = true
        )

        repositories[repoId] = repository
        commitHistory[repoId] = mutableListOf()
        branchHistory[repoId] = mutableListOf()

        return repository
    }

    /**
     * Add commit to history
     */
    suspend fun addCommit(
        repoId: String,
        commitId: String,
        author: String,
        message: String,
        timestamp: Long,
        parentIds: List<String> = emptyList()
    ): GitCommit {
        val commit = GitCommit(
            id = commitId,
            author = author,
            message = message,
            timestamp = timestamp,
            parentIds = parentIds
        )

        commitHistory.getOrPut(repoId) { mutableListOf() }.add(commit)
        return commit
    }

    /**
     * Add branch to history
     */
    suspend fun addBranch(
        repoId: String,
        branchName: String,
        commitId: String,
        isActive: Boolean = true
    ): GitBranch {
        val branch = GitBranch(
            name = branchName,
            commitId = commitId,
            isActive = isActive,
            createdAt = Clock.System.now().toEpochMilliseconds()
        )

        branchHistory.getOrPut(repoId) { mutableListOf() }.add(branch)
        return branch
    }

    /**
     * Get commit history
     */
    suspend fun getCommitHistory(repoId: String, limit: Int = 100): List<GitCommit> {
        return commitHistory[repoId]?.takeLast(limit) ?: emptyList()
    }

    /**
     * Get branch history
     */
    suspend fun getBranchHistory(repoId: String): List<GitBranch> {
        return branchHistory[repoId] ?: emptyList()
    }

    /**
     * Get repository info
     */
    suspend fun getRepositoryInfo(repoId: String): GitRepository? {
        return repositories[repoId]
    }

    /**
     * Analyze commit patterns
     */
    suspend fun analyzeCommitPatterns(repoId: String): GitCommitPatterns {
        val commits = commitHistory[repoId] ?: emptyList()
        
        val authorActivity = commits.groupBy { it.author }.mapValues { it.value.size }
        val timePatterns = commits.groupBy { it.timestamp / (24 * 60 * 60 * 1000) }.mapValues { it.value.size }
        
        return GitCommitPatterns(
            totalCommits = commits.size,
            uniqueAuthors = authorActivity.size,
            authorActivity = authorActivity,
            timePatterns = timePatterns,
            averageCommitsPerDay = if (timePatterns.isNotEmpty()) commits.size.toDouble() / timePatterns.size else 0.0
        )
    }
}

// ===== DATA STRUCTURES =====

data class GitForensicSession(
    val id: String,
    val databaseName: String,
    val startTime: Long,
    val attentionModel: GitAttentionModel,
    val patternDetector: GitPatternDetector
)

data class GitAttentionModel(
    val id: String,
    val weights: Map<String, Double>
)

data class GitPatternDetector(
    val id: String,
    val patterns: List<String>
)

data class GitObjectAnalysis(
    val objectId: String,
    val objectType: GitObjectType,
    val analysisTime: Long,
    val attentionScore: Double,
    val patterns: List<String>,
    val metadata: Map<String, Any>
)

data class GitForensicEvent(
    val id: String,
    val eventType: GitForensicEventType,
    val timestamp: Long,
    val data: Map<String, Any>,
    val attentionScore: Double
)

data class GitSceneReplay(
    val id: String,
    val startTime: Long,
    val endTime: Long,
    val events: List<GitForensicEvent>,
    val attentionScore: Double
)

data class GitRealtimeAnalysis(
    val timestamp: Long,
    val documentId: String,
    val changeType: String,
    val attentionScore: Double
)

data class GitRepository(
    val id: String,
    val path: String,
    val createdAt: Long,
    val isActive: Boolean
)

data class GitCommit(
    val id: String,
    val author: String,
    val message: String,
    val timestamp: Long,
    val parentIds: List<String>
)

data class GitBranch(
    val name: String,
    val commitId: String,
    val isActive: Boolean,
    val createdAt: Long
)

data class GitCommitPatterns(
    val totalCommits: Int,
    val uniqueAuthors: Int,
    val authorActivity: Map<String, Int>,
    val timePatterns: Map<Long, Int>,
    val averageCommitsPerDay: Double
)

// ===== ENUMS =====

enum class GitObjectType {
    COMMIT,
    TREE,
    BLOB,
    TAG
}

enum class GitForensicEventType {
    COMMIT,
    BRANCH_CREATE,
    BRANCH_DELETE,
    MERGE,
    FILE_MODIFY,
    SUSPICIOUS_ACTIVITY
}

// ===== EXCEPTIONS =====

class GitForensicsException(message: String, cause: Throwable? = null) : Exception(message, cause) 