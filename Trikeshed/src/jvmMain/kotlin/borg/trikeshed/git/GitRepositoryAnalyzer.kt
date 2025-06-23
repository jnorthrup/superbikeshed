package borg.trikeshed.git

import borg.trikeshed.lib.*
import kotlinx.datetime.Instant
import java.io.File
import java.nio.file.Path

/**
 * Git Repository Analyzer - Integrates with actual git repositories
 * Extracts taxonomical data from git history using git commands
 * Uses TrikeShed patterns for efficient data processing
 */

/**
 * Git repository analyzer that extracts taxonomical features from real git repos
 */
object GitRepositoryAnalyzer {
    
    /**
     * Analyze a git repository and extract taxonomical data
     */
    fun analyzeRepository(repoPath: Path): GitRepositoryAnalysis {
        val repo = File(repoPath.toFile(), ".git")
        if (!repo.exists()) {
            throw IllegalArgumentException("Not a git repository: $repoPath")
        }
        
        val commits = extractCommits(repoPath)
        val branches = extractBranches(repoPath)
        val tags = extractTags(repoPath)
        val authors = extractAuthors(repoPath)
        
        return GitRepositoryAnalysis(
            repositoryPath = repoPath.toString(),
            commits = commits,
            branches = branches,
            tags = tags,
            authors = authors,
            analysisTimestamp = kotlinx.datetime.Clock.System.now()
        )
    }
    
    /**
     * Extract commits from git repository
     */
    private fun extractCommits(repoPath: Path): Indexed<GitCommit> {
        val commits = mutableListOf<GitCommit>()
        
        try {
            // Get commit log with detailed information
            val process = ProcessBuilder(
                "git", "log", "--pretty=format:%H|%an|%cn|%s|%at|%T|%P",
                "--numstat"
            ).directory(repoPath.toFile()).start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                val lines = output.split("\n")
                var i = 0
                
                while (i < lines.size) {
                    val commitLine = lines[i]
                    if (commitLine.contains("|")) {
                        val parts = commitLine.split("|")
                        if (parts.size >= 7) {
                            val hash = GitCommitHash(parts[0])
                            val author = GitAuthor(parts[1])
                            val committer = GitCommitter(parts[2])
                            val message = GitMessage(parts[3])
                            val timestamp = Instant.fromEpochSeconds(parts[4].toLong())
                            val treeHash = parts[5]
                            val parentHashes = if (parts[6].isNotEmpty()) {
                                parts[6].split(" ").map { GitCommitHash(it) }.toIndexed()
                            } else {
                                emptyIndex()
                            }
                            
                            // Extract changes from subsequent lines
                            val changes = mutableListOf<ImpactfulChange>()
                            i++
                            while (i < lines.size && lines[i].isNotEmpty() && !lines[i].contains("|")) {
                                val changeLine = lines[i]
                                val changeParts = changeLine.split("\t")
                                if (changeParts.size >= 3) {
                                    val added = changeParts[0]
                                    val deleted = changeParts[1]
                                    val filePath = changeParts[2]
                                    
                                    val changeType = when {
                                        added == "-" && deleted == "-" -> GitChangeType(GitChangeType.RENAMED)
                                        added != "0" && deleted == "0" -> GitChangeType(GitChangeType.ADDED)
                                        added == "0" && deleted != "0" -> GitChangeType(GitChangeType.DELETED)
                                        else -> GitChangeType(GitChangeType.MODIFIED)
                                    }
                                    
                                    val change = changeType j GitFilePath(filePath)
                                    val impactLevel = assessFileImpact(filePath, added.toIntOrNull() ?: 0, deleted.toIntOrNull() ?: 0)
                                    changes.add(change j impactLevel)
                                }
                                i++
                            }
                            
                            val changesSeries = changes.toIndexed()
                            val commit = GitTaxonomicalAnalyzer.analyzeCommit(
                                hash, author, committer, message, timestamp, changesSeries
                            )
                            commits.add(commit)
                        } else {
                            i++
                        }
                    } else {
                        i++
                    }
                }
            }
        } catch (e: Exception) {
            println("Error extracting commits: ${e.message}")
        }
        
