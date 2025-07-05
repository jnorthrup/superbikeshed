package borg.trikeshed.lib

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

/**
 * A generic, compositional handler registry that lives in the CoroutineContext.
 * It maps a key of type K to a handler (a suspend function) of type V.
 * This is the refined replacement for MetaIndexed "chord sheets".
 *
 * @param K the type of the key used for handler lookup (e.g., IoCapability).
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
    @Suppress("UNCHECKED_CAST")
    val existingRegistry = coroutineContext[HandlerRegistry.Key] as? HandlerRegistry<K, V>
    val newRegistry = HandlerRegistry(newHandlers.toMap())

    val finalRegistry = existingRegistry?.plus(newRegistry) ?: newRegistry

    withContext(finalRegistry, block)
}

/**
 * Extension to easily access the handler registry from any coroutine context.
 */
fun <K, V : Function<*>> CoroutineContext.getHandlerRegistry(): HandlerRegistry<K, V>? = 
    this[HandlerRegistry.Key] as? HandlerRegistry<K, V>

// === Standard Granular Context Elements ===

/**
 * Specifies the desired IO backend capability for an operation.
 * This replaces complex, monolithic IOContexts.
 */
enum class IoCapability { URING, KQUEUE, NIO, EPOLL, POSIX_FD }
data class IoPreference(val capability: IoCapability) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<IoPreference>
}

/**
 * Carries a unique ID for a request or execution flow.
 */
data class ExecutionId(val id: String) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<ExecutionId>
}

// Easy-Access Extensions
val CoroutineContext.executionId: String? get() = this[ExecutionId]?.id
val CoroutineContext.ioCapability: IoCapability? get() = this[IoPreference]?.capability
