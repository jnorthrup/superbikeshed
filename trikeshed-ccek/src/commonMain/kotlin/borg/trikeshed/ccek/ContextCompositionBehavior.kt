@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.ccek

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import borg.trikeshed.channel.api.*
import borg.trikeshed.dht.IKademliaNode
import borg.trikeshed.dht.IMetaverseAgent
import borg.trikeshed.dht.DHTFactory
import borg.trikeshed.dht.kademlia.id.NUID

/**
 * Context Composition Behavior Examples
 * Demonstrates how context composition works with the + operator
 * and how services can be combined, replaced, and validated.
 */

/**
 * Example 1: Basic context composition with + operator
 */
suspend fun basicContextComposition() {
    val service1 = ChannelService(ChannelProvider.Stub)
    val service2 = CRDTChannelEngine("node1", EmptyCoroutineContext)
    val service3 = DHTFactory.createKademliaNode(NUID.random(), EmptyCoroutineContext)
    
    // Combine contexts with + operator
    val combined = service1 + service2 + service3
    
    withContext(combined) {
        println("ChannelService: ${combined[ChannelService] != null}")     // true
        println("CRDTChannelEngine: ${combined[CRDTChannelEngine] != null}")     // true
        println("KademliaNode: ${combined[IKademliaNode] != null}")    // true
    }
}

/**
 * Example 2: Context replacement behavior
 */
suspend fun contextReplacement() {
    val service1 = CRDTChannelEngine("node1", EmptyCoroutineContext)
    val service2 = CRDTChannelEngine("node2", EmptyCoroutineContext) // Same key, different instance
    
    // Later service replaces earlier one with same key
    val combined = service1 + service2
    val replacedContext = service2 + service1 // Reverse order
    
    withContext(combined) {
        println("Original node: ${combined[CRDTChannelEngine]?.nodeId}")         // "node1"
        println("Replaced node: ${replacedContext[CRDTChannelEngine]?.nodeId}")  // "node2"
    }
}

/**
 * Example 3: Progressive context building
 */
suspend fun progressiveContextBuilding() {
    var context: CoroutineContext = EmptyCoroutineContext
    
    // Add services progressively
    context += ChannelService(ChannelProvider.Stub)
    context += CRDTChannelEngine("progressive-node", context)
    context += DHTFactory.createKademliaNode(NUID.random(), context)
    context += ChannelizedRequestFactory(context)
    
    withContext(context) {
        val channel = coroutineContext[ChannelService]!!
        val crdt = coroutineContext[CRDTChannelEngine]!!
        val kademlia = coroutineContext[IKademliaNode]!!
        val requests = coroutineContext[ChannelizedRequestFactory]!!
        
        println("Progressive context built with ${crdt.nodeId}, ${kademlia.nodeId}")
    }
}

/**
 * Example 4: Context composition with validation
 */
suspend fun contextCompositionWithValidation() {
    val context = EmptyCoroutineContext + 
        ChannelService(ChannelProvider.Stub) +
        CRDTChannelEngine("validated-node", EmptyCoroutineContext) +
        DHTFactory.createKademliaNode(NUID.random(), EmptyCoroutineContext)
    
    withContext(context) {
        // Validate required services are present
        val channel = coroutineContext[ChannelService]!!
        val crdt = coroutineContext[CRDTChannelEngine]!!
        val kademlia = coroutineContext[IKademliaNode]!!
        
        println("Context validation passed: ${channel != null && crdt != null && kademlia != null}")
    }
}

/**
 * Example 5: Context composition with error handling
 */
