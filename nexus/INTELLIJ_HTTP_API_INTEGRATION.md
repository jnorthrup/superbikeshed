# IntelliJ HTTP API Integration for Nexus

## Overview
IntelliJ IDEA provides a REST API that allows programmatic control of the IDE. This integration enables Nexus to perform refactoring, code analysis, and other IDE operations without UI automation.

## Prerequisites

### 1. Enable IntelliJ REST API
```bash
# Add to IntelliJ VM options (Help → Edit Custom VM Options)
-Dide.rest.api=true
-Dide.rest.api.port=63342
```

### 2. Install Required Plugins
- **REST API Support** (built-in)
- **Remote Development Gateway** (optional)

## API Endpoints

### Core Endpoints
```
http://localhost:63342/api/
├── project/              # Project operations
├── file/                 # File operations  
├── refactor/            # Refactoring
├── inspection/          # Code inspections
├── navigation/          # Code navigation
└── completion/          # Code completion
```

## Nexus Integration Architecture

```kotlin
// nexus/src/commonMain/kotlin/nexus/intellij/IntelliJHttpClient.kt

interface IntelliJHttpClient {
    suspend fun refactor(operation: RefactorOperation): RefactorResult
    suspend fun inspect(file: String): List<Inspection>
    suspend fun complete(context: CompletionContext): List<Suggestion>
    suspend fun navigate(target: NavigationTarget): FilePosition
}

sealed class RefactorOperation {
    data class Rename(val element: String, val newName: String) : RefactorOperation()
    data class ExtractMethod(val range: TextRange, val name: String) : RefactorOperation()
    data class Move(val element: String, val target: String) : RefactorOperation()
    data class ChangeSignature(val method: String, val params: List<Param>) : RefactorOperation()
}
```

## Implementation

### 1. Basic HTTP Client
```kotlin
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class IntelliJApiClient(
    private val baseUrl: String = "http://localhost:63342/api",
    private val projectPath: String
) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    }
    
    suspend fun executeRefactoring(request: RefactorRequest): RefactorResponse {
        return client.post("$baseUrl/refactor") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
}
```

### 2. Refactoring Operations
```kotlin
// Rename Symbol
suspend fun renameSymbol(
    file: String,
    offset: Int,
    newName: String
): RefactorResult {
    val request = RefactorRequest(
        type = "rename",
        file = file,
        offset = offset,
        params = mapOf("newName" to newName)
    )
    return client.executeRefactoring(request)
}

// Extract Method
suspend fun extractMethod(
    file: String,
    startOffset: Int,
    endOffset: Int,
    methodName: String
): RefactorResult {
    val request = RefactorRequest(
        type = "extractMethod",
        file = file,
        startOffset = startOffset,
        endOffset = endOffset,
        params = mapOf(
            "name" to methodName,
            "visibility" to "private"
        )
    )
    return client.executeRefactoring(request)
}
```

### 3. Code Inspection
```kotlin
suspend fun runInspections(file: String): List<InspectionResult> {
    val response = client.get("$baseUrl/inspection/file") {
        parameter("path", file)
        parameter("includeDisabled", false)
    }
    return response.body()
}

data class InspectionResult(
    val severity: String,  // ERROR, WARNING, INFO
    val message: String,
    val file: String,
    val line: Int,
    val column: Int,
    val quickFixes: List<QuickFix>
)
```

### 4. Code Completion
```kotlin
suspend fun getCompletions(
    file: String,
    line: Int,
    column: Int
): List<CompletionItem> {
    val response = client.get("$baseUrl/completion") {
        parameter("file", file)
        parameter("line", line)
        parameter("column", column)
    }
    return response.body()
}

data class CompletionItem(
    val text: String,
    val type: String,
    val icon: String?,
    val detail: String?
)
```

## Nexus Integration Points

### 1. CCEK Integration
```kotlin
// In CCEK Control phase
class IntelliJRefactorControl : Control {
    override suspend fun execute(context: Context): Result {
        val intellij = context.get<IntelliJApiClient>()
        
        return when (val intent = context.intent) {
            is RefactorIntent.Rename -> {
                intellij.renameSymbol(
                    file = intent.file,
                    offset = intent.offset,
                    newName = intent.newName
                )
            }
            // ... other refactoring intents
        }
    }
}
```

