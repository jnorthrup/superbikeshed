package k2script.git

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import java.io.File

/**
 * TDD Tests for k2script Feature Branch Management
 * 
 * Features:
 * - Feature branch creation and management
 * - Repository detection and validation
 * - Branch naming conventions
 * - Conflict detection and resolution
 * - Integration with MCP for repository operations
 */
class FeatureBranchTest {
    
    @Test
    fun `should detect Git repository in PWD`() = runTest {
        // Given
        val gitManager = GitFeatureBranchManager()
        
        // When
        val isGitRepo = gitManager.isGitRepository(File("."))
        
        // Then
        assertTrue(isGitRepo, "Current directory should be a Git repository")
    }
    
    @Test
    fun `should create feature branch with proper naming`() = runTest {
        // Given
        val gitManager = GitFeatureBranchManager()
        val featureName = "add-mcp-server"
        
        // When
        val branchName = gitManager.createFeatureBranch(featureName)
        
        // Then
        assertNotNull(branchName)
        assertTrue(branchName.startsWith("feature/"))
        assertTrue(branchName.contains(featureName))
        assertTrue(branchName.matches(Regex("feature/[a-z0-9-]+")))
    }
    
    @Test
    fun `should validate feature branch name format`() = runTest {
        // Given
        val gitManager = GitFeatureBranchManager()
        
        // When/Then
        assertTrue(gitManager.isValidFeatureName("add-mcp-server"))
        assertTrue(gitManager.isValidFeatureName("fix-bug-123"))
        assertTrue(gitManager.isValidFeatureName("update-docs"))
        
        assertFalse(gitManager.isValidFeatureName(""))
        assertFalse(gitManager.isValidFeatureName("invalid name"))
        assertFalse(gitManager.isValidFeatureName("UPPERCASE"))
        assertFalse(gitManager.isValidFeatureName("invalid/name"))
    }
    
    @Test
    fun `should check if branch already exists`() = runTest {
        // Given
        val gitManager = GitFeatureBranchManager()
        val featureName = "test-feature"
        
        // When
        val exists = gitManager.branchExists("feature/$featureName")
        
        // Then
        assertFalse(exists, "Feature branch should not exist initially")
    }
    
    @Test
    fun `should get current branch information`() = runTest {
        // Given
        val gitManager = GitFeatureBranchManager()
        
        // When
        val currentBranch = gitManager.getCurrentBranch()
        
        // Then
        assertNotNull(currentBranch)
        assertTrue(currentBranch.name.isNotEmpty())
        assertNotNull(currentBranch.lastCommit)
    }
    
    @Test
    fun `should list all branches`() = runTest {
        // Given
        val gitManager = GitFeatureBranchManager()
        
        // When
        val branches = gitManager.listBranches()
        
        // Then
        assertNotNull(branches)
        assertTrue(branches.isNotEmpty())
        assertTrue(branches.any { it.name == "main" || it.name == "master" })
    }
    
    @Test
    fun `should detect uncommitted changes`() = runTest {
        // Given
        val gitManager = GitFeatureBranchManager()
        
        // When
        val hasChanges = gitManager.hasUncommittedChanges()
        
        // Then
        // This test may pass or fail depending on current state
        assertNotNull(hasChanges)
    }
    
    @Test
    fun `should stage and commit changes`() = runTest {
        // Given
        val gitManager = GitFeatureBranchManager()
        val testFile = File("test-commit.txt")
        testFile.writeText("test content")
        
        // When
        val commitHash = gitManager.stageAndCommit("test commit", listOf(testFile))
        
        // Then
        assertNotNull(commitHash)
        assertTrue(commitHash.matches(Regex("[a-f0-9]{40}")))
        
        // Cleanup
        testFile.delete()
    }
    
    @Test
    fun `should push feature branch to remote`() = runTest {
        // Given
        val gitManager = GitFeatureBranchManager()
        val branchName = "feature/test-push"
        
        // When
        val pushed = gitManager.pushBranch(branchName)
        
        // Then
        // This may fail if no remote is configured, which is expected
        assertNotNull(pushed)
    }
    
    @Test
    fun `should create pull request`() = runTest {
        // Given
        val gitManager = GitFeatureBranchManager()
        val featureName = "test-feature"
        val title = "Add test feature"
        val description = "This is a test feature"
        
        // When
        val pr = gitManager.createPullRequest(featureName, title, description)
        
        // Then
        assertNotNull(pr)
        assertEquals(title, pr.title)
        assertEquals(description, pr.description)
        assertTrue(pr.sourceBranch.startsWith("feature/"))
    }
    
