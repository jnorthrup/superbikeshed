package borg.trikeshed.git

import borg.trikeshed.lib.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Git Feature Extractor - Extracts taxonomical features from git commits
 * Uses TrikeShed patterns for efficient feature extraction and classification
 */

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