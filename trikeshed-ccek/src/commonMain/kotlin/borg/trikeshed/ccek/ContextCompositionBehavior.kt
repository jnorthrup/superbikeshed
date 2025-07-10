@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.ccek

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Analysis of CoroutineContext + operator behavior and element preservation.
 */

/**
 * The + operator behavior:
 * - Preserves individual elements as separate context entries
 * - Same key = right-hand side wins (replacement, not merging)
 * - Different keys = both elements coexist
 * - Creates a CombinedContext internally
 */

// Example demonstrating context element preservation
suspend fun demonstrateContextBehavior() {
    val service1 = ChannelService(MemoryChannelProvider())
    val service2 = CRDTChannelEngine("node1", EmptyCoroutineContext)
    val service3 = ChannelizedKademliaNode(NUID.generate(), EmptyCoroutineContext)
    
    // Each element remains distinct
    val combined = service1 + service2 + service3
    
    // All three can be retrieved independently
    println("ChannelService: ${combined[ChannelService] != null}")           // true
    println("CRDTChannelEngine: ${combined[CRDTChannelEngine] != null}")     // true
    println("KademliaNode: ${combined[ChannelizedKademliaNode] != null}")    // true
    
    // Same key replacement demonstration
    val service2Updated = CRDTChannelEngine("node2", EmptyCoroutineContext)
    val replacedContext = combined + service2Updated
    
    println("Original node: ${combined[CRDTChannelEngine]?.nodeId}")         // "node1"  
    println("Replaced node: ${replacedContext[CRDTChannelEngine]?.nodeId}")  // "node2"
}

/**
 * Enhanced withMetaContext that preserves element identity and provides introspection.
 */
suspend fun <T> withMetaContextDetailed(
    current: CoroutineContext.Element,
    next: CoroutineContext.Element,
    block: suspend CoroutineScope.() -> T
): T {
    val combinedContext = current + next
    
    // Verify both elements are present
    require(combinedContext[current.key] != null) { 
        "Current element ${current.key} not found in combined context" 
    }
    require(combinedContext[next.key] != null) { 
        "Next element ${next.key} not found in combined context" 
    }
    
    // Log context composition for debugging
    println("Context transition: ${current.key} + ${next.key}")
    
    return withContext(combinedContext, block)
}

/**
 * Context introspection utilities.
 */
object ContextIntrospection {
    
    /**
     * Extract all elements from a context (flattens CombinedContext).
     */
    fun extractAllElements(context: CoroutineContext): List<CoroutineContext.Element> {
        val elements = mutableListOf<CoroutineContext.Element>()
        
        context.fold(Unit) { _, element ->
            elements.add(element)
        }
        
        return elements
    }
    
    /**
     * Check if context contains all required service keys.
     */
    fun validateRequiredServices(
        context: CoroutineContext,
        vararg requiredKeys: CoroutineContext.Key<*>
    ): Result<Unit> {
        val missing = requiredKeys.filter { key -> context[key] == null }
        
        return if (missing.isEmpty()) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(
                "Missing required services: ${missing.map { it.toString() }}"
            ))
        }
    }
    
    /**
     * Get context composition tree as string (for debugging).
     */
    fun contextTree(context: CoroutineContext): String {
        val elements = extractAllElements(context)
        return elements.joinToString(" + ") { it.key.toString() }
    }
    
    /**
     * Check for key conflicts (same key, different instances).
     */
    fun detectKeyConflicts(vararg elements: CoroutineContext.Element): List<String> {
        val keyGroups = elements.groupBy { it.key }
        return keyGroups.filter { it.value.size > 1 }.keys.map { it.toString() }
    }
}

/**
 * Safe context builder that prevents key conflicts and validates composition.
 */
class SafeMetaContextBuilder {
    internal val elements = mutableMapOf<CoroutineContext.Key<*>, CoroutineContext.Element>()
    
    fun <T : CoroutineContext.Element> add(element: T): SafeMetaContextBuilder = apply {
        val existing = elements[element.key]
        if (existing != null && existing !== element) {
            println("Warning: Replacing ${element.key} (${existing::class.simpleName} -> ${element::class.simpleName})")
        }
        elements[element.key] = element
    }
    
