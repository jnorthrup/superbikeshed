package k2script.git

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * TrikeShed Taxonomy System for Feature Branch Management
 * 
 * Provides intelligent branch naming and categorization based on TrikeShed patterns
 * with fiduciary attention to semantic meaning and consistency.
 */
class TrikeShedTaxonomy {
    
    private val patterns = mapOf(
        "feature" to listOf("add", "implement", "create", "build", "develop"),
        "fix" to listOf("fix", "repair", "resolve", "correct", "patch"),
        "refactor" to listOf("refactor", "restructure", "reorganize", "cleanup", "optimize"),
        "docs" to listOf("docs", "documentation", "readme", "guide", "tutorial"),
        "test" to listOf("test", "spec", "specification", "coverage", "testing"),
        "perf" to listOf("perf", "performance", "optimize", "speed", "efficiency"),
        "chore" to listOf("chore", "maintenance", "update", "upgrade", "deps"),
        "mcp" to listOf("mcp", "protocol", "server", "client", "integration"),
        "lfs" to listOf("lfs", "large", "file", "storage", "binary"),
        "deploy" to listOf("deploy", "deployment", "docker", "kubernetes", "helm"),
        "security" to listOf("security", "auth", "authentication", "authorization", "vulnerability"),
        "api" to listOf("api", "endpoint", "interface", "contract", "schema")
    )
    
    private val categories = mapOf(
        "core" to listOf("feature", "fix", "refactor"),
        "infrastructure" to listOf("deploy", "lfs", "mcp"),
        "quality" to listOf("test", "docs", "perf"),
        "maintenance" to listOf("chore", "security"),
        "integration" to listOf("api", "mcp")
    )
    
    /**
     * Generate branch name with TrikeShed taxonomy
     */
    fun generateBranchName(featureName: String): String {
        val normalizedName = normalizeFeatureName(featureName)
        val category = detectCategory(normalizedName)
        val pattern = detectPattern(normalizedName)
        
        return buildString {
            append(pattern)
            append("/")
            append(category)
            append("/")
            append(normalizedName)
        }
    }
    
    /**
     * Validate feature name against TrikeShed taxonomy
     */
    fun isValidFeatureName(name: String): Boolean {
        if (name.isEmpty()) return false
        
        // Check basic format
        val basicPattern = Regex("^[a-z0-9-]+$")
        if (!basicPattern.matches(name)) return false
        
        // Check for semantic meaning
        val hasSemanticMeaning = patterns.values.flatten().any { pattern ->
            name.contains(pattern, ignoreCase = true)
        }
        
        // Check for category alignment
        val hasCategoryAlignment = categories.values.flatten().any { category ->
            name.contains(category, ignoreCase = true)
        }
        
        return hasSemanticMeaning || hasCategoryAlignment
    }
    
    /**
     * Detect category from feature name
     */
    fun detectCategory(featureName: String): String {
        val lowerName = featureName.lowercase()
        
        return when {
            lowerName.contains("mcp") || lowerName.contains("protocol") -> "mcp"
            lowerName.contains("lfs") || lowerName.contains("large") -> "lfs"
            lowerName.contains("deploy") || lowerName.contains("docker") || lowerName.contains("k8s") -> "deploy"
            lowerName.contains("test") || lowerName.contains("spec") -> "test"
            lowerName.contains("docs") || lowerName.contains("readme") -> "docs"
            lowerName.contains("perf") || lowerName.contains("optimize") -> "perf"
            lowerName.contains("security") || lowerName.contains("auth") -> "security"
            lowerName.contains("api") || lowerName.contains("endpoint") -> "api"
            lowerName.contains("fix") || lowerName.contains("bug") -> "fix"
            lowerName.contains("refactor") || lowerName.contains("cleanup") -> "refactor"
            lowerName.contains("chore") || lowerName.contains("deps") -> "chore"
            else -> "feature"
        }
    }
    
    /**
     * Detect pattern from feature name
     */
    fun detectPattern(featureName: String): String {
        val lowerName = featureName.lowercase()
        
        return when {
            lowerName.startsWith("add") || lowerName.startsWith("implement") -> "feature"
            lowerName.startsWith("fix") || lowerName.startsWith("repair") -> "fix"
            lowerName.startsWith("refactor") || lowerName.startsWith("restructure") -> "refactor"
            lowerName.startsWith("docs") || lowerName.startsWith("documentation") -> "docs"
            lowerName.startsWith("test") || lowerName.startsWith("spec") -> "test"
            lowerName.startsWith("perf") || lowerName.startsWith("optimize") -> "perf"
            lowerName.startsWith("chore") || lowerName.startsWith("maintenance") -> "chore"
            lowerName.startsWith("mcp") || lowerName.startsWith("protocol") -> "mcp"
            lowerName.startsWith("lfs") || lowerName.startsWith("large") -> "lfs"
            lowerName.startsWith("deploy") || lowerName.startsWith("docker") -> "deploy"
            lowerName.startsWith("security") || lowerName.startsWith("auth") -> "security"
            lowerName.startsWith("api") || lowerName.startsWith("endpoint") -> "api"
            else -> "feature"
        }
    }
    
