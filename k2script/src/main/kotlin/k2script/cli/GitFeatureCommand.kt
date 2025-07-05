package k2script.cli

import k2script.git.*
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Git Feature Branch Management CLI Command
 * 
 * Usage:
 *   k2script --git feature create <feature-name>
 *   k2script --git feature clone <url> <feature-name>
 *   k2script --git feature list
 *   k2script --git feature status <feature-name>
 *   k2script --git feature push <feature-name>
 *   k2script --git feature delete <feature-name>
 *   k2script --git feature recipes <feature-name>
 */
object GitFeatureCommand {
    
    private val gitManager = GitFeatureBranchManager()
    private val taxonomy = TrikeShedTaxonomy()
    
    fun execute(args: Array<String>) {
        if (args.isEmpty()) {
            showHelp()
            return
        }
        
        val command = args[0]
        
        runBlocking {
            when (command) {
                "create" -> handleCreate(args.drop(1).toTypedArray())
                "clone" -> handleClone(args.drop(1).toTypedArray())
                "list" -> handleList()
                "status" -> handleStatus(args.drop(1).toTypedArray())
                "push" -> handlePush(args.drop(1).toTypedArray())
                "delete" -> handleDelete(args.drop(1).toTypedArray())
                "recipes" -> handleRecipes(args.drop(1).toTypedArray())
                "taxonomy" -> handleTaxonomy(args.drop(1).toTypedArray())
                "lfs" -> handleLFS(args.drop(1).toTypedArray())
                "help" -> showHelp()
                else -> {
                    println("Unknown command: $command")
                    showHelp()
                }
            }
        }
    }
    
    private suspend fun handleCreate(args: Array<String>) {
        if (args.isEmpty()) {
            println("Error: Feature name required")
            return
        }
        
        val featureName = args[0]
        
        try {
            // Validate feature name with taxonomy
            if (!taxonomy.isValidFeatureName(featureName)) {
                println("Error: Invalid feature name '$featureName'")
                println("Feature names should follow TrikeShed taxonomy patterns")
                return
            }
            
            // Get taxonomy info
            val taxonomyInfo = taxonomy.getTaxonomyInfo(featureName)
            println("Creating feature branch with TrikeShed taxonomy:")
            println("  Original: ${taxonomyInfo.originalName}")
            println("  Normalized: ${taxonomyInfo.normalizedName}")
            println("  Category: ${taxonomyInfo.category}")
            println("  Pattern: ${taxonomyInfo.pattern}")
            println("  Branch: ${taxonomyInfo.branchName}")
            println("  Complexity: ${taxonomyInfo.complexity}")
            println("  Tags: ${taxonomyInfo.semanticTags.joinToString(", ")}")
            
            // Create feature branch
            val branchName = gitManager.createFeatureBranch(featureName)
            println("Created feature branch: $branchName")
            
        } catch (e: Exception) {
            println("Error creating feature branch: ${e.message}")
        }
    }
    
    private suspend fun handleClone(args: Array<String>) {
        if (args.size < 2) {
            println("Error: Usage: clone <url> <feature-name> [options]")
            return
        }
        
        val sourceUrl = args[0]
        val featureName = args[1]
        val setupRemotes = args.contains("--remotes")
        val includeLFS = args.contains("--lfs")
        val generateRecipes = args.contains("--recipes")
        
        try {
            println("Rapid cloning with fiduciary attention...")
            println("  Source: $sourceUrl")
            println("  Feature: $featureName")
            println("  Setup remotes: $setupRemotes")
            println("  Include LFS: $includeLFS")
            println("  Generate recipes: $generateRecipes")
            
            val result = gitManager.rapidClone(
                sourceUrl = sourceUrl,
                featureName = featureName,
                setupRemotes = setupRemotes,
                includeLFS = includeLFS,
                generateRecipes = generateRecipes
            )
            
            if (result.success) {
                println("Rapid clone completed successfully!")
                println("  Clone directory: ${result.cloneDir}")
                println("  Branch name: ${result.branchName}")
                println("  Remotes: ${result.remotes.size}")
                println("  Recipes: ${result.recipes.size}")
                println("  LFS objects: ${result.lfsObjects.size}")
                
                // Show remotes
                result.remotes.forEach { remote ->
                    println("    ${remote.name}: ${remote.url} (${remote.platform})")
                }
                
                // Show recipes
                result.recipes.forEach { recipe ->
                    println("    ${recipe.name}: ${recipe.description}")
                }
                
                // Show LFS objects
                result.lfsObjects.forEach { obj ->
                    println("    ${obj.path}: ${obj.size} bytes")
                }
            } else {
                println("Rapid clone failed: ${result.error}")
            }
            
        } catch (e: Exception) {
            println("Error during rapid clone: ${e.message}")
        }
    }
    