### 2. Main's Pursuit Loop
```kotlin
// In main()'s attention distribution
suspend fun distributeAttentionToRefactoring(
    fragment: AttentionFragment,
    intellij: IntelliJApiClient
) {
    when (fragment) {
        is RefactorFragment -> {
            val result = intellij.executeRefactoring(fragment.request)
            fragment.complete(result)
        }
    }
}
```

### 3. Error Handling
```kotlin
sealed class IntelliJError : Error() {
    object NotConnected : IntelliJError()
    object ProjectNotOpen : IntelliJError()
    data class RefactoringFailed(val reason: String) : IntelliJError()
    data class ApiError(val code: Int, val message: String) : IntelliJError()
}

suspend fun safeRefactor(
    operation: RefactorOperation
): Either<IntelliJError, RefactorResult> = either {
    ensure(isConnected()) { IntelliJError.NotConnected }
    ensure(isProjectOpen()) { IntelliJError.ProjectNotOpen }
    
    val result = try {
        executeRefactoring(operation)
    } catch (e: Exception) {
        raise(IntelliJError.RefactoringFailed(e.message ?: "Unknown error"))
    }
    
    result
}
```

## Advanced Features

### 1. Batch Refactoring
```kotlin
suspend fun batchRename(renames: List<RenameOperation>) {
    coroutineScope {
        renames.map { rename ->
            async {
                renameSymbol(rename.file, rename.offset, rename.newName)
            }
        }.awaitAll()
    }
}
```

### 2. Preview Changes
```kotlin
suspend fun previewRefactoring(
    operation: RefactorOperation
): RefactorPreview {
    val request = operation.toRequest().copy(preview = true)
    return client.post("$baseUrl/refactor/preview") {
        setBody(request)
    }.body()
}

data class RefactorPreview(
    val changes: List<FileChange>,
    val conflicts: List<Conflict>
)
```

### 3. WebSocket Integration
```kotlin
// For real-time updates
suspend fun connectWebSocket() {
    client.webSocket("ws://localhost:63342/api/ws") {
        incoming.consumeEach { frame ->
            when (frame) {
                is Frame.Text -> handleUpdate(frame.readText())
            }
        }
    }
}
```

## Security Considerations

1. **Authentication**: Add token-based auth for production
2. **Rate Limiting**: Implement request throttling
3. **Validation**: Validate all file paths and operations
4. **Sandboxing**: Run in restricted environment

## Example Usage

```kotlin
// In Nexus main
suspend fun main() {
    val intellij = IntelliJApiClient(projectPath = "/Users/jim/work/v2superbikeshed")
    
    // Rename Series to Indexed
    val renameResult = intellij.renameSymbol(
        file = "trikeshed-lib/src/commonMain/kotlin/borg/trikeshed/lib/CoreTypes.kt",
        offset = 1234, // offset of "Series" declaration
        newName = "Indexed"
    )
    
    // Run inspections
    val inspections = intellij.runInspections("trikeshed-lib/src")
    inspections.filter { it.severity == "ERROR" }.forEach { error ->
        println("Error at ${error.file}:${error.line} - ${error.message}")
    }
}
```

## Testing

```kotlin
class IntelliJApiTest {
    @Test
    fun testRename() = runTest {
        val mockServer = MockWebServer()
        mockServer.enqueue(MockResponse().setBody("""
            {"success": true, "filesChanged": 5}
        """))
        
        val client = IntelliJApiClient(
            baseUrl = mockServer.url("/api").toString()
        )
        
        val result = client.renameSymbol("Test.kt", 100, "NewName")
        assertTrue(result.success)
    }
}
```

## Future Enhancements

1. **Plugin Development**: Custom IntelliJ plugin for deeper integration
2. **Language Server Protocol**: Use LSP for cross-IDE support
3. **AI-Driven Refactoring**: Combine with LLM for intelligent suggestions
4. **Visual Diff**: Show before/after in Nexus UI

## References

- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- [REST API Documentation](https://www.jetbrains.com/help/idea/rest-api.html)
- [Remote Development](https://www.jetbrains.com/remote-development/)