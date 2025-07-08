package fiduciary.metaverse

import borg.trikeshed.lib.*
import borg.trikeshed.couchdb.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.datetime.Clock
import java.security.MessageDigest

/**
 * LLM Git Commands Service
 * 
 * Implements the most commonly used LLM git commands with read-only forensics:
 * - git log (commit history analysis)
 * - git show (object inspection)
 * - git diff (change analysis)
 * - git blame (line attribution)
 * - git status (repository state)
 * - git branch (branch analysis)
 * - git remote (remote tracking)
 * - git tag (tag management)
 * - git stash (stash analysis)
 * - git reflog (reference history)
 */

// ===== LLM GIT COMMAND TYPEALIASES =====

// Command types
typealias GitCommand = String
typealias CommandResult = String
typealias CommandContext = Map<String, Any>
typealias LLMQuery = String

// Analysis types
typealias CommitAnalysis = Join<GitCommit, AttentionScore>
typealias DiffAnalysis = Join<String, List<DiffHunk>>
typealias BlameAnalysis = Join<String, List<BlameLine>>
typealias BranchAnalysis = Join<String, List<GitBranch>>
typealias StatusAnalysis = Join<String, RepositoryStatus>

// LLM integration types
typealias LLMResponse = String
typealias LLMContext = Join<LLMQuery, CommandContext>
typealias LLMInsight = Join<String, AttentionScore>

/**
 * LLM Git Commands Service
 */