suspend fun contextCompositionWithErrors(): Result<String> {
    return try {
        val context = EmptyCoroutineContext + 
            ChannelService(ChannelProvider.Stub) +
            CRDTChannelEngine("error-node", EmptyCoroutineContext)
        
        withContext(context) {
            // Simulate an error condition
            val crdt = coroutineContext[CRDTChannelEngine]!!
            if (crdt.nodeId == "error-node") {
                throw IllegalStateException("Error node detected")
            }
            "Context composition successful"
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Example 6: Context composition with multiple instances
 */
suspend fun multipleInstanceComposition() {
    val crdt1 = CRDTChannelEngine("node1", EmptyCoroutineContext)
    val crdt2 = CRDTChannelEngine("node2", EmptyCoroutineContext)
    val kademlia1 = DHTFactory.createKademliaNode(NUID.random(), EmptyCoroutineContext)
    val kademlia2 = DHTFactory.createKademliaNode(NUID.random(), EmptyCoroutineContext)
    
    // Create separate contexts
    val context1 = EmptyCoroutineContext + crdt1 + kademlia1
    val context2 = EmptyCoroutineContext + crdt2 + kademlia2
    
    withContext(context1) {
        println("Context1 CRDT: ${coroutineContext[CRDTChannelEngine]?.nodeId}")  // "node1"
        println("Context1 Kademlia: ${coroutineContext[IKademliaNode]?.nodeId}")  // kademlia1.nodeId
    }
    
    withContext(context2) {
        println("Context2 CRDT: ${coroutineContext[CRDTChannelEngine]?.nodeId}")  // "node2"
        println("Context2 Kademlia: ${coroutineContext[IKademliaNode]?.nodeId}")  // kademlia2.nodeId
    }
}

/**
 * Example 7: Context composition with dynamic building
 */
suspend fun dynamicContextBuilding() {
    var context: CoroutineContext = EmptyCoroutineContext
    
    // Build context dynamically based on conditions
    context += ChannelService(ChannelProvider.Stub)
    
    if (kotlinx.datetime.Clock.System.now().toEpochMilliseconds() % 2 == 0L) {
        context += CRDTChannelEngine("even-node", context)
    } else {
        context += CRDTChannelEngine("odd-node", context)
    }
    
    context += DHTFactory.createKademliaNode(NUID.random(), context)
    
    withContext(context) {
        val crdt = coroutineContext[CRDTChannelEngine]!!
        val kademlia = coroutineContext[IKademliaNode]!!
        
        println("Dynamic context: ${crdt.nodeId}, ${kademlia.nodeId}")
    }
}

/**
 * Example 8: Context composition with service dependencies
 */
suspend fun serviceDependencyComposition() {
    // Create base context with channel service
    val baseContext = EmptyCoroutineContext + ChannelService(ChannelProvider.Stub)
    
    // Add services that depend on the base context
    val fullContext = baseContext + 
        CRDTChannelEngine("dependent-node", baseContext) +
        DHTFactory.createKademliaNode(NUID.random(), baseContext) +
        ChannelizedRequestFactory(baseContext)
    
    withContext(fullContext) {
        val channel = coroutineContext[ChannelService]!!
        val crdt = coroutineContext[CRDTChannelEngine]!!
        val kademlia = coroutineContext[IKademliaNode]!!
        val requests = coroutineContext[ChannelizedRequestFactory]!!
        
        println("Service dependencies satisfied: ${crdt.nodeId}, ${kademlia.nodeId}")
    }
}

/**
 * Example 9: Context composition with validation utilities
 */
suspend fun validationUtilityComposition() {
    val context = EmptyCoroutineContext + 
        ChannelService(ChannelProvider.Stub) +
        CRDTChannelEngine("validated-node", EmptyCoroutineContext) +
        DHTFactory.createKademliaNode(NUID.random(), EmptyCoroutineContext)
    
    // Use validation utilities
    val validation = ContextValidation.validateRequiredKeys(
        context,
        ChannelService.Key,
        CRDTChannelEngine.Key,
        IKademliaNode.Key
    )
    
    if (validation.isSuccess) {
        withContext(context) {
            println("Context validation passed")
        }
    } else {
        println("Context validation failed: ${validation.exceptionOrNull()?.message}")
    }
}

/**
 * Example 10: Context composition with type safety
 */
suspend fun typeSafeContextComposition(): String {
    val context = EmptyCoroutineContext + 
        ChannelService(ChannelProvider.Stub) +
        CRDTChannelEngine("type-safe-node", EmptyCoroutineContext) +
        DHTFactory.createKademliaNode(NUID.random(), EmptyCoroutineContext)
    
    return withContext(context) {
        when {
            context[ChannelService] == null ->
                Result.failure(IllegalStateException("ChannelService required"))
            context[CRDTChannelEngine] == null ->
                Result.failure(IllegalStateException("CRDTChannelEngine required"))
            context[IKademliaNode] == null ->
                Result.failure(IllegalStateException("IKademliaNode required"))
            else -> Result.success("Type-safe composition successful")
        }.getOrThrow()
    }
}

/**
 * Example 11: Context composition with metaverse agent
 */
suspend fun metaverseAgentComposition() {
    val context = EmptyCoroutineContext + 
        ChannelService(ChannelProvider.Stub) +
        CRDTChannelEngine("metaverse-node", EmptyCoroutineContext) +
        DHTFactory.createKademliaNode(NUID.random(), EmptyCoroutineContext) +
        DHTFactory.createMetaverseAgent(
            DHTFactory.createKademliaNode(NUID.random(), EmptyCoroutineContext),
            "metaverse-agent"
        )
    
    withContext(context) {
        val crdt = coroutineContext[CRDTChannelEngine]!!
        val kademlia = coroutineContext[IKademliaNode]!!
        val metaverse = coroutineContext[IMetaverseAgent]!!
        
        println("Metaverse context: ${crdt.nodeId}, ${kademlia.nodeId}, ${metaverse.agentId}")
    }
}

/**
 * Example 12: Context composition with error recovery
 */
suspend fun errorRecoveryComposition(): String {
    return try {
        // Try with problematic context
        val problematicContext = EmptyCoroutineContext + 
            CRDTChannelEngine("problematic-node", EmptyCoroutineContext)
        
        withContext(problematicContext) {
            throw RuntimeException("Simulated error")
        }
    } catch (e: Exception) {
        // Recover with safe context
        val safeContext = EmptyCoroutineContext + 
            ChannelService(ChannelProvider.Stub) +
            CRDTChannelEngine("safe-node", EmptyCoroutineContext)
        
        withContext(safeContext) {
            "Recovered from error with safe context"
        }
    }
}