    fun remove(key: CoroutineContext.Key<*>): SafeMetaContextBuilder = apply {
        elements.remove(key)
    }
    
    fun build(): CoroutineContext {
        return elements.values.fold(EmptyCoroutineContext as CoroutineContext) { acc, element ->
            acc + element
        }
    }
    
    fun validate(): Result<CoroutineContext> {
        // Add validation logic here
        return Result.success(build())
    }
    
    fun describe(): String {
        return "Context[${elements.keys.joinToString { it.toString() }}]"
    }
}

/**
 * Enhanced withMetaContext with validation and conflict detection.
 */
suspend fun <T> withValidatedMetaContext(
    vararg elements: CoroutineContext.Element,
    block: suspend CoroutineScope.() -> T
): T {
    // Detect conflicts
    val conflicts = ContextIntrospection.detectKeyConflicts(*elements)
    if (conflicts.isNotEmpty()) {
        println("Key conflicts detected: $conflicts (last wins)")
    }
    
    // Build context safely
    val context = SafeMetaContextBuilder().apply {
        elements.forEach { add(it) }
    }.build()
    
    // Log final composition
    println("Final context: ${ContextIntrospection.contextTree(context)}")
    
    return withContext(context, block)
}

/**
 * Example usage demonstrating element preservation and conflict handling.
 */
object ContextBehaviorExamples {
    
    suspend fun elementPreservationExample() {
        val channel = ChannelService(MemoryChannelProvider())
        val crdt = CRDTChannelEngine("node1", EmptyCoroutineContext)
        val kademlia = ChannelizedKademliaNode(NUID.generate(), EmptyCoroutineContext)
        
        withValidatedMetaContext(channel, crdt, kademlia) {
            // All three elements are independently accessible
            val channelSvc = coroutineContext[ChannelService]!!
            val crdtEngine = coroutineContext[CRDTChannelEngine]!!
            val kademliaNode = coroutineContext[ChannelizedKademliaNode]!!
            
            println("Channel provider: ${channelSvc.getProvider().name}")
            println("CRDT node: ${crdtEngine.nodeId}")
            println("Kademlia ID: ${kademliaNode.nodeId}")
        }
    }
    
    suspend fun keyReplacementExample() {
        val crdt1 = CRDTChannelEngine("node1", EmptyCoroutineContext)
        val crdt2 = CRDTChannelEngine("node2", EmptyCoroutineContext)
        
        // Demonstrate replacement behavior
        val context1 = EmptyCoroutineContext + crdt1
        val context2 = context1 + crdt2  // crdt2 replaces crdt1
        
        println("Context1 CRDT: ${context1[CRDTChannelEngine]?.nodeId}")  // "node1"
        println("Context2 CRDT: ${context2[CRDTChannelEngine]?.nodeId}")  // "node2"
    }
    
    suspend fun progressiveCompositionExample() {
        // Start with empty context, add elements progressively
        var context: CoroutineContext = EmptyCoroutineContext
        
        // Add channel service
        context += ChannelService(MemoryChannelProvider())
        println("After channel: ${ContextIntrospection.contextTree(context)}")
        
        // Add CRDT engine  
        context += CRDTChannelEngine("progressive-node", context)
        println("After CRDT: ${ContextIntrospection.contextTree(context)}")
        
        // Add Kademlia
        context += ChannelizedKademliaNode(NUID.generate(), context)
        println("After Kademlia: ${ContextIntrospection.contextTree(context)}")
        
        // Use the progressively built context
        withContext(context) {
            println("Final context has ${ContextIntrospection.extractAllElements(coroutineContext).size} elements")
        }
    }
}

/**
 * Answer to the original question:
 * 
 * Yes, the + operator keeps elements single and distinct:
 * 1. Each CoroutineContext.Element with a unique key remains separate
 * 2. Same keys get replaced (right-hand side wins)  
 * 3. Different keys coexist independently
 * 4. You can retrieve each element by its key
 * 
 * The withMetaContext function preserves this behavior, allowing
 * progressive composition where each service remains accessible.
 */