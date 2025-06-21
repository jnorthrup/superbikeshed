# K2Script Project Instructions

## CLI Architecture Excellence

The `k2script` entry point (`Kscript.kt`), `ConfigBuilder`, and `Executor` form a robust, professional-grade application core. The argument parsing, configuration loading (from files and environment variables), and command execution logic are solid and well-structured.

## AI-Powered Enhancement Priority

The recent addition of `LiteLLMClient.kt` is a game-changer. This powerful, asynchronous Large Language Model client is an "awesome" component waiting for integration.

### **Proposal: Add AI-Powered Features**
Add new command-line flag `--ai <prompt>` using `LiteLLMClient` for:

1. **Script Generation**: `kscript --ai "create a script to find all jpg files in a directory and resize them"`
2. **Code Explanation**: `kscript --ai "explain this script" < script.kts`

This leverages the awesome CLI foundation to host next-generation AI capabilities.

## Annotation Parser Modernization

**Current Issue**: `LineParser.kt` uses regex patterns to find/parse script annotations like `@file:DependsOn`. This approach is fragile, hard to extend, and cannot understand context.

### **Proposal: Replace with kotlin-entity-scanner**
- **Replace regex-based parsing** in `k2script.parser.LineParser` 
- **Use `KotlinEntityScanner`** with Inductive Graph Parsing and Chained Rules
- **Feed script content to scanner** to produce structured `ScriptAnnotation` entities
- **Benefits**: More reliable, extensible, and powerful dependency parsing

## Gradle Configuration Patterns

- Always defer to superbikeshed/ gradle for versions info and not alter them
- Our targets are common,conditionally-local-native,wasm,jvm
- Use `--console=plain --no-daemon` for gradle commands
- When creating new projects, copy trikeshed gradle as template

## Script Execution Architecture

- K2Script provides Kotlin scripting capabilities
- Integration with TrikeShed for data structures
- Support for multiplatform compilation targets

## Development Guidelines

- Script files should use `.kts` extension
- Prefer TrikeShed data structures in scripts
- Use `Series<T>` for ordered collections
- Use `Join<A,B>` for associative data
- Follow global museum preservation rules

## Dependencies Management

- Defer to parent gradle for version management
- Conditional native repository determination
- Support for dynamic dependency loading in scripts