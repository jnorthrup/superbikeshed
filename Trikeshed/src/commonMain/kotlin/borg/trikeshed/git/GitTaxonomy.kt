package borg.trikeshed.git

import borg.trikeshed.lib.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Git Taxonomical Feature Knowledge System
 * 
 * Comprehensive classification and feature extraction for git repositories
 * Uses TrikeShed patterns: Indexed<T>, Join<A,B>, taxonomical type aliases
 * Extends existing taxonomy with git-specific domain knowledge
 */

// ==== CORE GIT TAXONOMICAL TYPES ====

@JvmInline
value class GitCommitHash(val value: String)

@JvmInline
value class GitBranchName(val value: String)

@JvmInline
value class GitTagName(val value: String)

@JvmInline
value class GitAuthor(val value: String)

@JvmInline
value class GitCommitter(val value: String)

@JvmInline
value class GitMessage(val value: String)

@JvmInline
value class GitFilePath(val value: String)

@JvmInline
value class GitFileExtension(val value: String)

// ==== GIT ENTITY CLASSIFICATION ====

@JvmInline
value class GitEntityType(val category: UByte) {
    companion object {
        const val COMMIT: UByte = 1u
        const val BRANCH: UByte = 2u
        const val TAG: UByte = 3u
        const val FILE: UByte = 4u
        const val AUTHOR: UByte = 5u
        const val COMMITTER: UByte = 6u
        const val MERGE: UByte = 7u
        const val REBASE: UByte = 8u
        const val CHERRY_PICK: UByte = 9u
        const val REVERT: UByte = 10u
        const val HOTFIX: UByte = 11u
        const val FEATURE: UByte = 12u
        const val BUGFIX: UByte = 13u
        const val DOCUMENTATION: UByte = 14u
        const val REFACTOR: UByte = 15u
        const val TEST: UByte = 16u
        const val DEPENDENCY: UByte = 17u
        const val CONFIGURATION: UByte = 18u
        const val DEPLOYMENT: UByte = 19u
        const val SECURITY: UByte = 20u
        const val PERFORMANCE: UByte = 21u
        const val ACCESSIBILITY: UByte = 22u
        const val INTERNATIONALIZATION: UByte = 23u
        const val UNKNOWN: UByte = 0u
    }
}

@JvmInline
value class GitChangeType(val operation: UByte) {
    companion object {
        const val ADDED: UByte = 1u
        const val MODIFIED: UByte = 2u
        const val DELETED: UByte = 3u
        const val RENAMED: UByte = 4u
        const val COPIED: UByte = 5u
        const val MERGED: UByte = 6u
        const val CONFLICT_RESOLVED: UByte = 7u
        const val UNKNOWN: UByte = 0u
    }
}

@JvmInline
value class GitImpactLevel(val severity: UByte) {
    companion object {
        const val CRITICAL: UByte = 1u
        const val HIGH: UByte = 2u
        const val MEDIUM: UByte = 3u
        const val LOW: UByte = 4u
        const val MINIMAL: UByte = 5u
        const val UNKNOWN: UByte = 0u
    }
}

@JvmInline
value class GitConfidence(val score: UByte) // 0-255 confidence score

// ==== GIT TAXONOMICAL COMPOSITIONS ====

typealias ClassifiedGitEntity = Join<GitEntityType, GitCommitHash>
typealias ImpactfulGitEntity = Join<ClassifiedGitEntity, GitImpactLevel>
typealias ConfidentGitEntity = Join<ImpactfulGitEntity, GitConfidence>
typealias GitEntitySeries = Indexed<ConfidentGitEntity>

typealias GitChange = Join<GitChangeType, GitFilePath>
typealias ImpactfulChange = Join<GitChange, GitImpactLevel>
typealias GitChangeSeries = Indexed<ImpactfulChange>

typealias GitAuthorInfo = Join<GitAuthor, GitCommitter>
typealias TimestampedAuthor = Join<GitAuthorInfo, Instant>
typealias GitAuthorSeries = Indexed<TimestampedAuthor>