    private fun handleList() {
        try {
            val branches = gitManager.listFeatureBranches()
            
            if (branches.isEmpty()) {
                println("No feature branches found")
                return
            }
            
            println("Feature Branches:")
            println("==================")
            
            branches.forEach { branch ->
                val status = if (branch.isCurrent) "CURRENT" else ""
                val lastCommit = branch.lastCommit?.message?.take(50) ?: "No commits"
                println("${branch.name} $status")
                println("  Last commit: $lastCommit")
                println()
            }
            
        } catch (e: Exception) {
            println("Error listing branches: ${e.message}")
        }
    }
    
    private suspend fun handleStatus(args: Array<String>) {
        if (args.isEmpty()) {
            println("Error: Feature name required")
            return
        }
        
        val featureName = args[0]
        
        try {
            val status = gitManager.getFeatureBranchStatus(featureName)
            
            println("Feature Branch Status:")
            println("======================")
            println("Branch: ${status.branchName}")
            println("Last commit: ${status.lastCommit.message}")
            println("Ahead: ${status.aheadCount} commits")
            println("Behind: ${status.behindCount} commits")
            println("Uncommitted changes: ${status.hasUncommittedChanges}")
            
        } catch (e: Exception) {
            println("Error getting status: ${e.message}")
        }
    }
    
    private suspend fun handlePush(args: Array<String>) {
        if (args.isEmpty()) {
            println("Error: Feature name required")
            return
        }
        
        val featureName = args[0]
        
        try {
            val branchName = "feature/$featureName"
            println("Pushing feature branch: $branchName")
            
            val success = gitManager.pushBranch(branchName)
            
            if (success) {
                println("Feature branch pushed successfully")
            } else {
                println("Failed to push feature branch")
            }
            
        } catch (e: Exception) {
            println("Error pushing branch: ${e.message}")
        }
    }
    
    private suspend fun handleDelete(args: Array<String>) {
        if (args.isEmpty()) {
            println("Error: Feature name required")
            return
        }
        
        val featureName = args[0]
        
        try {
            println("Deleting feature branch: $featureName")
            
            val success = gitManager.deleteFeatureBranch(featureName)
            
            if (success) {
                println("Feature branch deleted successfully")
            } else {
                println("Failed to delete feature branch")
            }
            
        } catch (e: Exception) {
            println("Error deleting branch: ${e.message}")
        }
    }
    
    private suspend fun handleRecipes(args: Array<String>) {
        if (args.isEmpty()) {
            println("Error: Feature name required")
            return
        }
        
        val featureName = args[0]
        
        try {
            val recipes = gitManager.generateRecipes(File("."), featureName)
            
            if (recipes.isEmpty()) {
                println("No deployment recipes generated")
                return
            }
            
            println("Deployment Recipes for '$featureName':")
            println("=====================================")
            
            recipes.forEach { recipe ->
                println("${recipe.name} (${recipe.type}):")
                println("  Description: ${recipe.description}")
                println("  Content:")
                println(recipe.content.prependIndent("    "))
                println()
            }
            
        } catch (e: Exception) {
            println("Error generating recipes: ${e.message}")
        }
    }
    
