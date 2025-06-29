package nexus.k2script

import borg.trikeshed.lib.*
import k2script.bus.Router
import k2script.engine.Registry
import kotlinx.coroutines.*

/**
 * K2Script Integration - Wiring the cue ball to the 8 ball
 * 
 * This module registers nexus handlers with k2script's Router,
 * enabling k2script to launch nexus operations through its
 * message bus architecture.
 */
object K2ScriptIntegration {
    
    private var initialized = false
    private val integrationScope = CoroutineScope(Dispatchers.Default)
    
    /**
     * Initialize the k2script -> nexus causality chain
     * Call this from k2script's initialization to wire the systems together
     */
    fun initialize() {
        if (initialized) return
        
        integrationScope.launch {
            // Register the nexus handler with k2script's registry
            val nexusHandler = NexusK2Handler()
            Registry.register(nexusHandler)
            
            // Create convenience functions for k2script to use
            setupK2ScriptExtensions()
            
            initialized = true
            println("K2Script -> Nexus causality chain initialized")
        }
    }
    
    /**
     * Extension functions to make nexus operations feel native in k2script
     */
    private fun setupK2ScriptExtensions() {
        // These would be actual extension functions in practice
        // For now, we'll document the pattern
    }
}

/**
 * Extension functions for k2script to easily invoke nexus operations
 */

/**
 * Launch nexus main() from k2script
 * Example: Router.launchNexus()
 */
suspend fun Router.launchNexus(): NexusResult {
    val command = NexusK2Command(NexusCommand.LaunchMain())
    return dispatch(command).await()
}

/**
 * Execute AI task through nexus
 * Example: Router.nexusAI("analyze codebase")
 */
suspend fun Router.nexusAI(prompt: String, config: Map<String, Any> = emptyMap()): NexusResult {
    val command = NexusK2Command(NexusCommand.ExecuteAI(prompt, config))
    return dispatch(command).await()
}

/**
 * Query nexus spacegraph
 * Example: Router.querySpacegraph("peers")
 */
suspend fun Router.querySpacegraph(type: String): Indexed<Join<String, Any>> {
    val query = NexusSpacegraphQuery(SpacegraphQuery(type))
    return dispatch(query).await()
}

/**
 * Distribute attention across nexus abstractions
 * Example: Router.distributeAttention(agentIntelligence = 50, eventDriven = 30)
 */
suspend fun Router.distributeAttention(
    agentIntelligence: Int = 40,
    eventDriven: Int = 30,
    compositional: Int = 20,
    metaDevelopment: Int = 10
): AttentionResult {
    val distribution = AttentionDistribution(agentIntelligence, eventDriven, compositional, metaDevelopment)
    val message = NexusAttentionDistribution(distribution)
    return dispatch(message).await()
}

/**
 * DSL for building complex nexus operations from k2script
 */
class NexusOperationBuilder {
    private val operations = mutableListOf<suspend () -> Any>()
    
    fun launchMain() {
        operations.add { Router.launchNexus() }
    }
    
    fun ai(prompt: String, config: Map<String, Any> = emptyMap()) {
        operations.add { Router.nexusAI(prompt, config) }
    }
    
    fun query(type: String) {
        operations.add { Router.querySpacegraph(type) }
    }
    
    fun attention(
        agentIntelligence: Int = 40,
        eventDriven: Int = 30,
        compositional: Int = 20,
        metaDevelopment: Int = 10
    ) {
        operations.add { 
            Router.distributeAttention(agentIntelligence, eventDriven, compositional, metaDevelopment)
        }
    }
    
    suspend fun execute(): List<Any> = coroutineScope {
        operations.map { operation ->
            async { operation() }
        }.awaitAll()
    }
}

/**
 * DSL function for building nexus operations
 * Example:
 * ```
 * nexusOperation {
 *     launchMain()
 *     ai("analyze codebase")
 *     query("peers")
 *     attention(50, 30, 15, 5)
 * }.execute()
 * ```
 */
fun nexusOperation(block: NexusOperationBuilder.() -> Unit): NexusOperationBuilder {
    return NexusOperationBuilder().apply(block)
}