package borg.trikeshed.git

import borg.trikeshed.lib.*
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Basic Git Taxonomical System Tests
 */
class GitTaxonomyBasicTest {
    
    @Test
    fun testGitEntityClassification() {
        val featureMessage = GitMessage("feat: Add new user authentication system")
        val changes = (1 j { GitChangeType(GitChangeType.MODIFIED) j GitFilePath("src/main/kotlin/UserService.kt") }).toIndexed()
        
        val commit = GitTaxonomicalAnalyzer.analyzeCommit(
            GitCommitHash("abc123"),
            GitAuthor("John Doe"),
            GitCommitter("John Doe"),
            featureMessage,
            Instant.fromEpochSeconds(1640995200),
            changes
        )
        
        assertEquals(GitEntityType.FEATURE, commit.entityType.category)
    }
    
    @Test
    fun testImpactLevelAssessment() {
        val largeChanges = (25 j { GitChangeType(GitChangeType.MODIFIED) j GitFilePath("src/main/kotlin/UserService.kt") }).toIndexed()
        val breakingMessage = GitMessage("BREAKING: Major API changes")
        
        val commit = GitTaxonomicalAnalyzer.analyzeCommit(
            GitCommitHash("breaking456"),
            GitAuthor("John Doe"),
            GitCommitter("John Doe"),
            breakingMessage,
            Instant.fromEpochSeconds(1640995200),
            largeChanges
        )
        
        assertEquals(GitImpactLevel.CRITICAL, commit.impactLevel.severity)
    }
} 