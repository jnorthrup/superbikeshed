# Pure Functional Nexus Interactive System

The Nexus interactive system has been completely rebuilt using pure functional programming principles with algebraic data types, effect systems, and monadic composition.

## Architecture

### Core Components

1. **Algebraic Data Types** (`Algebra.kt`)
   - `Result<A>`, `Option<A>`, `Validated<E, A>` for safe error handling
   - `State<S, A>`, `Reader<R, A>`, `Writer<W, A>` monads
   - Type-safe tool specifications with `ToolDefinition<I, O>`

2. **Effects System** (`Effects.kt`)
   - Pure functional effects with `Eff<A>` algebra
   - Comprehensive operation signatures (file, network, console, time, etc.)
   - Effect handlers for different execution contexts

3. **Free Monad Interpreter** (`Interpreter.kt`)
   - Complete Free monad implementation for composable effects
   - Natural transformations between effect algebras
   - DSL for building effect programs

4. **Monadic Composition** (`Monads.kt`)
   - Tool composition operators: `compose`, `alongside`, `or`
   - Advanced combinators: `retry`, `timeout`, `cache`, `validate`
   - Pipeline builder DSL for complex workflows

5. **Effect Handlers** (`Handlers.kt`)
   - Comprehensive handler system with decorators
   - Production-ready handlers: logging, caching, retry, circuit breaker
   - Environment-specific configurations

6. **Interactive Interface** (`Interactive.kt`)
   - Command-line interface with tool execution
   - Session management with history and metrics
   - Tool discovery and composition

## Usage Examples

### Basic Tool Execution

```kotlin
// Simple tool usage
val result = PureAlgebraicTools.echo.execute("hello world")
println(result) // Result: hello world

// Composed tools
val processText = PureAlgebraicTools.echo compose PureAlgebraicTools.uppercase
val result = processText.execute("hello")
println(result) // Result: HELLO
```

### Effect Programming

```kotlin
// File processing with effects
val program = readFile("input.txt")
    .map { content -> content.uppercase() }
    .flatMap { upperContent -> 
        writeFile("output.txt", upperContent)
            .map { upperContent }
    }

// Execute with IO handler
val result = program.run(IOHandler())
```

### Free Monad Programs

```kotlin
// Interactive program using Free monad
val interactive = FreeProgramBuilder().program {
    val name = readLine("What's your name? ")
    name.flatMap { n ->
        if (n.isNotEmpty()) {
            print("Hello, $n!\n")
        } else {
            print("Hello, anonymous!\n")
        }
    }
}

// Execute the program
InterpreterRunner.runIO(interactive)
```

### Tool Composition

```kotlin
// Parallel execution
val analyzeFile = PureAlgebraicTools.readFileContent alongside 
                  (PureAlgebraicTools.readFileContent map { it.length })

// Choice between tools
val flexibleRead = PureAlgebraicTools.readFileContent or PureAlgebraicTools.echo

// Retry with validation and caching
val robustRead = PureAlgebraicTools.readFileContent
    .validate({ it.isNotBlank() }, "Path cannot be blank")
    .retry(3)
    .cache(30000) // 30 second cache
```

### Interactive Commands

When running the interactive system:

```bash
nexus> /tools                    # List all available tools
nexus> /describe echo            # Get detailed tool information
nexus> /exec echo "hello world"  # Execute a tool
nexus> /compose echo | uppercase # Compose tools
nexus> /history                  # Show execution history
nexus> /metrics                  # Show performance metrics
```

## Starting the System

### Development Mode
```kotlin
val interactive = PureFunctionalNexus.development()
runBlocking { interactive.start() }
```

### Production Mode
```kotlin
val interactive = PureFunctionalNexus.production()
runBlocking { interactive.start() }
```

### Custom Configuration
```kotlin
val handler = handler {
    base(IOHandler())
    withLogging()
    withRetry(3)
    withMetrics()
}
val interactive = NexusInteractive(handler)
runBlocking { interactive.start() }
```

## Key Benefits

1. **Type Safety**: All tool inputs/outputs are statically typed
2. **Composability**: Tools can be easily combined using monadic operators
3. **Testability**: Pure functional approach enables comprehensive testing
4. **Effect Management**: Clear separation between pure logic and side effects
5. **Error Handling**: Algebraic error types for safe failure handling
6. **Performance**: Built-in caching, retry, and circuit breaker patterns
7. **Observability**: Comprehensive metrics and logging

## Architecture Revamps

This is the **4th architectural revamp** of the Nexus system, each improving on the previous:

1. **Initial**: Basic tool calling with mutable state
2. **Actor-based**: Message passing with reactive streams 
3. **Capability-based**: Security model with metadata
4. **Pure Functional**: Current implementation with algebraic types and monadic composition

The pure functional approach provides the cleanest, most composable, and most maintainable architecture while enabling sophisticated tool composition and effect management.

## Implementation Status

✅ **Completed Components:**
- Core algebraic data types and monads
- Comprehensive effects system with handlers
- Free monad interpreter with DSL
- Monadic composition operators and combinators
- Interactive interface with command parsing
- JVM platform integration
- Effect handler decorators (logging, caching, retry, etc.)

The system is ready for use and provides a solid foundation for building sophisticated interactive LLM applications with composable tools.