typealias GitMessageAnalysis = Join<GitMessage, GitEntityType>
typealias CategorizedMessage = Join<GitMessageAnalysis, GitImpactLevel>
typealias GitMessageSeries = Indexed<CategorizedMessage>

// ==== GIT FEATURE EXTRACTION ====

/**
 * Git commit with comprehensive taxonomical features
 */
@Serializable
data class GitCommit(
    val hash: GitCommitHash,
    val author: GitAuthor,
    val committer: GitCommitter,
    val message: GitMessage,
    val timestamp: Instant,
    val parentHashes: Indexed<GitCommitHash>,
    val treeHash: String,
    val changes: GitChangeSeries,
    val entityType: GitEntityType,
    val impactLevel: GitImpactLevel,
    val confidence: GitConfidence,
    val features: Indexed<GitFeature>
)

/**
 * Extracted git features for ML/analysis
 */
@Serializable
data class GitFeature(
    val name: String,
    val value: Double,
    val category: GitFeatureCategory,
    val confidence: GitConfidence
)

/**
 * Git feature categories for taxonomical organization
 */
enum class GitFeatureCategory {
    // Commit-level features
    COMMIT_SIZE,           // Lines added/removed
    COMMIT_COMPLEXITY,     // Cyclomatic complexity
    COMMIT_COHESION,       // How focused the commit is
    COMMIT_COUPLING,       // How many files affected
    
    // Message features
    MESSAGE_LENGTH,        // Character count
    MESSAGE_READABILITY,   // Flesch-Kincaid score
    MESSAGE_SENTIMENT,     // Positive/negative sentiment
    MESSAGE_TOPIC,         // Extracted topic
    
    // Author features
    AUTHOR_EXPERIENCE,     // Time since first commit
    AUTHOR_ACTIVITY,       // Commits per time period
    AUTHOR_SPECIALIZATION, // File type preferences
    AUTHOR_COLLABORATION,  // Co-author patterns
    
    // File features
    FILE_SIZE_CHANGE,      // Bytes added/removed
    FILE_TYPE,             // Extension-based classification
    FILE_CRITICALITY,      // Importance in system
    FILE_DEPENDENCY,       // How many files depend on it
    
    // Temporal features
    TIME_OF_DAY,           // Hour of commit
    DAY_OF_WEEK,           // Day of week
    SEASONAL_PATTERN,      // Seasonal trends
    RELEASE_CYCLE,         // Distance from release
    
    // Branch features
    BRANCH_TYPE,           // main/feature/hotfix
    BRANCH_AGE,            // Time since creation
    BRANCH_ACTIVITY,       // Commits per day
    BRANCH_STABILITY,      // Merge success rate
    
    // Impact features
    DOWNSTREAM_IMPACT,     // Files that depend on changes
    UPSTREAM_IMPACT,       // Dependencies changed
    TEST_COVERAGE,         // Test files modified
    DOCUMENTATION_IMPACT,  // Docs updated
    
    // Quality features
    CODE_REVIEW_STATUS,    // Reviewed/not reviewed
    CI_STATUS,             // Build success/failure
    TEST_RESULTS,          // Test pass/fail
    SECURITY_SCAN,         // Security issues found
    
    // Social features
    REVIEWER_COUNT,        // Number of reviewers
    REVIEW_TIME,           // Time to review
    DISCUSSION_LENGTH,     // Comments on PR
    APPROVAL_RATE,         // PR approval rate
}

// ==== GIT TAXONOMICAL ANALYSIS ====

/**
 * Git taxonomical analyzer - extracts features and classifies git entities
 */
object GitTaxonomicalAnalyzer {
    
    /**
     * Analyze a git commit and extract taxonomical features
     */
    fun analyzeCommit(
        hash: GitCommitHash,
        author: GitAuthor,
        committer: GitCommitter,
        message: GitMessage,
        timestamp: Instant,
        changes: GitChangeSeries
    ): GitCommit {
        
        val entityType = classifyCommitType(message, changes)
        val impactLevel = assessImpactLevel(changes, message)
        val confidence = calculateConfidence(message, changes)
        val features = extractFeatures(message, changes, timestamp)
        
        return GitCommit(
            hash = hash,
            author = author,
            committer = committer,
            message = message,
            timestamp = timestamp,
            parentHashes = emptyIndex(), // Would extract from git
            treeHash = "", // Would extract from git
            changes = changes,
            entityType = entityType,
            impactLevel = impactLevel,
            confidence = confidence,
            features = features
        )
    }
    
