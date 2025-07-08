# TDD Approach for IntelliJ Plugin with Enumerator Integration

Based on the intellij-enumerator and the ideal API features, I'll create a 4-phase TDD approach for the IntelliJ plugin:

## Phase 1: Project Structure & Enumerator Integration

### Goals
- Integrate intellij-enumerator to discover project structure
- Expose v2superbikeshed module hierarchy via API
- Provide basic PSI access for symbol finding

### Test Cases
```kotlin
// Test 1: Enumerator Integration
@Test
fun `should discover v2superbikeshed project structure`() {
    val projectPath = "/Users/jim/work/v2superbikeshed"
    val enumerator = ProjectEnumeratorService.getInstance()
    val details = enumerator.enumerateProject(projectPath)
    
    assertThat(details.projectName).isEqualTo("v2superbikeshed")
    assertThat(details.modules).containsKeys("trikeshed-lib", "trikeshed-common")
    assertThat(details.buildSystemInfo.type).isEqualTo("GRADLE")
}

// Test 2: Module Discovery API
@Test
fun `should expose module hierarchy via REST API`() {
    val response = httpClient.get("/api/project/v2superbikeshed/modules")
    
    assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    val modules = response.body<ModulesResponse>()
    assertThat(modules.hierarchy).containsExactly(
        ModuleInfo("trikeshed-lib", dependencies = emptyList()),
        ModuleInfo("trikeshed-common", dependencies = listOf("trikeshed-lib"))
    )
}

// Test 3: PSI Symbol Access
@Test
fun `should find symbol across modules`() {
    val response = httpClient.get("/api/project/v2superbikeshed/symbol/Indexed")
    
    assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    val locations = response.body<SymbolLocations>()
    assertThat(locations.files).containsKey("borg/trikeshed/lib/CoreTypes.kt")
}
```

### Implementation Steps
1. Create `ProjectEnumeratorService` wrapper around intellij-enumerator
2. Add REST endpoints for module discovery
3. Implement PSI-based symbol search using IntelliJ's Find Usages API

## Phase 2: Structural Search & Replace

