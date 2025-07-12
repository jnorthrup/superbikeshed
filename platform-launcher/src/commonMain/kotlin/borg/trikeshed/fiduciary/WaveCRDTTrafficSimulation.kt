package borg.trikeshed.fiduciary

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.datetime.*
import kotlin.time.*
import kotlin.random.Random

/**
 * Wave CRDT Traffic Simulation using channelized coroutine composition.
 * 
 * Simulates distributed Wave document editing with realistic network conditions,
 * operation conflicts, and convergence patterns using Kotlin's Channel API.
 */

/**
 * Traffic pattern for Wave operations
 */
sealed class TrafficPattern {
    object Burst : TrafficPattern()
    data class Steady(val opsPerSecond: Double) : TrafficPattern()
    data class Poisson(val lambda: Double) : TrafficPattern()
    data class Replay(val operations: Indexed<TimedOperation>) : TrafficPattern()
}

@Serializable
data class TimedOperation(
    val operation: SerializedWaveOp,
    val timestamp: Instant,
    val participantId: ParticipantId
)

/**
 * Network conditions for simulation
 */
data class NetworkConditions(
    val latencyMs: IntRange = 10..100,
    val jitterMs: IntRange = 0..20,
    val packetLoss: Double = 0.01,
    val bandwidth: Long = 1_000_000 // bytes per second
)

/**
 * Wave CRDT Traffic Node - represents a distributed participant
 */
class WaveCRDTTrafficNode(
    val nodeId: ParticipantId,
    val networkConditions: NetworkConditions,
    internal val crdtEngine: WaveCRDTEngine = WaveCRDTEngine()
) {
    // Incoming operation channel with buffering
    internal val incomingOps = Channel<WaveOperationData>(capacity = Channel.BUFFERED)
    
    // Outgoing operation broadcast
    internal val _outgoingOps = MutableSharedFlow<WaveOperationData>(
        replay = 10,
        extraBufferCapacity = 100
    )
    val outgoingOps: SharedFlow<WaveOperationData> = _outgoingOps.asSharedFlow()
    
    // Local vector clock
    internal val vectorClock = VectorClock(nodeId)
    
    // Metrics
    internal val _metrics = MutableStateFlow(NodeMetrics())
    val metrics: StateFlow<NodeMetrics> = _metrics.asStateFlow()
    
    /**
     * Start the node's operation processing
     */
    fun start(scope: CoroutineScope) {
        // Process incoming operations
        scope.launch {
            for (op in incomingOps) {
                processIncomingOperation(op)
            }
        }
        
        // Periodic state synchronization
        scope.launch {
            while (isActive) {
                delay(1000)
                synchronizeState()
            }
        }
    }
    
    /**
     * Submit a local operation
     */
    suspend fun submitOperation(
        documentId: DocumentId,
        waveletId: WaveletId,
        operation: WaveDocumentOp
    ) {
        val startTime = Clock.System.now()
        
        // Apply locally
        val result = crdtEngine.applyOperation(
            documentId, waveletId, nodeId, operation, vectorClock
        )
        
        when (result) {
            is Either.Left -> {
                // Broadcast to network
                _outgoingOps.emit(result.value)
                
                // Update metrics
                _metrics.update { it.copy(
                    operationsSent = it.operationsSent + 1,
                    lastOperationLatency = Clock.System.now() - startTime
                ) }
            }
            is Either.Right -> {
                _metrics.update { it.copy(
                    operationsFailed = it.operationsFailed + 1
                ) }
            }
        }
    }
    
    /**
     * Receive operation from network with simulated conditions
     */
    suspend fun receiveOperation(op: WaveOperationData) {
        // Simulate network latency
        delay(networkConditions.latencyMs.random() + networkConditions.jitterMs.random())
        
        // Simulate packet loss
        if (Random.nextDouble() < networkConditions.packetLoss) {
            _metrics.update { it.copy(packetsLost = it.packetsLost + 1) }
            return
        }
        
        // Queue for processing
        incomingOps.send(op)
        _metrics.update { it.copy(operationsReceived = it.operationsReceived + 1) }
    }
    
    internal suspend fun processIncomingOperation(op: WaveOperationData) {
        // Update vector clock
        vectorClock.update(op.vectorClock)
        
        // Check for conflicts
        val conflicts = detectConflicts(op)
        if (conflicts.isNotEmpty()) {
            _metrics.update { it.copy(conflictsDetected = it.conflictsDetected + conflicts.size) }
        }
        
        // Apply operation (transformation happens inside engine)
        // In real implementation, would need to extract document/wavelet IDs from operation
    }
    
    internal fun detectConflicts(op: WaveOperationData): List<WaveOperationData> {
        // Simplified conflict detection based on vector clocks
        return emptyList() // Would check operation queue for concurrent ops
    }
    
    internal suspend fun synchronizeState() {
        // Periodic state sync logic
        _metrics.update { it.copy(
            lastSyncTime = Clock.System.now()
        ) }
    }
}

