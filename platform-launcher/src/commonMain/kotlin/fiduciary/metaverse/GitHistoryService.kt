package fiduciary.metaverse

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.datetime.Clock
import java.security.MessageDigest

/**
 * Git History Service
 * 
 * Records git commit history from wavelets and provides git-like functionality:
 * - Create commits from wavelet operations
 * - Maintain commit history with parent references
 * - Branch and merge support
 * - Diff generation between commits
 * - Git-like log and history queries
 */
class GitHistoryService {
    
    // Git repositories by session
    internal val repositories = mutableMapOf<String, GitRepository>()
    
    // Commit history by session
    internal val commitHistories = mutableMapOf<String, GitCommitHistory>()
    
    // Branch management
    internal val branches = mutableMapOf<String, MutableMap<String, String>>() // session -> branch -> commitId

    /**
     * Initialize git history for a session
     */
    fun initializeHistory(sessionId: String, initialContent: String): GitCommitHistory {
        val repository = GitRepository(sessionId)
        repositories[sessionId] = repository
        
        // Create initial commit
        val initialCommit = createInitialCommit(sessionId, initialContent)
        
        val history = GitCommitHistory(
            sessionId = sessionId,
            commits = mutableListOf(initialCommit),
            currentBranch = "main",
            branches = mutableMapOf("main" to initialCommit.commitId)
        )
        
        commitHistories[sessionId] = history
        branches[sessionId] = mutableMapOf("main" to initialCommit.commitId)
        
        return history
    }

    /**
     * Create a git commit from a wavelet operation
     */
    suspend fun createCommitFromWavelet(
        sessionId: String,
        operation: WaveOperation,
        documentState: CouchDocument
    ): GitCommit {
        
        val history = commitHistories[sessionId]
            ?: throw IllegalArgumentException("No history found for session: $sessionId")
        
        val repository = repositories[sessionId]
            ?: throw IllegalArgumentException("No repository found for session: $sessionId")
        
        // Create commit message from operation
        val commitMessage = generateCommitMessage(operation)
        
        // Calculate diff from previous commit
        val previousCommit = history.getLatestCommit()
        val diff = calculateDiff(previousCommit, documentState)
        
        // Create commit
        val commit = GitCommit(
            commitId = generateCommitId(operation),
            parentCommitId = previousCommit?.commitId,
            author = operation.participantId,
            timestamp = operation.timestamp,
            message = commitMessage,
            diff = diff,
            waveletId = operation.operationId,
            documentState = documentState.data.toString()
        )
        
        // Add to history
        history.addCommit(commit)
        
        // Update current branch
        val currentBranch = history.currentBranch
        history.branches[currentBranch] = commit.commitId
        branches[sessionId]?.put(currentBranch, commit.commitId)
        
        // Store in repository
        repository.storeCommit(commit)
        
        return commit
    }

    /**
     * Create a new branch
     */
    suspend fun createBranch(
        sessionId: String,
        branchName: String,
        fromCommitId: String? = null
    ): BranchResult {
        
        val history = commitHistories[sessionId]
            ?: return BranchResult.Failure("No history found for session: $sessionId")
        
        val baseCommitId = fromCommitId ?: history.getLatestCommit()?.commitId
            ?: return BranchResult.Failure("No base commit found")
        
        // Create branch
        history.branches[branchName] = baseCommitId
        branches[sessionId]?.put(branchName, baseCommitId)
        
        return BranchResult.Success(
            branchName = branchName,
            commitId = baseCommitId
        )
    }

    /**
     * Switch to a branch
     */
    suspend fun switchBranch(sessionId: String, branchName: String): BranchResult {
        val history = commitHistories[sessionId]
            ?: return BranchResult.Failure("No history found for session: $sessionId")
        
        val commitId = history.branches[branchName]
            ?: return BranchResult.Failure("Branch not found: $branchName")
        
        history.currentBranch = branchName
        
        return BranchResult.Success(
            branchName = branchName,
            commitId = commitId
        )
    }

