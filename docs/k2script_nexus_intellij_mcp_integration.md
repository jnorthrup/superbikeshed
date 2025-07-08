# k2script with Nexus IntelliJ MCP Server Integration

## Overview

This document outlines the integration between k2script and the Nexus IntelliJ MCP (Model Context Protocol) server, enabling seamless script execution, project analysis, and IDE integration capabilities.

## Current Architecture

### 1. Nexus IntelliJ Plugin MCP Server
- **Location**: `nexus/intellij-plugin/src/main/kotlin/com/v2superbikeshed/nexus/mcp/`
- **Components**:
  - `McpServerLauncher`: Auto-starts MCP server on IntelliJ startup
  - `McpStdioServer`: Handles JSON-RPC over stdin/stdout
  - `SimpleMcpServer`: Socket-based MCP server
  - HTTP/WebSocket endpoints on port 63343

### 2. k2script MCP Integration
- **Location**: `k2script/src/main/kotlin/k2script/cli/MCPCommand.kt`
- **Components**:
  - `MCPCommand`: CLI interface for MCP server management
  - `MCPServer`: Core MCP server implementation
  - `MCPRegistry`: Service discovery and registration
  - `MCPGateway`: Request routing and load balancing

## Integration Points

### 1. MCP Protocol Bridge
```kotlin
// k2script can connect to Nexus MCP server
k2script --mcp register nexus-intellij 1.0.0 tools resources prompts
k2script --mcp start nexus-intellij
k2script --mcp route tools/list
```

### 2. IntelliJ Project Analysis
```kotlin
// k2script scripts can analyze IntelliJ projects via MCP
#!/usr/bin/env k2script

import k2script.mcp.*

val nexusServer = MCPServer("nexus-intellij", "1.0.0", setOf("tools", "resources"))
nexusServer.registerHandler("project/analyze") { params ->
    // Analyze current IntelliJ project
    val projectPath = params["path"] as? String ?: "."
    val analysis = analyzeProject(projectPath)
    MCPResponse(200, mapOf("analysis" to analysis))
}
```

### 3. Code Refactoring Integration
```kotlin
// k2script can trigger IntelliJ refactoring via MCP
val refactoringRequest = MCPRequest("refactoring/rename_symbol", mapOf(
    "file" to "src/main/kotlin/MyClass.kt",
    "symbol" to "oldName",
    "newName" to "newName"
))

val response = nexusServer.handleRequest(refactoringRequest)
```

## Implementation Plan

### Phase 1: Basic MCP Connection
1. **k2script MCP Client**
   - Implement MCP client in k2script for connecting to Nexus server
   - Add connection management and error handling
   - Support both stdio and HTTP transport

2. **Nexus MCP Server Enhancement**
   - Extend existing MCP server with k2script-specific endpoints
   - Add project analysis capabilities
   - Implement script execution coordination

### Phase 2: Project Analysis Integration
1. **PSI Bridge**
   - Connect k2script to IntelliJ PSI (Program Structure Interface)
   - Enable code analysis and symbol resolution
   - Support Kotlin-specific analysis features

2. **Dependency Resolution**
   - Integrate k2script dependency resolution with IntelliJ project structure
   - Support Gradle/Maven project analysis
   - Enable cross-module dependency tracking

### Phase 3: Advanced Features
1. **Live Code Analysis**
   - Real-time code analysis during script execution
   - Integration with IntelliJ's analysis engine
   - Support for K2 (FIR-based) analysis

2. **Refactoring Support**
   - Trigger IntelliJ refactoring from k2script
   - Support for rename, move, extract operations
   - Integration with IntelliJ's refactoring engine

## API Design

### k2script MCP Client API
```kotlin
class K2ScriptMcpClient {
    suspend fun connectToNexus(host: String = "localhost", port: Int = 63343)
    suspend fun analyzeProject(projectPath: String): ProjectAnalysis
    suspend fun executeRefactoring(refactoring: RefactoringRequest): RefactoringResult
    suspend fun getProjectStructure(): ProjectStructure
    suspend fun findSymbols(query: String): List<Symbol>
}
```

