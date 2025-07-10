@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.ccek

import borg.trikeshed.lib.*
import borg.trikeshed.channel.api.*
import borg.trikeshed.dht.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Meta-context composition for progressive service layering.
 * Each transition composes the next context in the CCEK architecture.
 */

// Core composition function - progressive with + operator
suspend fun <T> withMetaContext(
    current: CoroutineContext.Element,
    next: CoroutineContext.Element,
    block: suspend CoroutineScope.() -> T
): T = withContext(current + next, block)

// Bulk composition function - multiple elements with vararg
suspend fun <T> withMultiContext(
    vararg elements: CoroutineContext.Element,
    block: suspend CoroutineScope.() -> T
): T = withContext(
    elements.fold(EmptyCoroutineContext as CoroutineContext) { acc, element -> 
        acc + element 
    }, 
    block
)

/**
 * Progressive context builders for channelized architecture.
 */

/**
 * Base: Channel Service Foundation
 */
suspend fun <T> withChannelContext(
    provider: ChannelProvider,
    block: suspend CoroutineScope.() -> T
): T = withContext(ChannelService(provider), block)

/**
 * Layer 1: Channel + CRDT Engine
 */
suspend fun <T> withCRDTContext(
    channelService: ChannelService,
    nodeId: String,
    block: suspend CoroutineScope.() -> T
): T = withMetaContext(
    channelService,
    CRDTChannelEngine(nodeId, coroutineContext),
    block
)

/**
 * Layer 2: CRDT + Kademlia DHT
 */
suspend fun <T> withKademliaContext(
    crdtEngine: CRDTChannelEngine,
    nodeId: NUID,
    block: suspend CoroutineScope.() -> T
): T = withMetaContext(
    crdtEngine,
    ChannelizedKademliaNode(nodeId, coroutineContext),
    block
)

/**
 * Layer 3: Kademlia + RequestFactory
 */
suspend fun <T> withRequestFactoryContext(
    kademliaNode: ChannelizedKademliaNode,
    block: suspend CoroutineScope.() -> T
): T = withMetaContext(
    kademliaNode,
    ChannelizedRequestFactory(coroutineContext),
    block
)

/**
 * Layer 4: RequestFactory + Metaverse Agent
 */
suspend fun <T> withMetaverseContext(
    requestFactory: ChannelizedRequestFactory,
    agentId: String,
    block: suspend CoroutineScope.() -> T
): T = withMetaContext(
    requestFactory,
    MetaverseKademliaAgent(coroutineContext[ChannelizedKademliaNode]!!, agentId),
    block
)

/**
 * Full stack composition: Channel -> CRDT -> Kademlia -> RequestFactory -> Metaverse
 */
suspend fun <T> withFullMetaContext(
    provider: ChannelProvider,
    nodeId: String,
    kademliaId: NUID,
    agentId: String,
    block: suspend CoroutineScope.() -> T
): T = 
    withChannelContext(provider) {
        withCRDTContext(coroutineContext[ChannelService]!!, nodeId) {
            withKademliaContext(coroutineContext[CRDTChannelEngine]!!, kademliaId) {
                withRequestFactoryContext(coroutineContext[ChannelizedKademliaNode]!!) {
                    withMetaverseContext(coroutineContext[ChannelizedRequestFactory]!!, agentId, block)
                }
            }
        }
    }

/**
 * Context transition helpers for protocol-specific workflows.
 */

/**
 * SSH Protocol Context Transition
 */
suspend fun <T> withSSHProtocolContext(
    baseContext: CoroutineContext,
    sshConfig: SSHConfig,
    block: suspend CoroutineScope.() -> T
): T = withMetaContext(
    baseContext[ChannelService]!!,
    SSHChannelAdapter(sshConfig),
    block
)

/**
 * HTTP Protocol Context Transition
 */
suspend fun <T> withHTTPProtocolContext(
    baseContext: CoroutineContext,
    httpProtocol: HTTPProtocol,
    block: suspend CoroutineScope.() -> T
): T = withMetaContext(
    baseContext[ChannelizedRequestFactory]!!,
    HTTPProtocolAdapter(httpProtocol),
    block
)

/**
 * QUIC Protocol Context Transition
 */
suspend fun <T> withQUICProtocolContext(
    baseContext: CoroutineContext,
    quicConfig: QUICConfig,
    block: suspend CoroutineScope.() -> T
): T = withMetaContext(
    baseContext[ChannelService]!!,
    QUICProtocolAdapter(quicConfig),
    block
)

/**
 * Recording Context Transition for Testing
 */