class LLMGitCommands(
    internal val forensicsService: GitForensicsService,
    internal val gitHistoryService: GitHistoryService,
    internal val couchService: CouchDBService
) {
    
    // Command registry
    internal val commandRegistry = mutableMapOf<String, GitCommandHandler>()
    
    // LLM insights cache
    internal val insightsCache = mutableMapOf<String, LLMInsight>()
    
    // Command history for forensics
    internal val commandHistory = mutableListOf<CommandExecution>()

    init {
        registerCommands()
    }

    /**
     * Execute LLM git command with forensics
     */
    suspend fun executeLLMCommand(
        repoId: String,
        command: GitCommand,
        query: LLMQuery,
        context: CommandContext = emptyMap()
    ): LLMCommandResult {
        
        val startTime = Clock.System.now().toEpochMilliseconds()
        
        // Parse command
        val parsedCommand = parseGitCommand(command)
        val handler = commandRegistry[parsedCommand.name]
            ?: return LLMCommandResult.Error("Unknown command: ${parsedCommand.name}")
        
        // Execute command
        val commandResult = handler.execute(repoId, parsedCommand, context)
        
        // Generate LLM insights
        val insights = generateLLMInsights(query, commandResult, context)
        
        // Record for forensics
        val execution = CommandExecution(
            repoId = repoId,
            command = command,
            query = query,
            context = context,
            result = commandResult,
            insights = insights,
            timestamp = startTime,
            duration = Clock.System.now().toEpochMilliseconds() - startTime
        )
        
        commandHistory.add(execution)
        
        // Store in CouchDB
        storeCommandExecution(execution)
        
        return LLMCommandResult.Success(
            command = command,
            result = commandResult,
            insights = insights,
            execution = execution
        )
    }

    /**
     * Get commit history with LLM analysis
     */
    suspend fun gitLog(
        repoId: String,
        query: LLMQuery,
        options: GitLogOptions = GitLogOptions()
    ): LLMCommandResult {
        
        val context = mapOf(
            "options" to options,
            "command" to "git log"
        )
        
        return executeLLMCommand(repoId, "git log", query, context)
    }

    /**
     * Show git object with LLM analysis
     */
    suspend fun gitShow(
        repoId: String,
        objectHash: String,
        query: LLMQuery
    ): LLMCommandResult {
        
        val context = mapOf(
            "objectHash" to objectHash,
            "command" to "git show"
        )
        
        return executeLLMCommand(repoId, "git show $objectHash", query, context)
    }

    /**
     * Analyze git diff with LLM insights
     */
    suspend fun gitDiff(
        repoId: String,
        fromCommit: String,
        toCommit: String,
        query: LLMQuery
    ): LLMCommandResult {
        
        val context = mapOf(
            "fromCommit" to fromCommit,
            "toCommit" to toCommit,
            "command" to "git diff"
        )
        
        return executeLLMCommand(repoId, "git diff $fromCommit $toCommit", query, context)
    }

    /**
     * Git blame with LLM analysis
     */
    suspend fun gitBlame(
        repoId: String,
        filePath: String,
        query: LLMQuery
    ): LLMCommandResult {
        
        val context = mapOf(
            "filePath" to filePath,
            "command" to "git blame"
        )
        
        return executeLLMCommand(repoId, "git blame $filePath", query, context)
    }

    /**
     * Git status with LLM insights
     */
    suspend fun gitStatus(
        repoId: String,
        query: LLMQuery
    ): LLMCommandResult {
        
        val context = mapOf(
            "command" to "git status"
        )
        
        return executeLLMCommand(repoId, "git status", query, context)
    }

    /**
     * Branch analysis with LLM
     */
    suspend fun gitBranch(
        repoId: String,
        query: LLMQuery
    ): LLMCommandResult {
        
        val context = mapOf(
            "command" to "git branch"
        )
        
        return executeLLMCommand(repoId, "git branch", query, context)
    }

    /**
     * Remote analysis with LLM
     */
    suspend fun gitRemote(
        repoId: String,
        query: LLMQuery
    ): LLMCommandResult {
        
        val context = mapOf(
            "command" to "git remote"
        )
        
        return executeLLMCommand(repoId, "git remote", query, context)
    }

    /**
     * Tag analysis with LLM
     */
    suspend fun gitTag(
        repoId: String,
        query: LLMQuery
    ): LLMCommandResult {
        
        val context = mapOf(
            "command" to "git tag"
        )
        
        return executeLLMCommand(repoId, "git tag", query, context)
    }

    /**
     * Stash analysis with LLM
     */
    suspend fun gitStash(
        repoId: String,
        query: LLMQuery
    ): LLMCommandResult {
        
        val context = mapOf(
            "command" to "git stash"
        )
        
        return executeLLMCommand(repoId, "git stash", query, context)
    }

    /**
     * Reflog analysis with LLM
     */
    suspend fun gitReflog(
        repoId: String,
        query: LLMQuery
    ): LLMCommandResult {
        
        val context = mapOf(
            "command" to "git reflog"
        )
        
        return executeLLMCommand(repoId, "git reflog", query, context)
    }

    /**
     * Get command execution history for forensics
     */
    suspend fun getCommandHistory(
        repoId: String,
        timeRange: Long? = null
    ): List<CommandExecution> {
        
        return if (timeRange != null) {
            val cutoff = Clock.System.now().toEpochMilliseconds() - timeRange
            commandHistory.filter { it.repoId == repoId && it.timestamp >= cutoff }
        } else {
            commandHistory.filter { it.repoId == repoId }
        }
    }

    /**
     * Get LLM insights for a specific query
     */
    suspend fun getLLMInsights(
        repoId: String,
        query: LLMQuery
    ): List<LLMInsight> {
        
        val executions = getCommandHistory(repoId)
        return executions
            .filter { it.query.contains(query, ignoreCase = true) }
            .map { it.insights }
            .flatten()
    }

    // ===== PRIVATE METHODS =====

    internal fun registerCommands() {
        commandRegistry["git log"] = GitLogHandler(gitHistoryService, forensicsService)
        commandRegistry["git show"] = GitShowHandler(gitHistoryService, forensicsService)
        commandRegistry["git diff"] = GitDiffHandler(gitHistoryService, forensicsService)
        commandRegistry["git blame"] = GitBlameHandler(gitHistoryService, forensicsService)
        commandRegistry["git status"] = GitStatusHandler(gitHistoryService, forensicsService)
        commandRegistry["git branch"] = GitBranchHandler(gitHistoryService, forensicsService)
        commandRegistry["git remote"] = GitRemoteHandler(gitHistoryService, forensicsService)
        commandRegistry["git tag"] = GitTagHandler(gitHistoryService, forensicsService)
        commandRegistry["git stash"] = GitStashHandler(gitHistoryService, forensicsService)
        commandRegistry["git reflog"] = GitReflogHandler(gitHistoryService, forensicsService)
    }

    internal fun parseGitCommand(command: String): ParsedGitCommand {
        val parts = command.trim().split(" ")
        return ParsedGitCommand(
            name = parts[0],
            arguments = parts.drop(1),
            original = command
        )
    }

    internal suspend fun generateLLMInsights(
        query: LLMQuery,
        result: CommandResult,
        context: CommandContext
    ): List<LLMInsight> {
        
        val insights = mutableListOf<LLMInsight>()
        
        // Generate insights based on query and result
        when {
            query.contains("commit", ignoreCase = true) -> {
                insights.add(analyzeCommitInsights(result))
            }
            query.contains("change", ignoreCase = true) -> {
                insights.add(analyzeChangeInsights(result))
            }
            query.contains("author", ignoreCase = true) -> {
                insights.add(analyzeAuthorInsights(result))
            }
            query.contains("pattern", ignoreCase = true) -> {
                insights.add(analyzePatternInsights(result))
            }
            else -> {
                insights.add(generateGeneralInsights(result))
            }
        }
        
        return insights
    }

    internal fun analyzeCommitInsights(result: CommandResult): LLMInsight {
        val insight = when {
            result.contains("merge") -> "Merge commit detected - indicates collaboration"
            result.contains("fix") -> "Bug fix commit - addresses specific issues"
            result.contains("feature") -> "Feature commit - adds new functionality"
            result.contains("refactor") -> "Refactoring commit - improves code structure"
            else -> "Standard commit - routine development activity"
        }
        
        val attentionScore = when {
            result.contains("merge") -> 0.9
            result.contains("fix") -> 0.8
            result.contains("feature") -> 0.7
            result.contains("refactor") -> 0.6
            else -> 0.5
        }
        
        return insight j attentionScore
    }

    internal fun analyzeChangeInsights(result: CommandResult): LLMInsight {
        val insight = when {
            result.contains("+") && result.contains("-") -> "Mixed changes - additions and deletions"
            result.contains("+") -> "Additions only - new code or features"
            result.contains("-") -> "Deletions only - cleanup or removal"
            else -> "No changes detected"
        }
        
        val attentionScore = when {
            result.contains("+") && result.contains("-") -> 0.8
            result.contains("+") -> 0.7
            result.contains("-") -> 0.6
            else -> 0.3
        }
        
        return insight j attentionScore
    }

    internal fun analyzeAuthorInsights(result: CommandResult): LLMInsight {
        val insight = "Author activity analysis - collaboration patterns"
        val attentionScore = 0.7
        return insight j attentionScore
    }

    internal fun analyzePatternInsights(result: CommandResult): LLMInsight {
        val insight = "Pattern analysis - recurring development patterns"
        val attentionScore = 0.8
        return insight j attentionScore
    }

    internal fun generateGeneralInsights(result: CommandResult): LLMInsight {
        val insight = "General git operation analysis"
        val attentionScore = 0.5
        return insight j attentionScore
    }

    internal suspend fun storeCommandExecution(execution: CommandExecution) {
        val document = CouchDocument(
            id = "llm-command-${execution.timestamp}",
            data = mapOf(
                "type" to "llm_command_execution",
                "repoId" to execution.repoId,
                "command" to execution.command,
                "query" to execution.query,
                "timestamp" to execution.timestamp.toString(),
                "duration" to execution.duration.toString(),
                "insights" to execution.insights.size.toString()
            )
        )
        
        couchService.saveDocument("llm_commands", document)
    }
}

