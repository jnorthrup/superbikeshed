# Compositional Context Patterns - Refined Approach

## Core Insight

The experimental "chord fingerpainting" with `MetaSeries` and monolithic `CcekContext` can be distilled into:
1. A generic `HandlerRegistry` that lives in the coroutine context
2. Granular, single-purpose context elements
3. Type-safe compositional assembly using Kotlin's built-in features

## The Core Tool: Generic HandlerRegistry

```kotlin
import kotlin.coroutines.CoroutineContext

/**
 * A generic, compositional handler registry that lives in the CoroutineContext.
 * It maps a key of type K to a handler (a suspend function) of type V.
 * This replaces the verbose, custom MetaSeries "chord sheets".
 *
 * @param K the type of the key used for handler lookup (e.g., EventType, ProtocolType, String).
 * @param V the functional type of the handler itself (e.g., suspend (Request) -> Response).
 */
class HandlerRegistry<K, V : Function<*>>(
    private val handlers: Map<K, V>
) : CoroutineContext.Element {

    override val key: CoroutineContext.Key<*> = Key

    /**
     * Retrieves a handler for the given key.
     */
    fun get(key: K): V? = handlers[key]

    /**
     * Composition operator. When adding a new registry to a context,
     * its handlers overlay the existing ones.
     */
    operator fun plus(other: HandlerRegistry<K, V>): HandlerRegistry<K, V> {
        return HandlerRegistry(handlers + other.handlers)
    }

    companion object Key : CoroutineContext.Key<HandlerRegistry<*, *>>
}

/**
 * A DSL helper to add or update handlers in a coroutine's context.
 */
suspend fun <K, V : Function<*>> withHandlers(
    vararg newHandlers: Pair<K, V>,
    block: suspend CoroutineScope.() -> Unit
) {
    val existingRegistry = coroutineContext[HandlerRegistry.Key] as? HandlerRegistry<K, V>
    val newRegistry = HandlerRegistry(newHandlers.toMap())
    
    val finalRegistry = existingRegistry?.plus(newRegistry) ?: newRegistry
    
    withContext(finalRegistry, block)
}

/**
 * Extension to easily access the handler registry from any coroutine context.
 */
inline val <K, V : Function<*>> CoroutineContext.handlerRegistry: HandlerRegistry<K, V>?
    get() = this[HandlerRegistry.Key] as? HandlerRegistry<K, V>
```

## Granular Context Elements

Instead of monolithic contexts, define small, focused elements:

```kotlin
import borg.trikeshed.lib.Indexed
import kotlin.coroutines.CoroutineContext

// === Execution Control Elements ===

data class ExecutionId(val id: String) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<ExecutionId>
}

data class ExecutionPhase(val phase: String) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<ExecutionPhase>
}

// === Knowledge & Schema Elements ===

data class TransformationRules(val rules: Indexed<TransformationRule>) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<TransformationRules>
}

data class ValidationConstraints(val constraints: Indexed<Constraint>) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<ValidationConstraints>
}

// === I/O & Protocol Elements ===

enum class IoCapability { URING, KQUEUE, NIO, EPOLL, POSIX_FD }
data class IoPreference(val capability: IoCapability) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<IoPreference>
}

// === Easy-Access Extensions ===

val CoroutineContext.executionId: String? get() = this[ExecutionId]?.id
val CoroutineContext.phase: String? get() = this[ExecutionPhase]?.phase
val CoroutineContext.rules: Indexed<TransformationRule>? get() = this[TransformationRules]?.rules
val CoroutineContext.ioCapability: IoCapability? get() = this[IoPreference]?.capability
```

## Real-World Service Examples

### 1. Protocol Router with Dynamic Dispatch

```kotlin
// Protocol handlers
typealias ProtocolHandler = suspend (ByteArray) -> ByteArray

val quicHandler: ProtocolHandler = { data ->
    // QUIC-specific processing
    "QUIC: ${data.size} bytes processed".toByteArray()
}

val httpHandler: ProtocolHandler = { data ->
    // HTTP-specific processing
    "HTTP: ${data.size} bytes processed".toByteArray()
}

val sshHandler: ProtocolHandler = { data ->
    // SSH-specific processing
    "SSH: ${data.size} bytes processed".toByteArray()
}

// Protocol detection and routing
suspend fun routeProtocol(data: ByteArray) {
    val protocol = detectProtocol(data) // QUIC, HTTP, SSH, etc.
    
    val registry = coroutineContext.handlerRegistry<String, ProtocolHandler>()
    val handler = registry?.get(protocol) ?: error("No handler for $protocol")
    
    val result = handler(data)
    println("Routed via $protocol: ${result.size} bytes")
}

// Usage
suspend fun main() {
    val protocolHandlers = mapOf(
        "QUIC" to quicHandler,
        "HTTP" to httpHandler,
        "SSH" to sshHandler
    )
    
    withContext(HandlerRegistry(protocolHandlers) + IoPreference(IoCapability.URING)) {
        routeProtocol("test data".toByteArray())
    }
}
```

### 2. Database Operations with Context-Driven Strategies