### Goals
- Implement SSR for Series → Indexed migration
- Support batch rename operations across modules
- Enable pattern replacement (j( → join())

### Test Cases
```kotlin
// Test 1: Pattern Matching
@Test
fun `should find all Series typealiases`() {
    val request = StructuralSearchRequest(
        pattern = "typealias \$name\$Series<\$type\$> = Series<\$type\$>",
        scope = "project"
    )
    
    val response = httpClient.post("/api/project/v2superbikeshed/ssr/search") {
        setBody(request)
    }
    
    val matches = response.body<SSRMatches>()
    assertThat(matches.results).hasSize(15) // Expected Series typealiases
    assertThat(matches.results.map { it.name }).contains("IntSeries", "CharSeries")
}

// Test 2: Batch Replace
@Test
fun `should replace Series with Indexed across modules`() {
    val request = BatchRenameRequest(
        oldPattern = "Series<\$T\$>",
        newPattern = "Indexed<\$T\$>",
        scope = listOf("trikeshed-lib", "trikeshed-common")
    )
    
    val response = httpClient.post("/api/project/v2superbikeshed/ssr/replace") {
        setBody(request)
    }
    
    val result = response.body<RefactorResult>()
    assertThat(result.filesChanged).isGreaterThan(100)
    assertThat(result.preview).containsKey("CoreTypes.kt")
}

// Test 3: Pattern Replacement
@Test
fun `should replace j operator with join function`() {
    val request = PatternReplaceRequest(
        searchPattern = "\$a\$ j \$b\$",
        replacePattern = "join(\$a\$, \$b\$)",
        filePattern = "**/*.kt"
    )
    
    val response = httpClient.post("/api/project/v2superbikeshed/pattern/replace") {
        setBody(request)
    }
    
    assertThat(response.body<RefactorResult>().success).isTrue()
}
```

### Implementation Steps
1. Create SSR engine using IntelliJ's StructuralSearchPlugin
2. Implement pattern variable extraction and substitution
3. Add preview mode for batch operations
4. Create atomic transaction support for multi-file changes

## Phase 3: Refactoring & Code Analysis

### Goals
- Enable batch refactoring with rollback
- Detect and fix unresolved references
- Remove kotlin.internal annotations
- Build quick fix engine

### Test Cases
```kotlin
// Test 1: Batch Refactoring with Rollback
@Test
fun `should perform atomic batch refactoring`() {
    val transaction = httpClient.post("/api/project/v2superbikeshed/refactor/begin")
        .body<TransactionId>()
    
    // Multiple refactoring operations
    val renameOp = RenameOperation("CoreTypes", "CoreTypesV2")
    val moveOp = MoveOperation("borg.trikeshed.lib", "borg.trikeshed.core")
    
    httpClient.post("/api/project/v2superbikeshed/refactor/apply") {
        setBody(BatchRefactorRequest(transaction.id, listOf(renameOp, moveOp)))
    }
    
    // Verify changes
    val status = httpClient.get("/api/project/v2superbikeshed/refactor/status/${transaction.id}")
        .body<RefactorStatus>()
    
    assertThat(status.operations).hasSize(2)
    assertThat(status.canCommit).isTrue()
    
    // Rollback
    httpClient.post("/api/project/v2superbikeshed/refactor/rollback/${transaction.id}")
}

// Test 2: Unresolved Reference Detection
@Test
fun `should detect unresolved references after refactoring`() {
    val response = httpClient.get("/api/project/v2superbikeshed/analysis/unresolved")
    
    val unresolved = response.body<UnresolvedReferences>()
    assertThat(unresolved.references).contains(
        UnresolvedRef("Series", "ColumnarExtensions.kt", line = 42)
    )
}

// Test 3: Quick Fix Engine
@Test
fun `should remove kotlin.internal annotations`() {
    val request = QuickFixRequest(
        type = "REMOVE_ANNOTATION",
        annotation = "kotlin.internal.*",
        scope = "project"
    )
    
    val response = httpClient.post("/api/project/v2superbikeshed/quickfix/apply") {
        setBody(request)
    }
    
    assertThat(response.body<QuickFixResult>().fixesApplied).isGreaterThan(0)
}
```

### Implementation Steps
1. Implement transaction-based refactoring using IntelliJ's RefactoringTransaction
2. Create unresolved reference analyzer using InspectionEngine
3. Build quick fix registry with common v2superbikeshed patterns
4. Add undo/redo support for all operations

## Phase 4: Navigation Graph & Live Code Model

### Goals
- Build usage graph for impact analysis
- Enable real-time AST updates
- Stream compilation errors
- Detect deprecated API usage

### Test Cases
```kotlin
// Test 1: Usage Graph Construction
@Test
fun `should build dependency graph for Indexed type`() {
    val response = httpClient.get("/api/project/v2superbikeshed/graph/usages/Indexed")
    
    val graph = response.body<UsageGraph>()
    assertThat(graph.nodes).hasSize(329) // Known usage count
    assertThat(graph.edges).contains(
        Edge(from = "CoreTypes.kt", to = "ColumnarExtensions.kt", type = "EXTENDS")
    )
}

// Test 2: Real-time AST Updates via WebSocket
@Test
fun `should stream AST changes during editing`() {
    val changes = mutableListOf<ASTChange>()
    
    webSocketClient.connect("/api/project/v2superbikeshed/ast/stream") {
        incoming.consumeEach { frame ->
            if (frame is Frame.Text) {
                changes.add(Json.decodeFromString<ASTChange>(frame.readText()))
            }
        }
    }
    
    // Simulate edit
    httpClient.post("/api/project/v2superbikeshed/edit") {
        setBody(EditRequest("CoreTypes.kt", line = 10, newText = "typealias Indexed<T> = Join<Int, (Int) -> T>"))
    }
    
    await().atMost(1, SECONDS).until { changes.isNotEmpty() }
    assertThat(changes.first().type).isEqualTo("TYPEALIAS_CHANGED")
}

// Test 3: Compilation Error Streaming
@Test
fun `should stream compilation errors in real-time`() {
    val errors = Channel<CompilationError>()
    
    webSocketClient.connect("/api/project/v2superbikeshed/compile/stream") {
        incoming.consumeEach { frame ->
            if (frame is Frame.Text) {
                errors.send(Json.decodeFromString(frame.readText()))
            }
        }
    }
    
    // Trigger compilation
    httpClient.post("/api/project/v2superbikeshed/compile/module/trikeshed-lib")
    
    val error = errors.receive()
    assertThat(error.message).contains("Unresolved reference: Series")
}

// Test 4: Deprecated API Detection
@Test
fun `should detect usage of deprecated Series type`() {
    val response = httpClient.get("/api/project/v2superbikeshed/analysis/deprecated")
    
    val deprecated = response.body<DeprecatedUsages>()
    assertThat(deprecated.usages).contains(
        DeprecatedUsage("Series", "Use Indexed instead", severity = "WARNING")
    )
}
```

### Implementation Steps
1. Build usage graph using IntelliJ's dependency analysis
2. Implement WebSocket server for real-time updates
3. Create PSI change listeners for AST streaming
4. Integrate with Kotlin compiler for error detection
5. Add deprecated API scanner using annotations

## Integration Plan

### Phase Dependencies
- Phase 1 is prerequisite for all others
- Phase 2 & 3 can be developed in parallel after Phase 1
- Phase 4 requires completion of Phase 2 & 3

### Testing Infrastructure
```kotlin
// Base test class for all plugin tests
abstract class IntelliJPluginTestBase : BasePlatformTestCase() {
    protected lateinit var apiServer: NexusApiServer
    protected lateinit var httpClient: HttpClient
    
    override fun setUp() {
        super.setUp()
        apiServer = NexusApiServer.getInstance()
        apiServer.start(testPort)
        
        httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
            defaultRequest {
                host = "localhost"
                port = testPort
            }
        }
    }
    
    override fun tearDown() {
        httpClient.close()
        apiServer.stop()
        super.tearDown()
    }
}
```

### CI/CD Integration
```yaml
# .github/workflows/intellij-plugin.yml
name: IntelliJ Plugin Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      
      - name: Run Plugin Tests
        run: |
          ./gradlew :nexus:intellij-plugin:test
          
      - name: Run Integration Tests
        run: |
          ./gradlew :nexus:intellij-plugin:integrationTest
          
      - name: Build Plugin
        run: |
          ./gradlew :nexus:intellij-plugin:buildPlugin
```

## Summary

This TDD approach provides a structured path to building a comprehensive IntelliJ plugin that:

1. **Leverages intellij-enumerator** for project structure discovery
2. **Enables large-scale refactoring** through SSR and batch operations
3. **Provides real-time code intelligence** via WebSocket streaming
4. **Supports the Series → Indexed migration** with safety guarantees
5. **Integrates with v2superbikeshed's build system** for continuous validation

Each phase builds upon the previous, creating a robust plugin that can handle the complexity of the v2superbikeshed codebase while maintaining IntelliJ's performance and responsiveness.