    /**
     * Merge branches
     */
    suspend fun mergeBranches(
        sessionId: String,
        sourceBranch: String,
        targetBranch: String
    ): MergeResult {
        
        val history = commitHistories[sessionId]
            ?: return MergeResult.Failure("No history found for session: $sessionId")
        
        val sourceCommitId = history.branches[sourceBranch]
            ?: return MergeResult.Failure("Source branch not found: $sourceBranch")
        
        val targetCommitId = history.branches[targetBranch]
            ?: return MergeResult.Failure("Target branch not found: $targetBranch")
        
        // Find common ancestor
        val commonAncestor = findCommonAncestor(history, sourceCommitId, targetCommitId)
        
        // Create merge commit
        val mergeCommit = GitCommit(
            commitId = generateMergeCommitId(sourceBranch, targetBranch),
            parentCommitId = "$sourceCommitId $targetCommitId", // Multiple parents for merge
            author = "system",
            timestamp = Clock.System.now().toEpochMilliseconds(),
            message = "Merge branch '$sourceBranch' into '$targetBranch'",
            diff = "", // Merge commits typically don't have diffs
            waveletId = "merge-${sourceBranch}-${targetBranch}",
            documentState = "" // Will be resolved from current state
        )
        
        // Add merge commit
        history.addCommit(mergeCommit)
        history.branches[targetBranch] = mergeCommit.commitId
        branches[sessionId]?.put(targetBranch, mergeCommit.commitId)
        
        return MergeResult.Success(
            mergeCommitId = mergeCommit.commitId,
            sourceBranch = sourceBranch,
            targetBranch = targetBranch
        )
    }

    /**
     * Get commit log
     */
    suspend fun getCommitLog(
        sessionId: String,
        branchName: String? = null,
        limit: Int = 10
    ): List<GitCommit> {
        
        val history = commitHistories[sessionId]
            ?: return emptyList()
        
        val branch = branchName ?: history.currentBranch
        val startCommitId = history.branches[branch]
            ?: return emptyList()
        
        return history.getCommitLog(startCommitId, limit)
    }

    /**
     * Get diff between commits
     */
    suspend fun getDiff(
        sessionId: String,
        fromCommitId: String,
        toCommitId: String
    ): DiffResult {
        
        val history = commitHistories[sessionId]
            ?: return DiffResult.Failure("No history found for session: $sessionId")
        
        val fromCommit = history.getCommit(fromCommitId)
            ?: return DiffResult.Failure("From commit not found: $fromCommitId")
        
        val toCommit = history.getCommit(toCommitId)
            ?: return DiffResult.Failure("To commit not found: $toCommitId")
        
        val diff = calculateDiff(fromCommit, toCommit)
        
        return DiffResult.Success(
            fromCommit = fromCommit,
            toCommit = toCommit,
            diff = diff
        )
    }

    /**
     * Query history based on criteria
     */
    suspend fun queryHistory(
        sessionId: String,
        query: KnowledgeQuery
    ): List<GitCommit> {
        
        val history = commitHistories[sessionId]
            ?: return emptyList()
        
        return history.commits.filter { commit ->
            // Filter by time window
            val commitTime = commit.timestamp
            val queryTime = Clock.System.now().toEpochMilliseconds() - query.timeWindow
            commitTime >= queryTime &&
            
            // Filter by keywords in commit message
            query.keywords.any { keyword ->
                commit.message.contains(keyword, ignoreCase = true)
            }
        }
    }

    // Private helper methods

    internal fun createInitialCommit(sessionId: String, content: String): GitCommit {
        return GitCommit(
            commitId = generateInitialCommitId(sessionId),
            parentCommitId = null,
            author = "system",
            timestamp = Clock.System.now().toEpochMilliseconds(),
            message = "Initial commit for session $sessionId",
            diff = "",
            waveletId = "initial",
            documentState = content
        )
    }

    internal fun generateCommitMessage(operation: WaveOperation): String {
        return when (operation.type) {
            WaveOperationType.INSERT -> "Insert: ${operation.content.take(50)}..."
            WaveOperationType.DELETE -> "Delete: ${operation.content.take(50)}..."
            WaveOperationType.ANNOTATE -> "Annotate: ${operation.content.take(50)}..."
            else -> "Operation: ${operation.type.name}"
        }
    }

    internal fun calculateDiff(fromCommit: GitCommit?, toDocument: CouchDocument): String {
        val fromContent = fromCommit?.documentState ?: ""
        val toContent = toDocument.data["content"]?.jsonPrimitive?.content ?: ""
        
        return generateDiff(fromContent, toContent)
    }

    internal fun calculateDiff(fromCommit: GitCommit, toCommit: GitCommit): String {
        return generateDiff(fromCommit.documentState, toCommit.documentState)
    }

    internal fun generateDiff(fromContent: String, toContent: String): String {
        // Simple diff generation (in real implementation, would use proper diff algorithm)
        return if (fromContent != toContent) {
            "--- a/content\n+++ b/content\n@@ -1,1 +1,1 @@\n-$fromContent\n+$toContent"
        } else {
            ""
        }
    }