    private fun handleTaxonomy(args: Array<String>) {
        if (args.isEmpty()) {
            println("Error: Feature name required")
            return
        }
        
        val featureName = args[0]
        
        try {
            val taxonomyInfo = taxonomy.getTaxonomyInfo(featureName)
            
            println("TrikeShed Taxonomy Analysis:")
            println("============================")
            println("Original name: ${taxonomyInfo.originalName}")
            println("Normalized name: ${taxonomyInfo.normalizedName}")
            println("Category: ${taxonomyInfo.category}")
            println("Pattern: ${taxonomyInfo.pattern}")
            println("Branch name: ${taxonomyInfo.branchName}")
            println("Complexity: ${taxonomyInfo.complexity}")
            println("Semantic tags: ${taxonomyInfo.semanticTags.joinToString(", ")}")
            
            // Show suggestions
            val suggestions = taxonomy.generateBranchNameSuggestions(featureName)
            println("Branch name suggestions:")
            suggestions.forEach { suggestion ->
                println("  - $suggestion")
            }
            
        } catch (e: Exception) {
            println("Error analyzing taxonomy: ${e.message}")
        }
    }
    
    private suspend fun handleLFS(args: Array<String>) {
        if (args.isEmpty()) {
            println("Error: LFS command required")
            return
        }
        
        val lfsCommand = args[0]
        
        try {
            when (lfsCommand) {
                "status" -> {
                    val status = gitManager.getLFSStatus(File("."))
                    println("LFS Status:")
                    println("===========")
                    println("Configured: ${status.isConfigured}")
                    println("Has objects: ${status.hasObjects}")
                    println("Object count: ${status.objectCount}")
                    println("Total size: ${status.totalSize} bytes")
                    
                    if (status.objects.isNotEmpty()) {
                        println("Objects:")
                        status.objects.forEach { obj ->
                            println("  ${obj.path}: ${obj.size} bytes (${obj.oid})")
                        }
                    }
                }
                "setup" -> {
                    println("Setting up LFS tracking...")
                    gitManager.setupLFSTracking(File("."))
                    println("LFS tracking setup complete")
                }
                "pull" -> {
                    println("Pulling LFS objects...")
                    gitManager.pullLFSObjects(File("."))
                    println("LFS objects pulled")
                }
                "push" -> {
                    println("Pushing LFS objects...")
                    gitManager.pushLFSObjects(File("."))
                    println("LFS objects pushed")
                }
                else -> {
                    println("Unknown LFS command: $lfsCommand")
                    println("Available commands: status, setup, pull, push")
                }
            }
            
        } catch (e: Exception) {
            println("Error with LFS operation: ${e.message}")
        }
    }
    
    private fun showHelp() {
        println("""
            Git Feature Branch Management Commands:
            =======================================
            
            create <feature-name>              Create feature branch with TrikeShed taxonomy
            clone <url> <feature-name>         Rapid clone with LFS and deployment recipes
            list                               List all feature branches
            status <feature-name>              Show feature branch status
            push <feature-name>                Push feature branch with LFS
            delete <feature-name>              Delete feature branch
            recipes <feature-name>             Generate deployment recipes
            taxonomy <feature-name>            Analyze TrikeShed taxonomy
            lfs <command>                      LFS operations (status, setup, pull, push)
            help                               Show this help
            
            Clone Options:
              --remotes                        Setup GitHub/GitLab remotes
              --lfs                           Include LFS support
              --recipes                       Generate deployment recipes
            
            Examples:
              k2script --git feature create add-mcp-server
              k2script --git feature clone https://github.com/user/repo.git my-feature --lfs --recipes
              k2script --git feature taxonomy add-mcp-server
              k2script --git feature lfs status
        """.trimIndent())
    }
} 