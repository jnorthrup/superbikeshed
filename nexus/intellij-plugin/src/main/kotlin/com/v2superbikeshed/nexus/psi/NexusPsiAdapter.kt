package com.v2superbikeshed.nexus.psi

import com.intellij.psi.*
import org.jetbrains.kotlin.psi.*
import kotlin.reflect.KClass

/**
 * Bridge interface between IntelliJ PSI and TrikeShed Series operations
 * 
 * This adapter provides a functional programming interface to PSI elements
 * using TrikeShed's Series<T> and Join<A,B> patterns.
 */
interface NexusPsiAdapter {
    
    // Core PSI to Series conversions
    fun PsiElement.toSeries(): PsiElementSeries
    fun PsiFile.getAllElements(): PsiElementSeries
    fun PsiElement.getChildren(): PsiElementSeries
    fun PsiElement.findUsages(): PsiElementSeries
    
    // Semantic queries using TrikeShed patterns
    suspend fun findElementsByType(type: KClass<out PsiElement>): PsiElementSeries
    suspend fun resolveReferences(): Join<PsiElementSeries, PsiElementSeries>
    suspend fun analyzeDataFlow(): Join<PsiElementSeries, DataFlowInfo>
    
    // Kotlin-specific operations
    fun KtElement.toKtSeries(): KtElementSeries
    fun KtFile.getAllKtElements(): KtElementSeries
    suspend fun analyzeKotlinFile(file: KtFile): KotlinAnalysisResult
}

/**
 * Wrapper for PSI elements to enable Series operations
 */
@JvmInline
value class PsiElementRef(val element: PsiElement) {
    val text: String get() = element.text
    val name: String? get() = element.name
    val containingFile: PsiFile? get() = element.containingFile
    val lineNumber: Int get() = element.lineNumber
}

/**
 * Wrapper for Kotlin PSI elements
 */
@JvmInline
value class KtElementRef(val element: KtElement) {
    val text: String get() = element.text
    val name: String? get() = (element as? KtNamedDeclaration)?.name
    val containingFile: KtFile? get() = element.containingFile as? KtFile
    val lineNumber: Int get() = element.lineNumber
}

/**
 * Data flow information for analysis
 */
data class DataFlowInfo(
    val inputElements: List<PsiElementRef>,
    val outputElements: List<PsiElementRef>,
    val dependencies: List<PsiElementRef>
)

/**
 * Kotlin analysis result
 */
data class KotlinAnalysisResult(
    val declarations: List<KtElementRef>,
    val types: List<KtTypeInfo>,
    val references: List<KtReferenceInfo>,
    val structure: String
)

data class KtTypeInfo(
    val element: KtElementRef,
    val type: String,
    val isNullable: Boolean
)

data class KtReferenceInfo(
    val element: KtElementRef,
    val referencedElement: KtElementRef?,
    val referenceType: String
)

// Type aliases for Series operations
typealias PsiElementSeries = Series<PsiElementRef>
typealias KtElementSeries = Series<KtElementRef>

// Placeholder for TrikeShed Series and Join types
// These would be imported from the actual TrikeShed library
interface Series<T> {
    fun <R> map(transform: (T) -> R): Series<R>
    fun filter(predicate: (T) -> Boolean): Series<T>
    fun <R> flatMap(transform: (T) -> Series<R>): Series<R>
    fun forEach(action: (T) -> Unit)
    fun toList(): List<T>
}

interface Join<A, B> {
    fun <C> map(transform: (A, B) -> C): Series<C>
    fun filter(predicate: (A, B) -> Boolean): Join<A, B>
}

// Extension functions for Series creation
fun <T> Series.Companion.from(elements: List<T>): Series<T> {
    // Placeholder implementation
    return object : Series<T> {
        private val list = elements
        
        override fun <R> map(transform: (T) -> R): Series<R> = 
            Series.from(list.map(transform))
            
        override fun filter(predicate: (T) -> Boolean): Series<T> = 
            Series.from(list.filter(predicate))
            
        override fun <R> flatMap(transform: (T) -> Series<R>): Series<R> = 
            Series.from(list.flatMap { transform(it).toList() })
            
        override fun forEach(action: (T) -> Unit) = list.forEach(action)
        
        override fun toList(): List<T> = list
    }
}

fun <T> Series.Companion.singleton(element: T): Series<T> = Series.from(listOf(element))

// Extension property for line number
val PsiElement.lineNumber: Int
    get() {
        val file = containingFile ?: return -1
        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
            .getDocument(file.virtualFile) ?: return -1
        return document.getLineNumber(textRange.startOffset) + 1
    }

val KtElement.lineNumber: Int
    get() {
        val file = containingFile as? KtFile ?: return -1
        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
            .getDocument(file.virtualFile) ?: return -1
        return document.getLineNumber(textRange.startOffset) + 1
    } 