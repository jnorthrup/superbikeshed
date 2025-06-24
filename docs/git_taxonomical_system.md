# Git Taxonomical Feature Knowledge System

## Overview

The Git Taxonomical Feature Knowledge System is a comprehensive framework for analyzing git repositories using taxonomical classification and feature extraction. It extends the existing TrikeShed taxonomy patterns with git-specific domain knowledge, providing ML-ready features for repository analysis.

## Architecture

### Core Components

1. **GitTaxonomy.kt** - Core taxonomical types and classifications
2. **GitFeatureExtractor.kt** - Feature extraction and analysis
3. **GitTaxonomicalQueries.kt** - Query engine for analysis
4. **GitRepositoryAnalyzer.kt** - Integration with actual git repositories

### Taxonomical Type System

The system uses TrikeShed patterns with git-specific type aliases:

```kotlin
// Core git types
typealias GitCommitHash = String
typealias GitBranchName = String
typealias GitAuthor = String
typealias GitMessage = String

// Taxonomical compositions
typealias ClassifiedGitEntity = Join<GitEntityType, GitCommitHash>
typealias ImpactfulGitEntity = Join<ClassifiedGitEntity, GitImpactLevel>
typealias ConfidentGitEntity = Join<ImpactfulGitEntity, GitConfidence>
typealias GitEntitySeries = Indexed<ConfidentGitEntity>
```

## Git Entity Classification

### Commit Types

The system classifies commits into 24 different types:

- **FEATURE** - New features and enhancements
- **BUGFIX** - Bug fixes and corrections
- **HOTFIX** - Critical fixes for production
- **REFACTOR** - Code refactoring and restructuring
- **TEST** - Test additions and modifications
- **DOCUMENTATION** - Documentation updates
- **DEPENDENCY** - Dependency management
- **CONFIGURATION** - Configuration changes
- **DEPLOYMENT** - Deployment and release changes
- **SECURITY** - Security-related changes
- **PERFORMANCE** - Performance optimizations
- **ACCESSIBILITY** - Accessibility improvements
- **INTERNATIONALIZATION** - i18n and l10n changes
- **MERGE** - Merge commits
- **REVERT** - Reverted changes
- And more...

### Impact Levels

Commits are classified by impact level:

- **CRITICAL** - Breaking changes, major refactors
- **HIGH** - Significant changes, important features
- **MEDIUM** - Moderate changes, updates
- **LOW** - Minor changes, typo fixes
- **MINIMAL** - Trivial changes

### Change Types

File changes are classified as:

- **ADDED** - New files
- **MODIFIED** - Changed files
- **DELETED** - Removed files
- **RENAMED** - Renamed files
- **COPIED** - Copied files
- **MERGED** - Merge conflicts resolved

## Feature Extraction

### Commit-Level Features

- **COMMIT_SIZE** - Number of files and lines changed
- **COMMIT_COMPLEXITY** - Cyclomatic complexity
- **COMMIT_COHESION** - How focused the commit is
- **COMMIT_COUPLING** - How many files affected

### Message Features

- **MESSAGE_LENGTH** - Character count
- **MESSAGE_READABILITY** - Flesch-Kincaid score
- **MESSAGE_SENTIMENT** - Positive/negative sentiment
- **MESSAGE_TOPIC** - Extracted topic

### Author Features

- **AUTHOR_EXPERIENCE** - Time since first commit
- **AUTHOR_ACTIVITY** - Commits per time period
- **AUTHOR_SPECIALIZATION** - File type preferences
- **AUTHOR_COLLABORATION** - Co-author patterns

### File Features

- **FILE_SIZE_CHANGE** - Bytes added/removed
- **FILE_TYPE** - Extension-based classification
- **FILE_CRITICALITY** - Importance in system
- **FILE_DEPENDENCY** - How many files depend on it

### Temporal Features