        return commits.toIndexed()
    }
    
    /**
     * Extract branches from git repository
     */
    private fun extractBranches(repoPath: Path): Indexed<GitBranchInfo> {
        val branches = mutableListOf<GitBranchInfo>()
        
        try {
            val process = ProcessBuilder("git", "branch", "-a", "--format=%(refname:short)|%(committerdate:iso)|%(authordate:iso)")
                .directory(repoPath.toFile()).start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                output.lines().forEach { line ->
                    if (line.contains("|")) {
                        val parts = line.split("|")
                        if (parts.size >= 3) {
                            val branchName = GitBranchName(parts[0])
                            val lastCommitDate = Instant.parse(parts[1])
                            val creationDate = Instant.parse(parts[2])
                            
                            val branchType = classifyBranchType(parts[0])
                            val branchInfo = GitBranchInfo(
                                name = branchName,
                                branchType = branchType,
                                lastCommitDate = lastCommitDate,
                                creationDate = creationDate,
                                isActive = true
                            )
                            branches.add(branchInfo)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("Error extracting branches: ${e.message}")
        }
        
        return branches.toIndexed()
    }
    
    /**
     * Extract tags from git repository
     */
    private fun extractTags(repoPath: Path): Indexed<GitTagInfo> {
        val tags = mutableListOf<GitTagInfo>()
        
        try {
            val process = ProcessBuilder("git", "tag", "-l", "--format=%(refname:short)|%(committerdate:iso)|%(contents:subject)")
                .directory(repoPath.toFile()).start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                output.lines().forEach { line ->
                    if (line.contains("|")) {
                        val parts = line.split("|")
                        if (parts.size >= 3) {
                            val tagName = GitTagName(parts[0])
                            val creationDate = Instant.parse(parts[1])
                            val message = parts[2]
                            
                            val tagType = classifyTagType(parts[0], message)
                            val tagInfo = GitTagInfo(
                                name = tagName,
                                tagType = tagType,
                                creationDate = creationDate,
                                message = message
                            )
                            tags.add(tagInfo)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("Error extracting tags: ${e.message}")
        }
        
        return tags.toIndexed()
    }
    
    /**
     * Extract author information from git repository
     */
    private fun extractAuthors(repoPath: Path): Indexed<GitAuthorInfo> {
        val authors = mutableMapOf<String, GitAuthorStats>()
        
        try {
            val process = ProcessBuilder("git", "shortlog", "-s", "-n", "--all")
                .directory(repoPath.toFile()).start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                output.lines().forEach { line ->
                    if (line.contains("\t")) {
                        val parts = line.split("\t")
                        if (parts.size >= 2) {
                            val commitCount = parts[0].trim().toIntOrNull() ?: 0
                            val authorName = parts[1].trim()
                            
                            val authorStats = GitAuthorStats(
                                author = GitAuthor(authorName),
                                commitCount = commitCount,
                                firstCommitDate = null, // Would need additional processing
                                lastCommitDate = null,  // Would need additional processing
                                preferredFileTypes = emptyIndex(),
                                collaborationPatterns = emptyIndex()
                            )
                            authors[authorName] = authorStats
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("Error extracting authors: ${e.message}")
        }
        
        return authors.values.toIndexed()
    }
    
    /**
     * Assess file impact based on changes
     */
    private fun assessFileImpact(filePath: String, added: Int, deleted: Int): GitImpactLevel {
        val totalChanges = added + deleted
        
        return when {
            totalChanges > 1000 -> GitImpactLevel(GitImpactLevel.CRITICAL)
            totalChanges > 500 -> GitImpactLevel(GitImpactLevel.HIGH)
            totalChanges > 100 -> GitImpactLevel(GitImpactLevel.MEDIUM)
            totalChanges > 10 -> GitImpactLevel(GitImpactLevel.LOW)
            else -> GitImpactLevel(GitImpactLevel.MINIMAL)
        }
    }
    
    /**
     * Classify branch type based on name
     */
    private fun classifyBranchType(branchName: String): GitBranchType {
        return when {
            branchName == "main" || branchName == "master" -> GitBranchType.MAIN
            branchName.startsWith("feature/") -> GitBranchType.FEATURE
            branchName.startsWith("hotfix/") -> GitBranchType.HOTFIX
            branchName.startsWith("bugfix/") -> GitBranchType.BUGFIX
            branchName.startsWith("release/") -> GitBranchType.RELEASE
            branchName.startsWith("develop") -> GitBranchType.DEVELOP
            else -> GitBranchType.OTHER
        }
    }
    
    /**
     * Classify tag type based on name and message
     */
    private fun classifyTagType(tagName: String, message: String): GitTagType {
        return when {
            tagName.matches(Regex("v\\d+\\.\\d+\\.\\d+")) -> GitTagType.RELEASE
            tagName.matches(Regex("v\\d+\\.\\d+")) -> GitTagType.MINOR_RELEASE
            tagName.matches(Regex("v\\d+")) -> GitTagType.MAJOR_RELEASE
            tagName.contains("alpha") || tagName.contains("beta") -> GitTagType.PRE_RELEASE
            tagName.contains("rc") -> GitTagType.RELEASE_CANDIDATE
            else -> GitTagType.OTHER
        }
    }
}

// ==== SUPPORTING DATA CLASSES ====

/**
 * Complete git repository analysis
 */
@Serializable
data class GitRepositoryAnalysis(
    val repositoryPath: String,
    val commits: Indexed<GitCommit>,
    val branches: Indexed<GitBranchInfo>,
    val tags: Indexed<GitTagInfo>,
    val authors: Indexed<GitAuthorStats>,
    val analysisTimestamp: Instant
)

/**
 * Git branch information
 */
@Serializable
data class GitBranchInfo(
    val name: GitBranchName,
    val branchType: GitBranchType,
    val lastCommitDate: Instant,
    val creationDate: Instant,
    val isActive: Boolean
)

/**
 * Git tag information
 */
@Serializable
data class GitTagInfo(
    val name: GitTagName,
    val tagType: GitTagType,
    val creationDate: Instant,
    val message: String
)

/**
 * Git author statistics
 */
@Serializable
data class GitAuthorStats(
    val author: GitAuthor,
    val commitCount: Int,
    val firstCommitDate: Instant?,
    val lastCommitDate: Instant?,
    val preferredFileTypes: Indexed<GitFileExtension>,
    val collaborationPatterns: Indexed<GitAuthor>
)

/**
 * Git branch types
 */
enum class GitBranchType {
    MAIN,
    FEATURE,
    HOTFIX,
    BUGFIX,
    RELEASE,
    DEVELOP,
    OTHER
}

/**
 * Git tag types
 */
enum class GitTagType {
    RELEASE,
    MINOR_RELEASE,
    MAJOR_RELEASE,
    PRE_RELEASE,
    RELEASE_CANDIDATE,
    OTHER
} 