# Kotlin Entity Scanner - Consolidated Documentation

## Project Overview
**Status**: Active development  
**Type**: Gradle module for Kotlin code analysis  
**Purpose**: Graph-based Kotlin code analysis with hierarchical token classification and inductive refinement

## Core Architecture

### Hierarchical Token Classification (5 Levels)
1. **Raw Character Classification** - `RawChar`, `CharClass`, `CharPosition`
2. **Lexical Token Classification** - `LexicalToken`, `TokenType`, `TokenBounds`
3. **Syntactic Classification** - `SyntaxToken`, `ScopeLevel`, `VisibilityToken`
4. **Semantic Entity Classification** - `EntityToken`, `RoleToken`, `ContextToken`
5. **Graph Node Classification** - `GraphNodeToken`, `DependencyToken`, `ConfidenceToken`

### Key Features
- **Inductive Graph Refinement** - Forward chaining logic with evidence accumulation
- **TrikeShed Type System Compliance** - Full use of `Series<T>`, `Join<A,B>`, α transforms
- **Zero-Cost Abstractions** - `@JvmInline value class` for performance
- **K2Script Integration** - Parse `@file:` annotations and dependencies

## Implementation Status

### Completed ✅
- Documentation consolidation (June 24, 2025)
- Project structure and build configuration
- Architecture design and API specification

### In Progress 🔄
- Core hierarchical token classification system
- Entity extraction (classes, functions, imports)
- K2Script integration features

### Pending ⏳
- Inductive graph refinement algorithm
- Evidence accumulation and forward chaining
- Performance optimizations
- Comprehensive test suite
- Language Server Protocol integration

## API Usage Examples

### Basic Entity Extraction
```kotlin
val classes = kotlinCode.extractClasses()
val functions = kotlinCode.extractFunctions()
val imports = kotlinCode.extractImports()
val dependencies = kotlinCode.extractDependencies()
```

### Advanced Analysis
```kotlin
val callGraph = kotlinCode.buildCallGraph()
val entityIndex = kotlinCode.buildEntityIndex()
val (graphNodes, refinements) = kotlinCode.parseInductively()
```

### K2Script Integration
```kotlin
val (annotations, dependencies) = kotlinCode.extractK2ScriptMetadata()
val spaceGraphData = kotlinCode.generateSpaceGraphData()
```

## TrikeShed Compliance
- **Series<T>** instead of `List<T>`
- **Join<A,B>** with `j` operator instead of `Pair<A,B>`
- **α Transforms** as the ONLY transformation operator
- **Play Materialization** only for final gateway to stdlib
- **Taxonomical Type Aliases** for semantic clarity

## Performance Features
- Zero-cost hierarchical classification
- Packed data structures for metadata
- Lazy evaluation for Series operations
- Incremental updates for real-time parsing
- Graph-based error recovery

## Dependencies
- **Trikeshed Core** - For type system
- **Kotlin Stdlib** - Standard library only
- **No external parsing libraries** - Self-contained

## Build Commands
```bash
./gradlew :kotlin-entity-scanner:build
./gradlew :kotlin-entity-scanner:test
./gradlew :kotlin-entity-scanner:jvmTest
./gradlew :kotlin-entity-scanner:wasmJsTest
```

## Future Enhancements
- Language Server Protocol integration
- IntelliJ PSI compatibility layer
- Advanced pattern learning
- Cross-file dependency analysis
- Semantic type inference
- Custom rule DSL

---
*Last Updated: June 24, 2025*  
*Documentation Status: Consolidated* 