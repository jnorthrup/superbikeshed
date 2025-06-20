package k2script.api

import k2script.engine.Component

/**
 * Defines the contract for a dependency resolver.
 * Implementations of this interface can resolve dependencies from different sources
 * like Maven, Gradle, etc.
 */
interface DependencyResolver : Component {
    /**
     * The unique name of the resolver (e.g., "maven", "ivy").
     * This is used to look up the resolver in the registry.
     */
    override val name: String
    
    /**
     * Resolves a dependency source string (e.g., "group:artifact:version")
     * into a list of file paths.
     *
     * @param source The dependency locator string.
     * @return A list of paths to the resolved dependency artifacts.
     */
    fun resolve(source: String): List<String>
} 