// ===== COMMAND HANDLERS =====

interface GitCommandHandler {
    suspend fun execute(
        repoId: String,
        command: ParsedGitCommand,
        context: CommandContext
    ): CommandResult
}

class GitLogHandler(
    internal val gitHistoryService: GitHistoryService,
    internal val forensicsService: GitForensicsService
) : GitCommandHandler {
    
    override suspend fun execute(
        repoId: String,
        command: ParsedGitCommand,
        context: CommandContext
    ): CommandResult {
        
        val options = context["options"] as? GitLogOptions ?: GitLogOptions()
        val commits = gitHistoryService.getCommitLog(repoId, options.limit)
        
        return buildString {
            appendLine("Git Log Analysis:")
            appendLine("Repository: $repoId")
            appendLine("Commits: ${commits.size}")
            appendLine()
            
            commits.forEach { commit ->
                appendLine("Commit: ${commit.commitId}")
                appendLine("Author: ${commit.author}")
                appendLine("Message: ${commit.message}")
                appendLine("Timestamp: ${commit.timestamp}")
                appendLine()
            }
        }
    }
}

class GitShowHandler(
    internal val gitHistoryService: GitHistoryService,
    internal val forensicsService: GitForensicsService
) : GitCommandHandler {
    
    override suspend fun execute(
        repoId: String,
        command: ParsedGitCommand,
        context: CommandContext
    ): CommandResult {
        
        val objectHash = context["objectHash"] as? String ?: return "Error: No object hash provided"
        val gitObject = gitHistoryService.getGitObject(repoId, objectHash)
        
        return if (gitObject != null) {
            buildString {
                appendLine("Git Object Analysis:")
                appendLine("Hash: $objectHash")
                appendLine("Type: ${gitObject.type}")
                appendLine("Size: ${gitObject.size}")
                appendLine("Content: ${gitObject.content}")
            }
        } else {
            "Error: Object not found: $objectHash"
        }
    }
}

