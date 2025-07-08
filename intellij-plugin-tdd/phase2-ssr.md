# Phase 2: Structural Search & Replace (SSR)

## Test-Driven Development Approach

### 2.1 SSR Pattern Matching Tests
```kotlin
@Test
fun `should find Series method calls`() {
    // Given: Code with Series method calls
    val code = """
        val series = Series()
        series.add(1.0)
        series.get(0)
    """.trimIndent()
    
    // When: Searching for Series method patterns
    val patterns = SSREngine.findPatterns(code, "Series.$Method$()")
    
    // Then: Should find method calls
    assertThat(patterns).hasSize(2)
    assertThat(patterns[0].methodName).isEqualTo("add")
    assertThat(patterns[1].methodName).isEqualTo("get")
}
```

### 2.2 Batch Rename Tests
```kotlin
@Test
fun `should rename Series to Indexed across project`() {
    // Given: Project with Series classes
    val project = loadProject("/path/to/v2superbikeshed")
    val renamer = BatchRenamer(project)
    
    // When: Renaming Series to Indexed
    val changes = renamer.renameAll("Series", "Indexed")
    
    // Then: Should rename all occurrences
    assertThat(changes.renamedClasses).isNotEmpty()
    assertThat(changes.renamedMethods).isNotEmpty()
    assertThat(changes.renamedVariables).isNotEmpty()
    
    // And: Should update imports
    assertThat(changes.updatedImports).isNotEmpty()
}
```

### 2.3 Pattern Replacement Tests
```kotlin
@Test
fun `should replace j( with join(`() {
    // Given: Code with j( method calls
    val code = """
        val result = j(", ", list)
        val joined = j("|", array)
    """.trimIndent()
    
    // When: Replacing j( with join(
    val result = SSREngine.replacePattern(code, "j(", "join(")
    
    // Then: Should replace all occurrences
    assertThat(result).contains("join(", "join(")
    assertThat(result).doesNotContain("j(")
}
```

## Implementation Tasks

1. **SSR Engine**
   - Pattern parser for IntelliJ SSR templates
   - AST-based pattern matching
   - Replacement engine with preview

2. **Batch Rename Service**
   - Cross-file symbol renaming
   - Import statement updates
   - Reference resolution

3. **Pattern Library**
   - Common v2superbikeshed patterns
   - Series → Indexed patterns
   - Method call patterns

## Success Criteria
- Can find Series classes and methods
- Can rename Series → Indexed across project
- Can replace j( → join( patterns
- SSR operations show preview before applying
- All references updated correctly 