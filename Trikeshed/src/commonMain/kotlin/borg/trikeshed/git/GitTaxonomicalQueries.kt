package borg.trikeshed.git

import borg.trikeshed.lib.*

/**
 * Git Taxonomical Query Engine - Analyzes git history and extracts insights
 * Uses TrikeShed patterns for efficient querying and analysis
 */

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
    
    /**
     * Get author activity analysis
     */
    fun getAuthorActivity(commits: Indexed<GitCommit>): Indexed<Join<GitAuthor, Map<String, Any>>> {
        val authorStats = mutableMapOf<GitAuthor, MutableMap<String, Any>>()
        
        commits.forEach { commit ->
            val author = commit.author
            val stats = authorStats.getOrPut(author) { mutableMapOf() }
            
            // Count commits
            stats["commit_count"] = (stats["commit_count"] as? Int ?: 0) + 1
            
            // Track entity types
            val entityTypes = stats.getOrPut("entity_types", { mutableMapOf<UByte, Int>() }) as MutableMap<UByte, Int>
            entityTypes[commit.entityType.category] = entityTypes.getOrDefault(commit.entityType.category, 0) + 1
            
            // Track impact levels
            val impactLevels = stats.getOrPut("impact_levels", { mutableMapOf<UByte, Int>() }) as MutableMap<UByte, Int>
            impactLevels[commit.impactLevel.severity] = impactLevels.getOrDefault(commit.impactLevel.severity, 0) + 1
            
            // Track file types
            val fileTypes = stats.getOrPut("file_types", { mutableSetOf<String>() }) as MutableSet<String>
            (0 until commit.changes.a).forEach { i ->
                val change = commit.changes.b(i)
                val filePath = change.b.value
                val extension = filePath.substringAfterLast('.', "").lowercase()
                if (extension.isNotEmpty()) fileTypes.add(extension)
            }
        }
        
        return authorStats.entries.map { (author, stats) ->
            author j stats.toMap()
        }.toIndexed()
    }
    
    /**
     * Get commit complexity analysis
     */
    fun getComplexityAnalysis(commits: Indexed<GitCommit>): Map<String, Any> {
        if (commits.a == 0) return emptyMap()
        
        val fileCounts = (0 until commits.a).map { commits.b(it).changes.a }
        val confidenceScores = (0 until commits.a).map { commits.b(it).confidence.score.toDouble() }
        
        return mapOf(
            "total_commits" to commits.a,
            "avg_files_per_commit" to fileCounts.average(),
            "max_files_per_commit" to fileCounts.maxOrNull() ?: 0,
            "avg_confidence" to confidenceScores.average(),
            "high_confidence_ratio" to confidenceScores.count { it >= 200.0 }.toDouble() / commits.a,
            "complexity_distribution" to mapOf(
                "simple" to fileCounts.count { it <= 3 },
                "moderate" to fileCounts.count { it in 4..10 },
                "complex" to fileCounts.count { it > 10 }
            )
        )
    }
    
    /**
     * Get feature correlation analysis
     */
    fun getFeatureCorrelations(commits: Indexed<GitCommit>): Map<String, Double> {
        if (commits.a < 2) return emptyMap()
        
        val correlations = mutableMapOf<String, Double>()
        
        // Extract feature values
        val messageLengths = mutableListOf<Double>()
        val fileCounts = mutableListOf<Double>()
        val confidenceScores = mutableListOf<Double>()
        
        commits.forEach { commit ->
            // Message length
            messageLengths.add(commit.message.value.length.toDouble())
            
            // File count
            fileCounts.add(commit.changes.a.toDouble())
            
            // Confidence
            confidenceScores.add(commit.confidence.score.toDouble())
        }
        
        // Calculate correlations
        correlations["message_length_file_count"] = calculateCorrelation(messageLengths, fileCounts)
        correlations["message_length_confidence"] = calculateCorrelation(messageLengths, confidenceScores)
        correlations["file_count_confidence"] = calculateCorrelation(fileCounts, confidenceScores)
        
        return correlations
    }
    
    /**
     * Calculate Pearson correlation coefficient
     */
    private fun calculateCorrelation(x: List<Double>, y: List<Double>): Double {
        if (x.size != y.size || x.size < 2) return 0.0
        
        val n = x.size
        val sumX = x.sum()
        val sumY = y.sum()
        val sumXY = x.zip(y).sumOf { it.first * it.second }
        val sumX2 = x.sumOf { it * it }
        val sumY2 = y.sumOf { it * it }
        
        val numerator = n * sumXY - sumX * sumY
        val denominator = kotlin.math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY))
        
        return if (denominator != 0.0) numerator / denominator else 0.0
    }
    
    /**
     * Get commit pattern analysis
     */
    fun getCommitPatterns(commits: Indexed<GitCommit>): Map<String, Any> {
        if (commits.a == 0) return emptyMap()
        
        val patterns = mutableMapOf<String, Any>()
        
        // Time patterns
        val hours = (0 until commits.a).map { i ->
            val timestamp = commits.b(i).timestamp.toEpochMilliseconds()
            java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneOffset.UTC).hour
        }
        
        val hourDistribution = (0..23).associateWith { hour ->
            hours.count { it == hour }
        }
        
        patterns["hour_distribution"] = hourDistribution
        patterns["peak_hour"] = hourDistribution.maxByOrNull { it.value }?.key ?: 0
        
        // Entity type patterns
        val entityTypeCounts = mutableMapOf<UByte, Int>()
        commits.forEach { commit ->
            entityTypeCounts[commit.entityType.category] = 
                entityTypeCounts.getOrDefault(commit.entityType.category, 0) + 1
        }
        patterns["entity_type_distribution"] = entityTypeCounts
        
        // Impact patterns
        val impactCounts = mutableMapOf<UByte, Int>()
        commits.forEach { commit ->
            impactCounts[commit.impactLevel.severity] = 
                impactCounts.getOrDefault(commit.impactLevel.severity, 0) + 1
        }
        patterns["impact_distribution"] = impactCounts
        
        return patterns
    }
} 