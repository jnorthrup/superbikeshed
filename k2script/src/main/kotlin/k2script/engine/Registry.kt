@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.engine

/**
 * A component that can be registered with the k2script engine.
 * All components must have a unique name.
 */
interface Component {
    val name: String
}

/**
 * A generic registry for engine components, inspired by FFmpeg's architecture.
 * This provides a central point for discovering and managing components like
 * dependency resolvers, script handlers, etc.
 *
 * It is a singleton object to provide a single source of truth for registered components.
 */
object Registry {
    internal val components = mutableMapOf<String, Component>()

    /**
     * Registers a component with the engine.
     * If a component with the same name already exists, it will be overwritten.
     * A warning will be logged in such cases.
     */
    fun register(component: Component) {
        if (components.containsKey(component.name)) {
            // In a real implementation, we'd use a proper logger
            println("Warning: Component with name '${component.name}' is already registered. Overwriting.")
        }
        components[component.name] = component
    }

    /**
     * Retrieves a component by its unique name.
     * The caller is responsible for casting it to the correct type.
     */
    fun <T : Component> get(name: String): T? {
        @Suppress("UNCHECKED_CAST")
        return components[name] as? T
    }

    /**
     * Retrieves all registered components of a specific type.
     */
    fun <T : Component> getAll(type: Class<T>): List<T> {
        return components.values.filterIsInstance(type)
    }

    /**
     * Returns a collection of all registered components.
     */
    fun all(): Collection<Component> = components.values
} 