package tests.tdd

import fiduciary.metaverse.*
import borg.trikeshed.lib.*
import borg.trikeshed.couchdb.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlin.test.*

/**
 * TDD Test Suite for LLM Git Commands
 * 
 * Tests the most commonly used LLM git commands with read-only forensics:
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
class LLMGitCommandsTest {
    
    internal lateinit var llmGitCommands: LLMGitCommands
    internal lateinit var forensicsService: GitForensicsService
    internal lateinit var gitHistoryService: GitHistoryService
    internal lateinit var couchService: CouchDBService
    
    @BeforeTest
    fun setup() {
        couchService = CouchDBService()
        gitHistoryService = GitHistoryService()
        forensicsService = GitForensicsService(couchService, gitHistoryService)
        llmGitCommands = LLMGitCommands(forensicsService, gitHistoryService, couchService)
    }
    
    // ===== GIT LOG TESTS =====
    
    @Test
    fun `should execute git log with LLM analysis`() = runTest {
        // Given: Repository with commits
        val repoId = "test-repo-log"
        setupTestRepository(repoId)
        
        val query = "Show me the recent commit history and analyze the patterns"
        
        // When: Executing git log
        val result = llmGitCommands.gitLog(repoId, query)
        
        // Then: Should return successful result with insights
        assertTrue(result is LLMCommandResult.Success)
        val success = result as LLMCommandResult.Success
        assertEquals("git log", success.command)
        assertTrue(success.result.contains("Git Log Analysis"))
        assertTrue(success.result.contains("Repository: $repoId"))
        assertTrue(success.insights.isNotEmpty())
        assertTrue(success.execution.duration > 0)
    }
    
    @Test
    fun `should analyze commit patterns in git log`() = runTest {
        // Given: Repository with specific commit types
        val repoId = "test-repo-patterns"
        setupRepositoryWithPatterns(repoId)
        
        val query = "What patterns do you see in the commit messages?"
        
        // When: Executing git log with pattern analysis
        val result = llmGitCommands.gitLog(repoId, query)
        
        // Then: Should generate pattern insights
        assertTrue(result is LLMCommandResult.Success)
        val success = result as LLMCommandResult.Success
        
        val patternInsights = success.insights.filter { insight ->
            insight.a.contains("pattern", ignoreCase = true)
        }
        assertTrue(patternInsights.isNotEmpty(), "Should generate pattern insights")
    }
    
    @Test
    fun `should handle git log with custom options`() = runTest {
        // Given: Repository and custom options
        val repoId = "test-repo-options"
        setupTestRepository(repoId)
        
        val options = GitLogOptions(limit = 5, author = "alice")
        val query = "Show me Alice's recent commits"
        
        // When: Executing git log with options
        val result = llmGitCommands.gitLog(repoId, query, options)
        
        // Then: Should respect options
        assertTrue(result is LLMCommandResult.Success)
        val success = result as LLMCommandResult.Success
        assertTrue(success.result.contains("Commits: 5") || success.result.contains("alice"))
    }
    
    // ===== GIT SHOW TESTS =====
    
    @Test
    fun `should execute git show with LLM analysis`() = runTest {
        // Given: Repository with commits
        val repoId = "test-repo-show"
        setupTestRepository(repoId)
        
        val objectHash = "abc1234567890abcdef1234567890abcdef1234"
        val query = "What does this commit contain and what are its implications?"
        
        // When: Executing git show
        val result = llmGitCommands.gitShow(repoId, objectHash, query)
        
        // Then: Should return successful result
        assertTrue(result is LLMCommandResult.Success)
        val success = result as LLMCommandResult.Success
        assertEquals("git show $objectHash", success.command)
        assertTrue(success.result.contains("Git Object Analysis"))
        assertTrue(success.result.contains("Hash: $objectHash"))
        assertTrue(success.insights.isNotEmpty())
    }
    
    @Test
    fun `should handle git show with non-existent object`() = runTest {
        // Given: Repository and non-existent object
        val repoId = "test-repo-show-error"
        setupTestRepository(repoId)
        
        val objectHash = "nonexistent1234567890abcdef1234567890abcdef"
        val query = "Show me this object"
        
        // When: Executing git show
        val result = llmGitCommands.gitShow(repoId, objectHash, query)
        
        // Then: Should handle error gracefully
        assertTrue(result is LLMCommandResult.Success)
        val success = result as LLMCommandResult.Success
        assertTrue(success.result.contains("Error: Object not found"))
    }
    
    // ===== GIT DIFF TESTS =====
    
    @Test
    fun `should execute git diff with LLM analysis`() = runTest {
        // Given: Repository with commits
        val repoId = "test-repo-diff"
        setupTestRepository(repoId)
        
        val fromCommit = "commit-1"
        val toCommit = "commit-2"
        val query = "What changes were made and what do they mean?"
        
        // When: Executing git diff
        val result = llmGitCommands.gitDiff(repoId, fromCommit, toCommit, query)
        
        // Then: Should return successful result
        assertTrue(result is LLMCommandResult.Success)
        val success = result as LLMCommandResult.Success
        assertEquals("git diff $fromCommit $toCommit", success.command)
        assertTrue(success.result.contains("Git Diff Analysis"))
        assertTrue(success.result.contains("From: $fromCommit"))
        assertTrue(success.result.contains("To: $toCommit"))
        
        // Check for change insights
        val changeInsights = success.insights.filter { insight ->
            insight.a.contains("change", ignoreCase = true)
        }
        assertTrue(changeInsights.isNotEmpty(), "Should generate change insights")
    }
    
    @Test
    fun `should analyze diff patterns and generate insights`() = runTest {
        // Given: Repository with significant changes
        val repoId = "test-repo-diff-patterns"
        setupRepositoryWithSignificantChanges(repoId)
        
        val fromCommit = "feature-start"
        val toCommit = "feature-end"
        val query = "Analyze the patterns in these changes"
        
        // When: Executing git diff
        val result = llmGitCommands.gitDiff(repoId, fromCommit, toCommit, query)
        
        // Then: Should generate pattern insights
        assertTrue(result is LLMCommandResult.Success)
        val success = result as LLMCommandResult.Success
        
        val patternInsights = success.insights.filter { insight ->
            insight.a.contains("pattern", ignoreCase = true)
        }
        assertTrue(patternInsights.isNotEmpty(), "Should generate pattern insights")
    }
    
    // ===== GIT BLAME TESTS =====
    
    @Test
    fun `should execute git blame with LLM analysis`() = runTest {
        // Given: Repository and file path
        val repoId = "test-repo-blame"
        setupTestRepository(repoId)
        
        val filePath = "src/main/kotlin/Example.kt"
        val query = "Who wrote this code and what are the authorship patterns?"
        
        // When: Executing git blame
        val result = llmGitCommands.gitBlame(repoId, filePath, query)
        
        // Then: Should return successful result
        assertTrue(result is LLMCommandResult.Success)
        val success = result as LLMCommandResult.Success
        assertEquals("git blame $filePath", success.command)
        assertTrue(success.result.contains("Git Blame Analysis"))
        assertTrue(success.result.contains("File: $filePath"))
        
        // Check for author insights
        val authorInsights = success.insights.filter { insight ->
            insight.a.contains("author", ignoreCase = true)
        }
        assertTrue(authorInsights.isNotEmpty(), "Should generate author insights")
    }
    
    // ===== GIT STATUS TESTS =====
    
    @Test
    fun `should execute git status with LLM analysis`() = runTest {
        // Given: Repository
        val repoId = "test-repo-status"
        setupTestRepository(repoId)
        
        val query = "What is the current state of the repository?"
        
        // When: Executing git status
        val result = llmGitCommands.gitStatus(repoId, query)
        
        // Then: Should return successful result
        assertTrue(result is LLMCommandResult.Success)
        val success = result as LLMCommandResult.Success
        assertEquals("git status", success.command)
        assertTrue(success.result.contains("Git Status Analysis"))
        assertTrue(success.result.contains("Repository: $repoId"))
        assertTrue(success.result.contains("Current branch: main"))
    }
    
    // ===== GIT BRANCH TESTS =====
    
    @Test
    fun `should execute git branch with LLM analysis`() = runTest {
        // Given: Repository
        val repoId = "test-repo-branch"
        setupTestRepository(repoId)
        
        val query = "What branches exist and what are their purposes?"
        
        // When: Executing git branch
        val result = llmGitCommands.gitBranch(repoId, query)
        
        // Then: Should return successful result
        assertTrue(result is LLMCommandResult.Success)
        val success = result as LLMCommandResult.Success
        assertEquals("git branch", success.command)
        assertTrue(success.result.contains("Git Branch Analysis"))
        assertTrue(success.result.contains("Repository: $repoId"))
        assertTrue(success.result.contains("Branches:"))
    }
    
    // ===== GIT REMOTE TESTS =====
    
    @Test
    fun `should execute git remote with LLM analysis`() = runTest {
        // Given: Repository
        val repoId = "test-repo-remote"
        setupTestRepository(repoId)
        
        val query = "What remotes are configured and what do they represent?"
        
        // When: Executing git remote
        val result = llmGitCommands.gitRemote(repoId, query)
        
        // Then: Should return successful result
        assertTrue(result is LLMCommandResult.Success)
        val success = result as LLMCommandResult.Success
        assertEquals("git remote", success.command)
        assertTrue(success.result.contains("Git Remote Analysis"))
        assertTrue(success.result.contains("Repository: $repoId"))
        assertTrue(success.result.contains("Remotes:"))
    }
    
    // ===== GIT TAG TESTS =====
    
    @Test
    fun `should execute git tag with LLM analysis`() = runTest {
        // Given: Repository
        val repoId = "test-repo-tag"
        setupTestRepository(repoId)
        
        val query = "What tags exist and what do they represent?"
        
        // When: Executing git tag
        val result = llmGitCommands.gitTag(repoId, query)
        
        // Then: Should return successful result
        assertTrue(result is LLMCommandResult.Success)
        val success = result as LLMCommandResult.Success
        assertEquals("git tag", success.command)
        assertTrue(success.result.contains("Git Tag Analysis"))
        assertTrue(success.result.contains("Repository: $repoId"))
        assertTrue(success.result.contains("Tags:"))
    }
    
    // ===== GIT STASH TESTS =====
    
    @Test
    fun `should execute git stash with LLM analysis`() = runTest {
        // Given: Repository
        val repoId = "test-repo-stash"
        setupTestRepository(repoId)
        
        val query = "What stashes exist and what do they contain?"
        
        // When: Executing git stash
        val result = llmGitCommands.gitStash(repoId, query)
        
        // Then: Should return successful result
        assertTrue(result is LLMCommandResult.Success)
        val success = result as LLMCommandResult.Success
        assertEquals("git stash", success.command)
        assertTrue(success.result.contains("Git Stash Analysis"))
        assertTrue(success.result.contains("Repository: $repoId"))
        assertTrue(success.result.contains("Stashes:"))
    }
    
    // ===== GIT REFLOG TESTS =====
    
    @Test
    fun `should execute git reflog with LLM analysis`() = runTest {
        // Given: Repository
        val repoId = "test-repo-reflog"
        setupTestRepository(repoId)
        
        val query = "What is the reference history and what does it tell us?"
        
        // When: Executing git reflog
        val result = llmGitCommands.gitReflog(repoId, query)
        
        // Then: Should return successful result
        assertTrue(result is LLMCommandResult.Success)
        val success = result as LLMCommandResult.Success
        assertEquals("git reflog", success.command)
        assertTrue(success.result.contains("Git Reflog Analysis"))
        assertTrue(success.result.contains("Repository: $repoId"))
        assertTrue(success.result.contains("Reference History:"))
    }
    
    // ===== COMMAND HISTORY TESTS =====
    
    @Test
    fun `should track command execution history`() = runTest {
        // Given: Repository and multiple commands
        val repoId = "test-repo-history"
        setupTestRepository(repoId)
        
        // Execute multiple commands
        llmGitCommands.gitLog(repoId, "Show commits")
        llmGitCommands.gitStatus(repoId, "Show status")
        llmGitCommands.gitBranch(repoId, "Show branches")
        
        // When: Getting command history
        val history = llmGitCommands.getCommandHistory(repoId)
        
        // Then: Should track all commands
        assertEquals(3, history.size)
        assertTrue(history.all { it.repoId == repoId })
        assertTrue(history.any { it.command == "git log" })
        assertTrue(history.any { it.command == "git status" })
        assertTrue(history.any { it.command == "git branch" })
    }
    
    @Test
    fun `should filter command history by time range`() = runTest {
        // Given: Repository and commands over time
        val repoId = "test-repo-time-filter"
        setupTestRepository(repoId)
        
        // Execute commands
        llmGitCommands.gitLog(repoId, "Show commits")
        
        // Wait a bit
        delay(100)
        
        llmGitCommands.gitStatus(repoId, "Show status")
        
        // When: Getting recent history
        val recentHistory = llmGitCommands.getCommandHistory(repoId, timeRange = 50L)
        
        // Then: Should only include recent commands
        assertTrue(recentHistory.size < 2, "Should filter by time range")
    }
    
    // ===== LLM INSIGHTS TESTS =====
    
    @Test
    fun `should generate insights for commit analysis`() = runTest {
        // Given: Repository with commits
        val repoId = "test-repo-insights"
        setupTestRepository(repoId)
        
        val query = "Analyze the commit patterns and identify trends"
        
        // When: Executing command with commit analysis
        val result = llmGitCommands.gitLog(repoId, query)
        
        // Then: Should generate commit insights
        assertTrue(result is LLMCommandResult.Success)
        val success = result as LLMCommandResult.Success
        
        val commitInsights = success.insights.filter { insight ->
            insight.a.contains("commit", ignoreCase = true)
        }
        assertTrue(commitInsights.isNotEmpty(), "Should generate commit insights")
    }
    
    @Test
    fun `should generate insights for change analysis`() = runTest {
        // Given: Repository with changes
        val repoId = "test-repo-change-insights"
        setupTestRepository(repoId)
        
        val query = "What changes were made and what do they mean?"
        
        // When: Executing diff command
        val result = llmGitCommands.gitDiff(repoId, "commit-1", "commit-2", query)
        
        // Then: Should generate change insights
        assertTrue(result is LLMCommandResult.Success)
        val success = result as LLMCommandResult.Success
        
        val changeInsights = success.insights.filter { insight ->
            insight.a.contains("change", ignoreCase = true)
        }
        assertTrue(changeInsights.isNotEmpty(), "Should generate change insights")
    }
    
    @Test
    fun `should get LLM insights for specific queries`() = runTest {
        // Given: Repository with command history
        val repoId = "test-repo-query-insights"
        setupTestRepository(repoId)
        
        val query = "commit analysis"
        llmGitCommands.gitLog(repoId, query)
        llmGitCommands.gitShow(repoId, "abc123", query)
        
        // When: Getting insights for query
        val insights = llmGitCommands.getLLMInsights(repoId, query)
        
        // Then: Should return relevant insights
        assertTrue(insights.isNotEmpty(), "Should return insights for query")
        assertTrue(insights.all { insight -> insight.a.contains("commit", ignoreCase = true) })
    }
    
    // ===== ERROR HANDLING TESTS =====
    
    @Test
    fun `should handle unknown commands gracefully`() = runTest {
        // Given: Unknown command
        val repoId = "test-repo-unknown"
        setupTestRepository(repoId)
        
        val query = "Execute unknown command"
        
        // When: Executing unknown command
        val result = llmGitCommands.executeLLMCommand(repoId, "git unknown", query)
        
        // Then: Should return error
        assertTrue(result is LLMCommandResult.Error)
        val error = result as LLMCommandResult.Error
        assertTrue(error.message.contains("Unknown command"))
    }
    
    @Test
    fun `should handle missing repository gracefully`() = runTest {
        // Given: Non-existent repository
        val repoId = "nonexistent-repo"
        val query = "Show me commits"
        
        // When: Executing command on non-existent repo
        val result = llmGitCommands.gitLog(repoId, query)
        
        // Then: Should handle gracefully (may return empty result or error)
        assertNotNull(result)
    }
    
    // ===== INTEGRATION TESTS =====
    
    @Test
    fun `should integrate with forensics service`() = runTest {
        // Given: Complete setup with forensics
        val repoId = "integration-test-repo"
        setupTestRepository(repoId)
        
        // Initialize forensics
        forensicsService.initializeForensics(repoId)
        
        val query = "Comprehensive analysis of the repository"
        
        // When: Executing multiple commands
        val logResult = llmGitCommands.gitLog(repoId, query)
        val statusResult = llmGitCommands.gitStatus(repoId, query)
        val branchResult = llmGitCommands.gitBranch(repoId, query)
        
        // Then: All should work together
        assertTrue(logResult is LLMCommandResult.Success)
        assertTrue(statusResult is LLMCommandResult.Success)
        assertTrue(branchResult is LLMCommandResult.Success)
        
        // Check forensics integration
        val history = llmGitCommands.getCommandHistory(repoId)
        assertEquals(3, history.size)
    }
    
    // ===== HELPER METHODS =====
    
    internal suspend fun setupTestRepository(repoId: String) {
        // Initialize git history
        gitHistoryService.initializeHistory(repoId, "Initial content")
        
        // Add test commits
        val commits = listOf(
            GitCommit(
                commitId = "commit-1",
                parentCommitId = null,
                author = "alice",
                timestamp = Clock.System.now().toEpochMilliseconds(),
                message = "Initial commit",
                diff = "Initial content",
                waveletId = "wavelet-1",
                documentState = "Initial content"
            ),
            GitCommit(
                commitId = "commit-2",
                parentCommitId = "commit-1",
                author = "bob",
                timestamp = Clock.System.now().toEpochMilliseconds() + 1000,
                message = "Add feature",
                diff = "+ New feature",
                waveletId = "wavelet-2",
                documentState = "Initial content\nNew feature"
            )
        )
        
        commits.forEach { commit ->
            gitHistoryService.addCommit(repoId, commit)
        }
    }
    
    internal suspend fun setupRepositoryWithPatterns(repoId: String) {
        setupTestRepository(repoId)
        
        // Add commits with specific patterns
        val patternCommits = listOf(
            GitCommit(
                commitId = "pattern-1",
                parentCommitId = "commit-2",
                author = "alice",
                timestamp = Clock.System.now().toEpochMilliseconds() + 2000,
                message = "fix: resolve bug in authentication",
                diff = "Fix auth bug",
                waveletId = "wavelet-3",
                documentState = "Fixed auth"
            ),
            GitCommit(
                commitId = "pattern-2",
                parentCommitId = "pattern-1",
                author = "bob",
                timestamp = Clock.System.now().toEpochMilliseconds() + 3000,
                message = "feat: add new user interface",
                diff = "Add UI components",
                waveletId = "wavelet-4",
                documentState = "Added UI"
            )
        )
        
        patternCommits.forEach { commit ->
            gitHistoryService.addCommit(repoId, commit)
        }
    }
    
    internal suspend fun setupRepositoryWithSignificantChanges(repoId: String) {
        setupTestRepository(repoId)
        
        // Add commits with significant changes
        val significantCommits = listOf(
            GitCommit(
                commitId = "feature-start",
                parentCommitId = "commit-2",
                author = "alice",
                timestamp = Clock.System.now().toEpochMilliseconds() + 2000,
                message = "Start implementing new feature",
                diff = "Begin feature implementation",
                waveletId = "wavelet-3",
                documentState = "Feature start"
            ),
            GitCommit(
                commitId = "feature-end",
                parentCommitId = "feature-start",
                author = "alice",
                timestamp = Clock.System.now().toEpochMilliseconds() + 3000,
                message = "Complete new feature implementation",
                diff = "Complete feature with tests",
                waveletId = "wavelet-4",
                documentState = "Feature complete"
            )
        )
        
        significantCommits.forEach { commit ->
            gitHistoryService.addCommit(repoId, commit)
        }
    }
} 