package com.v2superbikeshed.nexus.service

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringFactory
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.util.Query
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName

/**
 * Service that provides comprehensive access to IntelliJ's core functionality
 * including PSI, refactoring, search, and code analysis capabilities.
 */
@Service(Service.Level.PROJECT)
class IntelliJAccessService(internal val project: Project) {
    
    companion object {
        internal val LOG = Logger.getInstance(IntelliJAccessService::class.java)
        
        fun getInstance(project: Project): IntelliJAccessService {
            return project.getService(IntelliJAccessService::class.java)
        }
    }
    
    // PSI Access Methods
    
    /**
     * Find a PSI element by its fully qualified name
     */
    fun findPsiElement(fqName: String): PsiElement? {
        return ReadAction.compute<PsiElement?, RuntimeException> {
            val psiManager = PsiManager.getInstance(project)
            val scope = GlobalSearchScope.projectScope(project)
            
            // Try to find as a class first
            JavaPsiFacade.getInstance(project).findClass(fqName, scope)
                ?: findKotlinElement(fqName, scope)
        }
    }
    
    /**
     * Find Kotlin-specific elements (classes, functions, properties)
     */
    internal fun findKotlinElement(fqName: String, scope: GlobalSearchScope): PsiElement? {
        val parts = fqName.split(".")
        if (parts.isEmpty()) return null
        
        // Search in all Kotlin files
        val kotlinFiles = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)
        