- **TIME_OF_DAY** - Hour of commit
- **DAY_OF_WEEK** - Day of week
- **SEASONAL_PATTERN** - Seasonal trends
- **RELEASE_CYCLE** - Distance from release

### Branch Features

- **BRANCH_TYPE** - main/feature/hotfix
- **BRANCH_AGE** - Time since creation
- **BRANCH_ACTIVITY** - Commits per day
- **BRANCH_STABILITY** - Merge success rate

### Impact Features

- **DOWNSTREAM_IMPACT** - Files that depend on changes
- **UPSTREAM_IMPACT** - Dependencies changed
- **TEST_COVERAGE** - Test files modified
- **DOCUMENTATION_IMPACT** - Docs updated

### Quality Features

- **CODE_REVIEW_STATUS** - Reviewed/not reviewed
- **CI_STATUS** - Build success/failure
- **TEST_RESULTS** - Test pass/fail
- **SECURITY_SCAN** - Security issues found

### Social Features

- **REVIEWER_COUNT** - Number of reviewers
- **REVIEW_TIME** - Time to review
- **DISCUSSION_LENGTH** - Comments on PR
- **APPROVAL_RATE** - PR approval rate

## Usage Examples

### Basic Commit Analysis

```kotlin
val commit = GitTaxonomicalAnalyzer.analyzeCommit(
    hash = GitCommitHash("abc123"),
    author = GitAuthor("John Doe"),
    committer = GitCommitter("John Doe"),
    message = GitMessage("feat: Add user authentication"),
    timestamp = Instant.fromEpochSeconds(1640995200),
    changes = changesSeries
)

println("Commit type: ${commit.entityType}")
println("Impact level: ${commit.impactLevel}")
println("Confidence: ${commit.confidence}")
```

### Repository Analysis

```kotlin
val analysis = GitRepositoryAnalyzer.analyzeRepository(Paths.get("./my-repo"))
println("Total commits: ${analysis.commits.a}")
println("Total branches: ${analysis.branches.a}")
println("Total authors: ${analysis.authors.a}")
```

### Querying Commits

```kotlin
// Find all feature commits
val featureCommits = GitTaxonomicalQueries.findByEntityType(
    commits, 
    GitEntityType(GitEntityType.FEATURE)
)

// Find high-impact commits
val highImpactCommits = GitTaxonomicalQueries.findByImpactLevel(
    commits, 
    GitImpactLevel(GitImpactLevel.HIGH)
)

// Find commits by author
val authorCommits = GitTaxonomicalQueries.findByAuthor(
    commits, 
    GitAuthor("John Doe")
)
```

### Statistical Analysis

```kotlin
// Get timeline analysis
val timeline = GitTaxonomicalQueries.getTimelineAnalysis(commits)
println("Time span: ${timeline["time_span_days"]} days")
println("Commit frequency: ${timeline["commit_frequency"]} commits/day")

// Get author activity
val authorActivity = GitTaxonomicalQueries.getAuthorActivity(commits)
authorActivity.forEach { authorInfo ->
    val author = authorInfo.a
    val stats = authorInfo.b
    println("${author.value}: ${stats["commit_count"]} commits")
}

// Get complexity analysis
val complexity = GitTaxonomicalQueries.getComplexityAnalysis(commits)
println("Average files per commit: ${complexity["avg_files_per_commit"]}")
```

### Feature Correlation Analysis

```kotlin
val correlations = GitTaxonomicalQueries.getFeatureCorrelations(commits)
println("Message length vs file count correlation: ${correlations["message_length_file_count"]}")
println("Message length vs confidence correlation: ${correlations["message_length_confidence"]}")
```

## Command Line Usage

### Basic Analysis

```bash
# Run taxonomical analysis on current repository
./bin/git_taxonomical_analysis.sh

# Specify output directory
OUTPUT_DIR=./my-analysis ./bin/git_taxonomical_analysis.sh
```

### Output Files

The analysis generates several output files:

