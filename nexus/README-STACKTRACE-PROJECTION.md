# Stacktrace Context Projection: The Double Dispatch Mechanism

## The Projection Mechanism

Stacktrace context projection is a technique for extracting multi-layered contextual information from error tuples, creating a navigable map of error sites and their surrounding code contexts.

## Error Tuple Structure

Each error in a stacktrace forms a tuple:
```
(file_path, line_number, error_type, error_message, context_window)
```

## Double Dispatch Advantage

The double dispatch pattern provides two key benefits:

### 1. Type-Specific Context Extraction
```kotlin
interface ErrorProjector {
    fun project(error: CompilationError): ProjectedContext
}

// First dispatch: Error type selects projector
sealed class CompilationError {
    abstract fun accept(projector: ErrorProjector): ProjectedContext
}

// Second dispatch: Projector specializes by error characteristics  
class PublicAPIError : CompilationError() {
    override fun accept(projector: ErrorProjector) = 
        projector.project(this) // Specialized handling
}
```

### 2. Layered Context Accumulation

The projection mechanism builds context layers:

```
Layer 0: Error site (immediate line)
Layer 1: Function scope (±10 lines)
Layer 2: Class/module scope (±50 lines)  
Layer 3: Related files (imports/dependencies)
Layer 4: Module boundaries (build files)
```

## Projection Algorithm

```python
def project_error_context(error_tuple, depth=5):
    """
    Projects error context through multiple dispatch layers
    """
    file_path, line_num, error_type, msg, _ = error_tuple
    
    projections = []
    
    # First dispatch: Error type determines projection strategy
    strategy = select_projection_strategy(error_type)
    
    # Second dispatch: Strategy determines context extraction
    for layer in range(depth):
        context = strategy.extract_at_layer(
            file_path, 
            line_num,
            layer,
            radius=calculate_radius(layer)
        )
        projections.append(context)
    
    return compose_projections(projections)
```

## Benefits of Stacktrace Projection

### 1. **Systematic Error Navigation**
- Each error becomes a navigable waypoint
- Context windows provide surrounding code understanding
- Related errors cluster through shared context

### 2. **Pattern Recognition**
- Similar error contexts reveal systemic issues
- Repeated patterns indicate architectural problems
- Context overlap shows coupling points

### 3. **Efficient Debugging**
- Pre-computed context reduces file switching
- Layered views from narrow to broad scope
- Related code visible without manual searching

### 4. **Automated Fix Generation**
- Context provides enough information for fixes
- Pattern matching against known solutions
- Type information from surrounding code

## Implementation in v2superbikeshed

The stacktrace projection mechanism helped identify:

1. **Public API inline function errors** - Context showed private function calls
2. **Generic type arithmetic issues** - Surrounding code revealed type constraints
3. **Missing imports** - File context exposed unresolved references
4. **Syntax errors** - Line context pinpointed exact character positions

## Double Dispatch in Practice

```kotlin
// Error visitor pattern
interface ErrorVisitor<T> {
    fun visit(publicAPIError: PublicAPIError): T
    fun visit(typeInferenceError: TypeInferenceError): T
    fun visit(syntaxError: SyntaxError): T
}

// Context projector using visitor
class ContextProjector : ErrorVisitor<ProjectedContext> {
    override fun visit(publicAPIError: PublicAPIError) = 
        ProjectedContext(
            immediateContext = extractLines(publicAPIError.line, 3),
            functionContext = extractFunction(publicAPIError.line),
            visibilityChain = traceVisibility(publicAPIError.function)
        )
    
    override fun visit(typeInferenceError: TypeInferenceError) =
        ProjectedContext(
            immediateContext = extractLines(typeInferenceError.line, 5),
            typeContext = extractTypeDeclarations(typeInferenceError.file),
            genericBounds = findGenericConstraints(typeInferenceError.type)
        )
    
    override fun visit(syntaxError: SyntaxError) =
        ProjectedContext(
            immediateContext = extractLines(syntaxError.line, 1),
            charContext = extractChars(syntaxError.line, syntaxError.column),
            precedingTokens = extractTokens(syntaxError.line - 1)
        )
}
```

## The Gain from Double Dispatch

1. **Type Safety** - Compiler ensures all error types handled
2. **Extensibility** - New error types don't break existing projectors
3. **Specialization** - Each error type gets optimal context extraction
4. **Composability** - Projectors can be combined and layered
5. **Performance** - Static dispatch eliminates runtime type checking

The double dispatch mechanism transforms linear error lists into rich, navigable context graphs, enabling both human understanding and automated remediation.