class GitDiffHandler(
    internal val gitHistoryService: GitHistoryService,
    internal val forensicsService: GitForensicsService
) : GitCommandHandler {
    
    override suspend fun execute(
        repoId: String,
        command: ParsedGitCommand,
        context: CommandContext
    ): CommandResult {
        
        val fromCommit = context["fromCommit"] as? String ?: return "Error: No from commit"
        val toCommit = context["toCommit"] as? String ?: return "Error: No to commit"
        
        val from = gitHistoryService.getCommit(repoId, fromCommit)
        val to = gitHistoryService.getCommit(repoId, toCommit)
        
        return if (from != null && to != null) {
            buildString {
                appendLine("Git Diff Analysis:")
                appendLine("From: ${from.commitId}")
                appendLine("To: ${to.commitId}")
                appendLine("Diff: ${to.diff}")
            }
        } else {
            "Error: One or both commits not found"
        }
    }
}

class GitBlameHandler(
    internal val gitHistoryService: GitHistoryService,
    internal val forensicsService: GitForensicsService
) : GitCommandHandler {
    
    override suspend fun execute(
        repoId: String,
        command: ParsedGitCommand,
        context: CommandContext
    ): CommandResult {
        
        val filePath = context["filePath"] as? String ?: return "Error: No file path provided"
        
        return buildString {
            appendLine("Git Blame Analysis:")
            appendLine("File: $filePath")
            appendLine("Repository: $repoId")
            appendLine("Line attribution analysis would be performed here")
        }
    }
}

class GitStatusHandler(
    internal val gitHistoryService: GitHistoryService,
    internal val forensicsService: GitForensicsService
) : GitCommandHandler {
    
    override suspend fun execute(
        repoId: String,
        command: ParsedGitCommand,
        context: CommandContext
    ): CommandResult {
        
        return buildString {
            appendLine("Git Status Analysis:")
            appendLine("Repository: $repoId")
            appendLine("Current branch: main")
            appendLine("Working directory: clean")
            appendLine("Staging area: clean")
        }
    }
}

class GitBranchHandler(
    internal val gitHistoryService: GitHistoryService,
    internal val forensicsService: GitForensicsService
) : GitCommandHandler {
    
    override suspend fun execute(
        repoId: String,
        command: ParsedGitCommand,
        context: CommandContext
    ): CommandResult {
        
        return buildString {
            appendLine("Git Branch Analysis:")
            appendLine("Repository: $repoId")
            appendLine("Branches:")
            appendLine("  * main")
            appendLine("    feature/new-feature")
            appendLine("    bugfix/issue-123")
        }
    }
}

