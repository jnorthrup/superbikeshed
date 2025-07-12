package borg.trikeshed.ccek

import kotlinx.coroutines.CoroutineContext
import borg.trikeshed.lib.*

/**
 * CCEK - CoroutineContext.Element.Key Migration
 * 
 * This file migrates from service-based to capability-based architecture
 * by associating APIs directly with CoroutineContext.Key instances.
 * 
 * AXIOM 1: Context IS Platform
 * The CoroutineContext becomes the complete execution environment,
 * eliminating the need for external service containers.
 * 
 * AXIOM 2: Keys ARE Capabilities
 * Every key represents a capability, not a container.
 * Functions are bound to keys, not services.
 * 
 * AXIOM 3: Composition IS Execution
 * Execution flows are composed through context manipulation,
 * not method chaining on objects.
 */

// === Core Context Keys ===

/**
 * Base capability interface for all CCEK operations
 */
interface CcekCapability : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<CcekCapability>
    override val key: CoroutineContext.Key<*> get() = Key
}

/**
 * HTTP Request capability - provides HTTP client operations
 */
interface HttpCapability : CcekCapability {
    companion object Key : CoroutineContext.Key<HttpCapability>
    override val key: CoroutineContext.Key<*> get() = Key
    
    data class Request(
        val method: String,
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null
    )
    
    data class Response(
        val status: Int,
        val headers: Map<String, String>,
        val body: String
    )
}

/**
 * Database Query capability - provides CouchDB-style operations
 */
interface DbCapability : CcekCapability {
    companion object Key : CoroutineContext.Key<DbCapability>
    override val key: CoroutineContext.Key<*> get() = Key
    
    data class Query(
        val selector: Map<String, Any>,
        val fields: List<String> = emptyList(),
        val limit: Int? = null
    )
    
    data class Transaction(
        val id: String,
        val operations: MutableList<Operation> = mutableListOf()
    )
    
    sealed class Operation {
        data class Insert(val doc: Map<String, Any>) : Operation()
        data class Update(val id: String, val doc: Map<String, Any>) : Operation()
        data class Delete(val id: String) : Operation()
    }
}

/**
 * File I/O capability - provides file system operations
 */
interface FileCapability : CcekCapability {
    companion object Key : CoroutineContext.Key<FileCapability>
    override val key: CoroutineContext.Key<*> get() = Key
    
    data class Stream(
        val path: String,
        val mode: String = "r"
    )
}

/**
 * Distributed Hash Table capability - provides DHT operations
 */
interface DhtCapability : CcekCapability {
    companion object Key : CoroutineContext.Key<DhtCapability>
    override val key: CoroutineContext.Key<*> get() = Key
    
    data class Discovery(
        val targetId: String,
        val maxPeers: Int = 20
    )
}

/**
 * Execution State capability - tracks operation state
 */
interface ExecutionCapability : CcekCapability {
    companion object Key : CoroutineContext.Key<ExecutionCapability>
    override val key: CoroutineContext.Key<*> get() = Key
    
    data class State(
        val id: String,
        val status: Status = Status.PENDING,
        val result: Any? = null,
        val error: Throwable? = null
    )
    
    enum class Status {
        PENDING, RUNNING, SUCCESS, FAILED
    }
}

// === AXIOM 4: Openers Create Context ===

/**
 * HTTP Request opener - establishes HTTP capability in context
 */
suspend fun CoroutineContext.httpRequest(request: HttpCapability.Request): CoroutineContext {
    val httpCapability = object : HttpCapability {}
    val executionCapability = object : ExecutionCapability {
        override val state = ExecutionCapability.State(
            id = "http_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}",
            status = ExecutionCapability.Status.RUNNING
        )
    }
    
    return this + httpCapability + executionCapability
}

/**
 * Database Query opener - establishes DB capability in context
 */
suspend fun CoroutineContext.dbQuery(query: DbCapability.Query): CoroutineContext {
    val dbCapability = object : DbCapability {}
    val executionCapability = object : ExecutionCapability {
        override val state = ExecutionCapability.State(
            id = "db_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}",
            status = ExecutionCapability.Status.RUNNING
        )
    }
    
    return this + dbCapability + executionCapability
}

/**
 * File Stream opener - establishes File capability in context
 */
suspend fun CoroutineContext.fileStream(stream: FileCapability.Stream): CoroutineContext {
    val fileCapability = object : FileCapability {}
    val executionCapability = object : ExecutionCapability {
        override val state = ExecutionCapability.State(
            id = "file_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}",
            status = ExecutionCapability.Status.RUNNING
        )
    }
    
    return this + fileCapability + executionCapability
}

