package nexus.intellij

import borg.trikeshed.lib.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * IntelliJ HTTP API Client for Nexus
 * 
 * Provides programmatic access to IntelliJ IDEA's REST API for:
 * - Refactoring operations
 * - Code inspections
 * - Navigation
 * - Code completion
 */

// Request/Response Models
@Serializable
data class RefactorRequest(
    val type: String,
    val file: String,
    val offset: Int? = null,
    val startOffset: Int? = null,
    val endOffset: Int? = null,
    val params: Map<String, String> = emptyMap(),
    val preview: Boolean = false
)

@Serializable
data class RefactorResponse(
    val success: Boolean,
    val filesChanged: Int = 0,
    val changes: List<FileChange> = emptyList(),
    val errors: List<String> = emptyList()
)

@Serializable
data class FileChange(
    val file: String,
    val diff: String
)

@Serializable
data class InspectionResult(
    val severity: String,
    val message: String,
    val file: String,
    val line: Int,
    val column: Int,
    val quickFixes: List<QuickFix> = emptyList()
)

@Serializable
data class QuickFix(
    val description: String,
    val action: String
)

@Serializable
data class CompletionItem(
    val text: String,
    val type: String,
    val icon: String? = null,
    val detail: String? = null,
    val insertText: String? = null
)

// Refactoring Operations
sealed class RefactorOperation {
    data class Rename(
        val file: String,
        val offset: Int,
        val newName: String
    ) : RefactorOperation()
    
    data class ExtractMethod(
        val file: String,
        val startOffset: Int,
        val endOffset: Int,
        val methodName: String,
        val visibility: String = "private"
    ) : RefactorOperation()
    
    data class Move(
        val sourceFile: String,
        val targetPackage: String
    ) : RefactorOperation()
    
    data class ChangeSignature(
        val file: String,
        val methodOffset: Int,
        val newSignature: String
    ) : RefactorOperation()
    
    data class InlineVariable(
        val file: String,
        val offset: Int
    ) : RefactorOperation()
}

// Error types
sealed class IntelliJError {
    object NotConnected : IntelliJError()
    object ProjectNotOpen : IntelliJError()
    data class RefactoringFailed(val reason: String) : IntelliJError()
    data class ApiError(val code: Int, val message: String) : IntelliJError()
}

// Main API Client
class IntelliJApiClient(
    private val baseUrl: String = "http://localhost:63342/api",
    private val projectPath: String,
    private val timeout: Long = 30000L
) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        
        install(HttpTimeout) {
            requestTimeoutMillis = timeout
        }
    }
    
    // Connection check
    suspend fun isConnected(): Boolean = try {
        client.get("$baseUrl/status").status == HttpStatusCode.OK
    } catch (e: Exception) {
        false
    }
    
    // Refactoring operations
    suspend fun executeRefactoring(operation: RefactorOperation): Join<RefactorResponse?, IntelliJError> {
        if (!isConnected()) return null j IntelliJError.NotConnected
        
        val request = when (operation) {
            is RefactorOperation.Rename -> RefactorRequest(
                type = "rename",
                file = operation.file,
                offset = operation.offset,
                params = mapOf("newName" to operation.newName)
            )
            
            is RefactorOperation.ExtractMethod -> RefactorRequest(
                type = "extractMethod",
                file = operation.file,
                startOffset = operation.startOffset,
                endOffset = operation.endOffset,
                params = mapOf(
                    "name" to operation.methodName,
                    "visibility" to operation.visibility
                )
            )
            
            is RefactorOperation.Move -> RefactorRequest(
                type = "move",
                file = operation.sourceFile,
                params = mapOf("target" to operation.targetPackage)
            )
            
            is RefactorOperation.ChangeSignature -> RefactorRequest(
                type = "changeSignature",
                file = operation.file,
                offset = operation.methodOffset,
                params = mapOf("signature" to operation.newSignature)
            )
            
            is RefactorOperation.InlineVariable -> RefactorRequest(
                type = "inline",
                file = operation.file,
                offset = operation.offset
            )
        }
        
        return try {
            val response: RefactorResponse = client.post("$baseUrl/refactor") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
            
            if (response.success) {
                response j null
            } else {
                null j IntelliJError.RefactoringFailed(
                    response.errors.joinToString(", ")
                )
            }
        } catch (e: Exception) {
            null j IntelliJError.ApiError(500, e.message ?: "Unknown error")
        }
    }
    
    // Preview refactoring
    suspend fun previewRefactoring(operation: RefactorOperation): Join<RefactorResponse?, IntelliJError> {
        // Same as execute but with preview flag
        val request = when (operation) {
            is RefactorOperation.Rename -> RefactorRequest(
                type = "rename",
                file = operation.file,
                offset = operation.offset,
                params = mapOf("newName" to operation.newName),
                preview = true
            )
            // ... other operations with preview = true
            else -> return null j IntelliJError.RefactoringFailed("Preview not supported")
        }
        
        return try {
            val response: RefactorResponse = client.post("$baseUrl/refactor/preview") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
            response j null
        } catch (e: Exception) {
            null j IntelliJError.ApiError(500, e.message ?: "Unknown error")
        }
    }
    
    // Code inspections
    suspend fun runInspections(path: String): List<InspectionResult> = try {
        client.get("$baseUrl/inspection/run") {
            parameter("path", path)
            parameter("project", projectPath)
        }.body()
    } catch (e: Exception) {
        emptyList()
    }
    
    // Code completion
    suspend fun getCompletions(
        file: String,
        line: Int,
        column: Int
    ): List<CompletionItem> = try {
        client.get("$baseUrl/completion") {
            parameter("file", file)
            parameter("line", line)
            parameter("column", column)
            parameter("project", projectPath)
        }.body()
    } catch (e: Exception) {
        emptyList()
    }
    
    // Batch operations
    suspend fun batchRename(renames: List<RefactorOperation.Rename>): List<Join<RefactorResponse?, IntelliJError>> =
        coroutineScope {
            renames.map { rename ->
                async { executeRefactoring(rename) }
            }.awaitAll()
        }
    
    // Close client
    fun close() {
        client.close()
    }
}

// Nexus integration extension
suspend fun IntentionCycle.refactorWithIntelliJ(
    operation: RefactorOperation,
    client: IntelliJApiClient
): IntentionCycle {
    val result = client.executeRefactoring(operation)
    
    return when (result.b) {
        null -> copy(
            attention = attention + AttentionFragment.Success(
                "Refactoring completed: ${result.a?.filesChanged} files changed"
            )
        )
        is IntelliJError -> copy(
            attention = attention + AttentionFragment.Error(
                "Refactoring failed: ${result.b}"
            )
        )
    }
}