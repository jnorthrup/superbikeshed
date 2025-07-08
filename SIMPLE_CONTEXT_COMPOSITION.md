# Simple Context Composition (What CCEK Should Have Been)

## The Original Intent

CCEK = CoroutineContextElementKey. That's it. Just a way to compose context by keys.

## Production-Focused Pattern

```kotlin
// Simple context elements as keys
data class DbConnection(val conn: Connection) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<DbConnection>
}

data class RequestId(val id: String) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<RequestId>
}

data class IoStrategy(val type: IoType) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<IoStrategy>
}

// Simple usage
suspend fun handleRequest(request: Request) {
    withContext(
        RequestId(request.id) + 
        DbConnection(getConnection()) +
        IoStrategy(IoType.URING)
    ) {
        // Your actual business logic
        processRequest(request)
    }
}

// Access when needed
suspend fun processRequest(request: Request) {
    val reqId = coroutineContext[RequestId]?.id
    val conn = coroutineContext[DbConnection]?.conn
    val io = coroutineContext[IoStrategy]?.type
    
    // Do actual work
}
```

## What Actually Matters for Production

1. **Performance**: Direct key lookup, no abstraction overhead
2. **Debuggability**: Simple context you can inspect in debugger
3. **Testability**: Easy to mock/inject contexts
4. **Type Safety**: Compile-time checked keys
5. **No Magic**: Just Kotlin's built-in coroutine context

## Anti-Patterns to Avoid

❌ Creating elaborate "phases" (Control, Context, Environment, Knowledge)
❌ Building complex state machines in context
❌ Over-abstracting simple key-value storage
❌ Forcing every service into the same "pattern"

## Real Production Examples

### Database Transaction Context
```kotlin
suspend fun <T> inTransaction(block: suspend () -> T): T {
    val conn = dataSource.connection
    return withContext(DbConnection(conn)) {
        try {
            conn.autoCommit = false
            val result = block()
            conn.commit()
            result
        } catch (e: Exception) {
            conn.rollback()
            throw e
        }
    }
}
```

### Request Tracing
```kotlin
suspend fun traced(block: suspend () -> Unit) {
    withContext(
        RequestId(UUID.randomUUID().toString()) +
        Timestamp(System.currentTimeMillis())
    ) {
        logger.info("Start: ${coroutineContext[RequestId]?.id}")
        block()
        logger.info("End: ${coroutineContext[RequestId]?.id}")
    }
}
```

### IO Strategy Selection
```kotlin
suspend fun readFile(path: String): ByteArray {
    return when (coroutineContext[IoStrategy]?.type) {
        IoType.URING -> readViaUring(path)
        IoType.NIO -> readViaNio(path)
        else -> readViaStdlib(path)
    }
}
```

## The Bottom Line

Context composition by keys is useful for:
- Dependency injection without frameworks
- Request-scoped values (IDs, auth, etc)
- Strategy selection
- Resource management

That's it. No methodology. No phases. Just practical key-value storage that flows with your coroutines.