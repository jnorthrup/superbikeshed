package com.v2superbikeshed.nexus.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.v2superbikeshed.nexus.psi.*
import kotlinx.coroutines.*

/**
 * Action to perform K2-based semantic analysis
 */
class K2AnalysisAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(CommonDataKeys.PROJECT)
        
        // Run analysis in background
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = performK2Analysis(project)
                showAnalysisResult(project, result)
            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "K2 analysis failed: ${ex.message}", "K2 Analysis Error")
            }
        }
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT)
        e.presentation.isEnabledAndVisible = project != null
    }
    
    private suspend fun performK2Analysis(project: Project): String {
        return withContext(Dispatchers.IO) {
            val k2Analyzer = project.getService(K2TrikeShedBridge::class.java)
            
            val result = StringBuilder()
            result.append("K2 Analysis Results\n")
            result.append("=" * 30).append("\n\n")
            
            try {
                // Create analysis session
                result.append("Creating K2 Analysis Session...\n")
                val session = k2Analyzer.createAnalysisSession()
                result.append("✓ Analysis session created\n\n")
                
                // Analyze symbols
                result.append("Analyzing Symbols...\n")
                val symbolResult = k2Analyzer.analyzeSymbols()
                result.append("✓ Symbols analyzed\n")
                result.append("  - Total Symbols: ${symbolResult.symbols.toList().size}\n")
                result.append("  - Total Types: ${symbolResult.types.toList().size}\n")
                result.append("  - Total Modules: ${symbolResult.modules.toList().size}\n\n")
                
                // Perform type inference
                result.append("Performing Type Inference...\n")
                val typeResult = k2Analyzer.performTypeInference()
                result.append("✓ Type inference completed\n")
                result.append("  - Inferred Types: ${typeResult.inferredTypes.toList().size}\n")
                result.append("  - Errors: ${typeResult.errors.toList().size}\n\n")
                
                // Analyze cross-module dependencies
                result.append("Analyzing Cross-Module Dependencies...\n")
                val dependencyResult = k2Analyzer.analyzeCrossModuleDependencies()
                result.append("✓ Dependency analysis completed\n")
                result.append("  - Dependencies: ${dependencyResult.dependencies.toList().size}\n")
                result.append("  - Cycles: ${dependencyResult.cycles.toList().size}\n\n")
                
                // Show some details
                result.append("Sample Symbols:\n")
                symbolResult.symbols.toList().take(5).forEach { symbol ->
                    result.append("  - ${symbol.name} (${symbol.fqName})\n")
                }
                
                result.append("\nSample Types:\n")
                symbolResult.types.toList().take(5).forEach { type ->
                    result.append("  - ${type.name} (nullable: ${type.isNullable})\n")
                }
                
                if (dependencyResult.dependencies.toList().isNotEmpty()) {
                    result.append("\nSample Dependencies:\n")
                    dependencyResult.dependencies.toList().take(3).forEach { dep ->
                        result.append("  - ${dep.from.name} → ${dep.to.name} (${dep.type})\n")
                    }
                }
                
            } catch (ex: Exception) {
                result.append("❌ K2 Analysis Error: ${ex.message}\n")
                result.append("This might be due to:\n")
                result.append("  - K2 analysis APIs not available in this IntelliJ version\n")
                result.append("  - Project not properly configured for K2 analysis\n")
                result.append("  - Missing dependencies\n")
            }
            
            result.toString()
        }
    }
    
    private fun showAnalysisResult(project: Project, result: String) {
        Messages.showInfoMessage(project, result, "K2 Analysis Results")
    }
} 