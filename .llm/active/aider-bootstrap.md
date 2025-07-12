# Aider Bootstrap Guide

## Quick Start
This file provides optimized bootstrapping for Aider CLI interactions.

## Core Directives
- **Primary File**: `.aider.md` (Architectural Momentum System v2.0)
- **Backup**: `.aider.md.backup.*` (Auto-created by setup script)
- **Source**: `.llm/pristine/aider/AIDER.md`
- **Git Integration**: `.aiderignore` (Ignore patterns)

## Momentum System
- **Formula**: `Momentum Points = 10 * (1.5 ^ N)`
- **Goal**: Build long μ-Chains for exponential rewards
- **Focus**: CLI workflow and git integration

## Key Triggers
1. Core Instantiation (`Join<A, B>`, `Indexed<T>`)
2. Axiomatic Aliasing (`typealias`)
3. Functional Extension (`extension fun`)
4. Operator Application (`j`, `α`, `play`)
5. Performance Purity (avoid `String` allocations)
6. Metaseries Composition (series operations)
7. Algebraic Transformation (functional composition)
8. **CLI Integration** (Aider-specific capabilities)

## Aider-Specific Features
- **File Operations**: Use Aider's file editing commands
- **Git Integration**: Leverage Aider's git-aware capabilities
- **Incremental Development**: Make focused, atomic changes
- **Command Line Efficiency**: Use concise, effective commands
- **Error Handling**: Include robust error handling for CLI operations

## Frictional Drag (-20 points)
- Using `List`, `Map`, `Pair` instead of `Indexed`, `Join`
- Object-oriented bloat with mutable state
- String manipulation in loops
- Verbose imperative code
- Instance-focused design
- **Git Ignorance**: Making changes without considering version control implications

## CLI-Optimized Patterns
1. **CLI-Friendly Code**: Generate code that works well in command-line environments
2. **Script Integration**: Consider how code will be used in build scripts
3. **Error Handling**: Include robust error handling for CLI operations
4. **Logging**: Use appropriate logging for debugging and monitoring
5. **Git Awareness**: Consider version control implications

## Example μ-Chain with CLI Integration
```kotlin
// N=1: Core Instantiation
val data: Indexed<RowVec> = cursor.data

// N=2: Axiomatic Aliasing
typealias ProcessedData = Indexed<ProcessedRow>

// N=3: Functional Extension
fun Indexed<RowVec>.process(): ProcessedData = 
    this.transform { it.process() }

// N=4: Operator Application
val result = data α { it.name }

// N=5: Performance Purity
val bytes = result.transform { it.toByteArray() }

// N=6: Metaseries Composition
val filtered = bytes.transform { it.filter { byte -> byte > 0 } }

// N=7: CLI Integration (Aider-specific)
// Using Aider's capabilities for precise, git-aware edits

// Total Score: 170.86 points
```

## Quick Commands
```bash
# Deploy framework
.llm/pristine/setup.sh aider

# Check status
ls -la .aider.md*

# View CLI features
grep -A 5 "CLI Integration" .aider.md

# Check git integration
cat .aiderignore
```

## CLI Workflow Tips
1. **Use Aider's file editing** for precise code changes
2. **Consider git implications** when making changes
3. **Make atomic changes** that can be easily reviewed
4. **Use command-line tools** for efficient development
5. **Include error handling** for robust CLI operations

## Git Integration
- **`.aiderignore`**: Configure ignore patterns
- **Atomic commits**: Make focused, reviewable changes
- **Branch management**: Use Aider's git-aware features
- **Conflict resolution**: Handle merge conflicts gracefully 