    /**
     * Classify commit type based on message and changes
     */
    private fun classifyCommitType(message: GitMessage, changes: GitChangeSeries): GitEntityType {
        val messageLower = message.value.lowercase()
        
        return when {
            messageLower.contains("feat") || messageLower.contains("feature") -> 
                GitEntityType(GitEntityType.FEATURE)
            messageLower.contains("fix") || messageLower.contains("bug") -> 
                GitEntityType(GitEntityType.BUGFIX)
            messageLower.contains("hotfix") -> 
                GitEntityType(GitEntityType.HOTFIX)
            messageLower.contains("refactor") -> 
                GitEntityType(GitEntityType.REFACTOR)
            messageLower.contains("test") -> 
                GitEntityType(GitEntityType.TEST)
            messageLower.contains("doc") || messageLower.contains("readme") -> 
                GitEntityType(GitEntityType.DOCUMENTATION)
            messageLower.contains("dep") || messageLower.contains("dependency") -> 
                GitEntityType(GitEntityType.DEPENDENCY)
            messageLower.contains("config") || messageLower.contains("settings") -> 
                GitEntityType(GitEntityType.CONFIGURATION)
            messageLower.contains("deploy") || messageLower.contains("release") -> 
                GitEntityType(GitEntityType.DEPLOYMENT)
            messageLower.contains("security") -> 
                GitEntityType(GitEntityType.SECURITY)
            messageLower.contains("perf") || messageLower.contains("performance") -> 
                GitEntityType(GitEntityType.PERFORMANCE)
            messageLower.contains("i18n") || messageLower.contains("international") -> 
                GitEntityType(GitEntityType.INTERNATIONALIZATION)
            messageLower.contains("a11y") || messageLower.contains("accessibility") -> 
                GitEntityType(GitEntityType.ACCESSIBILITY)
            messageLower.contains("merge") -> 
                GitEntityType(GitEntityType.MERGE)
            messageLower.contains("revert") -> 
                GitEntityType(GitEntityType.REVERT)
            else -> GitEntityType(GitEntityType.UNKNOWN)
        }
    }
    
    /**
     * Assess impact level based on changes and message
     */
    private fun assessImpactLevel(changes: GitChangeSeries, message: GitMessage): GitImpactLevel {
        val fileCount = changes.a
        val messageLower = message.value.lowercase()
        
        return when {
            fileCount > 50 || messageLower.contains("breaking") || messageLower.contains("major") -> 
                GitImpactLevel(GitImpactLevel.CRITICAL)
            fileCount > 20 || messageLower.contains("significant") || messageLower.contains("important") -> 
                GitImpactLevel(GitImpactLevel.HIGH)
            fileCount > 5 || messageLower.contains("update") || messageLower.contains("improve") -> 
                GitImpactLevel(GitImpactLevel.MEDIUM)
            fileCount > 1 || messageLower.contains("minor") || messageLower.contains("typo") -> 
                GitImpactLevel(GitImpactLevel.LOW)
            else -> GitImpactLevel(GitImpactLevel.MINIMAL)
        }
    }
    
    /**
     * Calculate confidence in classification
     */
    private fun calculateConfidence(message: GitMessage, changes: GitChangeSeries): GitConfidence {
        var confidence = 128u // Base confidence
        
        // Boost confidence for clear commit messages
        if (message.value.length > 10) confidence += 32u
        if (message.value.contains(":")) confidence += 16u
        if (message.value.matches(Regex("^[A-Z].*"))) confidence += 16u
        
        // Boost confidence for focused changes
        if (changes.a <= 5) confidence += 32u
        if (changes.a == 1) confidence += 16u
        
        return GitConfidence(confidence.coerceAtMost(255u))
    }
    
