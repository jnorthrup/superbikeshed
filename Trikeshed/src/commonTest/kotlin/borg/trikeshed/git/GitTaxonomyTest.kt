package borg.trikeshed.git

import borg.trikeshed.lib.*
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Git Taxonomical System Tests
 * Demonstrates the comprehensive git taxonomical feature knowledge system
 */
class GitTaxonomyTest {
    
    @Test
    fun testGitEntityClassification() {
        // Test commit type classification
        val featureMessage = GitMessage("feat: Add new user authentication system")
        val bugfixMessage = GitMessage("fix: Resolve login issue with special characters")
        val refactorMessage = GitMessage("refactor: Simplify database connection logic")
        val testMessage = GitMessage("test: Add unit tests for user service")
        val docMessage = GitMessage("docs: Update API documentation")
        
        val changes = (1 j { GitChangeType(GitChangeType.MODIFIED) j GitFilePath("src/main/kotlin/UserService.kt") }).toIndexed()
        
        val featureCommit = GitTaxonomicalAnalyzer.analyzeCommit(
            GitCommitHash("abc123"),
            GitAuthor("John Doe"),
            GitCommitter("John Doe"),
            featureMessage,
            Instant.fromEpochSeconds(1640995200),
            changes
        )
        
        val bugfixCommit = GitTaxonomicalAnalyzer.analyzeCommit(
            GitCommitHash("def456"),
            GitAuthor("Jane Smith"),
            GitCommitter("Jane Smith"),
            bugfixMessage,
            Instant.fromEpochSeconds(1640995200),
            changes
        )
        
        assertEquals(GitEntityType.FEATURE, featureCommit.entityType.category)
        assertEquals(GitEntityType.BUGFIX, bugfixCommit.entityType.category)
    }
    
    @Test
    fun testImpactLevelAssessment() {
        // Test impact level assessment based on file count and message
        val smallChanges = (2 j { GitChangeType(GitChangeType.MODIFIED) j GitFilePath("src/main/kotlin/UserService.kt") }).toIndexed()
        val largeChanges = (25 j { GitChangeType(GitChangeType.MODIFIED) j GitFilePath("src/main/kotlin/UserService.kt") }).toIndexed()
        
        val minorMessage = GitMessage("minor: Fix typo in comment")
        val breakingMessage = GitMessage("BREAKING: Major API changes")
        
        val minorCommit = GitTaxonomicalAnalyzer.analyzeCommit(
            GitCommitHash("minor123"),
            GitAuthor("John Doe"),
            GitCommitter("John Doe"),
            minorMessage,
            Instant.fromEpochSeconds(1640995200),
            smallChanges
        )
        
        val breakingCommit = GitTaxonomicalAnalyzer.analyzeCommit(
            GitCommitHash("breaking456"),
            GitAuthor("John Doe"),
            GitCommitter("John Doe"),
            breakingMessage,
            Instant.fromEpochSeconds(1640995200),
            largeChanges
        )
        
        assertEquals(GitImpactLevel.LOW, minorCommit.impactLevel.severity)
        assertEquals(GitImpactLevel.CRITICAL, breakingCommit.impactLevel.severity)
    }
    
    @Test
    fun testFeatureExtraction() {
        // Test feature extraction from commits
        val message = GitMessage("feat: Implement user authentication with OAuth2 support")
        val changes = (3 j { i ->
            when (i) {
                0 -> GitChangeType(GitChangeType.ADDED) j GitFilePath("src/main/kotlin/auth/OAuth2Service.kt")
                1 -> GitChangeType(GitChangeType.MODIFIED) j GitFilePath("src/main/kotlin/UserService.kt")
                2 -> GitChangeType(GitChangeType.ADDED) j GitFilePath("src/test/kotlin/auth/OAuth2ServiceTest.kt")
                else -> GitChangeType(GitChangeType.MODIFIED) j GitFilePath("src/main/kotlin/UserService.kt")
            }
        }).toIndexed()
        
        val commit = GitTaxonomicalAnalyzer.analyzeCommit(
            GitCommitHash("feature123"),
            GitAuthor("John Doe"),
            GitCommitter("John Doe"),
            message,
            Instant.fromEpochSeconds(1640995200),
            changes
        )
        
        // Verify features were extracted
        assertTrue(commit.features.a > 0)
        
        // Check specific features
        val messageLengthFeature = (0 until commit.features.a).find { i ->
            commit.features.b(i).name == "message_length"
        }
        assertTrue(messageLengthFeature != null)
        assertEquals(message.value.length.toDouble(), commit.features.b(messageLengthFeature!!).value)
        
        val fileCountFeature = (0 until commit.features.a).find { i ->
            commit.features.b(i).name == "file_count"
        }
        assertTrue(fileCountFeature != null)
        assertEquals(3.0, commit.features.b(fileCountFeature!!).value)
    }
    