suspend fun <T> withRecordingContext(
    baseContext: CoroutineContext,
    sessionId: String,
    block: suspend CoroutineScope.() -> T
): T = withMetaContext(
    baseContext[ChannelService]!!,
    RecordingService(FileChannelRecorder(sessionId)),
    block
)

/**
 * Enhanced composition functions with validation and conflict detection.
 */

// Validated bulk composition with conflict detection
suspend fun <T> withValidatedMultiContext(
    vararg elements: CoroutineContext.Element,
    block: suspend CoroutineScope.() -> T
): T {
    // Detect key conflicts
    val keyGroups = elements.groupBy { it.key }
    val conflicts = keyGroups.filter { it.value.size > 1 }.keys
    
    if (conflicts.isNotEmpty()) {
        println("Context key conflicts detected: ${conflicts.map { it.toString() }} (last element wins)")
    }
    
    return withMultiContext(*elements, block = block)
}

// Safe composition that validates required dependencies
suspend fun <T> withRequiredContext(
    vararg elements: CoroutineContext.Element,
    requiredKeys: Array<CoroutineContext.Key<*>> = emptyArray(),
    block: suspend CoroutineScope.() -> T
): T = withMultiContext(*elements) {
    // Validate all required keys are present
    val missing = requiredKeys.filter { key -> coroutineContext[key] == null }
    if (missing.isNotEmpty()) {
        throw IllegalStateException("Missing required context elements: ${missing.map { it.toString() }}")
    }
    
    block()
}

/**
 * Context introspection and debugging utilities.
 */
object ContextUtils {
    
    /**
     * Extract all elements from a context.
     */
    fun extractAllElements(context: CoroutineContext): List<CoroutineContext.Element> {
        val elements = mutableListOf<CoroutineContext.Element>()
        context.fold(Unit) { _, element ->
            elements.add(element)
        }
        return elements
    }
    
    /**
     * Get context composition tree as string.
     */
    fun contextTree(context: CoroutineContext): String {
        val elements = extractAllElements(context)
        return elements.joinToString(" + ") { it.key.toString() }
    }
    
    /**
     * Validate context contains required services.
     */
    fun validateServices(
        context: CoroutineContext,
        vararg requiredKeys: CoroutineContext.Key<*>
    ): Result<Unit> {
        val missing = requiredKeys.filter { key -> context[key] == null }
        return if (missing.isEmpty()) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(
                "Missing services: ${missing.map { it.toString() }}"
            ))
        }
    }
    
    /**
     * Check for key conflicts in element array.
     */
    fun detectConflicts(vararg elements: CoroutineContext.Element): List<String> {
        val keyGroups = elements.groupBy { it.key }
        return keyGroups.filter { it.value.size > 1 }.keys.map { it.toString() }
    }
    
    /**
     * Debug context composition.
     */
    fun debugContext(context: CoroutineContext): String {
        val elements = extractAllElements(context)
        return buildString {
            appendLine("CoroutineContext Debug:")
            appendLine("  Total elements: ${elements.size}")
            elements.forEach { element ->
                appendLine("  - ${element.key}: ${element::class.simpleName}")
            }
        }
    }
}

/**
 * Context composition DSL for fluent service assembly.
 */
class MetaContextBuilder {
    internal var context: CoroutineContext = EmptyCoroutineContext
    
    fun channel(provider: ChannelProvider) = apply {
        context += ChannelService(provider)
    }
    
    fun crdt(nodeId: String) = apply {
        context += CRDTChannelEngine(nodeId, context)
    }
    
    fun kademlia(nodeId: NUID) = apply {
        context += ChannelizedKademliaNode(nodeId, context)
    }
    
    fun requestFactory() = apply {
        context += ChannelizedRequestFactory(context)
    }
    
    fun metaverse(agentId: String) = apply {
        val kademlia = context[ChannelizedKademliaNode]!!
        context += MetaverseKademliaAgent(kademlia, agentId)
    }
    
    fun recording(sessionId: String) = apply {
        context += RecordingService(FileChannelRecorder(sessionId))
    }
    
    suspend fun <T> execute(block: suspend CoroutineScope.() -> T): T {
        return withContext(context, block)
    }
    
    fun build(): CoroutineContext = context
}

/**
 * DSL function to build meta-contexts fluently.
 */
fun metaContext(builder: MetaContextBuilder.() -> Unit): MetaContextBuilder {
    return MetaContextBuilder().apply(builder)
}

/**
 * Example protocol adapter placeholders for context transitions.
 */
