package k2script.engine

import k2script.api.models.ExecuteScriptCommand
import k2script.api.models.ParseDependenciesQuery
import k2script.api.models.SCRIPT_ENGINE_ADDRESS
import k2script.api.models.ValidateScriptQuery
import k2script.api.DependencyResolver
import k2script.bus.Address
import k2script.bus.Handler
import k2script.bus.Message
import k2script.bus.Router
import k2script.engine.resolvers.MavenResolver
import k2script.trikeshed.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The DefaultScriptEngine IS the DSL - it creates its own vines (dependencies)
 * and manages its own component graph. This follows the principle that the
 * implementation should be self-contained and create its own ecosystem.
 */
@Suppress("UNCHECKED_CAST")
class DefaultScriptEngine : Handler {
    override val name: String = "DefaultScriptEngine"
    override val address: Address = SCRIPT_ENGINE_ADDRESS

    // Internal component registry - the engine creates its own vines
    private val internalRegistry = mutableMapOf<String, Any>()
    private val dependencyResolvers = mutableListOf<DependencyResolver>()
    
    // Coroutine scope for async operations
    private val engineScope = CoroutineScope(Dispatchers.IO)
    
    init {
        // The engine creates its own vines - self-registration
        createVines()
        registerWithRouter()
    }
    
    /**
     * DSL-style builder for creating a configured engine
     * This demonstrates how the implementation IS the DSL
     */
    companion object {
        fun build(): DefaultScriptEngine {
            return DefaultScriptEngine()
        }
        
        fun build(configure: DefaultScriptEngine.() -> Unit): DefaultScriptEngine {
            return DefaultScriptEngine().apply(configure)
        }
    }
    
    /**
     * Creates the engine's own dependency vines - this is where the DSL nature
     * of the implementation becomes apparent. The engine knows what it needs
     * and creates those dependencies itself.
     */
    private fun createVines() {
        // Register self in internal registry
        internalRegistry[name] = this
        
        // Create dependency resolvers using DSL-style builder
        createResolvers()
        
        // Create other internal components using DSL-style builder
        createComponents()
    }
    
    /**
     * DSL-style resolver creation - the engine knows what resolvers it needs
     */
    private fun createResolvers() {
        // Maven resolver is essential for Kotlin scripting
        val mavenResolver = MavenResolver()
        dependencyResolvers.add(mavenResolver)
        internalRegistry["mavenResolver"] = mavenResolver
        
        // Future: Add other resolvers as needed
        // val gradleResolver = GradleResolver()
        // val ivyResolver = IvyResolver()
    }
    
    /**
     * DSL-style component creation - the engine creates its own ecosystem
     */
    private fun createComponents() {
        // Script validation is core functionality
        val scriptValidator = ScriptValidator()
        internalRegistry["scriptValidator"] = scriptValidator
        
        // Dependency parsing requires resolvers
        val dependencyParser = DependencyParser(dependencyResolvers)
        internalRegistry["dependencyParser"] = dependencyParser
        
        // Script execution is the main purpose
        val scriptExecutor = ScriptExecutor()
        internalRegistry["scriptExecutor"] = scriptExecutor
        
        // Add more components as the DSL grows
        val engineContext = Context.create("engine")
        val resourceManager = ResourceManager(engineContext)
        internalRegistry["resourceManager"] = resourceManager
    }
    
    /**
     * Registers this engine with the global router so it can receive messages
     */
    private fun registerWithRouter() {
        engineScope.launch {
            Registry.register(this@DefaultScriptEngine)
        }
    }
    
    /**
     * DSL-style access to internal components
     */
    fun <T> getComponent(name: String): T? {
        @Suppress("UNCHECKED_CAST")
        return internalRegistry[name] as? T
    }
    
    /**
     * DSL-style access to dependency resolvers
     */
    fun getResolvers(): List<DependencyResolver> = dependencyResolvers.toList()
    
    /**
     * DSL-style component introspection
     */
    fun listComponents(): List<String> = internalRegistry.keys.toList()
    
    /**
     * DSL-style configuration - allows the engine to be configured after creation
     */
    fun configure(block: DefaultScriptEngine.() -> Unit): DefaultScriptEngine {
        block()
        return this
    }

    override suspend fun handle(message: Message<*, *>) {
        when (message) {
            is ValidateScriptQuery -> {
                val validator = getComponent<ScriptValidator>("scriptValidator")
                val result = validator?.validate(message.payload) ?: validateScript(message.payload)
                message.reply.complete(result)
            }
            is ParseDependenciesQuery -> {
                val parser = getComponent<DependencyParser>("dependencyParser")
                val result = parser?.parse(message.payload) ?: parseDependencies(message.payload)
                message.reply.complete(result)
            }
            is ExecuteScriptCommand -> {
                val executor = getComponent<ScriptExecutor>("scriptExecutor")
                val result = executor?.execute(message.payload.first, message.payload.second) 
                    ?: executeScript(message.payload.first, message.payload.second)
                message.reply.complete(result)
            }
            else -> {
                message.reply.completeExceptionally(
                    IllegalArgumentException("Unsupported message type for ScriptEngine: ${message::class.simpleName}")
                )
            }
        }
    }

    // Fallback implementations - these should be replaced by the DSL components
    private fun validateScript(scriptFile: File): List<String> {
        return emptyList()
    }

    private fun parseDependencies(scriptFile: File): borg.trikeshed.lib.Indexed<String> {
        return borg.trikeshed.lib.emptySeries()
    }

    private fun executeScript(scriptFile: File, scriptArgs: Array<String>): Boolean {
        return true
    }
}

/**
 * Internal DSL components - these are created by the engine itself
 * Each component represents a "vine" in the engine's dependency graph
 */
private class ScriptValidator {
    fun validate(scriptFile: File): List<String> {
        // Implementation will be added here
        return emptyList()
    }
}

private class DependencyParser(private val resolvers: List<DependencyResolver>) {
    fun parse(scriptFile: File): borg.trikeshed.lib.Indexed<String> {
        // Implementation will be added here
        return borg.trikeshed.lib.emptySeries()
    }
}

private class ScriptExecutor {
    fun execute(scriptFile: File, scriptArgs: Array<String>): Boolean {
        // Implementation will be added here
        return true
    }
} 