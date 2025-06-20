# Kotlin Entity Scanner

Graph-based Kotlin code analysis with hierarchical token classification and inductive refinement.

## Overview

The Kotlin Entity Scanner is a standalone module that provides sophisticated code analysis capabilities using:

- **Hierarchical Token Classification Stairway** - Zero-cost inline classes that progressively add semantic meaning
- **Inductive Graph Refinement** - Forward chaining logic that improves parsing accuracy through evidence accumulation
- **TrikeShed Type System Compliance** - Full use of `Series<T>`, `Join<A,B>`, α transforms, and taxonomical type aliases

## Architecture

### Token Classification Stairway

The scanner uses a 5-level hierarchical classification system:

1. **Level 1: Raw Character Classification** - `RawChar`, `CharClass`, `CharPosition`
2. **Level 2: Lexical Token Classification** - `LexicalToken`, `TokenType`, `TokenBounds`
3. **Level 3: Syntactic Classification** - `SyntaxToken`, `ScopeLevel`, `VisibilityToken`
4. **Level 4: Semantic Entity Classification** - `EntityToken`, `RoleToken`, `ContextToken`
5. **Level 5: Graph Node Classification** - `GraphNodeToken`, `DependencyToken`, `ConfidenceToken`

Each level uses `@JvmInline value class` for zero-cost abstractions and composes with `Join<A,B>` patterns.

### Inductive Graph Refinement

The parser uses evidence-based learning to improve accuracy:

- **Evidence Types**: Syntax patterns, semantic context, historical success, cross-references
- **Forward Chaining**: Rules propagate evidence through the parse graph
- **Confidence Accumulation**: Parse states gain confidence through multiple evidence sources
- **Deductive Reduction**: N candidate states reduce to 1 or 0 valid interpretations

## API Usage

### Basic Entity Extraction

```kotlin
val kotlinCode = """
    @JvmInline
    value class UserId(val id: String)
    
    data class User(val id: UserId, val name: String)
    
    fun findUser(id: UserId): User? = null
"""

// Extract different entity types
val classes = kotlinCode.extractClasses()
val functions = kotlinCode.extractFunctions()
val imports = kotlinCode.extractImports()
val dependencies = kotlinCode.extractDependencies()
```

### Advanced Analysis

```kotlin
// Build call graph
val callGraph = kotlinCode.buildCallGraph()

// Create entity index for IDE integration
val entityIndex = kotlinCode.buildEntityIndex()

// Full analysis with inductive refinement
val (graphNodes, refinements) = KotlinEntityScanner.scan(
    kotlinCode, 
    ScanConfig.FULL_ANALYSIS
)
```

### K2Script Integration

```kotlin
// Extract k2script-specific metadata
val (annotations, dependencies) = kotlinCode.extractK2ScriptMetadata()

// Generate data for spacegraph visualization
val spaceGraphData = kotlinCode.generateSpaceGraphData()
```

### Inductive Parsing

```kotlin
// Parse with learning and refinement
val (graphNodes, refinements) = kotlinCode.parseInductively()

// Show accuracy improvements
refinements.materialize().forEach { refinement ->
    val (position, delta) = refinement
    println("Position ${position.position}: accuracy improved by ${delta.delta}")
}
```

## TrikeShed Compliance

The module fully adheres to TrikeShed patterns:

- **Series<T>** - All collections use `Series<T>` instead of `List<T>`
- **Join<A,B>** - All compositions use `Join<A,B>` with `j` operator instead of `Pair<A,B>`
- **α Transforms** - All transformations use `series.α { transform }` as the ONLY transformation operator
- **Play Materialization** - Use `series.play` only for final gateway to stdlib collections
- **Taxonomical Type Aliases** - All types have descriptive semantic names
- **Zero-Cost Abstractions** - Extensive use of `@JvmInline value class`

## Performance Features

- **Zero-Cost Hierarchical Classification** - Inline classes compile to primitives
- **Packed Data Structures** - Efficient bit-packing for metadata (e.g., `TokenBounds`, `EntityMetadata`)
- **Lazy Evaluation** - Series operations are computed on-demand
- **Incremental Updates** - Support for real-time parsing as code changes
- **Graph-Based Error Recovery** - Graceful handling of incomplete/malformed code

## Integration Points

### K2Script
- Extract `@DependsOn` annotations and Maven coordinates
- Parse `@file:Import` statements
- Generate dependency graphs for project analysis
- Support shebang detection

### SpaceGraph
- Generate graph nodes with confidence scores
- Create dependency relationships for visualization
- Export entity metadata for interactive exploration

### IDE Features
- Real-time entity indexing
- Call graph generation
- Dependency analysis
- Incremental parsing for live updates

## Configuration

```kotlin
// Scan configurations
val config = ScanConfig.FULL_ANALYSIS  // All features enabled
val config = ScanConfig.FAST_SCAN      // Basic parsing only
val config = ScanConfig.DEFAULT        // Balanced approach

// Custom configuration
val config = ScanConfig(
    flags = ScanConfig.INDUCTIVE_REFINEMENT or 
            ScanConfig.DEPENDENCY_ANALYSIS
)
```

## Dependencies

The module has minimal dependencies to maintain performance and modularity:

- **Trikeshed Core** - For `Series<T>`, `Join<A,B>`, and core type system
- **Kotlin Stdlib** - Standard library only
- **No external parsing libraries** - Self-contained implementation

## Build

```bash
# Build the module
./gradlew :kotlin-entity-scanner:build

# Run tests
./gradlew :kotlin-entity-scanner:test

# Build for specific platforms
./gradlew :kotlin-entity-scanner:jvmTest
./gradlew :kotlin-entity-scanner:wasmJsTest
```

## Example: Complete Analysis

```kotlin
fun analyzeKotlinFile() {
    val source = File("MyClass.kt").readText()
    
    // Comprehensive analysis
    val classes = source.extractClasses()
    val functions = source.extractFunctions()
    val callGraph = source.buildCallGraph()
    val entityIndex = source.buildEntityIndex()
    
    // K2Script integration
    val (annotations, deps) = source.extractK2ScriptMetadata()
    val graphData = source.generateSpaceGraphData()
    
    // Inductive refinement
    val (nodes, refinements) = source.parseInductively()
    
    println("Analysis complete:")
    println("- Classes: ${classes.size}")
    println("- Functions: ${functions.size}")
    println("- Call graph edges: ${callGraph.size}")
    println("- Entity index entries: ${entityIndex.size}")
    println("- Graph nodes: ${nodes.size}")
    println("- Accuracy refinements: ${refinements.size}")
}
```

## Future Enhancements

- **Language Server Protocol** integration
- **IntelliJ PSI** compatibility layer
- **Advanced pattern learning** from large codebases
- **Cross-file dependency analysis**
- **Semantic type inference** integration
- **Custom rule DSL** for domain-specific analysis

## License

Licensed under the same terms as the TrikeShed project.