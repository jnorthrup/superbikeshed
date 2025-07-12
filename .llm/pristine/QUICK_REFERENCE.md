# GoalStrikeshed LLM Framework Quick Reference

## 🚀 Quick Setup

```bash
# Deploy specific framework
.llm/pristine/setup.sh claude    # For Claude
.llm/pristine/setup.sh gemini    # For Gemini CLI
.llm/pristine/setup.sh cursor    # For Cursor IDE
.llm/pristine/setup.sh aider     # For Aider CLI

# Deploy all frameworks
.llm/pristine/setup.sh all
```

## 📁 File Mappings

| Framework | Source | Target | Purpose |
|-----------|--------|--------|---------|
| Claude | `.llm/pristine/claude/CLAUDE.md` | `CLAUDE.md` | Full Architectural Momentum System |
| Gemini | `.llm/pristine/gemini/GEMINI.md` | `.gemini.md` | Enhanced type safety & JSON formatting |
| Cursor | `.llm/pristine/cursor/CURSOR.md` | `.cursorrules` | IDE integration & context awareness |
| Aider | `.llm/pristine/aider/AIDER.md` | `.aider.md` | CLI workflow & git integration |

## 🎯 Momentum Calculus

**Formula:** `Momentum Points = 10 * (1.5 ^ N)`

| Chain Length (N) | Points | Example |
|------------------|--------|---------|
| 1 | 15.0 | Core instantiation |
| 2 | 22.5 | Core + aliasing |
| 3 | 33.75 | Core + aliasing + extension |
| 4 | 50.625 | Core + aliasing + extension + operator |
| 5 | 75.94 | Core + aliasing + extension + operator + purity |
| 6 | 113.9 | Core + aliasing + extension + operator + purity + metaseries |

## ⚡ Momentum Triggers

1. **Core Instantiation** - Using `Join<A, B>` or `Indexed<T>`
2. **Axiomatic Aliasing** - Creating `typealias` for compositions
3. **Functional Extension** - Defining extension functions
4. **Operator Application** - Using `j`, `α`, `play` operators
5. **Performance Purity** - Avoiding String allocations
6. **Metaseries Composition** - Operating on series, not instances
7. **Algebraic Transformation** - Functional composition
8. **Tool Integration** - Framework-specific capabilities

## 🛑 Frictional Drag (-20 points)

- Using `List`, `Map`, `Pair` instead of `Indexed`, `Join`
- Object-oriented bloat with mutable state
- String manipulation in loops
- Verbose imperative code
- Instance-focused design
- Context ignorance

## 🏗️ Core Axioms

1. **Core Composition** - All data structures are `Join`, collections are `Indexed`
2. **Functional Extension** - Data is inert, behavior via extensions
3. **Type Aliasing** - Name compositions of truth
4. **Performance Purity** - String Performance War is always in effect
5. **Declarative Structure** - `Indexed<T>` is `(Int) -> T`
6. **Elegant Syntax** - DSL operators are canonical
7. **Metaseries Design** - Design for series operations
8. **Transform Composition** - Operations compose algebraically

## 🔧 Framework-Specific Features

### Claude
- Full momentum system implementation
- Mathematical elegance focus
- Comprehensive constraints

### Gemini
- Type safety emphasis
- JSON response formatting
- Mermaid diagram integration
- Type-safe composition trigger

### Cursor
- IDE integration capabilities
- Context awareness
- Refactoring-friendly patterns
- IDE integration trigger

### Aider
- CLI workflow optimization
- Git integration
- Command-line efficiency
- CLI integration trigger

## 📊 Example μ-Chain

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

// Total Score: 113.9 points
```

## 🎯 Best Practices

1. **Chain Operations** - Build long, unbroken sequences
2. **Compose Functions** - Use functional composition
3. **Avoid Strings** - Use `ByteArray` or `CharIndexed`
4. **Design for Series** - Think metaseries, not instances
5. **Use Type Aliases** - Name your compositions
6. **Leverage Extensions** - Add behavior to data
7. **Embrace Operators** - Use DSL syntax
8. **Consider Context** - Framework-specific capabilities 