package borg.trikeshed.dsl

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * SELF-CONFIGURING DSL - Implementation IS the DSL
 * 
 * This integrates the beneficial patterns from K2Script:
 * - DSL-driven script engine with self-registering components
 * - Bus pattern with validation, dependency parsing, and script execution
 * - Self-configuring architecture that creates its own "vines" (components)
 * - Router-based execution through unified router
 */

// ═══════════════════════════════════════════════════════════════════════════════
// SELF-CONFIGURING DSL CORE
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * DSL component that knows what it needs and creates it
 */
interface DSLComponent {
    val name: String
    val dependencies: Indexed<String>
    fun initialize(): DSLComponent
    fun validate(): Boolean
}

/**
 * DSL builder for creating self-configuring components
 */
class DSLBuilder {
    private val components = mutableMapOf<String, DSLComponent>()
    private val vines = mutableListOf<String>()
    
    /**
     * Register a component with the DSL
     */
    fun register(component: DSLComponent) {
        components[component.name] = component
        vines.add("${component.name} (${component.dependencies.size} deps)")
    }
    
    /**
     * Build the DSL with automatic dependency resolution
     */
    suspend fun build(): SelfConfiguringDSL {
        // Resolve dependencies automatically
        resolveDependencies()
        
        // Initialize all components
        components.values.forEach { it.initialize() }
        
        // Validate the configuration
        val validComponents = components.values.filter { it.validate() }
        
        return SelfConfiguringDSL(validComponents.toIndexed(), vines.toIndexed())
    }
    
    /**
     * Resolve dependencies automatically
     */
    private fun resolveDependencies() {
        val resolved = mutableSetOf<String>()
        val toResolve = components.values.toMutableList()
        
        while (toResolve.isNotEmpty()) {
            val resolvedThisRound = mutableListOf<DSLComponent>()
            
            for (component in toResolve) {
                if (component.dependencies.all { dep -> 
                    resolved.contains(dep) || components.containsKey(dep) 
                }) {
                    resolved.add(component.name)
                    resolvedThisRound.add(component)
                }
            }
            
            toResolve.removeAll(resolvedThisRound)
            
            if (resolvedThisRound.isEmpty() && toResolve.isNotEmpty()) {
                throw IllegalStateException("Circular dependency detected: ${toResolve.map { it.name }}")
            }
        }
    }
}

/**
 * Self-configuring DSL that creates its own vines
 */