    @Test
    fun testGitTaxonomicalQueries() {
        // Create test commits
        val commits = (5 j { i ->
            val message = when (i) {
                0 -> "feat: Add user authentication"
                1 -> "fix: Resolve login bug"
                2 -> "refactor: Simplify database layer"
                3 -> "test: Add unit tests"
                4 -> "docs: Update README"
                else -> "chore: Update dependencies"
            }
            
            val entityType = when (i) {
                0 -> GitEntityType(GitEntityType.FEATURE)
                1 -> GitEntityType(GitEntityType.BUGFIX)
                2 -> GitEntityType(GitEntityType.REFACTOR)
                3 -> GitEntityType(GitEntityType.TEST)
                4 -> GitEntityType(GitEntityType.DOCUMENTATION)
                else -> GitEntityType(GitEntityType.UNKNOWN)
            }
            
            val changes = (1 j { GitChangeType(GitChangeType.MODIFIED) j GitFilePath("src/main/kotlin/Service.kt") }).toIndexed()
            
            GitTaxonomicalAnalyzer.analyzeCommit(
                GitCommitHash("commit$i"),
                GitAuthor("Author $i"),
                GitCommitter("Author $i"),
                GitMessage(message),
                Instant.fromEpochSeconds(1640995200 + i * 3600),
                changes
            )
        }).toIndexed()
        
        // Test finding commits by entity type
        val featureCommits = GitTaxonomicalQueries.findByEntityType(commits, GitEntityType(GitEntityType.FEATURE))
        assertEquals(1, featureCommits.a)
        
        val bugfixCommits = GitTaxonomicalQueries.findByEntityType(commits, GitEntityType(GitEntityType.BUGFIX))
        assertEquals(1, bugfixCommits.a)
        
        // Test finding commits by author
        val authorCommits = GitTaxonomicalQueries.findByAuthor(commits, GitAuthor("Author 0"))
        assertEquals(1, authorCommits.a)
        
        // Test finding commits by file type
        val kotlinCommits = GitTaxonomicalQueries.findByFileType(commits, GitFileExtension("kt"))
        assertEquals(5, kotlinCommits.a)
        
        // Test high confidence commits
        val highConfidenceCommits = GitTaxonomicalQueries.findHighConfidence(commits, 200u)
        assertTrue(highConfidenceCommits.a > 0)
    }
    
    @Test
    fun testTimelineAnalysis() {
        // Create commits with different timestamps
        val commits = (3 j { i ->
            val changes = (1 j { GitChangeType(GitChangeType.MODIFIED) j GitFilePath("src/main/kotlin/Service.kt") }).toIndexed()
            
            GitTaxonomicalAnalyzer.analyzeCommit(
                GitCommitHash("commit$i"),
                GitAuthor("Author"),
                GitCommitter("Author"),
                GitMessage("Commit $i"),
                Instant.fromEpochSeconds(1640995200 + i * 86400), // 1 day apart
                changes
            )
        }).toIndexed()
        
        val timeline = GitTaxonomicalQueries.getTimelineAnalysis(commits)
        
        assertEquals(3, timeline["total_commits"])
        assertTrue(timeline["time_span_days"] as Double > 0)
        assertTrue(timeline["avg_interval_hours"] as Double > 0)
    }
    
    @Test
    fun testAuthorActivityAnalysis() {
        // Create commits from different authors
        val commits = (4 j { i ->
            val author = if (i % 2 == 0) "John Doe" else "Jane Smith"
            val changes = (1 j { GitChangeType(GitChangeType.MODIFIED) j GitFilePath("src/main/kotlin/Service.kt") }).toIndexed()
            
            GitTaxonomicalAnalyzer.analyzeCommit(
                GitCommitHash("commit$i"),
                GitAuthor(author),
                GitCommitter(author),
                GitMessage("Commit $i"),
                Instant.fromEpochSeconds(1640995200 + i * 3600),
                changes
            )
        }).toIndexed()
        
        val authorActivity = GitTaxonomicalQueries.getAuthorActivity(commits)
        
        assertEquals(2, authorActivity.a) // Two different authors
        
        // Check that each author has commit count
        (0 until authorActivity.a).forEach { i ->
            val authorInfo = authorActivity.b(i)
            val stats = authorInfo.b
            assertTrue(stats.containsKey("commit_count"))
        }
    }
    