- **git_taxonomical_analysis.json** - Complete analysis results
- **git_feature_matrix.json** - ML-ready feature matrix
- **git_taxonomical_stats.json** - Statistical summaries

### Analysis Output

```json
{
  "repository": "my-project",
  "total_commits": 1250,
  "analyzed_commits": 1248,
  "commits": [
    {
      "hash": "abc123",
      "author": "John Doe",
      "message": "feat: Add user authentication",
      "commit_type": "feature",
      "impact_level": "high",
      "file_count": 5,
      "message_length": 32,
      "features": {
        "message_readability": 85.2,
        "commit_cohesion": 0.8,
        "hour_of_day": 14,
        "day_of_week": 3
      }
    }
  ],
  "analysis_timestamp": "2024-01-15T10:30:00Z"
}
```

## Integration with Existing Systems

### TrikeShed Integration

The git taxonomical system integrates seamlessly with existing TrikeShed patterns:

- Uses `Indexed<T>` for efficient collections
- Uses `Join<A,B>` for composition
- Follows taxonomical type alias patterns
- Extends existing taxonomy domain models

### Museum Integration

The system can analyze museum artifacts and provide taxonomical classification:

```kotlin
// Analyze museum code for git patterns
val museumCommits = museumCode.map { artifact ->
    GitTaxonomicalAnalyzer.analyzeCommit(
        hash = GitCommitHash(artifact.id),
        author = GitAuthor(artifact.author),
        message = GitMessage(artifact.description),
        // ... other parameters
    )
}
```

### Nexus Integration

The system provides features for the Nexus agentic system:

```kotlin
// Provide git insights to Nexus agents
val gitInsights = GitTaxonomicalQueries.getCommitPatterns(commits)
nexusAgent.provideContext("git_patterns", gitInsights)
```

## Advanced Features

### Confidence Scoring

Each classification includes a confidence score (0-255):

- **High (200-255)** - Clear patterns, high confidence
- **Medium (128-199)** - Some patterns, moderate confidence  
- **Low (0-127)** - Weak patterns, low confidence

### Readability Analysis

Message readability is calculated using Flesch-Kincaid:

- **90-100** - Very easy to read
- **80-89** - Easy to read
- **70-79** - Fairly easy to read
- **60-69** - Standard
- **50-59** - Fairly difficult
- **30-49** - Difficult
- **0-29** - Very difficult

### Cohesion Analysis

Commit cohesion measures how focused changes are:

- **1.0** - All changes to same file type
- **0.8** - Most changes to same file type
- **0.5** - Mixed file types
- **0.2** - Very diverse file types

## Future Enhancements

### Planned Features

1. **LLM Integration** - Use LLMs for better message classification
2. **Dependency Analysis** - Track file dependencies and impact
3. **Code Quality Metrics** - Integrate with code quality tools
4. **Team Dynamics** - Analyze collaboration patterns
5. **Predictive Analytics** - Predict commit success/failure

### Extensibility

The system is designed for easy extension:

```kotlin
// Add custom commit types
enum class CustomCommitType {
    SECURITY_PATCH,
    PERFORMANCE_OPTIMIZATION,
    ACCESSIBILITY_IMPROVEMENT
}

// Add custom features
enum class CustomFeatureCategory {
    SECURITY_SCORE,
    PERFORMANCE_IMPACT,
    ACCESSIBILITY_SCORE
}
```

## Conclusion

The Git Taxonomical Feature Knowledge System provides a comprehensive framework for analyzing git repositories with ML-ready features. It extends TrikeShed patterns with git-specific domain knowledge, enabling sophisticated repository analysis and insights.

The system is designed to be:
- **Comprehensive** - Covers all aspects of git analysis
- **Extensible** - Easy to add new features and classifications
- **Efficient** - Uses TrikeShed patterns for performance
- **ML-Ready** - Provides structured features for machine learning
- **Integrable** - Works with existing TrikeShed systems 