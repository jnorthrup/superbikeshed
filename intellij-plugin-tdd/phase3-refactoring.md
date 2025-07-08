# Phase 3: Refactoring & Code Analysis

## Test-Driven Development Approach

### 3.1 Batch Refactoring Tests
```kotlin
@Test
fun `should execute batch refactoring operations`() {
    // Given: Multiple refactoring operations
    val operations = listOf(
        RefactoringOp.Rename("Series", "Indexed"),
        RefactoringOp.ExtractMethod("calculateAverage", 10..20),
        RefactoringOp.RemoveAnnotation("@kotlin.internal")
    )
    
    // When: Executing batch refactoring
    val result = BatchRefactoringEngine.execute(project, operations)
    
    // Then: Should complete all operations
    assertThat(result.successful).hasSize(3)
    assertThat(result.failed).isEmpty()
    assertThat(result.preview).isNotNull()
}
```

### 3.2 Code Analysis Tests
```kotlin
@Test
fun `should detect unresolved references`() {
    // Given: Code with unresolved references
    val code = """
        val buffer = ByteBuffer.allocate(1024)
        val series = Series()
    """.trimIndent()
    
    // When: Running code analysis
    val problems = CodeAnalyzer.analyze(code)
    
    // Then: Should detect missing imports
    assertThat(problems).anyMatch { 
        it.type == ProblemType.UNRESOLVED_REFERENCE && 
        it.symbol == "ByteBuffer" 
    }
}
```

### 3.3 Quick Fix Tests
```kotlin
@Test
fun `should apply missing import fix`() {
    // Given: Unresolved ByteBuffer reference
    val problem = Problem(
        type = ProblemType.UNRESOLVED_REFERENCE,
        symbol = "ByteBuffer",
        location = Location(file = "Test.kt", line = 1, column = 10)
    )
    
    // When: Applying quick fix
    val fix = QuickFixEngine.applyFix(problem, FixType.ADD_IMPORT)
    
    // Then: Should add java.nio.ByteBuffer import
    assertThat(fix.applied).isTrue()
    assertThat(fix.changes).anyMatch { 
        it.type == ChangeType.ADD_IMPORT && 
        it.content.contains("java.nio.ByteBuffer") 
    }
}
```

### 3.4 Annotation Removal Tests
```kotlin
@Test
fun `should remove kotlin.internal annotations`() {
    // Given: Code with kotlin.internal annotations
    val code = """
        @kotlin.internal.InlineOnly
        fun process() { }
        
        @kotlin.internal.LowPriorityInOverloadResolution
        fun overload() { }
    """.trimIndent()
    
    // When: Removing annotations
    val result = AnnotationRemover.removeKotlinInternal(code)
    
    // Then: Should remove all kotlin.internal annotations
    assertThat(result).doesNotContain("@kotlin.internal")
    assertThat(result).contains("fun process() { }")
    assertThat(result).contains("fun overload() { }")
}
```

## Implementation Tasks

1. **Batch Refactoring Engine**
   - Atomic refactoring operations
   - Preview generation
   - Rollback capability

2. **Code Analysis Service**
   - Problem detection
   - Quick fix suggestions
   - Inspection integration

3. **Quick Fix Engine**
   - Import addition
   - Symbol resolution
   - Auto-completion integration

4. **Annotation Cleanup**
   - kotlin.internal detection
   - Safe removal logic
   - Code formatting preservation

## Success Criteria
- Can execute multiple refactoring operations atomically
- Can detect and fix unresolved references
- Can remove kotlin.internal annotations safely
- All operations show preview before applying
- Rollback capability for failed operations 