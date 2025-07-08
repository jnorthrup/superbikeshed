package com.v2superbikeshed.nexus.psi

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.*
import kotlin.reflect.KClass

/**
 * Concrete implementation of NexusPsiAdapter that bridges PSI to TrikeShed
 */
class PsiTrikeShedBridge(private val project: Project) : NexusPsiAdapter {
    
    override fun PsiElement.toSeries(): PsiElementSeries = 
        Series.singleton(PsiElementRef(this))
        
    override fun PsiFile.getAllElements(): PsiElementSeries =
        ReadAction.compute<PsiElementSeries, RuntimeException> {
            val elements = PsiTreeUtil.collectElements(this) { true }
            Series.from(elements.map { PsiElementRef(it) })
        }
        
    override fun PsiElement.getChildren(): PsiElementSeries =
        Series.from(children.map { PsiElementRef(it) })
        
    override fun PsiElement.findUsages(): PsiElementSeries =
        ReadAction.compute<PsiElementSeries, RuntimeException> {
            val usages = ReferencesSearch.search(this, GlobalSearchScope.projectScope(project))
            Series.from(usages.map { PsiElementRef(it.element) })
        }
        
    override suspend fun findElementsByType(type: KClass<out PsiElement>): PsiElementSeries =
        ReadAction.compute<PsiElementSeries, RuntimeException> {
            val scope = GlobalSearchScope.projectScope(project)
            val elements = mutableListOf<PsiElement>()
            
            // Search through all files in the project
            val psiManager = PsiManager.getInstance(project)
            val virtualFileManager = com.intellij.openapi.vfs.VirtualFileManager.getInstance()
            
            // This is a simplified implementation - in practice you'd want to cache this
            // and use more efficient search mechanisms
            project.baseDir.walkTopDown()
                .filter { it.isFile }
                .forEach { virtualFile ->
                    val psiFile = psiManager.findFile(virtualFile) ?: return@forEach
                    val fileElements = PsiTreeUtil.collectElements(psiFile) { true }
                    elements.addAll(fileElements.filter { it::class == type })
                }
                
            Series.from(elements.map { PsiElementRef(it) })
        }
        
    override suspend fun resolveReferences(): Join<PsiElementSeries, PsiElementSeries> =
        object : Join<PsiElementSeries, PsiElementSeries> {
            override fun <C> map(transform: (PsiElementSeries, PsiElementSeries) -> C): Series<C> {
                // This would need a proper implementation with actual TrikeShed Join
                // For now, return empty series
                return Series.from(emptyList())
            }
            
            override fun filter(predicate: (PsiElementSeries, PsiElementSeries) -> Boolean): Join<PsiElementSeries, PsiElementSeries> {
                return this
            }
        }
        
    override suspend fun analyzeDataFlow(): Join<PsiElementSeries, DataFlowInfo> =
        object : Join<PsiElementSeries, DataFlowInfo> {
            override fun <C> map(transform: (PsiElementSeries, DataFlowInfo) -> C): Series<C> {
                // Simplified data flow analysis
                return Series.from(emptyList())
            }
            
            override fun filter(predicate: (PsiElementSeries, DataFlowInfo) -> Boolean): Join<PsiElementSeries, DataFlowInfo> {
                return this
            }
        }
        
    override fun KtElement.toKtSeries(): KtElementSeries = 
        Series.singleton(KtElementRef(this))
        
    override fun KtFile.getAllKtElements(): KtElementSeries =
        ReadAction.compute<KtElementSeries, RuntimeException> {
            val elements = PsiTreeUtil.collectElements(this) { it is KtElement }
            Series.from(elements.map { KtElementRef(it as KtElement) })
        }
        
    override suspend fun analyzeKotlinFile(file: KtFile): KotlinAnalysisResult =
        ReadAction.compute<KotlinAnalysisResult, RuntimeException> {
            val declarations = mutableListOf<KtElementRef>()
            val types = mutableListOf<KtTypeInfo>()
            val references = mutableListOf<KtReferenceInfo>()
            
            // Collect all declarations
            file.declarations.forEach { declaration ->
                declarations.add(KtElementRef(declaration))
                
                // Analyze types for functions and properties
                when (declaration) {
                    is KtFunction -> {
                        val returnType = declaration.typeReference?.text ?: "Unit"
                        types.add(KtTypeInfo(
                            element = KtElementRef(declaration),
                            type = returnType,
                            isNullable = returnType.endsWith("?")
                        ))
                    }
                    is KtProperty -> {
                        val propertyType = declaration.typeReference?.text ?: "Any"
                        types.add(KtTypeInfo(
                            element = KtElementRef(declaration),
                            type = propertyType,
                            isNullable = propertyType.endsWith("?")
                        ))
                    }
                }
            }
            
            // Collect references
            file.accept(object : KtVisitorVoid() {
                override fun visitKtElement(element: KtElement) {
                    super.visitKtElement(element)
                    
                    if (element is KtReferenceExpression) {
                        val referencedElement = element.resolve()
                        references.add(KtReferenceInfo(
                            element = KtElementRef(element),
                            referencedElement = referencedElement?.let { KtElementRef(it as KtElement) },
                            referenceType = element.javaClass.simpleName
                        ))
                    }
                }
            })
            
            KotlinAnalysisResult(
                declarations = declarations,
                types = types,
                references = references,
                structure = generateStructureString(file)
            )
        }
        
    private fun generateStructureString(file: KtFile): String {
        val structure = StringBuilder()
        structure.append("File: ${file.name}\n")
        structure.append("Package: ${file.packageFqName}\n")
        structure.append("Declarations: ${file.declarations.size}\n")
        
        file.declarations.forEach { declaration ->
            structure.append("  ${declaration.javaClass.simpleName}: ${declaration.name ?: "unnamed"}\n")
        }
        
        return structure.toString()
    }
} 