### Nexus MCP Server API Extensions
```kotlin
// New MCP methods for k2script integration
"k2script/execute" -> handleK2ScriptExecution(request)
"k2script/analyze" -> handleK2ScriptAnalysis(request)
"project/structure" -> handleProjectStructure(request)
"dependencies/resolve" -> handleDependencyResolution(request)
"refactoring/execute" -> handleRefactoringExecution(request)
```

## Usage Examples

### 1. Project Analysis Script
```kotlin
#!/usr/bin/env k2script

import k2script.mcp.*

val client = K2ScriptMcpClient()
client.connectToNexus()

val analysis = client.analyzeProject(".")
println("Project Analysis:")
println("  Files: ${analysis.fileCount}")
println("  Classes: ${analysis.classCount}")
println("  Functions: ${analysis.functionCount}")
println("  Dependencies: ${analysis.dependencies.size}")
```

### 2. Automated Refactoring Script
```kotlin
#!/usr/bin/env k2script

import k2script.mcp.*

val client = K2ScriptMcpClient()
client.connectToNexus()

// Find all occurrences of old naming pattern
val symbols = client.findSymbols("oldPattern*")

// Execute refactoring for each symbol
symbols.forEach { symbol ->
    val newName = symbol.name.replace("oldPattern", "newPattern")
    val result = client.executeRefactoring(RefactoringRequest(
        type = "rename",
        symbol = symbol,
        newName = newName
    ))
    println("Refactored: ${symbol.name} -> $newName")
}
```

### 3. Dependency Analysis Script
```kotlin
#!/usr/bin/env k2script

import k2script.mcp.*

val client = K2ScriptMcpClient()
client.connectToNexus()

val structure = client.getProjectStructure()
val dependencies = structure.modules.flatMap { it.dependencies }

println("Project Dependencies:")
dependencies.groupBy { it.type }.forEach { (type, deps) ->
    println("  $type:")
    deps.forEach { dep ->
        println("    ${dep.group}:${dep.name}:${dep.version}")
    }
}
```

## Configuration

### k2script Configuration
```kotlin
// k2script configuration for Nexus integration
k2script {
    mcp {
        nexus {
            host = "localhost"
            port = 63343
            autoConnect = true
            timeout = 30000
        }
    }
}
```

### Nexus Plugin Configuration
```kotlin
// Nexus IntelliJ plugin configuration
nexus {
    mcp {
        enabled = true
        port = 63343
        k2scriptIntegration = true
        autoStart = true
    }
}
```

## Testing Strategy

### 1. Unit Tests
- Test MCP protocol handling
- Test k2script client-server communication
- Test project analysis integration

### 2. Integration Tests
- Test full k2script execution with Nexus MCP server
- Test IntelliJ project analysis capabilities
- Test refactoring integration

### 3. End-to-End Tests
- Test complete workflow from k2script execution to IntelliJ integration
- Test error handling and recovery
- Test performance under load

## Performance Considerations

### 1. Connection Management
- Implement connection pooling for multiple k2script instances
- Support connection reuse across script executions
- Implement connection health monitoring

### 2. Caching Strategy
- Cache project analysis results
- Cache dependency resolution results
- Implement intelligent cache invalidation

### 3. Resource Management
- Monitor memory usage during analysis
- Implement timeouts for long-running operations
- Support graceful degradation under load

## Security Considerations

### 1. Authentication
- Implement API key authentication for MCP connections
- Support secure communication over HTTPS/WSS
- Implement access control for sensitive operations

### 2. Input Validation
- Validate all MCP request parameters
- Sanitize file paths and project references
- Implement rate limiting for API calls

## Future Enhancements

### 1. Advanced Analysis
- Support for cross-language analysis
- Integration with external analysis tools
- Support for custom analysis rules

### 2. Collaboration Features
- Multi-user project analysis
- Shared refactoring sessions
- Real-time collaboration support

### 3. AI Integration
- AI-powered code suggestions
- Automated refactoring recommendations
- Intelligent dependency management

## Conclusion

The integration between k2script and Nexus IntelliJ MCP server provides a powerful foundation for script-based IDE automation and project analysis. This integration enables developers to leverage the full power of IntelliJ's analysis and refactoring capabilities from k2script scripts, creating a seamless development experience.

The modular architecture allows for incremental implementation and testing, ensuring robust and maintainable code. The comprehensive API design supports both simple and complex use cases, making it suitable for a wide range of development workflows. 