class HTTPProtocolAdapter(val protocol: HTTPProtocol) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<HTTPProtocolAdapter>
    override val key = Key
}

class QUICProtocolAdapter(val config: QUICConfig) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<QUICProtocolAdapter>
    override val key = Key
}

data class QUICConfig(
    val version: String = "1.0",
    val alpn: List<String> = listOf("h3")
)

data class SSHConfig(
    val version: String = "2.0",
    val algorithms: List<String> = listOf("ssh-ed25519")
)

/**
 * Usage examples demonstrating both progressive (+) and bulk (vararg) composition patterns.
 */
object MetaContextExamples {
    
    /**
     * Example 1: Basic CRDT collaboration setup
     */
    suspend fun setupCRDTCollaboration(provider: ChannelProvider, nodeId: String) {
        withChannelContext(provider) {
            withCRDTContext(coroutineContext[ChannelService]!!, nodeId) {
                val engine = coroutineContext[CRDTChannelEngine]!!
                val waveletId = engine.createWavelet("Initial document content")
                engine.insertText(waveletId, 0, "Hello, collaborative world!")
            }
        }
    }
    
    /**
     * Example 2: DHT peer discovery with CRDT sync
     */
    suspend fun setupDistributedCRDT(
        provider: ChannelProvider,
        nodeId: String,
        kademliaId: NUID
    ) {
        withFullMetaContext(provider, nodeId, kademliaId, "agent-${nodeId}") {
            val crdt = coroutineContext[CRDTChannelEngine]!!
            val kademlia = coroutineContext[ChannelizedKademliaNode]!!
            
            // Create subnet for CRDT collaboration
            kademlia.joinSubnet(
                "crdt-collaborators",
                ConcentricSubnet.SubnetType.APPLICATION,
                SubnetCriteria(nodeId, setOf("crdt", "collaborative"))
            )
            
            // Setup CRDT document
            val waveletId = crdt.createWavelet("Distributed document")
            
            // Sync CRDT operations through DHT
            crdt.operations.collect { operation ->
                kademlia.store(
                    operation.id.value.encodeToByteArray(),
                    kotlinx.serialization.json.Json.encodeToString(operation).encodeToByteArray()
                )
            }
        }
    }
    
    /**
     * Example 3: HTTP transaction with full context stack
     */
    suspend fun executeHTTPTransaction(
        provider: ChannelProvider,
        nodeId: String,
        url: String
    ) {
        metaContext {
            channel(provider)
            crdt(nodeId)
            requestFactory()
        }.execute {
            val factory = coroutineContext[ChannelizedRequestFactory]!!
            
            withHTTPProtocolContext(coroutineContext, HTTPProtocol.HTTP_2) {
                val request = ChannelizedHTTPRequest(
                    method = "GET",
                    path = url,
                    headers = mapOf("User-Agent" to listOf("TrikeShed-Channelized/1.0"))
                )
                
                val result = factory.executeRoundtrip(HTTPProtocol.HTTP_2, request)
                println("HTTP/2 transaction completed: ${result.isSuccess}")
            }
        }
    }
    
    /**
     * Example 4: Bulk composition with vararg - efficient service setup
     */
    suspend fun bulkServiceSetup(provider: ChannelProvider, nodeId: String) {
        // All services at once with vararg
        withMultiContext(
            ChannelService(provider),
            CRDTChannelEngine(nodeId, EmptyCoroutineContext),
            ChannelizedKademliaNode(NUID.generate(), EmptyCoroutineContext),
            ChannelizedRequestFactory(EmptyCoroutineContext),
            RecordingService(FileChannelRecorder())
        ) {
            // All services immediately available
            val channel = coroutineContext[ChannelService]!!
            val crdt = coroutineContext[CRDTChannelEngine]!!
            val kademlia = coroutineContext[ChannelizedKademliaNode]!!
            val requests = coroutineContext[ChannelizedRequestFactory]!!
            val recording = coroutineContext[RecordingService]!!
            
            println("Bulk setup complete with ${ContextUtils.extractAllElements(coroutineContext).size} services")
        }
    }
    
    /**
     * Example 5: Validated composition with conflict detection
     */
    suspend fun validatedComposition(provider: ChannelProvider, nodeId: String) {
        val crdt1 = CRDTChannelEngine("node1", EmptyCoroutineContext)
        val crdt2 = CRDTChannelEngine("node2", EmptyCoroutineContext) // Conflict!
        
        withValidatedMultiContext(
            ChannelService(provider),
            crdt1,
            crdt2, // This will trigger conflict warning
            ChannelizedRequestFactory(EmptyCoroutineContext)
        ) {
            // crdt2 wins due to + operator behavior
            val finalCrdt = coroutineContext[CRDTChannelEngine]!!
            println("Final CRDT node: ${finalCrdt.nodeId}") // Will be "node2"
        }
    }
    
