# Type System Patterns Anchor

## Core Type System Memory

### Indexed Type Hoisting
**Pattern**: Discovered vtable pointer hoisting strategy for `Indexed<Twin<I>>` cast to `Indexed2<I,I>`
**Usage**: Use for performance-critical type conversions
**Context**: Optimized for Kotlin Multiplatform native targets

### CCEK Pattern
**Definition**: coroutinecontextelementkey
**Usage**: Coroutine context element key patterns
**Context**: Used throughout the codebase for coroutine management

### Shunned Types
**String**: Fine for keys, bad in speculative loops
**MutableList**: Use {List|Array}CowView for lazy mutable, Indexed for interfaces and returns

## Required Type Patterns

### For Mutable List Interfaces
```kotlin
// ✅ CORRECT
fun getItems(): Indexed<Item>

// ❌ WRONG
fun getItems(): MutableList<Item>
```

### For Lazy Mutable Operations
```kotlin
// ✅ CORRECT
val items = ListCowView(originalList) { /* transform */ }

// ❌ WRONG
val items = originalList.toMutableList()
```

### For Speculative Loops
```kotlin
// ✅ CORRECT
val key = "static_key"
for (item in items) {
    // Use key here
}

// ❌ WRONG
for (item in items) {
    val key = "dynamic_key" // String in speculative loop
}
```

## Type System Constraints
- **5**: Has no inherent meaning - explain when encountered
- **Indexed<T>**: Primary interface for mutable list operations
- **CowView**: Lazy mutable operations with copy-on-write semantics
- **Twin<I>**: Type system pattern for paired operations 