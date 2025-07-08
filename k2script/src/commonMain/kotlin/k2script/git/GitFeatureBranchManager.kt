@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package k2script.git

import borg.trikeshed.io.*
import k2script.platform.*
import k2script.platform.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.runBlocking

/**
 * Git Feature Branch Manager with TrikeShed Taxonomy Integration
 * 
 * Features:
 * - Rapid cloning with LFS support
 * - TrikeShed taxonomy-based branch naming
 * - Automatic GitHub/GitLab/upstream setup
 * - Deployment recipes (Docker/Kubernetes)
 * - Fiduciary attention to Git tree and LFS objects
 */
class GitFeatureBranchManager(
    internal var workDir: String = ".",
    internal val tmpDir: String = "${Environment.getProperty("java.io.tmpdir") ?: "/tmp"}/k2script-features"
) {
    internal val taxonomy = TrikeShedTaxonomy()
    internal val lfsManager = GitLFSManager()
    internal val recipeManager = DeploymentRecipeManager()
    
    init {
        runBlocking { // Use runBlocking for init block to call suspend functions
            coroutineContext.fileSystemOperations.createTempDir(prefix = tmpDir) // Assuming tmpDir is a prefix for temp dir
        }
    }
    
    /**
     * Detect Git repository in PWD with fiduciary attention
     */
    suspend fun isGitRepository(dir: String): Boolean {
        val fileSystem = coroutineContext.fileSystemOperations
        val gitDir = File("$dir/.git")
        if (!fileSystem.fileExists(gitDir)) return false
        
        // Fiduciary attention: Check Git tree integrity
        val objectsDir = File("$gitDir/objects")
        val refsDir = File("$gitDir/refs")
        val headFile = File("$gitDir/HEAD")
        
        return fileSystem.fileExists(objectsDir) && fileSystem.fileExists(refsDir) && fileSystem.fileExists(headFile)
    }
    
    /**
     * Create feature branch with TrikeShed taxonomy naming
     */
    suspend fun createFeatureBranch(featureName: String): String {
        if (!isValidFeatureName(featureName)) {
            throw IllegalArgumentException("Invalid feature name: $featureName")
        }
        
        val taxonomyBranch = taxonomy.generateBranchName(featureName)
        val branchName = "feature/$taxonomyBranch"
        
        if (branchExists(branchName)) {
            throw IllegalStateException("Branch $branchName already exists")
        }
        
        // Create branch with fiduciary attention
        executeGitCommandInDir(workDir, "checkout", "-b", branchName)
        
        // Setup LFS tracking with attention
        lfsManager.setupLFSTracking(workDir)
        
        return branchName
    }
    
    /**
     * Rapid clone with LFS and deployment recipes
     */
    suspend fun rapidClone(
        sourceUrl: String,
        featureName: String,
        setupRemotes: Boolean = true,
        includeLFS: Boolean = true,
        generateRecipes: Boolean = true
    ): RapidCloneResult {
        val cloneDir = "$tmpDir/clone-${System.currentTimeMillis()}"
        createDirectory(cloneDir)
        
        try {
            // Clone with LFS support
            val cloneResult = if (includeLFS) {
                executeGitCommandInDir(cloneDir, "clone", "--recurse-submodules", sourceUrl, ".")
            } else {
                executeGitCommandInDir(cloneDir, "clone", sourceUrl, ".")
            }
            
            // Setup LFS with fiduciary attention
            if (includeLFS) {
                lfsManager.setupLFSTracking(cloneDir)
                lfsManager.pullLFSObjects(cloneDir)
            }
            
            // Create feature branch
            val branchName = createFeatureBranchInDir(cloneDir, featureName)
            
            // Setup remotes with attention
            val remotes = if (setupRemotes) {
                setupRemotesWithAttention(cloneDir, sourceUrl)
            } else {
                emptyList()
            }
            
            // Generate deployment recipes
            val recipes = if (generateRecipes) {
                recipeManager.generateRecipes(cloneDir, featureName)
            } else {
                emptyList()
            }
            
            return RapidCloneResult(
                success = true,
                cloneDir = cloneDir,
                branchName = branchName,
                remotes = remotes,
                recipes = recipes,
                lfsObjects = if (includeLFS) lfsManager.getLFSObjects(cloneDir) else emptyList()
            )
            
        } catch (e: Exception) {
            return RapidCloneResult(
                success = false,
                error = e.message,
                cloneDir = cloneDir
            )
        }
    }
    
    /**
     * Setup remotes with fiduciary attention to Git tree
     */
    internal suspend fun setupRemotesWithAttention(cloneDir: String, sourceUrl: String): List<GitRemote> {
        val remotes = mutableListOf<GitRemote>()
        
        // Parse source URL to determine platform
        val platform = detectGitPlatform(sourceUrl)
        
        when (platform) {
            GitPlatform.GITHUB -> {
                // Setup GitHub remotes
                val githubUrl = convertToGitHubUrl(sourceUrl)
                executeGitCommandInDir(cloneDir, "remote", "add", "origin", githubUrl)
                executeGitCommandInDir(cloneDir, "remote", "add", "upstream", sourceUrl)
                remotes.add(GitRemote("origin", githubUrl, GitPlatform.GITHUB))
                remotes.add(GitRemote("upstream", sourceUrl, GitPlatform.GITHUB))
            }
            GitPlatform.GITLAB -> {
                // Setup GitLab remotes
                val gitlabUrl = convertToGitLabUrl(sourceUrl)
                executeGitCommandInDir(cloneDir, "remote", "add", "origin", gitlabUrl)
                executeGitCommandInDir(cloneDir, "remote", "add", "upstream", sourceUrl)
                remotes.add(GitRemote("origin", gitlabUrl, GitPlatform.GITLAB))
                remotes.add(GitRemote("upstream", sourceUrl, GitPlatform.GITLAB))
            }
            else -> {
                // Generic remote setup
                executeGitCommandInDir(cloneDir, "remote", "add", "origin", sourceUrl)
                remotes.add(GitRemote("origin", sourceUrl, GitPlatform.GENERIC))
            }
        }
        
        return remotes
    }
    
    /**
     * Validate feature name with TrikeShed taxonomy
     */
    fun isValidFeatureName(name: String): Boolean {
        if (name.isEmpty()) return false
        
        // Check basic format
        val basicPattern = Regex("^[a-z0-9-]+$")
        if (!basicPattern.matches(name)) return false
        
        // Check TrikeShed taxonomy compatibility
        return taxonomy.isValidFeatureName(name)
    }
    
    /**
     * Check if branch exists with fiduciary attention
     */
    fun branchExists(branchName: String): Boolean {
        val result = executeGitCommand(workDir, "branch", "--list", branchName)
        return result.isNotEmpty()
    }
    
    /**
     * Get current branch with full attention to Git tree
     */
    fun getCurrentBranch(): GitBranch {
        val branchName = executeGitCommand(workDir, "branch", "--show-current").trim()
        val lastCommit = getLastCommit()
        
        return GitBranch(
            name = branchName,
            isCurrent = true,
            lastCommit = lastCommit
        )
    }
    
    /**
     * List all branches with fiduciary attention
     */
    fun listBranches(): List<GitBranch> {
        val branches = mutableListOf<GitBranch>()
        val currentBranch = getCurrentBranch()
        
        val branchList = executeGitCommandInDir(workDir, "branch", "--list", "--format=%(refname:short)")
        branchList.split("\n").filter { it.isNotEmpty() }.forEach { branchName ->
            val isCurrent = branchName == currentBranch.name
            val lastCommit = if (isCurrent) currentBranch.lastCommit else getLastCommit(branchName)
            
            branches.add(GitBranch(
                name = branchName,
                isCurrent = isCurrent,
                lastCommit = lastCommit
            ))
        }
        
        return branches
    }
    
    /**
     * Check for uncommitted changes with attention
     */
    fun hasUncommittedChanges(): Boolean {
        val status = executeGitCommandInDir(workDir, "status", "--porcelain")
        return status.isNotEmpty()
    }
    
    /**
     * Stage and commit with fiduciary attention
     */
    suspend fun stageAndCommit(message: String, files: List<String>): String {
        // Stage files with attention
        files.forEach { filePath ->
            executeGitCommandInDir(workDir, "add", filePath)
        }
        
        // Commit with attention to Git tree
        val commitHash = executeGitCommandInDir(workDir, "commit", "-m", message)
        
        // Extract hash from commit output
        val hashPattern = Regex("\\b[a-f0-9]{40}\\b")
        val match = hashPattern.find(commitHash)
        
        return match?.value ?: throw IllegalStateException("Failed to get commit hash")
    }
    
    /**
     * Push branch with LFS attention
     */
    suspend fun pushBranch(branchName: String): Boolean {
        try {
            // Push Git objects
            executeGitCommandInDir(workDir, "push", "origin", branchName)
            
            // Push LFS objects with fiduciary attention
            lfsManager.pushLFSObjects(workDir)
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Create pull request with deployment recipes
     */
    suspend fun createPullRequest(
        featureName: String,
        title: String,
        description: String
    ): PullRequest {
        val branchName = "feature/$featureName"
        val recipes = recipeManager.generateRecipes(workDir, featureName)
        
        // Create PR description with recipes
        val enhancedDescription = buildString {
            appendLine(description)
            appendLine()
            appendLine("## Deployment Recipes")
            recipes.forEach { recipe ->
                appendLine("- ${recipe.name}: ${recipe.description}")
            }
        }
        
        return PullRequest(
            id = generatePRId(),
            title = title,
            description = enhancedDescription,
            sourceBranch = branchName,
            targetBranch = getDefaultBranch(),
            status = "open"
        )
    }
    
    /**
     * Get repository information with fiduciary attention
     */
    fun getRepositoryInfo(): GitRepositoryInfo {
        val name = workDir.split("/").last()
        val remoteUrl = getRemoteUrl("origin")
        val defaultBranch = getDefaultBranch()
        val currentBranch = getCurrentBranch().name
        
        return GitRepositoryInfo(
            name = name,
            remoteUrl = remoteUrl,
            defaultBranch = defaultBranch,
            currentBranch = currentBranch
        )
    }
    
    /**
     * Validate repository state with full attention
     */
    fun validateRepositoryState(): GitValidationResult {
        val messages = mutableListOf<String>()
        var isValid = true
        
        // Check Git tree integrity
        if (!isGitRepository(workDir)) {
            messages.add("Not a valid Git repository")
            isValid = false
        }
        
        // Check LFS setup
        if (lfsManager.hasLFSFiles(workDir) && !lfsManager.isLFSConfigured(workDir)) {
            messages.add("LFS files detected but LFS not configured")
            isValid = false
        }
        
        // Check for uncommitted changes
        if (hasUncommittedChanges()) {
            messages.add("Uncommitted changes detected")
        }
        
        return GitValidationResult(isValid, messages)
    }
    
    /**
     * Get feature branch status with attention
     */
    fun getFeatureBranchStatus(featureName: String): FeatureBranchStatus {
        val branchName = "feature/$featureName"
        val lastCommit = getLastCommit(branchName)
        val aheadCount = getAheadCount(branchName)
        val behindCount = getBehindCount(branchName)
        val hasChanges = hasUncommittedChanges()
        
        return FeatureBranchStatus(
            branchName = branchName,
            lastCommit = lastCommit,
            aheadCount = aheadCount,
            behindCount = behindCount,
            hasUncommittedChanges = hasChanges
        )
    }
    
    /**
     * List feature branches with taxonomy attention
     */
    fun listFeatureBranches(): List<GitBranch> {
        return listBranches().filter { it.name.startsWith("feature/") }
    }
    
    /**
     * Delete feature branch with cleanup attention
     */
    suspend fun deleteFeatureBranch(featureName: String): Boolean {
        val branchName = "feature/$featureName"
        
        try {
            // Switch to default branch first
            val defaultBranch = getDefaultBranch()
            executeGitCommandInDir(workDir, "checkout", defaultBranch)
            
            // Delete local branch
            executeGitCommandInDir(workDir, "branch", "-D", branchName)
            
            // Delete remote branch if exists
            try {
                executeGitCommandInDir(workDir, "push", "origin", "--delete", branchName)
            } catch (e: Exception) {
                // Remote branch might not exist, which is fine
            }
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Get commit history with attention
     */
    fun getCommitHistory(branchName: String, limit: Int): List<GitCommit> {
        val commits = mutableListOf<GitCommit>()
        
        val logOutput = executeGitCommand(
            "log", "--format=%H|%s|%an|%ad", "--date=short", "-n", limit.toString(), branchName
        )
        
        logOutput.split("\n").filter { it.isNotEmpty() }.forEach { line ->
            val parts = line.split("|")
            if (parts.size >= 4) {
                commits.add(GitCommit(
                    hash = parts[0],
                    message = parts[1],
                    author = parts[2],
                    date = parts[3]
                ))
            }
        }
        
        return commits
    }
    
    // Private helper methods
    
    internal suspend fun createFeatureBranchInDir(dir: String, featureName: String): String {
        val originalDir = workDir
        workDir = dir
        val result = createFeatureBranch(featureName)
        workDir = originalDir
        return result
    }
    
    internal suspend fun executeGitCommand(vararg args: String): String {
        val processExecutor = coroutineContext.processExecutor
        val result = processExecutor.runCommand("git " + args.joinToString(" "), File(workDir))
        if (result.exitCode != 0) {
            throw RuntimeException("Git command failed: ${result.stderr}")
        }
        return result.stdout
    }
    
    internal suspend fun executeGitCommandInDir(dir: String, vararg args: String): String {
        val processExecutor = coroutineContext.processExecutor
        val result = processExecutor.runCommand("git " + args.joinToString(" "), File(dir))
        if (result.exitCode != 0) {
            throw RuntimeException("Git command failed in $dir: ${result.stderr}")
        }
        return result.stdout
    }
    
    internal fun getLastCommit(branchName: String = "HEAD"): GitCommit {
        val hash = executeGitCommandInDir(workDir, "rev-parse", branchName).trim()
        val message = executeGitCommandInDir(workDir, "log", "-1", "--format=%s", branchName).trim()
        val author = executeGitCommandInDir(workDir, "log", "-1", "--format=%an", branchName).trim()
        val date = executeGitCommandInDir(workDir, "log", "-1", "--format=%ad", "--date=short", branchName).trim()
        
        return GitCommit(hash, message, author, date)
    }
    
    internal fun getRemoteUrl(remoteName: String): String? {
        return try {
            executeGitCommandInDir(workDir, "remote", "get-url", remoteName).trim()
        } catch (e: Exception) {
            null
        }
    }
    
    internal fun getDefaultBranch(): String {
        return try {
            executeGitCommandInDir(workDir, "symbolic-ref", "refs/remotes/origin/HEAD")
                .trim()
                .removePrefix("refs/remotes/origin/")
        } catch (e: Exception) {
            "main"
        }
    }
    
    internal fun getAheadCount(branchName: String): Int {
        return try {
            val output = executeGitCommandInDir(workDir, "rev-list", "--count", "$branchName..origin/${getDefaultBranch()}")
            output.trim().toInt()
        } catch (e: Exception) {
            0
        }
    }
    
    internal fun getBehindCount(branchName: String): Int {
        return try {
            val output = executeGitCommandInDir(workDir, "rev-list", "--count", "origin/${getDefaultBranch()}..$branchName")
            output.trim().toInt()
        } catch (e: Exception) {
            0
        }
    }
    
    internal fun detectGitPlatform(url: String): GitPlatform {
        return when {
            url.contains("github.com") -> GitPlatform.GITHUB
            url.contains("gitlab.com") -> GitPlatform.GITLAB
            else -> GitPlatform.GENERIC
        }
    }
    
    internal fun convertToGitHubUrl(url: String): String {
        // Convert various GitHub URL formats to SSH
        return url.replace("https://github.com/", "git@github.com:")
    }
    
    internal fun convertToGitLabUrl(url: String): String {
        // Convert various GitLab URL formats to SSH
        return url.replace("https://gitlab.com/", "git@gitlab.com:")
    }
    
    internal fun generatePRId(): String {
        return "pr-${System.currentTimeMillis()}"
    }
}

enum class GitPlatform {
    GITHUB, GITLAB, GENERIC
}

data class GitRemote(
    val name: String,
    val url: String,
    val platform: GitPlatform
)

data class RapidCloneResult(
    val success: Boolean,
    val cloneDir: String? = null,
    val branchName: String? = null,
    val remotes: List<GitRemote> = emptyList(),
    val recipes: List<DeploymentRecipe> = emptyList(),
    val lfsObjects: List<LFSObject> = emptyList(),
    val error: String? = null
)

data class LFSObject(
    val path: String,
    val size: Long,
    val oid: String
)

data class DeploymentRecipe(
    val name: String,
    val type: RecipeType,
    val content: String,
    val description: String
)

enum class RecipeType {
    DOCKER, KUBERNETES, DOCKER_COMPOSE, HELM, TERRAFORM
}

data class GitBranch(
    val name: String,
    val isCurrent: Boolean,
    val lastCommit: GitCommit
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

data class GitCommit(
    val hash: String,
    val message: String,
    val author: String,
    val date: String
)

data class PullRequest(
    val id: String,
    val title: String,
    val description: String,
    val sourceBranch: String,
    val targetBranch: String,
    val status: String
) 