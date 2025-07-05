# IntelliJ HTTP API + Nexus Quick Start Guide

## 1. Enable IntelliJ REST API

### Option A: Via UI
1. Help → Edit Custom VM Options
2. Add these lines:
```
-Dide.rest.api=true
-Dide.rest.api.port=63342
-Dide.rest.api.cors.enabled=true
```
3. Restart IntelliJ

### Option B: Direct Edit
```bash
# macOS
echo "-Dide.rest.api=true" >> ~/Library/Application\ Support/JetBrains/IntelliJIdea2024.3/idea.vmoptions
echo "-Dide.rest.api.port=63342" >> ~/Library/Application\ Support/JetBrains/IntelliJIdea2024.3/idea.vmoptions

# Linux
echo "-Dide.rest.api=true" >> ~/.config/JetBrains/IntelliJIdea2024.3/idea64.vmoptions

# Windows
# Add to: %APPDATA%\JetBrains\IntelliJIdea2024.3\idea64.exe.vmoptions
```

## 2. Test API Connection

```bash
# Check if API is running
curl http://localhost:63342/api/status

# Get project info
curl http://localhost:63342/api/project?path=/Users/jim/work/v2superbikeshed
```

## 3. Run Nexus with IntelliJ Integration

```kotlin
// nexus-intellij-demo.kt
import nexus.intellij.*
import kotlinx.coroutines.*

fun main() = runBlocking {
    val intellij = IntelliJApiClient(
        projectPath = "/Users/jim/work/v2superbikeshed"
    )
    
    println("IntelliJ Connected: ${intellij.isConnected()}")
    
    // Example: Rename Series to Indexed
    val renameOp = RefactorOperation.Rename(
        file = "trikeshed-lib/src/commonMain/kotlin/borg/trikeshed/lib/CoreTypes.kt",
        offset = 1234, // Find actual offset
        newName = "Indexed"
    )
    
    // Preview first
    val preview = intellij.previewRefactoring(renameOp)
    println("Preview: ${preview.a?.changes}")
    
    // Execute if preview looks good
    if (preview.a?.success == true) {
        val result = intellij.executeRefactoring(renameOp)
        println("Renamed in ${result.a?.filesChanged} files")
    }
    
    intellij.close()
}
```

## 4. Common Operations

### Find Symbol Offset
```kotlin
// Use IntelliJ's PSI to find offset
suspend fun findSymbolOffset(
    file: String,
    symbolName: String
): Int? {
    val response = client.get("$baseUrl/find/symbol") {
        parameter("file", file)
        parameter("name", symbolName)
    }
    return response.body<SymbolInfo>().offset
}
```

### Batch Rename Example
```kotlin
// Rename all Series typealiases to Indexed
val renames = listOf(
    "ByteSeries" to "ByteIndexed",
    "CharSeries" to "CharIndexed",
    "IntSeries" to "IntIndexed"
).map { (old, new) ->
    RefactorOperation.Rename(
        file = findFileContaining(old),
        offset = findSymbolOffset(old),
        newName = new
    )
}

val results = intellij.batchRename(renames)
```

### Run Inspections
```kotlin
val errors = intellij.runInspections("trikeshed-lib/src")
    .filter { it.severity == "ERROR" }
    
errors.forEach { error ->
    println("${error.file}:${error.line} - ${error.message}")
    
    // Apply quick fix
    if (error.quickFixes.isNotEmpty()) {
        applyQuickFix(error.quickFixes.first())
    }
}
```

## 5. Nexus Integration Pattern

```kotlin
// In your Nexus intention cycle
suspend fun IntentionCycle.withIntelliJRefactoring(): IntentionCycle {
    val intellij = context.getOrCreate { 
        IntelliJApiClient(projectPath = currentProject)
    }
    
    return when (val intent = currentIntent) {
        is RefactorIntent -> {
            val operation = intent.toRefactorOperation()
            refactorWithIntelliJ(operation, intellij)
        }
        else -> this
    }
}
```

## 6. Troubleshooting

### API Not Responding
```bash
# Check if IntelliJ is listening
lsof -i :63342

# Check logs
tail -f ~/Library/Logs/JetBrains/IntelliJIdea2024.3/idea.log | grep "rest.api"
```

### Permission Issues
```kotlin
// Add authentication header if configured
client.get(url) {
    header("Authorization", "Bearer $apiToken")
}
```

### CORS Issues
```
# In vmoptions:
-Dide.rest.api.cors.allowed.origins=*
```

## 7. Advanced Usage

### WebSocket for Real-time Updates
```kotlin
client.webSocket("ws://localhost:63342/api/ws") {
    send(Frame.Text("""{"subscribe": "refactoring"}"""))
    
    incoming.consumeEach { frame ->
        if (frame is Frame.Text) {
            val update = Json.decodeFromString<RefactorUpdate>(frame.readText())
            println("Refactoring progress: ${update.progress}%")
        }
    }
}
```

### Custom Plugin Integration
For deeper integration, create an IntelliJ plugin that exposes additional endpoints:

```kotlin
// In your IntelliJ plugin
class NexusRestService : RestService() {
    @GET
    @Path("/nexus/symbols")
    fun getSymbols(@QueryParam("type") type: String): List<Symbol> {
        return ProjectSymbolIndex.getAllSymbols(project)
            .filter { it.type == type }
    }
}
```

## Next Steps
1. Run the demo script to test connection
2. Integrate into your Nexus workflow
3. Add more refactoring operations as needed
4. Consider building a custom IntelliJ plugin for advanced features