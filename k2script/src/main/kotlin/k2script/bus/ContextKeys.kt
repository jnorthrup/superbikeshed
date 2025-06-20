package k2script.bus

import kotlinx.coroutines.CoroutineContext
import kotlinx.coroutines.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Centralized enum for all coroutine context element keys used in the k2script message bus.
 * This provides type safety, discoverability, and a single source of truth for all
 * contextual data that flows through the router.
 */
enum class ContextKey {
    // Execution context
    SCRIPT_FILE,
    SCRIPT_ARGS,
    EXECUTION_ID,
    
    // User context
    USER_ID,
    SESSION_ID,
    REQUEST_ID,
    
    // Environment context
    ENVIRONMENT,
    LOG_LEVEL,
    VERBOSE_MODE,
    
    // Performance context
    TIMEOUT_MS,
    PRIORITY,
    DISPATCHER_TYPE,
    
    // Security context
    API_KEY,
    PERMISSIONS,
    AUTH_TOKEN,
    
    // Debugging context
    TRACE_ID,
    DEBUG_FLAGS,
    METRICS_ENABLED
}

/**
 * Base class for all context elements in the k2script system.
 * Provides a consistent interface for accessing contextual data.
 */
abstract class K2ScriptContextElement<T>(
    val key: ContextKey,
    val value: T
) : AbstractCoroutineContextElement(key) {
    companion object {
        /**
         * Extension function to get a context element from a CoroutineContext.
         */
        inline fun <reified T> CoroutineContext.get(key: ContextKey): T? {
            return this[key]?.let { it as? K2ScriptContextElement<T> }?.value
        }
        
        /**
         * Extension function to set a context element in a CoroutineContext.
         */
        inline fun <reified T> CoroutineContext.with(key: ContextKey, value: T): CoroutineContext {
            return this + createContextElement(key, value)
        }
    }
}

/**
 * Factory function to create context elements based on the key type.
 */
inline fun <reified T> createContextElement(key: ContextKey, value: T): K2ScriptContextElement<T> {
    return object : K2ScriptContextElement<T>(key, value) {}
}

/**
 * Predefined context elements for common use cases.
 */
object ContextElements {
    fun scriptFile(file: java.io.File) = createContextElement(ContextKey.SCRIPT_FILE, file)
    fun scriptArgs(args: Array<String>) = createContextElement(ContextKey.SCRIPT_ARGS, args)
    fun executionId(id: String) = createContextElement(ContextKey.EXECUTION_ID, id)
    fun userId(id: String) = createContextElement(ContextKey.USER_ID, id)
    fun sessionId(id: String) = createContextElement(ContextKey.SESSION_ID, id)
    fun requestId(id: String) = createContextElement(ContextKey.REQUEST_ID, id)
    fun environment(env: String) = createContextElement(ContextKey.ENVIRONMENT, env)
    fun logLevel(level: String) = createContextElement(ContextKey.LOG_LEVEL, level)
    fun verboseMode(enabled: Boolean) = createContextElement(ContextKey.VERBOSE_MODE, enabled)
    fun timeoutMs(timeout: Long) = createContextElement(ContextKey.TIMEOUT_MS, timeout)
    fun priority(priority: Int) = createContextElement(ContextKey.PRIORITY, priority)
    fun dispatcherType(type: String) = createContextElement(ContextKey.DISPATCHER_TYPE, type)
    fun apiKey(key: String) = createContextElement(ContextKey.API_KEY, key)
    fun permissions(perms: List<String>) = createContextElement(ContextKey.PERMISSIONS, perms)
    fun authToken(token: String) = createContextElement(ContextKey.AUTH_TOKEN, token)
    fun traceId(id: String) = createContextElement(ContextKey.TRACE_ID, id)
    fun debugFlags(flags: Set<String>) = createContextElement(ContextKey.DEBUG_FLAGS, flags)
    fun metricsEnabled(enabled: Boolean) = createContextElement(ContextKey.METRICS_ENABLED, enabled)
} 