    /**
     * Normalize feature name for consistent formatting
     */
    private fun normalizeFeatureName(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9-]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
    }
    
    /**
     * Get taxonomy information for feature
     */
    fun getTaxonomyInfo(featureName: String): TaxonomyInfo {
        val normalizedName = normalizeFeatureName(featureName)
        val category = detectCategory(normalizedName)
        val pattern = detectPattern(normalizedName)
        val branchName = generateBranchName(featureName)
        
        return TaxonomyInfo(
            originalName = featureName,
            normalizedName = normalizedName,
            category = category,
            pattern = pattern,
            branchName = branchName,
            semanticTags = extractSemanticTags(normalizedName),
            complexity = estimateComplexity(normalizedName)
        )
    }
    
    /**
     * Extract semantic tags from feature name
     */
    private fun extractSemanticTags(featureName: String): List<String> {
        val tags = mutableListOf<String>()
        val lowerName = featureName.lowercase()
        
        patterns.forEach { (pattern, keywords) ->
            if (keywords.any { lowerName.contains(it) }) {
                tags.add(pattern)
            }
        }
        
        return tags.distinct()
    }
    
    /**
     * Estimate complexity based on feature name
     */
    private fun estimateComplexity(featureName: String): ComplexityLevel {
        val lowerName = featureName.lowercase()
        
        return when {
            lowerName.contains("simple") || lowerName.contains("basic") -> ComplexityLevel.SIMPLE
            lowerName.contains("complex") || lowerName.contains("advanced") -> ComplexityLevel.COMPLEX
            lowerName.contains("refactor") || lowerName.contains("restructure") -> ComplexityLevel.MEDIUM
            lowerName.contains("mcp") || lowerName.contains("protocol") -> ComplexityLevel.COMPLEX
            lowerName.contains("lfs") || lowerName.contains("large") -> ComplexityLevel.MEDIUM
            lowerName.contains("deploy") || lowerName.contains("docker") -> ComplexityLevel.MEDIUM
            else -> ComplexityLevel.SIMPLE
        }
    }
    
    /**
     * Get all available patterns
     */
    fun getAvailablePatterns(): Map<String, List<String>> = patterns
    
    /**
     * Get all available categories
     */
    fun getAvailableCategories(): Map<String, List<String>> = categories
    
    /**
     * Validate taxonomy consistency
     */
    fun validateTaxonomy(): TaxonomyValidation {
        val issues = mutableListOf<String>()
        
        // Check for pattern consistency
        patterns.forEach { (pattern, keywords) ->
            if (keywords.isEmpty()) {
                issues.add("Pattern '$pattern' has no keywords")
            }
        }
        
        // Check for category consistency
        categories.forEach { (category, patterns) ->
            if (patterns.isEmpty()) {
                issues.add("Category '$category' has no patterns")
            }
        }
        
        return TaxonomyValidation(
            isValid = issues.isEmpty(),
            issues = issues
        )
    }
    
    /**
     * Generate branch name suggestions
     */
    fun generateBranchNameSuggestions(featureName: String): List<String> {
        val suggestions = mutableListOf<String>()
        val normalizedName = normalizeFeatureName(featureName)
        
        // Generate variations based on different patterns
        patterns.keys.take(3).forEach { pattern ->
            suggestions.add("$pattern/$normalizedName")
        }
        
        // Generate variations based on categories
        categories.keys.take(3).forEach { category ->
            suggestions.add("feature/$category/$normalizedName")
        }
        
        return suggestions.distinct()
    }
}

@Serializable
data class TaxonomyInfo(
    val originalName: String,
    val normalizedName: String,
    val category: String,
    val pattern: String,
    val branchName: String,
    val semanticTags: List<String>,
    val complexity: ComplexityLevel
)

enum class ComplexityLevel {
    SIMPLE, MEDIUM, COMPLEX
}

@Serializable
data class TaxonomyValidation(
    val isValid: Boolean,
    val issues: List<String>
) 