class SelfConfiguringDSL(
    val components: Indexed<DSLComponent>,
    val vines: Indexed<String>
) {
    
    /**
     * List all components (DSL vines)
     */
    fun listComponents(): Indexed<String> = components.size j { i -> components[i].name }
    
    /**
     * Get all vines (component descriptions)
     */
    fun getVines(): Indexed<String> = vines
    
    /**
     * Execute a DSL operation
     */
    suspend fun execute(operation: String, payload: Any? = null): Any? {
        return when (operation) {
            "list" -> listComponents()
            "vines" -> getVines()
            "validate" -> validateAll()
            "analyze" -> analyzeComponents()
            else -> throw IllegalArgumentException("Unknown operation: $operation")
        }
    }
    
    /**
     * Validate all components
     */
    private fun validateAll(): Boolean {
        return components.play.all { it.validate() }
    }
    
    /**
     * Analyze components using Series operations
     */
    private fun analyzeComponents(): Indexed<String> {
        val analysis = mutableListOf<String>()
        
        // Analyze component dependencies
        val totalDeps = components.play.sumOf { it.dependencies.size }
        analysis.add("Total components: ${components.size}")
        analysis.add("Total dependencies: $totalDeps")
        
        // Find components with most dependencies
        val maxDeps = components.play.maxOfOrNull { it.dependencies.size } ?: 0
        analysis.add("Max dependencies per component: $maxDeps")
        
        // Find independent components
        val independent = components.play.count { it.dependencies.isEmpty() }
        analysis.add("Independent components: $independent")
        
        return analysis.size j { i -> analysis[i] }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DSL COMPONENTS - Self-Registering Implementations
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Router component for DSL operations
 */
class DSLRouter : DSLComponent {
    override val name = "router"
    override val dependencies = emptyList<String>().toIndexed()
    
    private val routes = mutableMapOf<String, suspend (Any?) -> Any?>()
    
    override fun initialize(): DSLComponent {
        // Self-register basic routes
        routes["validate"] = { payload -> validateScript(payload) }
        routes["parse"] = { payload -> parseDependencies(payload) }
        routes["execute"] = { payload -> executeScript(payload) }
        return this
    }
    
    override fun validate(): Boolean = routes.isNotEmpty()
    
    suspend fun route(operation: String, payload: Any?): Any? {
        return routes[operation]?.invoke(payload) ?: throw IllegalArgumentException("Unknown route: $operation")
    }
    
    private suspend fun validateScript(payload: Any?): Indexed<String> {
        // Simplified validation - would need full implementation
        return listOf("Script validation passed").toIndexed()
    }
    
    private suspend fun parseDependencies(payload: Any?): Indexed<String> {
        // Simplified dependency parsing - would need full implementation
        return listOf("kotlinx-coroutines", "kotlinx-serialization").toIndexed()
    }
    
    private suspend fun executeScript(payload: Any?): Boolean {
        // Simplified execution - would need full implementation
        return true
    }
}

/**
 * Environment manager component
 */
class DSLEnvironmentManager : DSLComponent {
    override val name = "environment"
    override val dependencies = emptyList<String>().toIndexed()
    
    private val environment = mutableMapOf<String, String>()
    
    override fun initialize(): DSLComponent {
        // Self-configure environment
        environment["KOTLIN_VERSION"] = "2.1.21"
        environment["COROUTINES_VERSION"] = "1.8.0"
        environment["SERIALIZATION_VERSION"] = "1.6.3"
        return this
    }
    
    override fun validate(): Boolean = environment.isNotEmpty()
    
    fun getEnvironment(): Map<String, String> = environment.toMap()
    
    fun isVerbose(): Boolean = environment["VERBOSE"] == "true"
}

/**
 * Dependency resolver component
 */
class DSLDependencyResolver : DSLComponent {
    override val name = "resolver"
    override val dependencies = listOf("environment").toIndexed()
    
    private val resolvers = mutableListOf<String>()
    
    override fun initialize(): DSLComponent {
        // Self-register resolvers
        resolvers.add("Maven Central")
        resolvers.add("Gradle Plugin Portal")
        resolvers.add("Local Repository")
        return this
    }
    
    override fun validate(): Boolean = resolvers.isNotEmpty()
    
    fun getResolvers(): Indexed<String> = resolvers.size j { i -> resolvers[i] }
}

/**
 * Script engine component
 */
class DSLScriptEngine : DSLComponent {
    override val name = "engine"
    override val dependencies = listOf("router", "environment", "resolver").toIndexed()
    
    private var isInitialized = false
    
    override fun initialize(): DSLComponent {
        isInitialized = true
        return this
    }
    
    override fun validate(): Boolean = isInitialized
    
    suspend fun executeScript(scriptPath: String, args: Indexed<String>): Boolean {
        // Simplified script execution - would need full implementation
        return true
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DSL BUILDER EXTENSIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * DSL builder extension for easy component registration
 */
fun DSLBuilder.router(block: DSLRouter.() -> Unit = {}): DSLRouter {
    val router = DSLRouter()
    router.block()
    register(router)
    return router
}

fun DSLBuilder.environment(block: DSLEnvironmentManager.() -> Unit = {}): DSLEnvironmentManager {
    val env = DSLEnvironmentManager()
    env.block()
    register(env)
    return env
}

fun DSLBuilder.resolver(block: DSLDependencyResolver.() -> Unit = {}): DSLDependencyResolver {
    val resolver = DSLDependencyResolver()
    resolver.block()
    register(resolver)
    return resolver
}

fun DSLBuilder.engine(block: DSLScriptEngine.() -> Unit = {}): DSLScriptEngine {
    val engine = DSLScriptEngine()
    engine.block()
    register(engine)
    return engine
}

// ═══════════════════════════════════════════════════════════════════════════════
// UNIFIED DSL INTERFACE
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Unified DSL interface that creates its own vines
 */
object UnifiedDSL {
    
    /**
     * Build a self-configuring DSL
     */
    suspend fun build(block: DSLBuilder.() -> Unit = {}): SelfConfiguringDSL {
        val builder = DSLBuilder()
        builder.block()
        return builder.build()
    }
    
    /**
     * Execute a DSL operation through the unified interface
     */
    suspend fun execute(operation: String, payload: Any? = null): Any? {
        val dsl = build()
        return dsl.execute(operation, payload)
    }
    
    /**
     * Show DSL components (vines)
     */
    suspend fun showComponents(): Indexed<String> {
        val dsl = build()
        return dsl.listComponents()
    }
    
    /**
     * Show DSL vines (component descriptions)
     */
    suspend fun showVines(): Indexed<String> {
        val dsl = build()
        return dsl.getVines()
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DSL VALIDATION AND ANALYSIS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * DSL validation utilities
 */
object DSLValidation {
    
    /**
     * Validate DSL configuration
     */
    suspend fun validateDSL(): ValidationResult {
        val dsl = UnifiedDSL.build()
        
        val components = dsl.listComponents()
        val vines = dsl.getVines()
        val isValid = dsl.execute("validate") as Boolean
        
        return ValidationResult(
            components = components,
            vines = vines,
            isValid = isValid,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Analyze DSL structure
     */
    suspend fun analyzeDSL(): DSLAnalysis {
        val dsl = UnifiedDSL.build()
        
        val componentCount = dsl.listComponents().size
        val vineCount = dsl.getVines().size
        val analysis = dsl.execute("analyze") as Indexed<String>
        
        return DSLAnalysis(
            componentCount = componentCount,
            vineCount = vineCount,
            analysis = analysis,
            timestamp = System.currentTimeMillis()
        )
    }
}

/**
 * Validation result
 */
@Serializable
data class ValidationResult(
    val components: Indexed<String>,
    val vines: Indexed<String>,
    val isValid: Boolean,
    val timestamp: Long
)

/**
 * DSL analysis result
 */
@Serializable
data class DSLAnalysis(
    val componentCount: Int,
    val vineCount: Int,
    val analysis: Indexed<String>,
    val timestamp: Long
)

// ═══════════════════════════════════════════════════════════════════════════════
// UTILITY EXTENSIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Convert List to Indexed
 */
fun <T> List<T>.toIndexed(): Indexed<T> = this.size j { i -> this[i] }

/**
 * Extension for DSL builder pattern
 */
suspend fun dsl(block: DSLBuilder.() -> Unit): SelfConfiguringDSL {
    return UnifiedDSL.build(block)
} 