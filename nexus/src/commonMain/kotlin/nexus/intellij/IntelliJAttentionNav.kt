package nexus.intellij

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * IntelliJ Navigation via Attention Fragments
 * LLM navigates compilation errors as attention distribution in KMP
 */

// Attention fragments for IntelliJ navigation
sealed class IntelliJAttention : AttentionFragment {
    data class CompilationError(
        val file: String,
        val line: Int,
        val column: Int,
        val message: String,
        val quickFixes: List<String> = emptyList()
    ) : IntelliJAttention()
    
    data class NavigateToSymbol(
        val symbol: String,
        val file: String? = null
    ) : IntelliJAttention()
    
    data class RefactorRequest(
        val operation: RefactorOperation,
        val preview: Boolean = true
    ) : IntelliJAttention()
    
    data class ApplyQuickFix(
        val error: CompilationError,
        val fixIndex: Int = 0
    ) : IntelliJAttention()
}

// Attention-based API client
class IntelliJAttentionClient(
    private val baseUrl: String = "http://localhost:63342/api",
    private val projectPath: String
) {
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true 
    }
    
    // Convert errors to attention fragments
    suspend fun errorsAsAttention(): Indexed<IntelliJAttention> {
        val errors = fetchErrors()
        return errors.size j { i -> 
            val err = errors[i]
            IntelliJAttention.CompilationError(
                file = err.file,
                line = err.line,
                column = err.column,
                message = err.message,
                quickFixes = err.quickFixes.map { it.description }
            )
        }
    }
    
    // Navigate attention cycle through errors
    suspend fun navigateErrorAttention(
        cycle: IntentionCycle,
        maxAttention: Int = 10
    ): IntentionCycle {
        val errorAttention = errorsAsAttention()
        
        // Distribute attention across errors
        var updatedCycle = cycle
        for (i in 0 until minOf(errorAttention.a, maxAttention)) {
            val attention = errorAttention.b(i)
            updatedCycle = updatedCycle.copy(
                attention = updatedCycle.attention + attention
            )
        }
        
        return updatedCycle
    }
    
    // Process attention fragment
    suspend fun processAttention(
        attention: IntelliJAttention
    ): Join<String, String?> { // result j error
        return when (attention) {
            is IntelliJAttention.CompilationError -> {
                if (attention.quickFixes.isNotEmpty()) {
                    applyQuickFix(attention, 0)
                } else {
                    "No quick fixes available" j attention.message
                }
            }
            
            is IntelliJAttention.NavigateToSymbol -> {
                navigateToSymbol(attention.symbol, attention.file)
            }
            
            is IntelliJAttention.RefactorRequest -> {
                val result = executeRefactoring(attention.operation)
                if (result.a != null) {
                    "Refactored ${result.a.filesChanged} files" j null
                } else {
                    "Refactoring failed" j result.b.toString()
                }
            }
            
            is IntelliJAttention.ApplyQuickFix -> {
                applyQuickFix(attention.error, attention.fixIndex)
            }
        }
    }
    
    // Batch process attention fragments
    suspend fun batchProcessAttention(
        fragments: Indexed<IntelliJAttention>
    ): Indexed<Join<String, String?>> = coroutineScope {
        val results = (0 until fragments.a).map { i ->
            async { processAttention(fragments.b(i)) }
        }.awaitAll()
        
        results.size j { i -> results[i] }
    }
    
    // Low-level API calls
    @Serializable
    private data class ErrorResponse(
        val file: String,
        val line: Int,
        val column: Int,
        val message: String,
        val quickFixes: List<QuickFixInfo> = emptyList()
    )
    
    @Serializable
    private data class QuickFixInfo(
        val id: String,
        val description: String
    )
    
    private suspend fun fetchErrors(): List<ErrorResponse> = 
        withContext(Dispatchers.IO) {
            try {
                // Simulated API call - replace with actual HTTP client
                emptyList() // TODO: Implement actual API call
            } catch (e: Exception) {
                emptyList()
            }
        }
    
    private suspend fun applyQuickFix(
        error: IntelliJAttention.CompilationError,
        fixIndex: Int
    ): Join<String, String?> = 
        withContext(Dispatchers.IO) {
            try {
                // TODO: Implement actual API call
                "Applied fix: ${error.quickFixes.getOrNull(fixIndex)}" j null
            } catch (e: Exception) {
                "Failed to apply fix" j e.message
            }
        }
    
    private suspend fun navigateToSymbol(
        symbol: String,
        file: String?
    ): Join<String, String?> =
        withContext(Dispatchers.IO) {
            try {
                // TODO: Implement actual API call
                "Navigated to $symbol" j null
            } catch (e: Exception) {
                "Navigation failed" j e.message
            }
        }
    
    private suspend fun executeRefactoring(
        operation: RefactorOperation
    ): Join<RefactorResponse?, IntelliJError> =
        withContext(Dispatchers.IO) {
            try {
                // TODO: Implement actual API call
                RefactorResponse(true, 1) j null
            } catch (e: Exception) {
                null j IntelliJError.ApiError(500, e.message ?: "Unknown")
            }
        }
}

// Extension for main's attention distribution
suspend fun distributeAttentionToIntelliJ(
    fragments: Indexed<AttentionFragment>,
    intellij: IntelliJAttentionClient
): Indexed<AttentionFragment> {
    val intellijFragments = mutableListOf<IntelliJAttention>()
    val otherFragments = mutableListOf<AttentionFragment>()
    
    // Separate IntelliJ-specific attention
    for (i in 0 until fragments.a) {
        when (val frag = fragments.b(i)) {
            is IntelliJAttention -> intellijFragments.add(frag)
            else -> otherFragments.add(frag)
        }
    }
    
    // Process IntelliJ fragments
    if (intellijFragments.isNotEmpty()) {
        val intellijIndexed = intellijFragments.size j { i -> intellijFragments[i] }
        val results = intellij.batchProcessAttention(intellijIndexed)
        
        // Convert results back to attention fragments
        for (i in 0 until results.a) {
            val result = results.b(i)
            otherFragments.add(
                if (result.b == null) {
                    AttentionFragment.Success(result.a ?: "Completed")
                } else {
                    AttentionFragment.Error("${result.a}: ${result.b}")
                }
            )
        }
    }
    
    return otherFragments.size j { i -> otherFragments[i] }
}

// Minimal token navigation strategy
class TokenEfficientNavigator(
    private val client: IntelliJAttentionClient
) {
    suspend fun fixFirstNErrors(n: Int = 5): String {
        val errors = client.errorsAsAttention()
        val toFix = minOf(n, errors.a)
        
        val results = (0 until toFix).map { i ->
            client.processAttention(errors.b(i))
        }
        
        val fixed = results.count { it.b == null }
        return "$fixed/$toFix"
    }
}