# Phase 4: Navigation Graph & Live Code Model

## Test-Driven Development Approach

### 4.1 Usage Graph Tests
```kotlin
@Test
fun `should build usage graph for MetaSeries`() {
    // Given: Project with MetaSeries class
    val project = loadProject("/path/to/v2superbikeshed")
    val graphBuilder = UsageGraphBuilder(project)
    
    // When: Building usage graph
    val graph = graphBuilder.buildGraph("MetaSeries")
    
    // Then: Should find all usages
    assertThat(graph.nodes).anyMatch { it.name == "MetaSeries" }
    assertThat(graph.edges).isNotEmpty()
    assertThat(graph.usages).anyMatch { it.type == UsageType.METHOD_CALL }
    assertThat(graph.usages).anyMatch { it.type == UsageType.INHERITANCE }
}
```

### 4.2 Impact Analysis Tests
```kotlin
@Test
fun `should analyze impact of CoreTypes changes`() {
    // Given: CoreTypes.kt file
    val file = project.findFile("CoreTypes.kt")
    val impactAnalyzer = ImpactAnalyzer(project)
    
    // When: Analyzing impact
    val impact = impactAnalyzer.analyzeImpact(file)
    
    // Then: Should find affected files
    assertThat(impact.affectedFiles).isNotEmpty()
    assertThat(impact.affectedClasses).isNotEmpty()
    assertThat(impact.breakingChanges).isNotNull()
    assertThat(impact.safeChanges).isNotNull()
}
```

### 4.3 Live AST Updates Tests
```kotlin
@Test
fun `should stream AST updates in real-time`() {
    // Given: WebSocket connection for live updates
    val liveAST = LiveASTService(project)
    val updates = mutableListOf<ASTUpdate>()
    
    // When: Subscribing to AST updates
    liveAST.subscribe { update ->
        updates.add(update)
    }
    
    // And: Making code changes
    fileManager.modifyFile("Test.kt", "val newVar = 42")
    
    // Then: Should receive AST update
    assertThat(updates).isNotEmpty()
    assertThat(updates.last().type).isEqualTo(ASTUpdateType.VARIABLE_ADDED)
    assertThat(updates.last().nodeName).isEqualTo("newVar")
}
```

### 4.4 Error Streaming Tests
```kotlin
@Test
fun `should stream compilation errors`() {
    // Given: Code with errors
    val errorStream = CompilationErrorStream(project)
    val errors = mutableListOf<CompilationError>()
    
    // When: Subscribing to error stream
    errorStream.subscribe { error ->
        errors.add(error)
    }
    
    // And: Introducing syntax error
    fileManager.modifyFile("Test.kt", "val broken = ")
    
    // Then: Should receive error notification
    assertThat(errors).isNotEmpty()
    assertThat(errors.last().type).isEqualTo(ErrorType.SYNTAX_ERROR)
    assertThat(errors.last().message).contains("expecting")
}
```

### 4.5 Deprecated API Detection Tests
```kotlin
@Test
fun `should detect deprecated API usage`() {
    // Given: Code with deprecated APIs
    val code = """
        @Deprecated("Use newAPI instead")
        fun oldAPI() { }
        
        fun usage() {
            oldAPI() // Should be flagged
        }
    """.trimIndent()
    
    // When: Analyzing for deprecated usage
    val deprecatedUsages = DeprecatedAPIDetector.findUsages(code)
    
    // Then: Should find deprecated API usage
    assertThat(deprecatedUsages).isNotEmpty()
    assertThat(deprecatedUsages.first().apiName).isEqualTo("oldAPI")
    assertThat(deprecatedUsages.first().suggestion).isEqualTo("Use newAPI instead")
}
```

## Implementation Tasks

1. **Usage Graph Builder**
   - Symbol resolution across modules
   - Dependency graph construction
   - Usage type classification

2. **Impact Analyzer**
   - Change impact calculation
   - Breaking change detection
   - Safe refactoring suggestions

3. **Live AST Service**
   - WebSocket-based streaming
   - Real-time AST updates
   - Change notification system

4. **Compilation Error Stream**
   - Real-time error detection
   - Error classification
   - Quick fix suggestions

5. **Deprecated API Detector**
   - Deprecated symbol detection
   - Migration suggestions
   - Batch update capabilities

## Success Criteria
- Can build complete usage graphs for any symbol
- Can analyze impact of changes across project
- Real-time AST updates via WebSocket
- Live compilation error streaming
- Deprecated API detection and suggestions
- All navigation features work with v2superbikeshed project structure 