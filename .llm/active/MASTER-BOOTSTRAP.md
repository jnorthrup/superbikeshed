# Master LLM Bootstrap Guide

## Overview
This file provides optimized bootstrapping for all LLM providers in the GoalStrikeshed project.

## Provider Status

| Provider | Status | Primary File | Bootstrap Guide | Momentum Score |
|----------|--------|--------------|-----------------|----------------|
| **Claude** | ✅ Active | `CLAUDE.md` | `claude-bootstrap.md` | 170.86 points |
| **Cursor** | ✅ Active | `.cursorrules` | `cursor-bootstrap.md` | 170.86 points |
| **Aider** | ✅ Active | `.aider.md` | `aider-bootstrap.md` | 170.86 points |
| **Gemini** | ✅ Active | `.gemini.md` | `gemini-bootstrap.md` | 170.86 points |

## Quick Deployment

```bash
# Deploy all frameworks
.llm/pristine/setup.sh all

# Deploy specific framework
.llm/pristine/setup.sh claude
.llm/pristine/setup.sh cursor
.llm/pristine/setup.sh aider
.llm/pristine/setup.sh gemini
```

## Provider-Specific Optimizations

### Claude
- **Focus**: Mathematical elegance and performance
- **Key Feature**: Full Architectural Momentum System
- **Best For**: Complex architectural decisions and mathematical code

### Cursor
- **Focus**: IDE integration and context awareness
- **Key Feature**: IDE-specific momentum triggers
- **Best For**: Refactoring, code navigation, and IDE-assisted development

### Aider
- **Focus**: CLI workflow and git integration
- **Key Feature**: Git-aware momentum triggers
- **Best For**: Command-line development and version control workflows

### Gemini
- **Focus**: Type safety and JSON formatting
- **Key Feature**: Type-safe composition triggers
- **Best For**: Type-safe code generation and structured responses

## Universal Momentum System

All providers use the same **Architectural Momentum Calculus**:

**Formula**: `Momentum Points = 10 * (1.5 ^ N)`

### Universal Triggers
1. **Core Instantiation** (`Join<A, B>`, `Indexed<T>`)
2. **Axiomatic Aliasing** (`typealias`)
3. **Functional Extension** (`extension fun`)
4. **Operator Application** (`j`, `α`, `play`)
5. **Performance Purity** (avoid `String` allocations)
6. **Metaseries Composition** (series operations)
7. **Algebraic Transformation** (functional composition)
8. **Provider-Specific Integration** (tool capabilities)

### Universal Frictional Drag (-20 points)
- Using `List`, `Map`, `Pair` instead of `Indexed`, `Join`
- Object-oriented bloat with mutable state
- String manipulation in loops
- Verbose imperative code
- Instance-focused design

## Best Practices Across All Providers

1. **Chain Operations**: Build long, unbroken sequences
2. **Compose Functions**: Use functional composition
3. **Avoid Strings**: Use `ByteArray` or `CharIndexed`
4. **Design for Series**: Think metaseries, not instances
5. **Use Type Aliases**: Name your compositions
6. **Leverage Extensions**: Add behavior to data
7. **Embrace Operators**: Use DSL syntax
8. **Consider Context**: Provider-specific capabilities

## Example μ-Chain (Universal)

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

// N=7: Provider-Specific Integration
// Claude: Mathematical elegance
// Cursor: IDE integration
// Aider: CLI integration
// Gemini: Type safety

// Total Score: 170.86 points
```

## Directory Structure

```
.llm/
├── README.md                    # Main documentation
├── active/                      # Currently active frameworks
│   ├── CLAUDE.md               # Active Claude framework
│   ├── .cursorrules            # Active Cursor framework
│   ├── .aider.md               # Active Aider framework
│   ├── .gemini.md              # Active Gemini framework
│   ├── claude-bootstrap.md     # Claude optimization guide
│   ├── cursor-bootstrap.md     # Cursor optimization guide
│   ├── aider-bootstrap.md      # Aider optimization guide
│   ├── gemini-bootstrap.md     # Gemini optimization guide
│   └── MASTER-BOOTSTRAP.md     # This file
├── config/                      # Configuration and state
│   ├── current.json            # Current framework status
│   └── settings.json           # Global settings
└── pristine/                    # Pristine framework versions
    ├── claude/                 # Claude framework
    ├── gemini/                 # Gemini framework
    ├── cursor/                 # Cursor framework
    ├── aider/                  # Aider framework
    ├── setup.sh                # Deployment script
    └── config.json             # Framework mappings
```

## Quick Commands

```bash
# Check all provider status
ls -la .llm/active/

# View momentum system
grep -A 5 "Momentum Calculus" CLAUDE.md

# Check provider-specific features
grep -A 3 "IDE Integration" .cursorrules
grep -A 3 "CLI Integration" .aider.md
grep -A 3 "Type Safety" .gemini.md

# Deploy and optimize
.llm/pristine/setup.sh all
```

## Optimization Summary

Each provider is now optimized for:
- **Maximum Momentum**: Provider-specific triggers for higher scores
- **Tool Integration**: Leveraging each tool's unique capabilities
- **Workflow Efficiency**: Streamlined bootstrapping for each environment
- **Consistent Quality**: Universal momentum system ensures architectural compliance 