        for (virtualFile in kotlinFiles) {
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile
                ?: continue
                
            // Check for matching declarations
            val declarations = psiFile.declarations
            for (declaration in declarations) {
                when (declaration) {
                    is KtClass -> {
                        if (declaration.fqName?.asString() == fqName) {
                            return declaration
                        }
                    }
                    is KtNamedFunction -> {
                        val functionFqName = "${psiFile.packageFqName}.${declaration.name}"
                        if (functionFqName == fqName) {
                            return declaration
                        }
                    }
                    is KtProperty -> {
                        val propertyFqName = "${psiFile.packageFqName}.${declaration.name}"
                        if (propertyFqName == fqName) {
                            return declaration
                        }
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * Get PSI file from virtual file path
     */
    fun getPsiFile(filePath: String): PsiFile? {
        return ReadAction.compute<PsiFile?, RuntimeException> {
            val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .findFileByPath(filePath)
                ?: return@compute null
                
            PsiManager.getInstance(project).findFile(virtualFile)
        }
    }
    
    /**
     * Get element at specific offset in file
     */
    fun getElementAtOffset(filePath: String, offset: Int): PsiElement? {
        return ReadAction.compute<PsiElement?, RuntimeException> {
            val psiFile = getPsiFile(filePath) ?: return@compute null
            psiFile.findElementAt(offset)
        }
    }
    
    // Search and Navigation
    
    /**
     * Find all usages of a symbol
     */
    fun findUsages(element: PsiElement): List<UsageLocation> {
        return ReadAction.compute<List<UsageLocation>, RuntimeException> {
            val usages = mutableListOf<UsageLocation>()
            
            val query: Query<PsiReference> = ReferencesSearch.search(element)
            query.forEach { reference ->
                val psiElement = reference.element
                val containingFile = psiElement.containingFile
                val virtualFile = containingFile.virtualFile
                
                usages.add(UsageLocation(
                    file = virtualFile.path,
                    line = getLineNumber(containingFile, psiElement),
                    column = getColumnNumber(containingFile, psiElement),
                    text = psiElement.text,
                    context = getContextText(psiElement)
                ))
            }
            
            usages
        }
    }
    
    /**
     * Find symbol by name pattern
     */
    fun findSymbolsByPattern(pattern: String, includeNonProjectItems: Boolean = false): List<SymbolInfo> {
        return ReadAction.compute<List<SymbolInfo>, RuntimeException> {
            val symbols = mutableListOf<SymbolInfo>()
            val scope = if (includeNonProjectItems) {
                GlobalSearchScope.allScope(project)
            } else {
                GlobalSearchScope.projectScope(project)
            }
            
            // Search for classes
            val classesByName = JavaPsiFacade.getInstance(project)
                .findClasses(pattern, scope)
            
            classesByName.forEach { psiClass ->
                symbols.add(SymbolInfo(
                    name = psiClass.name ?: "",
                    fqName = psiClass.qualifiedName ?: "",
                    type = "class",
                    file = psiClass.containingFile.virtualFile?.path ?: "",
                    line = getLineNumber(psiClass.containingFile, psiClass)
                ))
            }
            
            // Search in Kotlin files for more symbol types
            val kotlinFiles = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)
            kotlinFiles.forEach { virtualFile ->
                val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile
                    ?: return@forEach
                    
                psiFile.declarations.forEach { declaration ->
                    val name = when (declaration) {
                        is KtNamedDeclaration -> declaration.name
                        else -> null
                    }
                    
                    if (name != null && name.contains(pattern)) {
                        val type = when (declaration) {
                            is KtClass -> "class"
                            is KtNamedFunction -> "function"
                            is KtProperty -> "property"
                            is KtTypeAlias -> "typealias"
                            else -> "unknown"
                        }
                        
                        symbols.add(SymbolInfo(
                            name = name,
                            fqName = declaration.kotlinFqName?.asString() ?: name,
                            type = type,
                            file = virtualFile.path,
                            line = getLineNumber(psiFile, declaration)
                        ))
                    }
                }
            }
            
            symbols
        }
    }
    
    // Refactoring Operations
    
    /**
     * Rename a symbol across the project
     */
    fun renameSymbol(element: PsiElement, newName: String): RefactorResult {
        return WriteAction.compute<RefactorResult, RuntimeException> {
            try {
                val processor = when (element) {
                    is PsiNamedElement -> {
                        RenameProcessor(project, element, newName, false, false)
                    }
                    else -> {
                        return@compute RefactorResult(
                            success = false,
                            error = "Element is not renameable"
                        )
                    }
                }
                
                processor.setPreviewUsages(false)
                processor.run()
                
                RefactorResult(
                    success = true,
                    filesChanged = processor.elements.size
                )
            } catch (e: Exception) {
                LOG.error("Rename failed", e)
                RefactorResult(
                    success = false,
                    error = e.message
                )
            }
        }
    }
    
    /**
     * Move elements to a different package or directory
     */
    fun moveElement(element: PsiElement, targetPackage: String): RefactorResult {
        return WriteAction.compute<RefactorResult, RuntimeException> {
            try {
                val refactoringFactory = RefactoringFactory.getInstance(project)
                
                when (element) {
                    is PsiClass -> {
                        val targetDirectory = findOrCreatePackageDirectory(targetPackage)
                            ?: return@compute RefactorResult(
                                success = false,
                                error = "Could not find or create target package"
                            )
                            
                        val moveRefactoring = refactoringFactory.createMoveClassesOrPackages(
                            arrayOf(element),
                            targetDirectory,
                            false,
                            false,
                            null
                        )
                        
                        moveRefactoring.run()
                        
                        RefactorResult(success = true, filesChanged = 1)
                    }
                    else -> {
                        RefactorResult(
                            success = false,
                            error = "Unsupported element type for move operation"
                        )
                    }
                }
            } catch (e: Exception) {
                LOG.error("Move failed", e)
                RefactorResult(
                    success = false,
                    error = e.message
                )
            }
        }
    }
    
    // Code Analysis
    
    /**
     * Get code inspections for a file or module
     */
    fun getCodeInspections(filePath: String? = null): List<InspectionInfo> {
        return ReadAction.compute<List<InspectionInfo>, RuntimeException> {
            val inspections = mutableListOf<InspectionInfo>()
            
            // This is a simplified version - real implementation would use
            // InspectionManager and run actual inspections
            val psiFile = filePath?.let { getPsiFile(it) }
            
            if (psiFile != null) {
                // Example: Find unresolved references
                psiFile.accept(object : PsiRecursiveElementVisitor() {
                    override fun visitElement(element: PsiElement) {
                        super.visitElement(element)
                        
                        if (element is KtSimpleNameExpression) {
                            val reference = element.reference
                            if (reference != null && reference.resolve() == null) {
                                inspections.add(InspectionInfo(
                                    id = "UnresolvedReference",
                                    severity = "ERROR",
                                    message = "Unresolved reference: ${element.text}",
                                    file = filePath,
                                    line = getLineNumber(psiFile, element)
                                ))
                            }
                        }
                    }
                })
            }
            
            inspections
        }
    }
    
    /**
     * Get compilation errors for the project
     */
    fun getCompilationErrors(): List<CompilationError> {
        // This would integrate with the Kotlin compiler or build system
        // For now, return empty list as this requires deeper integration
        return emptyList()
    }
    
    // Editor Operations
    
    /**
     * Apply text edits to a file
     */
    fun applyEdits(filePath: String, edits: List<TextEdit>): Boolean {
        return WriteAction.compute<Boolean, RuntimeException> {
            try {
                val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                    .findFileByPath(filePath)
                    ?: return@compute false
                    
                val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                    ?: return@compute false
                
                CommandProcessor.getInstance().executeCommand(
                    project,
                    {
                        edits.sortedByDescending { it.startOffset }.forEach { edit ->
                            document.replaceString(edit.startOffset, edit.endOffset, edit.newText)
                        }
                    },
                    "Apply Edits",
                    null
                )
                
                FileDocumentManager.getInstance().saveDocument(document)
                true
            } catch (e: Exception) {
                LOG.error("Failed to apply edits", e)
                false
            }
        }
    }
    
    /**
     * Execute an IntelliJ action by ID
     */
    fun executeAction(actionId: String): Boolean {
        return ApplicationManager.getApplication().invokeAndWait {
            try {
                val action = ActionManager.getInstance().getAction(actionId)
                    ?: return@invokeAndWait false
                
                val dataContext = SimpleDataContext.builder()
                    .add(CommonDataKeys.PROJECT, project)
                    .build()
                    
                val event = AnActionEvent.createFromAction(
                    action,
                    null,
                    "",
                    dataContext
                )
                
                action.actionPerformed(event)
                true
            } catch (e: Exception) {
                LOG.error("Failed to execute action: $actionId", e)
                false
            }
        }
    }
    
    // Helper Methods
    
    internal fun getLineNumber(file: PsiFile, element: PsiElement): Int {
        val document = PsiDocumentManager.getInstance(project).getDocument(file)
            ?: return -1
        val offset = element.textRange.startOffset
        return document.getLineNumber(offset) + 1
    }
    
    internal fun getColumnNumber(file: PsiFile, element: PsiElement): Int {
        val document = PsiDocumentManager.getInstance(project).getDocument(file)
            ?: return -1
        val offset = element.textRange.startOffset
        val lineNumber = document.getLineNumber(offset)
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        return offset - lineStartOffset + 1
    }
    
    internal fun getContextText(element: PsiElement, contextLines: Int = 2): String {
        val parent = PsiTreeUtil.getParentOfType(
            element,
            KtNamedFunction::class.java,
            KtClass::class.java,
            KtProperty::class.java
        )
        return parent?.text?.take(200) ?: element.text
    }
    
    internal fun findOrCreatePackageDirectory(packageName: String): PsiDirectory? {
        val sourceRoots = ProjectRootManager.getInstance(project).contentSourceRoots
        if (sourceRoots.isEmpty()) return null
        
        val sourceRoot = sourceRoots.first()
        var currentDir = PsiManager.getInstance(project).findDirectory(sourceRoot)
        
        packageName.split(".").forEach { part ->
            currentDir = currentDir?.findSubdirectory(part)
                ?: currentDir?.createSubdirectory(part)
        }
        
        return currentDir
    }
}

// Data Classes

@Serializable
data class UsageLocation(
    val file: String,
    val line: Int,
    val column: Int,
    val text: String,
    val context: String
)

@Serializable
data class SymbolInfo(
    val name: String,
    val fqName: String,
    val type: String,
    val file: String,
    val line: Int
)

@Serializable
data class TextEdit(
    val startOffset: Int,
    val endOffset: Int,
    val newText: String
)

@Serializable
data class CompilationError(
    val file: String,
    val line: Int,
    val column: Int,
    val message: String,
    val severity: String
)