    /**
     * Extract features from commit data
     */
    private fun extractFeatures(
        message: GitMessage,
        changes: GitChangeSeries,
        timestamp: Instant
    ): Indexed<GitFeature> {
        val features = mutableListOf<GitFeature>()
        
        // Message features
        features.add(GitFeature(
            name = "message_length",
            value = message.value.length.toDouble(),
            category = GitFeatureCategory.MESSAGE_LENGTH,
            confidence = GitConfidence(255u)
        ))
        
        features.add(GitFeature(
            name = "message_readability",
            value = calculateReadability(message.value),
            category = GitFeatureCategory.MESSAGE_READABILITY,
            confidence = GitConfidence(200u)
        ))
        
        // Change features
        features.add(GitFeature(
            name = "file_count",
            value = changes.a.toDouble(),
            category = GitFeatureCategory.COMMIT_SIZE,
            confidence = GitConfidence(255u)
        ))
        
        features.add(GitFeature(
            name = "commit_cohesion",
            value = calculateCohesion(changes),
            category = GitFeatureCategory.COMMIT_COHESION,
            confidence = GitConfidence(180u)
        ))
        
        // Temporal features
        features.add(GitFeature(
            name = "hour_of_day",
            value = timestamp.toEpochMilliseconds().let { 
                java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).hour.toDouble() 
            },
            category = GitFeatureCategory.TIME_OF_DAY,
            confidence = GitConfidence(255u)
        ))
        
        return features.toIndexed()
    }
    
    /**
     * Calculate message readability score
     */
    private fun calculateReadability(message: String): Double {
        val words = message.split("\\s+".toRegex()).size
        val sentences = message.split("[.!?]+".toRegex()).size
        val syllables = message.lowercase().replace(Regex("[^a-z]"), "").length * 0.4
        
        return if (words > 0 && sentences > 0) {
            206.835 - (1.015 * (words.toDouble() / sentences)) - (84.6 * (syllables / words))
        } else 0.0
    }
    
    /**
     * Calculate commit cohesion (how focused the changes are)
     */
    private fun calculateCohesion(changes: GitChangeSeries): Double {
        if (changes.a <= 1) return 1.0
        
        val fileTypes = (0 until changes.a).map { i ->
            val change = changes.b(i)
            val filePath = change.b.value
            filePath.substringAfterLast('.', "").lowercase()
        }.toSet()
        
        return 1.0 - (fileTypes.size.toDouble() / changes.a)
    }
}

// ==== GIT TAXONOMICAL QUERIES ====

/**
 * Git taxonomical query engine for analyzing git history
 */
object GitTaxonomicalQueries {
    
    /**
     * Find commits by entity type
     */
    fun findByEntityType(commits: Indexed<GitCommit>, entityType: GitEntityType): Indexed<GitCommit> {
        return commits.filter { it.entityType.category == entityType.category }.toIndexed()
    }
    
    /**
     * Find commits by impact level
     */
    fun findByImpactLevel(commits: Indexed<GitCommit>, impactLevel: GitImpactLevel): Indexed<GitCommit> {
        return commits.filter { it.impactLevel.severity <= impactLevel.severity }.toIndexed()
    }
    
    /**
     * Find commits by author
     */
    fun findByAuthor(commits: Indexed<GitCommit>, author: GitAuthor): Indexed<GitCommit> {
        return commits.filter { it.author.value == author.value }.toIndexed()
    }
    
    /**
     * Find commits affecting specific file types
     */
    fun findByFileType(commits: Indexed<GitCommit>, extension: GitFileExtension): Indexed<GitCommit> {
        return commits.filter { commit ->
            (0 until commit.changes.a).any { i ->
                val change = commit.changes.b(i)
                val filePath = change.b.value
                filePath.endsWith(".${extension.value}")
            }
        }.toIndexed()
    }
    
