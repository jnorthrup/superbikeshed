package borg.trikeshed.launcher

/**
 * Context-as-a-Service interfaces for the choreography pattern.
 * These are the missing interfaces that enable the sophisticated 
 * Context-as-a-Service Subsumption Hierarchy.
 */

/**
 * Marker interface for components that provide capabilities to the context registry.
 * Components implementing this can register their capabilities for discovery.
 */
interface ContextProvider {
    // Marker interface - no methods required
}

/**
 * Interface for components that discover capabilities through context.
 * Components implementing this declare what context keys they need.
 */
interface ContextAware {
    /**
     * Set of context keys this component requires.
     * Used for dependency validation and service discovery.
     */
    val contextKeys: Set<ContextKey>
}

/**
 * Extension function to get current service from context.
 * This is the runtime capability discovery mechanism.
 */
inline fun <reified T> ContextAware.currentService(): T? {
    // In a real implementation, this would look up the service
    // from the current coroutine context or thread-local storage
    return null // Simplified for demo
}

/**
 * Extension property to get the current context.
 * Enables components to access the context for service discovery.
 */
val ContextAware.context: ContextAware
    get() = this