```kotlin
// Database operation types
sealed class DbOperation
data class Query(val sql: String) : DbOperation()
data class Transaction(val operations: List<DbOperation>) : DbOperation()

typealias DbHandler = suspend (DbOperation) -> Result<Any>

// Different database backends
val postgresHandler: DbHandler = { op ->
    when (op) {
        is Query -> Result.success("PostgreSQL: ${op.sql}")
        is Transaction -> Result.success("PostgreSQL: ${op.operations.size} ops")
    }
}

val cassandraHandler: DbHandler = { op ->
    when (op) {
        is Query -> Result.success("Cassandra: ${op.sql}")
        is Transaction -> Result.failure(Exception("Cassandra doesn't support transactions"))
    }
}

// Context-aware database executor
class DatabaseExecutor {
    suspend fun execute(op: DbOperation): Result<Any> {
        val dbType = coroutineContext[DatabaseType]?.type ?: "postgres"
        val registry = coroutineContext.handlerRegistry<String, DbHandler>()
        
        val handler = registry?.get(dbType) ?: error("No handler for $dbType")
        
        return withContext(ExecutionPhase("db-operation")) {
            handler(op)
        }
    }
}

// Database type context element
data class DatabaseType(val type: String) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<DatabaseType>
}
```

### 3. IO Operations with Capability-Based Dispatch

```kotlin
// IO operation abstraction
data class ReadOperation(val fd: Int, val size: Int)
typealias IoHandler = suspend (ReadOperation) -> ByteArray

// Platform-specific implementations
val uringHandler: IoHandler = { op ->
    // io_uring implementation
    suspendCoroutineUninterceptedOrReturn { cont ->
        // Direct io_uring submission
        COROUTINE_SUSPENDED
    }
}

val nioHandler: IoHandler = { op ->
    // Java NIO implementation
    withContext(Dispatchers.IO) {
        // NIO channel read
        ByteArray(op.size)
    }
}

val posixHandler: IoHandler = { op ->
    // POSIX read implementation
    withContext(Dispatchers.IO) {
        // Direct POSIX read
        ByteArray(op.size)
    }
}

// Capability-aware IO executor
suspend fun performRead(fd: Int, size: Int): ByteArray {
    val capability = coroutineContext.ioCapability ?: IoCapability.NIO
    val registry = coroutineContext.handlerRegistry<IoCapability, IoHandler>()
    
    val handler = registry?.get(capability) ?: nioHandler
    
    return handler(ReadOperation(fd, size))
}

// Usage with different IO backends
suspend fun demonstrateIO() {
    val ioHandlers = mapOf(
        IoCapability.URING to uringHandler,
        IoCapability.NIO to nioHandler,
        IoCapability.POSIX_FD to posixHandler
    )
    
    // Use io_uring on Linux
    withContext(HandlerRegistry(ioHandlers) + IoPreference(IoCapability.URING)) {
        val data = performRead(fd = 3, size = 4096)
        println("Read ${data.size} bytes using io_uring")
    }
    
    // Fallback to NIO on other platforms
    withContext(HandlerRegistry(ioHandlers) + IoPreference(IoCapability.NIO)) {
        val data = performRead(fd = 3, size = 4096)
        println("Read ${data.size} bytes using NIO")
    }
}
```

### 4. Composable Middleware Pipeline

```kotlin
// Middleware function type
typealias Middleware<T> = suspend (T, suspend (T) -> T) -> T

// Create a middleware registry
class MiddlewareRegistry<T>(
    private val middlewares: List<Middleware<T>>
) : CoroutineContext.Element {
    override val key = Key
    
    suspend fun process(input: T, core: suspend (T) -> T): T {
        return middlewares.foldRight(core) { middleware, next ->
            { value -> middleware(value, next) }
        }(input)
    }
    
    companion object Key : CoroutineContext.Key<MiddlewareRegistry<*>>
}

// Example middlewares
val loggingMiddleware: Middleware<String> = { input, next ->
    println("Before: $input")
    val result = next(input)
    println("After: $result")
    result
}

val cachingMiddleware: Middleware<String> = { input, next ->
    val cached = coroutineContext[CacheContext]?.get(input)
    if (cached != null) {
        println("Cache hit: $cached")
        cached
    } else {
        val result = next(input)
        coroutineContext[CacheContext]?.put(input, result)
        result
    }
}

// Cache context
data class CacheContext(
    private val cache: MutableMap<String, String> = mutableMapOf()
) : CoroutineContext.Element {
    override val key = Key
    fun get(key: String) = cache[key]
    fun put(key: String, value: String) { cache[key] = value }
    companion object Key : CoroutineContext.Key<CacheContext>
}

// Usage
suspend fun processWithMiddleware(input: String): String {
    val registry = coroutineContext[MiddlewareRegistry.Key] as? MiddlewareRegistry<String>
    
    return registry?.process(input) { it.uppercase() } ?: input.uppercase()
}
```

## Key Benefits

1. **Conciseness**: Single `HandlerRegistry` replaces verbose MetaSeries definitions
2. **Type Safety**: Generic constraints ensure correct handler signatures
3. **Compositionality**: Context elements compose naturally with `+`
4. **Single Responsibility**: Each element has one clear purpose
5. **Testability**: Easy to test individual context elements in isolation
6. **Performance**: No reflection, direct map lookups
7. **Flexibility**: Handlers can be added/overlaid dynamically

## Design Principles

- **Granular Over Monolithic**: Many small context elements instead of one large one
- **Type-Safe Dispatch**: Use generics to ensure compile-time safety
- **Composition Over Configuration**: Build behavior by combining context elements
- **Explicit Over Implicit**: Clear context element access patterns
- **Function Over Framework**: Simple functions that use context, not complex frameworks