    @Test
    fun testComplexityAnalysis() {
        // Create commits with different complexity levels
        val commits = (3 j { i ->
            val fileCount = when (i) {
                0 -> 1  // Simple
                1 -> 5  // Moderate
                2 -> 15 // Complex
                else -> 1
            }
            
            val changes = (fileCount j { GitChangeType(GitChangeType.MODIFIED) j GitFilePath("src/main/kotlin/Service.kt") }).toIndexed()
            
            GitTaxonomicalAnalyzer.analyzeCommit(
                GitCommitHash("commit$i"),
                GitAuthor("Author"),
                GitCommitter("Author"),
                GitMessage("Commit $i"),
                Instant.fromEpochSeconds(1640995200 + i * 3600),
                changes
            )
        }).toIndexed()
        
        val complexity = GitTaxonomicalQueries.getComplexityAnalysis(commits)
        
        assertEquals(3, complexity["total_commits"])
        assertEquals(7.0, complexity["avg_files_per_commit"]) // (1+5+15)/3
        assertEquals(15, complexity["max_files_per_commit"])
        
        val distribution = complexity["complexity_distribution"] as Map<*, *>
        assertEquals(1, distribution["simple"])
        assertEquals(1, distribution["moderate"])
        assertEquals(1, distribution["complex"])
    }
    
    @Test
    fun testFeatureCorrelations() {
        // Create commits with varying message lengths and file counts
        val commits = (5 j { i ->
            val messageLength = 10 + i * 20
            val fileCount = 1 + i * 2
            
            val message = "A".repeat(messageLength)
            val changes = (fileCount j { GitChangeType(GitChangeType.MODIFIED) j GitFilePath("src/main/kotlin/Service.kt") }).toIndexed()
            
            GitTaxonomicalAnalyzer.analyzeCommit(
                GitCommitHash("commit$i"),
                GitAuthor("Author"),
                GitCommitter("Author"),
                GitMessage(message),
                Instant.fromEpochSeconds(1640995200 + i * 3600),
                changes
            )
        }).toIndexed()
        
        val correlations = GitTaxonomicalQueries.getFeatureCorrelations(commits)
        
        assertTrue(correlations.containsKey("message_length_file_count"))
        assertTrue(correlations.containsKey("message_length_confidence"))
        assertTrue(correlations.containsKey("file_count_confidence"))
        
        // Should have positive correlation between message length and file count
        val messageFileCorrelation = correlations["message_length_file_count"] ?: 0.0
        assertTrue(messageFileCorrelation > 0.5) // Strong positive correlation expected
    }
    
    @Test
    fun testCommitPatterns() {
        // Create commits at different times
        val commits = (3 j { i ->
            val changes = (1 j { GitChangeType(GitChangeType.MODIFIED) j GitFilePath("src/main/kotlin/Service.kt") }).toIndexed()
            
            GitTaxonomicalAnalyzer.analyzeCommit(
                GitCommitHash("commit$i"),
                GitAuthor("Author"),
                GitCommitter("Author"),
                GitMessage("Commit $i"),
                Instant.fromEpochSeconds(1640995200 + i * 3600), // Different hours
                changes
            )
        }).toIndexed()
        
        val patterns = GitTaxonomicalQueries.getCommitPatterns(commits)
        
        assertTrue(patterns.containsKey("hour_distribution"))
        assertTrue(patterns.containsKey("entity_type_distribution"))
        assertTrue(patterns.containsKey("impact_distribution"))
        
        val hourDistribution = patterns["hour_distribution"] as Map<*, *>
        assertTrue(hourDistribution.isNotEmpty())
    }
    
    @Test
    fun testGitTaxonomicalCompositions() {
        // Test the taxonomical composition types
        val entityType = GitEntityType(GitEntityType.FEATURE)
        val commitHash = GitCommitHash("abc123")
        val impactLevel = GitImpactLevel(GitImpactLevel.HIGH)
        val confidence = GitConfidence(200u)
        
        // Test composition chain
        val classifiedEntity = entityType j commitHash
        val impactfulEntity = classifiedEntity j impactLevel
        val confidentEntity = impactfulEntity j confidence
        
        // Test change composition
        val changeType = GitChangeType(GitChangeType.MODIFIED)
        val filePath = GitFilePath("src/main/kotlin/Service.kt")
        val change = changeType j filePath
        val impactfulChange = change j impactLevel
        
        // Test author composition
        val author = GitAuthor("John Doe")
        val committer = GitCommitter("John Doe")
        val authorInfo = author j committer
        val timestamp = Instant.fromEpochSeconds(1640995200)
        val timestampedAuthor = authorInfo j timestamp
        
        // Verify compositions work
        assertTrue(confidentEntity is ConfidentGitEntity)
        assertTrue(impactfulChange is ImpactfulChange)
        assertTrue(timestampedAuthor is TimestampedAuthor)
    }
} 