data class NodeMetrics(
    val operationsSent: Long = 0,
    val operationsReceived: Long = 0,
    val operationsFailed: Long = 0,
    val conflictsDetected: Long = 0,
    val packetsLost: Long = 0,
    val lastOperationLatency: Duration = Duration.ZERO,
    val lastSyncTime: Instant = Clock.System.now()
)

/**
 * Wave CRDT Traffic Simulator
 */
class WaveCRDTTrafficSimulator(
    internal val nodeCount: Int = 3,
    internal val documentId: DocumentId = DocumentId("sim-doc"),
    internal val waveletId: WaveletId = WaveletId("sim-wavelet")
) {
    internal val nodes = mutableListOf<WaveCRDTTrafficNode>()
    internal val networkRouter = NetworkRouter()
    
    /**
     * Initialize simulation nodes
     */
    fun initialize(): WaveCRDTTrafficSimulator {
        repeat(nodeCount) { i ->
            val node = WaveCRDTTrafficNode(
                nodeId = ParticipantId("node-$i"),
                networkConditions = NetworkConditions(
                    latencyMs = (10 * (i + 1))..(50 * (i + 1)),
                    jitterMs = 0..10,
                    packetLoss = 0.01 * (i + 1)
                )
            )
            nodes.add(node)
        }
        return this
    }
    
    /**
     * Run simulation with specified traffic pattern
     */
    suspend fun simulate(
        pattern: TrafficPattern,
        duration: Duration,
        scope: CoroutineScope
    ): SimulationResults = coroutineScope {
        // Start all nodes
        nodes.forEach { it.start(scope) }
        
        // Setup network routing
        setupNetworkRouting()
        
        // Generate traffic based on pattern
        val trafficJob = launch {
            generateTraffic(pattern, duration)
        }
        
        // Collect metrics
        val metricsJob = launch {
            collectMetrics(duration)
        }
        
        // Wait for simulation to complete
        trafficJob.join()
        metricsJob.join()
        
        // Compute results
        computeResults()
    }
    
    internal fun setupNetworkRouting() {
        // Setup broadcast routing between nodes
        nodes.forEach { sender ->
            sender.outgoingOps
                .onEach { op ->
                    // Broadcast to all other nodes
                    nodes.filter { it.nodeId != sender.nodeId }
                        .forEach { receiver ->
                            networkRouter.route(op, sender, receiver)
                        }
                }
                .launchIn(GlobalScope) // Use appropriate scope in production
        }
    }
    
    internal suspend fun generateTraffic(pattern: TrafficPattern, duration: Duration) {
        val endTime = Clock.System.now() + duration
        
        when (pattern) {
            is TrafficPattern.Burst -> generateBurstTraffic(endTime)
            is TrafficPattern.Steady -> generateSteadyTraffic(pattern.opsPerSecond, endTime)
            is TrafficPattern.Poisson -> generatePoissonTraffic(pattern.lambda, endTime)
            is TrafficPattern.Replay -> replayTraffic(pattern.operations, endTime)
        }
    }
    
    internal suspend fun generateBurstTraffic(endTime: Instant) {
        while (Clock.System.now() < endTime) {
            // Generate burst of operations
            val burstSize = (5..20).random()
            repeat(burstSize) {
                val node = nodes.random()
                val op = generateRandomOperation()
                node.submitOperation(documentId, waveletId, op)
            }
            
            // Wait between bursts
            delay((500..2000).random())
        }
    }
    
    internal suspend fun generateSteadyTraffic(opsPerSecond: Double, endTime: Instant) {
        val delayMs = (1000.0 / opsPerSecond).toLong()
        
        while (Clock.System.now() < endTime) {
            val node = nodes.random()
            val op = generateRandomOperation()
            node.submitOperation(documentId, waveletId, op)
            delay(delayMs)
        }
    }
    
    internal suspend fun generatePoissonTraffic(lambda: Double, endTime: Instant) {
        while (Clock.System.now() < endTime) {
            val node = nodes.random()
            val op = generateRandomOperation()
            node.submitOperation(documentId, waveletId, op)
            
            // Poisson process inter-arrival time
            val delay = (-ln(Random.nextDouble()) / lambda * 1000).toLong()
            delay(delay)
        }
    }
    
    internal suspend fun replayTraffic(operations: Indexed<TimedOperation>, endTime: Instant) {
        val startTime = Clock.System.now()
        
        operations.toList().forEach { timedOp ->
            val relativeDelay = timedOp.timestamp - operations.component2()(0).timestamp
            val actualDelay = startTime + relativeDelay.toKotlinDuration()
            
            delay((actualDelay - Clock.System.now()).inWholeMilliseconds.coerceAtLeast(0))
            
            if (Clock.System.now() >= endTime) return
            
            val node = nodes.find { it.nodeId == timedOp.participantId } ?: nodes.first()
            node.submitOperation(documentId, waveletId, timedOp.operation.toWaveOp())
        }
    }
    
    internal fun generateRandomOperation(): WaveDocumentOp {
        return when ((0..5).random()) {
            0 -> WaveDocumentOp.Retain((1..10).random())
            1 -> WaveDocumentOp.Insert(
                text = "text${(1000..9999).random()}",
                position = (0..100).random()
            )
            2 -> WaveDocumentOp.Delete(
                text = "text",
                position = (0..50).random()
            )
            3 -> WaveDocumentOp.ElementStart(
                type = "paragraph",
                attributes = mapOf("id" to "p${(100..999).random()}")
            )
            4 -> WaveDocumentOp.ElementEnd("paragraph")
            else -> WaveDocumentOp.AnnotationBoundary(
                changes = mapOf("style" to "bold"),
                position = (0..100).random()
            )
        }
    }
    
    internal suspend fun collectMetrics(duration: Duration) {
        val endTime = Clock.System.now() + duration
        val metricsHistory = mutableListOf<Map<ParticipantId, NodeMetrics>>()
        
        while (Clock.System.now() < endTime) {
            val snapshot = nodes.associate { it.nodeId to it.metrics.value }
            metricsHistory.add(snapshot)
            delay(100) // Collect metrics every 100ms
        }
    }
    
    internal fun computeResults(): SimulationResults {
        val nodeMetrics = nodes.associate { it.nodeId to it.metrics.value }
        
        return SimulationResults(
            nodeMetrics = nodeMetrics,
            totalOperations = nodeMetrics.values.sumOf { it.operationsSent },
            totalConflicts = nodeMetrics.values.sumOf { it.conflictsDetected },
            averageLatency = nodeMetrics.values
                .map { it.lastOperationLatency }
                .takeIf { it.isNotEmpty() }
                ?.let { latencies ->
                    latencies.reduce { acc, duration -> acc + duration } / latencies.size
                } ?: Duration.ZERO,
            convergenceTime = estimateConvergenceTime()
        )
    }
    
    internal fun estimateConvergenceTime(): Duration {
        // Simplified convergence estimation
        return 500.milliseconds * nodeCount
    }
}