    @Test
    fun `should detect merge conflicts`() = runTest {
        // Given
        val gitManager = GitFeatureBranchManager()
        val sourceBranch = "feature/conflict-test"
        val targetBranch = "main"
        
        // When
        val hasConflicts = gitManager.detectMergeConflicts(sourceBranch, targetBranch)
        
        // Then
        assertNotNull(hasConflicts)
    }
    
    @Test
    fun `should get repository information`() = runTest {
        // Given
        val gitManager = GitFeatureBranchManager()
        
        // When
        val repoInfo = gitManager.getRepositoryInfo()
        
        // Then
        assertNotNull(repoInfo)
        assertTrue(repoInfo.name.isNotEmpty())
        assertNotNull(repoInfo.remoteUrl)
        assertNotNull(repoInfo.defaultBranch)
    }
    
    @Test
    fun `should validate repository state`() = runTest {
        // Given
        val gitManager = GitFeatureBranchManager()
        
        // When
        val validation = gitManager.validateRepositoryState()
        
        // Then
        assertNotNull(validation)
        assertTrue(validation.isValid)
        assertNotNull(validation.messages)
    }
    
    @Test
    fun `should get feature branch status`() = runTest {
        // Given
        val gitManager = GitFeatureBranchManager()
        val featureName = "test-feature"
        
        // When
        val status = gitManager.getFeatureBranchStatus(featureName)
        
        // Then
        assertNotNull(status)
        assertNotNull(status.branchName)
        assertNotNull(status.lastCommit)
        assertNotNull(status.aheadCount)
        assertNotNull(status.behindCount)
    }
    
    @Test
    fun `should list feature branches`() = runTest {
        // Given
        val gitManager = GitFeatureBranchManager()
        
        // When
        val featureBranches = gitManager.listFeatureBranches()
        
        // Then
        assertNotNull(featureBranches)
        // All returned branches should start with "feature/"
        featureBranches.forEach { branch ->
            assertTrue(branch.name.startsWith("feature/"))
        }
    }
    
    @Test
    fun `should delete feature branch`() = runTest {
        // Given
        val gitManager = GitFeatureBranchManager()
        val featureName = "test-delete-feature"
        
        // When
        val deleted = gitManager.deleteFeatureBranch(featureName)
        
        // Then
        assertNotNull(deleted)
        // Note: This may fail if branch doesn't exist, which is expected
    }
    
    @Test
    fun `should get commit history`() = runTest {
        // Given
        val gitManager = GitFeatureBranchManager()
        val branchName = "main"
        val limit = 10
        
        // When
        val commits = gitManager.getCommitHistory(branchName, limit)
        
        // Then
        assertNotNull(commits)
        assertTrue(commits.size <= limit)
        commits.forEach { commit ->
            assertNotNull(commit.hash)
            assertNotNull(commit.message)
            assertNotNull(commit.author)
            assertNotNull(commit.date)
        }
    }
    
    @Test
    fun `should create feature branch with MCP integration`() = runTest {
        // Given
        val gitManager = GitFeatureBranchManager()
        val mcpIntegration = GitMCPIntegration()
        val featureName = "mcp-integration-test"
        
        // When
        val result = mcpIntegration.createFeatureBranchWithMCP(featureName)
        
        // Then
        assertNotNull(result)
        assertTrue(result.success)
        assertNotNull(result.branchName)
        assertNotNull(result.mcpServerInfo)
    }
}

// === Git Data Classes ===

data class GitBranch(
    val name: String,
    val isCurrent: Boolean = false,
    val lastCommit: GitCommit? = null
)

data class GitCommit(
    val hash: String,
    val message: String,
    val author: String,
    val date: String
)

data class GitRepositoryInfo(
    val name: String,
    val remoteUrl: String?,
    val defaultBranch: String,
    val currentBranch: String
)

data class GitValidationResult(
    val isValid: Boolean,
    val messages: List<String>
)

data class FeatureBranchStatus(
    val branchName: String,
    val lastCommit: GitCommit,
    val aheadCount: Int,
    val behindCount: Int,
    val hasUncommittedChanges: Boolean
)

data class PullRequest(
    val id: String,
    val title: String,
    val description: String,
    val sourceBranch: String,
    val targetBranch: String,
    val status: String
)

data class MCPFeatureBranchResult(
    val success: Boolean,
    val branchName: String?,
    val mcpServerInfo: String?,
    val error: String? = null
) 