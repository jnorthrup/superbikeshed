package com.v2superbikeshed.nexus.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.v2superbikeshed.nexus.psi.*
import kotlinx.coroutines.*
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile

/**
 * Action to analyze the entire project using PSI
 */
class AnalyzeProjectAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(CommonDataKeys.PROJECT)
        
        // Run analysis in background
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = performProjectAnalysis(project)
                showAnalysisResult(project, result)
            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Project analysis failed: ${ex.message}", "Analysis Error")
            }
        }
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT)
        e.presentation.isEnabledAndVisible = project != null
    }
    
    private suspend fun performProjectAnalysis(project: Project): String {
        return withContext(Dispatchers.IO) {
            val psiAdapter = project.getService(PsiTrikeShedBridge::class.java)
            val kotlinAnalyzer = project.getService(KotlinAnalysisBridge::class.java)
            val k2Analyzer = project.getService(K2TrikeShedBridge::class.java)
            
            val result = StringBuilder()
            result.append("Project PSI Analysis Results\n")
            result.append("=" * 40).append("\n\n")
            
            // Get all Kotlin files in the project
            val scope = GlobalSearchScope.projectScope(project)
            val kotlinFiles = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)
            
            result.append("Total Kotlin Files: ${kotlinFiles.size}\n\n")
            
            var totalDeclarations = 0
            var totalTypes = 0
            var totalReferences = 0
            
            // Analyze each Kotlin file
            kotlinFiles.take(10).forEach { virtualFile -> // Limit to first 10 files for performance
                val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile
                    ?: return@forEach
                
                try {
                    val kotlinResult = kotlinAnalyzer.analyzeFile(psiFile)
                    
                    result.append("File: ${virtualFile.name}\n")
                    result.append("  Declarations: ${kotlinResult.declarations.size}\n")
                    result.append("  Types: ${kotlinResult.types.size}\n")
                    result.append("  References: ${kotlinResult.references.size}\n")
                    
                    totalDeclarations += kotlinResult.declarations.size
                    totalTypes += kotlinResult.types.size
                    totalReferences += kotlinResult.references.size
                    
                } catch (ex: Exception) {
                    result.append("File: ${virtualFile.name} - Error: ${ex.message}\n")
                }
            }
            
            result.append("\nSummary:\n")
            result.append("-" * 20).append("\n")
            result.append("Total Declarations: $totalDeclarations\n")
            result.append("Total Types: $totalTypes\n")
            result.append("Total References: $totalReferences\n")
            
            // Try K2 analysis if available
            try {
                result.append("\nK2 Analysis:\n")
                result.append("-" * 20).append("\n")
                
                val k2Result = k2Analyzer.analyzeSymbols()
                result.append("K2 Symbols: ${k2Result.symbols.toList().size}\n")
                result.append("K2 Types: ${k2Result.types.toList().size}\n")
                result.append("K2 Modules: ${k2Result.modules.toList().size}\n")
                
            } catch (ex: Exception) {
                result.append("K2 Analysis not available: ${ex.message}\n")
            }
            
            result.toString()
        }
    }
    
    private fun showAnalysisResult(project: Project, result: String) {
        Messages.showInfoMessage(project, result, "Project PSI Analysis Results")
    }
} 