    internal fun findCommonAncestor(
        history: GitCommitHistory,
        commitId1: String,
        commitId2: String
    ): String? {
        // Simple common ancestor finding (in real implementation, would use proper algorithm)
        val commits1 = history.getCommitAncestors(commitId1)
        val commits2 = history.getCommitAncestors(commitId2)
        
        return commits1.intersect(commits2.toSet()).firstOrNull()
    }

    internal fun generateCommitId(operation: WaveOperation): String {
        val content = "${operation.operationId}${operation.timestamp}${operation.content}"
        return generateHash(content)
    }

    internal fun generateInitialCommitId(sessionId: String): String {
        return generateHash("initial-$sessionId")
    }

    internal fun generateMergeCommitId(sourceBranch: String, targetBranch: String): String {
        return generateHash("merge-$sourceBranch-$targetBranch")
    }

    internal fun generateHash(content: String): String {
        val bytes = content.toByteArray()
        val digest = MessageDigest.getInstance("SHA-1")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }.take(7)
    }
}

/**
 * Git Repository
 */
class GitRepository(val sessionId: String) {
    
    internal val commits = mutableMapOf<String, GitCommit>()
    internal val objects = mutableMapOf<String, GitObject>()

    suspend fun storeCommit(commit: GitCommit) {
        commits[commit.commitId] = commit
        
        // Store as git object
        val gitObject = GitObject(
            id = commit.commitId,
            type = GitObjectType.COMMIT,
            content = commit.toString(),
            size = commit.toString().length
        )
        
        objects[commit.commitId] = gitObject
    }

    suspend fun getCommit(commitId: String): GitCommit? {
        return commits[commitId]
    }

    suspend fun getAllCommits(): List<GitCommit> {
        return commits.values.toList()
    }
}

/**
 * Git Commit History
 */
class GitCommitHistory(
    val sessionId: String,
    val commits: MutableList<GitCommit>,
    var currentBranch: String,
    val branches: MutableMap<String, String>
) {
    
    fun addCommit(commit: GitCommit) {
        commits.add(commit)
    }
    
    fun getLatestCommit(): GitCommit? {
        return commits.lastOrNull()
    }
    
    fun getCommit(commitId: String): GitCommit? {
        return commits.find { it.commitId == commitId }
    }
    
    fun getCommitLog(startCommitId: String, limit: Int): List<GitCommit> {
        val result = mutableListOf<GitCommit>()
        var currentCommitId = startCommitId
        var count = 0
        
        while (currentCommitId != null && count < limit) {
            val commit = getCommit(currentCommitId)
            if (commit != null) {
                result.add(commit)
                currentCommitId = commit.parentCommitId?.split(" ")?.firstOrNull()
                count++
            } else {
                break
            }
        }
        
        return result
    }
    
    fun getCommitAncestors(commitId: String): List<String> {
        val ancestors = mutableListOf<String>()
        var currentCommitId = commitId
        
        while (currentCommitId != null) {
            ancestors.add(currentCommitId)
            val commit = getCommit(currentCommitId)
            currentCommitId = commit?.parentCommitId?.split(" ")?.firstOrNull()
        }
        
        return ancestors
    }
    
    fun getRecentCommits(limit: Int): List<GitCommit> {
        return commits.takeLast(limit)
    }
}

// Data types

data class GitCommit(
    val commitId: String,
    val parentCommitId: String?,
    val author: String,
    val timestamp: Long,
    val message: String,
    val diff: String,
    val waveletId: String,
    val documentState: String
)

data class GitObject(
    val id: String,
    val type: GitObjectType,
    val content: String,
    val size: Int
)

enum class GitObjectType {
    COMMIT, TREE, BLOB, TAG
}

// Result types

sealed class BranchResult {
    data class Success(
        val branchName: String,
        val commitId: String
    ) : BranchResult()
    data class Failure(val error: String) : BranchResult()
}

sealed class MergeResult {
    data class Success(
        val mergeCommitId: String,
        val sourceBranch: String,
        val targetBranch: String
    ) : MergeResult()
    data class Failure(val error: String) : MergeResult()
}

sealed class DiffResult {
    data class Success(
        val fromCommit: GitCommit,
        val toCommit: GitCommit,
        val diff: String
    ) : DiffResult()
    data class Failure(val error: String) : DiffResult()
} 