data class SimulationResults(
    val nodeMetrics: Map<ParticipantId, NodeMetrics>,
    val totalOperations: Long,
    val totalConflicts: Long,
    val averageLatency: Duration,
    val convergenceTime: Duration
)

/**
 * Network router with channel-based traffic shaping
 */
class NetworkRouter {
    internal val routingChannels = mutableMapOf<Pair<ParticipantId, ParticipantId>, Channel<WaveOperationData>>()
    
    init {
        // Setup routing channels with traffic shaping
        GlobalScope.launch {
            for ((route, channel) in routingChannels) {
                launch {
                    for (op in channel) {
                        // Apply traffic shaping
                        delay(10) // Minimum inter-packet delay
                    }
                }
            }
        }
    }
    
    suspend fun route(
        operation: WaveOperationData,
        from: WaveCRDTTrafficNode,
        to: WaveCRDTTrafficNode
    ) {
        val route = from.nodeId to to.nodeId
        val channel = routingChannels.getOrPut(route) {
            Channel(capacity = 100)
        }
        
        // Non-blocking send with overflow handling
        channel.trySend(operation).onFailure {
            // Handle congestion
            to.metrics.value.let { metrics ->
                // Update congestion metrics
            }
        }
        
        // Deliver to destination
        to.receiveOperation(operation)
    }
}

/**
 * Traffic analysis extensions
 */
fun SimulationResults.analyze(): String = buildString {
    appendLine("=== Wave CRDT Traffic Simulation Results ===")
    appendLine("Total Operations: $totalOperations")
    appendLine("Total Conflicts: $totalConflicts (${totalConflicts.toDouble() / totalOperations * 100}%)")
    appendLine("Average Latency: $averageLatency")
    appendLine("Estimated Convergence Time: $convergenceTime")
    appendLine("\n--- Per-Node Metrics ---")
    nodeMetrics.forEach { (nodeId, metrics) ->
        appendLine("$nodeId:")
        appendLine("  Sent: ${metrics.operationsSent}")
        appendLine("  Received: ${metrics.operationsReceived}")
        appendLine("  Failed: ${metrics.operationsFailed}")
        appendLine("  Conflicts: ${metrics.conflictsDetected}")
        appendLine("  Packet Loss: ${metrics.packetsLost}")
    }
}

// Math import for Poisson process
internal fun ln(x: Double): Double = kotlin.math.ln(x)