    /**
     * Example 6: Required dependencies validation
     */
    suspend fun dependencyValidation(provider: ChannelProvider) {
        try {
            withRequiredContext(
                ChannelService(provider),
                // Missing CRDTChannelEngine intentionally
                requiredKeys = arrayOf(ChannelService.Key, CRDTChannelEngine.Key)
            ) {
                // This will throw because CRDTChannelEngine is missing
                println("This won't execute")
            }
        } catch (e: IllegalStateException) {
            println("Validation caught missing dependency: ${e.message}")
        }
    }
    
    /**
     * Example 7: Context debugging and introspection
     */
    suspend fun contextDebugging(provider: ChannelProvider) {
        withMultiContext(
            ChannelService(provider),
            CRDTChannelEngine("debug-node", EmptyCoroutineContext),
            ChannelizedRequestFactory(EmptyCoroutineContext)
        ) {
            // Debug current context
            println(ContextUtils.debugContext(coroutineContext))
            println("Context tree: ${ContextUtils.contextTree(coroutineContext)}")
            
            // Validate specific services
            val validation = ContextUtils.validateServices(
                coroutineContext,
                ChannelService.Key,
                CRDTChannelEngine.Key
            )
            println("Validation result: ${validation.isSuccess}")
        }
    }
    
    /**
     * Example 8: Recording session for protocol testing (updated)
     */
    suspend fun recordProtocolSession(provider: ChannelProvider, sessionId: String) {
        // Use bulk composition for recording setup
        withMultiContext(
            ChannelService(provider),
            RecordingService(FileChannelRecorder()),
            ChannelizedRequestFactory(EmptyCoroutineContext)
        ) {
            val recording = coroutineContext[RecordingService]!!
            val requests = coroutineContext[ChannelizedRequestFactory]!!
            
            recording.startRecording(sessionId)
            
            // Perform recorded operations
            val result = requests.executeRoundtrip(
                HTTPProtocol.HTTP_2,
                ChannelizedHTTPRequest("GET", "/test", emptyMap())
            )
            
            recording.stopRecording()
            val events = recording.replaySession(sessionId)
            println("Recorded ${events.size} channel events")
        }
    }
    
    /**
     * Example 9: Progressive vs Bulk comparison
     */
    suspend fun compositionPatternComparison(provider: ChannelProvider, nodeId: String) {
        // Progressive pattern (+ operator) - clean chain
        withChannelContext(provider) {
            withCRDTContext(coroutineContext[ChannelService]!!, nodeId) {
                withRequestFactoryContext(coroutineContext[CRDTChannelEngine]!!) {
                    println("Progressive: ${ContextUtils.contextTree(coroutineContext)}")
                }
            }
        }
        
        // Bulk pattern (vararg) - efficient setup
        withMultiContext(
            ChannelService(provider),
            CRDTChannelEngine(nodeId, EmptyCoroutineContext),
            ChannelizedRequestFactory(EmptyCoroutineContext)
        ) {
            println("Bulk: ${ContextUtils.contextTree(coroutineContext)}")
        }
        
        // Both produce equivalent contexts but different composition styles
    }
}

/**
 * Context validation helpers to ensure proper service composition.
 */
object ContextValidation {
    
    fun validateChannelContext(context: CoroutineContext): Result<Unit> {
        return if (context[ChannelService] != null) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("ChannelService required in context"))
        }
    }
    
    fun validateCRDTContext(context: CoroutineContext): Result<Unit> {
        return when {
            context[ChannelService] == null -> 
                Result.failure(IllegalStateException("ChannelService required for CRDT"))
            context[CRDTChannelEngine] == null -> 
                Result.failure(IllegalStateException("CRDTChannelEngine required"))
            else -> Result.success(Unit)
        }
    }
    
    fun validateFullContext(context: CoroutineContext): Result<Unit> {
        val required = listOf(
            ChannelService::class,
            CRDTChannelEngine::class,
            ChannelizedKademliaNode::class,
            ChannelizedRequestFactory::class
        )
        
        required.forEach { serviceClass ->
            if (context[serviceClass as CoroutineContext.Key<*>] == null) {
                return Result.failure(IllegalStateException("${serviceClass.simpleName} required"))
            }
        }
        
        return Result.success(Unit)
    }
}