#!/usr/bin/env k2script

// Git Feature Branch Example - Demonstrates feature branch management with TrikeShed taxonomy
// This script shows rapid cloning, LFS support, and deployment recipe generation

@file:Import("k2script.git.*")

println("=== Git Feature Branch Management Example ===")
println()

// Initialize Git feature branch manager
val gitManager = GitFeatureBranchManager()
val taxonomy = TrikeShedTaxonomy()

// Test repository detection
println("Repository Detection:")
val isGitRepo = gitManager.isGitRepository(File("."))
println("  Is Git repository: $isGitRepo")
println()

// Test TrikeShed taxonomy
println("TrikeShed Taxonomy Examples:")
val featureNames = listOf(
    "add-mcp-server",
    "fix-bug-123", 
    "refactor-user-authentication",
    "docs-api-reference",
    "test-integration-coverage",
    "perf-optimize-database-queries",
    "chore-update-dependencies",
    "lfs-migrate-large-files",
    "deploy-kubernetes-config",
    "security-fix-vulnerability"
)

featureNames.forEach { featureName ->
    val taxonomyInfo = taxonomy.getTaxonomyInfo(featureName)
    println("  ${taxonomyInfo.originalName}:")
    println("    Category: ${taxonomyInfo.category}")
    println("    Pattern: ${taxonomyInfo.pattern}")
    println("    Branch: ${taxonomyInfo.branchName}")
    println("    Complexity: ${taxonomyInfo.complexity}")
    println("    Tags: ${taxonomyInfo.semanticTags.joinToString(", ")}")
    println()
}

// Test feature name validation
println("Feature Name Validation:")
val testNames = listOf(
    "valid-feature-name",
    "invalid name with spaces",
    "UPPERCASE-NAME",
    "name/with/slashes",
    ""
)

testNames.forEach { name ->
    val isValid = taxonomy.isValidFeatureName(name)
    println("  '$name': ${if (isValid) "VALID" else "INVALID"}")
}
println()

// Test branch name suggestions
println("Branch Name Suggestions:")
val suggestions = taxonomy.generateBranchNameSuggestions("add-mcp-server")
println("  Suggestions for 'add-mcp-server':")
suggestions.forEach { suggestion ->
    println("    - $suggestion")
}
println()

// Test taxonomy validation
println("Taxonomy Validation:")
val validation = taxonomy.validateTaxonomy()
println("  Is valid: ${validation.isValid}")
if (!validation.isValid) {
    println("  Issues:")
    validation.issues.forEach { issue ->
        println("    - $issue")
    }
}
println()

// Test LFS functionality
println("LFS Management:")
val lfsManager = GitLFSManager()

// Check LFS status
val lfsStatus = lfsManager.getLFSStatus(File("."))
println("  LFS configured: ${lfsStatus.isConfigured}")
println("  Has LFS objects: ${lfsStatus.hasObjects}")
println("  Object count: ${lfsStatus.objectCount}")
println("  Total size: ${lfsStatus.totalSize} bytes")

if (lfsStatus.objects.isNotEmpty()) {
    println("  LFS objects:")
    lfsStatus.objects.forEach { obj ->
        println("    - ${obj.path}: ${obj.size} bytes")
    }
}
println()

// Test deployment recipe generation
println("Deployment Recipe Generation:")
val recipeManager = DeploymentRecipeManager()

// Generate recipes for different project types
val projectTypes = listOf(
    ProjectType.KOTLIN_JVM,
    ProjectType.NODE_JS,
    ProjectType.PYTHON,
    ProjectType.GO,
    ProjectType.JAVA
)

projectTypes.forEach { projectType ->
    println("  $projectType recipes:")
    val recipes = recipeManager.generateRecipes(File("."), "test-feature")
    recipes.forEach { recipe ->
        println("    - ${recipe.name} (${recipe.type}): ${recipe.description}")
    }
    println()
}

// Test rapid clone simulation
println("Rapid Clone Simulation:")
println("  This would perform a rapid clone with:")
println("    - LFS support with fiduciary attention")
println("    - Automatic GitHub/GitLab remote setup")
println("    - Deployment recipe generation")
println("    - TrikeShed taxonomy branch naming")
println("    - Feature branch creation in tmp directory")
println()

// Test repository information
println("Repository Information:")
val repoInfo = gitManager.getRepositoryInfo()
println("  Name: ${repoInfo.name}")
println("  Remote URL: ${repoInfo.remoteUrl ?: "None"}")
println("  Default branch: ${repoInfo.defaultBranch}")
println("  Current branch: ${repoInfo.currentBranch}")
println()

// Test repository validation
println("Repository Validation:")
val validationResult = gitManager.validateRepositoryState()
println("  Is valid: ${validationResult.isValid}")
if (validationResult.messages.isNotEmpty()) {
    println("  Messages:")
    validationResult.messages.forEach { message ->
        println("    - $message")
    }
}
println()

// Test branch listing
println("Branch Information:")
val branches = gitManager.listBranches()
println("  Total branches: ${branches.size}")
branches.forEach { branch ->
    val status = if (branch.isCurrent) "CURRENT" else ""
    println("    ${branch.name} $status")
}
println()

// Test feature branches
println("Feature Branches:")
val featureBranches = gitManager.listFeatureBranches()
if (featureBranches.isEmpty()) {
    println("  No feature branches found")
} else {
    featureBranches.forEach { branch ->
        println("    ${branch.name}")
    }
}
println()

// Test commit history
println("Recent Commit History:")
val commits = gitManager.getCommitHistory("main", 5)
commits.forEach { commit ->
    println("  ${commit.hash.take(8)} - ${commit.message} (${commit.author}, ${commit.date})")
}
println()

println("=== Git Feature Branch Example Completed ===")
println()
println("Next steps:")
println("  1. Use 'k2script --git feature create <name>' to create feature branches")
println("  2. Use 'k2script --git feature clone <url> <name> --lfs --recipes' for rapid cloning")
println("  3. Use 'k2script --git feature taxonomy <name>' to analyze branch naming")
println("  4. Use 'k2script --git feature lfs status' to check LFS configuration")
println("  5. Use 'k2script --git feature recipes <name>' to generate deployment recipes")
println()
println("Key Features:")
println("  ✓ TrikeShed taxonomy-based branch naming")
println("  ✓ Rapid cloning with LFS support")
println("  ✓ Automatic GitHub/GitLab remote setup")
println("  ✓ Deployment recipe generation (Docker, Kubernetes, etc.)")
println("  ✓ Fiduciary attention to Git tree and LFS objects")
println("  ✓ Feature branch management in tmp directories") 