/**
 * DHT Discovery opener - establishes DHT capability in context
 */
suspend fun CoroutineContext.dhtDiscover(discovery: DhtCapability.Discovery): CoroutineContext {
    val dhtCapability = object : DhtCapability {}
    val executionCapability = object : ExecutionCapability {
        override val state = ExecutionCapability.State(
            id = "dht_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}",
            status = ExecutionCapability.Status.RUNNING
        )
    }
    
    return this + dhtCapability + executionCapability
}

// === AXIOM 5: Conditionals Control Flow ===

/**
 * On Result conditional - executes block only on successful result
 */
suspend fun CoroutineContext.onResult(block: suspend (Any?) -> CoroutineContext): CoroutineContext {
    val execution = this[ExecutionCapability.Key]
    return if (execution?.state?.status == ExecutionCapability.Status.SUCCESS) {
        block(execution.state.result)
    } else {
        this
    }
}

/**
 * On Error conditional - executes block only on error
 */
suspend fun CoroutineContext.onError(block: suspend (Throwable) -> CoroutineContext): CoroutineContext {
    val execution = this[ExecutionCapability.Key]
    return if (execution?.state?.status == ExecutionCapability.Status.FAILED && execution.state.error != null) {
        block(execution.state.error)
    } else {
        this
    }
}

/**
 * When State conditional - executes block only when in expected state
 */
suspend fun CoroutineContext.whenState(
    expected: ExecutionCapability.Status,
    block: suspend () -> CoroutineContext
): CoroutineContext {
    val execution = this[ExecutionCapability.Key]
    return if (execution?.state?.status == expected) {
        block()
    } else {
        this
    }
}

/**
 * Has Capability conditional - executes block only if capability exists
 */
suspend fun <T : CcekCapability> CoroutineContext.hasCapability(
    key: CoroutineContext.Key<T>,
    block: suspend (T) -> CoroutineContext
): CoroutineContext {
    val capability = this[key]
    return if (capability != null) {
        block(capability)
    } else {
        this
    }
}

// === AXIOM 6: Terminals Execute and Finalize ===

/**
 * Await Result terminal - suspends until operation completes
 */
suspend fun CoroutineContext.awaitResult(): Any? {
    val execution = this[ExecutionCapability.Key]
    return when (execution?.state?.status) {
        ExecutionCapability.Status.SUCCESS -> execution.state.result
        ExecutionCapability.Status.FAILED -> throw execution.state.error ?: RuntimeException("Operation failed")
        else -> {
            // In real implementation, this would suspend until completion
            // For now, simulate completion
            "Mock result for ${execution?.state?.id}"
        }
    }
}

/**
 * Commit terminal - finalizes a transaction
 */
suspend fun CoroutineContext.commit(): Boolean {
    val dbCapability = this[DbCapability.Key]
    val execution = this[ExecutionCapability.Key]
    
    return if (dbCapability != null && execution != null) {
        // In real implementation, this would commit the transaction
        println("Committing transaction: ${execution.state.id}")
        true
    } else {
        false
    }
}

/**
 * Release terminal - releases resources held by context
 */
suspend fun CoroutineContext.release(): CoroutineContext {
    // In real implementation, this would release all resources
    println("Releasing context resources")
    return this
}

// === AXIOM 7: Context Composition Rules ===

/**
 * Merge contexts following CCEK composition rules
 */
operator fun CoroutineContext.plus(other: CcekCapability): CoroutineContext {
    return this + (other as CoroutineContext.Element)
}

/**
 * Context subtraction for capability removal
 */
operator fun CoroutineContext.minus(key: CoroutineContext.Key<*>): CoroutineContext {
    return this.minusKey(key)
}

// === AXIOM 8: Platform Abstraction ===

/**
 * Platform-agnostic execution context
 * Any platform can be virtualized through context composition
 */
interface PlatformCapability : CcekCapability {
    companion object Key : CoroutineContext.Key<PlatformCapability>
    override val key: CoroutineContext.Key<*> get() = Key
    
    val platform: String
    val capabilities: Set<String>
}

/**
 * Create platform-specific context
 */
fun createPlatformContext(platform: String, capabilities: Set<String>): CoroutineContext {
    val platformCapability = object : PlatformCapability {
        override val platform = platform
        override val capabilities = capabilities
    }
    
    return platformCapability
}