# IntelliJ Plugin Integration Plan

## Overview
This plan integrates the 4-phase TDD approach with the intellij-project-enumerator to create a comprehensive IntelliJ plugin for v2superbikeshed development.

## Phase Integration Strategy

### Phase 1 → Phase 2 Integration
```kotlin
// Enumerator provides project structure for SSR operations
val projectDetails = enumerator.enumerate("/path/to/v2superbikeshed")
val ssrEngine = SSREngine(projectDetails.modules)

// Use project structure to scope SSR operations
val seriesPatterns = ssrEngine.findPatterns(
    modules = projectDetails.modules,
    pattern = "Series.$Method$()"
)
```

### Phase 2 → Phase 3 Integration
```kotlin
// SSR results feed into refactoring operations
val renameOps = seriesPatterns.map { pattern ->
    RefactoringOp.Rename(
        from = pattern.symbol,
        to = pattern.symbol.replace("Series", "Indexed")
    )
}

// Batch refactoring with SSR preview
val batchResult = BatchRefactoringEngine.execute(
    project = projectDetails,
    operations = renameOps
)
```

### Phase 3 → Phase 4 Integration
```kotlin
// Refactoring changes trigger live updates
val liveAST = LiveASTService(projectDetails)
liveAST.onRefactoringComplete { changes ->
    // Update navigation graph
    val graph = UsageGraphBuilder.rebuild(changes)
    
    // Stream impact analysis
    val impact = ImpactAnalyzer.analyzeImpact(changes)
    impactStream.emit(impact)
}
```

## Enumerator Integration Points

### 1. Project Structure Discovery
```kotlin
class EnumeratorIntegration {
    fun discoverProjectStructure(projectPath: String): ProjectStructure {
        val details = enumerator.enumerate(projectPath)
        return ProjectStructure(
            modules = details.modules.map { it.toModule() },
            dependencies = details.modules.flatMap { it.dependencies },
            buildSystem = details.buildSystemInfo
        )
    }
}
```

### 2. Module-Aware Operations
```kotlin
class ModuleAwareSSR {
    fun findPatternsAcrossModules(
        projectDetails: IntelliJProjectDetails,
        pattern: String
    ): List<PatternMatch> {
        return projectDetails.modules.flatMap { module ->
            ssrEngine.findPatternsInModule(module, pattern)
        }
    }
}
```

### 3. Dependency-Aware Refactoring
```kotlin
class DependencyAwareRefactoring {
    fun safeRename(
        projectDetails: IntelliJProjectDetails,
        from: String,
        to: String
    ): RefactoringResult {
        // Check dependencies before renaming
        val affectedDeps = findAffectedDependencies(projectDetails, from)
        
        return if (affectedDeps.isEmpty()) {
            performRename(from, to)
        } else {
            RefactoringResult.withWarnings(affectedDeps)
        }
    }
}
```

## API Endpoints (REST + WebSocket)

### REST API
```kotlin
@RestController
class IntelliJPluginAPI {
    
    @GetMapping("/api/psi/find")
    fun findSymbols(
        @RequestParam type: String,
        @RequestParam pattern: String
    ): List<Symbol>
    
    @PostMapping("/api/ssr/search")
    fun searchPatterns(@RequestBody request: SSRRequest): List<PatternMatch>
    
    @PostMapping("/api/refactor/batch")
    fun batchRefactor(@RequestBody operations: List<RefactoringOp>): RefactoringResult
    
    @GetMapping("/api/graph/usages")
    fun getUsages(@RequestParam symbol: String): UsageGraph
    
    @GetMapping("/api/inspect")
    fun runInspection(@RequestParam inspection: String): List<Problem>
}
```

### WebSocket API
```kotlin
@Controller
class LiveCodeAPI {
    
    @MessageMapping("/api/live/ast")
    @SendTo("/topic/ast-updates")
    fun subscribeToASTUpdates(): ASTUpdate
    
    @MessageMapping("/api/live/errors")
    @SendTo("/topic/compilation-errors")
    fun subscribeToErrors(): CompilationError
}
```

## Priority Implementation for v2superbikeshed

### 1. Series → Indexed Migration
```kotlin
// Phase 1: Discover all Series usages
val projectDetails = enumerator.enumerate("/path/to/v2superbikeshed")
val seriesUsages = findSymbols("Series", projectDetails)

// Phase 2: SSR for Series patterns
val patterns = ssrEngine.findPatterns(projectDetails, "Series.*")

// Phase 3: Batch rename with preview
val renameOps = seriesUsages.map { RefactoringOp.Rename(it, "Indexed") }
val result = BatchRefactoringEngine.execute(projectDetails, renameOps)

// Phase 4: Live monitoring
liveAST.subscribe { update ->
    if (update.symbol == "Series") {
        notifyMigrationProgress(update)
    }
}
```

### 2. Unresolved Reference Fixes
```kotlin
// Use enumerator to find all modules
val modules = projectDetails.modules

// Analyze each module for unresolved references
val problems = modules.flatMap { module ->
    CodeAnalyzer.analyzeModule(module)
}

// Apply quick fixes
problems.filter { it.type == ProblemType.UNRESOLVED_REFERENCE }
    .forEach { problem ->
        QuickFixEngine.applyFix(problem, FixType.ADD_IMPORT)
    }
```

### 3. Annotation Cleanup
```kotlin
// Find all kotlin.internal annotations
val annotationPatterns = ssrEngine.findPatterns(
    projectDetails,
    "@kotlin.internal.*"
)

// Remove annotations safely
annotationPatterns.forEach { pattern ->
    AnnotationRemover.removeAnnotation(pattern)
}
```

## Success Metrics

1. **Phase 1**: Plugin loads and enumerates v2superbikeshed project
2. **Phase 2**: Can find and preview Series → Indexed changes
3. **Phase 3**: Can fix unresolved references and remove annotations
4. **Phase 4**: Live monitoring of all changes with impact analysis

## Testing Strategy

- **Unit Tests**: Each phase component tested in isolation
- **Integration Tests**: Phase interactions tested together
- **End-to-End Tests**: Complete workflows with v2superbikeshed project
- **Performance Tests**: Large project handling and real-time updates 