    /**
     * Get feature statistics for commits
     */
    fun getFeatureStats(commits: Indexed<GitCommit>, category: GitFeatureCategory): Map<String, Double> {
        val features = mutableListOf<Double>()
        
        commits.forEach { commit ->
            (0 until commit.features.a).forEach { i ->
                val feature = commit.features.b(i)
                if (feature.category == category) {
                    features.add(feature.value)
                }
            }
        }
        
        return if (features.isNotEmpty()) {
            mapOf(
                "count" to features.size.toDouble(),
                "mean" to features.average(),
                "min" to features.minOrNull() ?: 0.0,
                "max" to features.maxOrNull() ?: 0.0,
                "std_dev" to calculateStdDev(features)
            )
        } else {
            emptyMap()
        }
    }
    
    /**
     * Calculate standard deviation
     */
    private fun calculateStdDev(values: List<Double>): Double {
        if (values.size <= 1) return 0.0
        
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance)
    }
    
    /**
     * Find commits with high confidence classifications
     */
    fun findHighConfidence(commits: Indexed<GitCommit>, threshold: UByte = 200u): Indexed<GitCommit> {
        return commits.filter { it.confidence.score >= threshold }.toIndexed()
    }
    
    /**
     * Get commit timeline analysis
     */
    fun getTimelineAnalysis(commits: Indexed<GitCommit>): Map<String, Any> {
        if (commits.a == 0) return emptyMap()
        
        val timestamps = (0 until commits.a).map { i ->
            commits.b(i).timestamp.toEpochMilliseconds()
        }.sorted()
        
        val intervals = timestamps.zipWithNext().map { (a, b) -> b - a }
        
        return mapOf(
            "total_commits" to commits.a,
            "time_span_days" to (timestamps.last() - timestamps.first()) / (1000.0 * 60 * 60 * 24),
            "avg_interval_hours" to intervals.average() / (1000.0 * 60 * 60),
            "commit_frequency" to commits.a.toDouble() / (timestamps.last() - timestamps.first()) * (1000.0 * 60 * 60 * 24)
        )
    }
}

// ==== GIT TAXONOMICAL EXPORT ====

/**
 * Export git taxonomical data for external analysis
 */
object GitTaxonomicalExport {
    
    /**
     * Export commits to JSON format
     */
    fun exportToJson(commits: Indexed<GitCommit>): String {
        // Would implement JSON serialization
        return "{\"commits\": ${commits.a}}"
    }
    
    /**
     * Export feature matrix for ML
     */
    fun exportFeatureMatrix(commits: Indexed<GitCommit>): Indexed<Indexed<Double>> {
        if (commits.a == 0) return emptyIndex()
        
        val featureNames = mutableSetOf<String>()
        
        // Collect all feature names
        commits.forEach { commit ->
            (0 until commit.features.a).forEach { i ->
                featureNames.add(commit.features.b(i).name)
            }
        }
        
        val featureNameList = featureNames.toList()
        
        // Create feature matrix
        return commits.a j { commitIndex ->
            val commit = commits.b(commitIndex)
            featureNameList.size j { featureIndex ->
                val featureName = featureNameList[featureIndex]
                val feature = (0 until commit.features.a).find { i ->
                    commit.features.b(i).name == featureName
                }?.let { commit.features.b(it).value } ?: 0.0
                feature
            }
        }
    }
    
    /**
     * Export taxonomy summary
     */
    fun exportTaxonomySummary(commits: Indexed<GitCommit>): Map<String, Any> {
        val entityTypeCounts = mutableMapOf<UByte, Int>()
        val impactLevelCounts = mutableMapOf<UByte, Int>()
        val authorCounts = mutableMapOf<String, Int>()
        
        commits.forEach { commit ->
            entityTypeCounts[commit.entityType.category] = 
                entityTypeCounts.getOrDefault(commit.entityType.category, 0) + 1
            impactLevelCounts[commit.impactLevel.severity] = 
                impactLevelCounts.getOrDefault(commit.impactLevel.severity, 0) + 1
            authorCounts[commit.author.value] = 
                authorCounts.getOrDefault(commit.author.value, 0) + 1
        }
        
        return mapOf(
            "total_commits" to commits.a,
            "entity_type_distribution" to entityTypeCounts,
            "impact_level_distribution" to impactLevelCounts,
            "author_distribution" to authorCounts,
            "avg_confidence" to (0 until commits.a).map { commits.b(it).confidence.score }.average()
        )
    }
} 