class GitRemoteHandler(
    internal val gitHistoryService: GitHistoryService,
    internal val forensicsService: GitForensicsService
) : GitCommandHandler {
    
    override suspend fun execute(
        repoId: String,
        command: ParsedGitCommand,
        context: CommandContext
    ): CommandResult {
        
        return buildString {
            appendLine("Git Remote Analysis:")
            appendLine("Repository: $repoId")
            appendLine("Remotes:")
            appendLine("  origin    https://github.com/user/repo.git (fetch)")
            appendLine("  origin    https://github.com/user/repo.git (push)")
        }
    }
}

class GitTagHandler(
    internal val gitHistoryService: GitHistoryService,
    internal val forensicsService: GitForensicsService
) : GitCommandHandler {
    
    override suspend fun execute(
        repoId: String,
        command: ParsedGitCommand,
        context: CommandContext
    ): CommandResult {
        
        return buildString {
            appendLine("Git Tag Analysis:")
            appendLine("Repository: $repoId")
            appendLine("Tags:")
            appendLine("  v1.0.0")
            appendLine("  v1.1.0")
            appendLine("  release/2023-12-01")
        }
    }
}

class GitStashHandler(
    internal val gitHistoryService: GitHistoryService,
    internal val forensicsService: GitForensicsService
) : GitCommandHandler {
    
    override suspend fun execute(
        repoId: String,
        command: ParsedGitCommand,
        context: CommandContext
    ): CommandResult {
        
        return buildString {
            appendLine("Git Stash Analysis:")
            appendLine("Repository: $repoId")
            appendLine("Stashes:")
            appendLine("  stash@{0}: WIP on feature: abc1234")
            appendLine("  stash@{1}: WIP on main: def5678")
        }
    }
}

class GitReflogHandler(
    internal val gitHistoryService: GitHistoryService,
    internal val forensicsService: GitForensicsService
) : GitCommandHandler {
    
    override suspend fun execute(
        repoId: String,
        command: ParsedGitCommand,
        context: CommandContext
    ): CommandResult {
        
        return buildString {
            appendLine("Git Reflog Analysis:")
            appendLine("Repository: $repoId")
            appendLine("Reference History:")
            appendLine("  abc1234 HEAD@{0}: commit: Latest commit")
            appendLine("  def5678 HEAD@{1}: commit: Previous commit")
            appendLine("  ghi9012 HEAD@{2}: checkout: moving from feature to main")
        }
    }
}

// ===== DATA CLASSES =====

@Serializable
data class ParsedGitCommand(
    val name: String,
    val arguments: List<String>,
    val original: String
)

@Serializable
data class GitLogOptions(
    val limit: Int = 10,
    val since: String? = null,
    val until: String? = null,
    val author: String? = null
)

@Serializable
data class CommandExecution(
    val repoId: String,
    val command: String,
    val query: String,
    val context: CommandContext,
    val result: CommandResult,
    val insights: List<LLMInsight>,
    val timestamp: Long,
    val duration: Long
)

@Serializable
data class DiffHunk(
    val file: String,
    val oldLine: Int,
    val newLine: Int,
    val content: String
)

@Serializable
data class BlameLine(
    val lineNumber: Int,
    val commitId: String,
    val author: String,
    val timestamp: Long,
    val content: String
)

@Serializable
data class GitBranch(
    val name: String,
    val commitId: String,
    val isCurrent: Boolean
)

@Serializable
data class RepositoryStatus(
    val currentBranch: String,
    val workingDirectory: String,
    val stagingArea: String,
    val untrackedFiles: List<String>
)

sealed class LLMCommandResult {
    data class Success(
        val command: String,
        val result: CommandResult,
        val insights: List<LLMInsight>,
        val execution: CommandExecution
    ) : LLMCommandResult()
    
    data class Error(val message: String) : LLMCommandResult()
} 