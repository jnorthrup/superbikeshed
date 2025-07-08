package com.v2superbikeshed.nexus.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.v2superbikeshed.nexus.psi.*
import kotlinx.coroutines.*
import org.jetbrains.kotlin.psi.KtFile

/**
 * Action to analyze the current file using PSI
 */
class AnalyzeCurrentFileAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(CommonDataKeys.PROJECT)
        val editor = e.getData(CommonDataKeys.EDITOR)
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        
        if (virtualFile == null) {
            Messages.showErrorDialog(project, "No file selected", "Analysis Error")
            return
        }
        
        // Run analysis in background
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = performAnalysis(project, virtualFile.path)
                showAnalysisResult(project, result)
            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Analysis failed: ${ex.message}", "Analysis Error")
            }
        }
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT)
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        
        e.presentation.isEnabledAndVisible = project != null && virtualFile != null
    }
    
    private suspend fun performAnalysis(project: Project, filePath: String): String {
        return withContext(Dispatchers.IO) {
            val psiManager = com.intellij.psi.PsiManager.getInstance(project)
            val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .findFileByPath(filePath)
                ?: throw IllegalArgumentException("File not found: $filePath")
            
            val psiFile = psiManager.findFile(virtualFile)
                ?: throw IllegalArgumentException("PSI file not found: $filePath")
            
            val psiAdapter = project.getService(PsiTrikeShedBridge::class.java)
            val kotlinAnalyzer = project.getService(KotlinAnalysisBridge::class.java)
            
            val result = StringBuilder()
            result.append("PSI Analysis Results for: ${virtualFile.name}\n")
            result.append("=" * 50).append("\n\n")
            
            // Basic PSI analysis
            val allElements = psiAdapter.getAllElements(psiFile)
            result.append("Total PSI Elements: ${allElements.toList().size}\n")
            
            // Kotlin-specific analysis if it's a Kotlin file
            if (psiFile is KtFile) {
                result.append("\nKotlin Analysis:\n")
                result.append("-" * 20).append("\n")
                
                val kotlinResult = kotlinAnalyzer.analyzeFile(psiFile)
                result.append(kotlinResult.structure)
                
                result.append("\nDeclarations: ${kotlinResult.declarations.size}\n")
                result.append("Types: ${kotlinResult.types.size}\n")
                result.append("References: ${kotlinResult.references.size}\n")
                
                // Show some details
                kotlinResult.declarations.take(5).forEach { decl ->
                    result.append("  - ${decl.name ?: "unnamed"} (${decl.element.javaClass.simpleName})\n")
                }
            }
            
            result.toString()
        }
    }
    
    private fun showAnalysisResult(project: Project, result: String) {
        Messages.showInfoMessage(project, result, "PSI Analysis Results")
    }
} 