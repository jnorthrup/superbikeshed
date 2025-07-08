package borg.trikeshed.fiduciary

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.*

/**
 * Example usage of Wave CRDT Traffic Simulation demonstrating
 * channelized coroutine composition for distributed document editing.
 */

suspend fun main() = coroutineScope {
    println("=== Wave CRDT Traffic Simulation Example ===\n")
    
    // Example 1: Burst traffic pattern simulating collaborative editing sprint
    println("1. Burst Traffic Pattern - Simulating editing sprint")
    val burstSimulator = WaveCRDTTrafficSimulator(nodeCount = 3).initialize()
    val burstResults = burstSimulator.simulate(
        pattern = TrafficPattern.Burst,
        duration = 10.seconds,
        scope = this
    )
    println(burstResults.analyze())
    
    // Example 2: Steady traffic with different network conditions
    println("\n2. Steady Traffic - Different network conditions per node")
    val steadySimulator = WaveCRDTTrafficSimulator(nodeCount = 4).initialize()
    val steadyResults = steadySimulator.simulate(
        pattern = TrafficPattern.Steady(opsPerSecond = 5.0),
        duration = 15.seconds,
        scope = this
    )
    println(steadyResults.analyze())
    
    // Example 3: Poisson traffic modeling real-world usage
    println("\n3. Poisson Traffic - Real-world usage pattern")
    val poissonSimulator = WaveCRDTTrafficSimulator(nodeCount = 5).initialize()
    val poissonResults = poissonSimulator.simulate(
        pattern = TrafficPattern.Poisson(lambda = 3.0),
        duration = 20.seconds,
        scope = this
    )
    println(poissonResults.analyze())
}

/**
 * Advanced example with custom channel routing and backpressure
 */
class AdvancedWaveCRDTSimulation {
    
    suspend fun demonstrateChannelizedFlow() = coroutineScope {
        // Create a custom channel topology for Wave operations
        val operationChannel = Channel<WaveOperationData>(capacity = 100)
        val transformChannel = Channel<WaveOperationData>(capacity = 50)
        val persistenceChannel = Channel<WaveOperationData>(capacity = Channel.UNLIMITED)
        
        // Operation producer with backpressure
        val producer = launch {
            repeat(1000) { i ->
                val op = createTestOperation(i)
                operationChannel.send(op)
                delay(10) // Simulate operation generation rate
            }
            operationChannel.close()
        }
        
        // Transformation pipeline with fan-out
        val transformers = (1..3).map { id ->
            launch {
                for (op in operationChannel) {
                    val transformed = transformOperation(op, id)
                    transformChannel.send(transformed)
                }
            }
        }
        
        // Persistence consumer with batching
        val persister = launch {
            val batch = mutableListOf<WaveOperationData>()
            for (op in transformChannel) {
                batch.add(op)
                if (batch.size >= 10) {
                    persistBatch(batch.toList())
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) {
                persistBatch(batch)
            }
        }
        
        // Monitor flow metrics
        val monitor = launch {
            while (isActive) {
                println("Channel sizes - Operations: ${operationChannel.toString()}, " +
                       "Transform: ${transformChannel.toString()}")
                delay(1000)
            }
        }
        
        // Wait for completion
        producer.join()
        transformers.forEach { it.join() }
        transformChannel.close()
        persister.join()
        monitor.cancel()
    }
    
    internal fun createTestOperation(index: Int): WaveOperationData {
        return WaveOperationData(
            id = WaveOperationId("op-$index"),
            participantId = ParticipantId("test-participant"),
            operation = SerializedWaveOp.Insert("test-$index", index % 100),
            timestamp = kotlinx.datetime.Clock.System.now(),
            vectorClock = mapOf("test-participant" to index.toLong()),
            dependencies = 0 j { throw IndexOutOfBoundsException() }
        )
    }
    
    internal suspend fun transformOperation(op: WaveOperationData, transformerId: Int): WaveOperationData {
        delay(5) // Simulate transformation time
        return op.copy(
            id = WaveOperationId("${op.id.value}-t$transformerId")
        )
    }
    
    internal suspend fun persistBatch(batch: List<WaveOperationData>) {
        delay(50) // Simulate batch persistence
        println("Persisted batch of ${batch.size} operations")
    }
}

/**
 * Example demonstrating MetaSeries projection for traffic analysis
 */
class MetaSeriesTrafficAnalysis {
    
    fun analyzeTrafficWithProjections() {
        // Create traffic data as MetaSeries
        val trafficData: MetaSeries<Instant, Long> = createTrafficTimeSeries()
        
        // Project to different views
        val opsPerSecond = projectToOpsPerSecond(trafficData)
        val cumulativeOps = projectToCumulative(trafficData)
        val movingAverage = projectToMovingAverage(trafficData, windowSize = 5)
        
        // Compose projections for complex analysis
        val smoothedRate = movingAverage ⚬ opsPerSecond
        
        println("Traffic Analysis using MetaSeries projections:")
        println("- Operations per second: ${opsPerSecond.a} samples")
        println("- Cumulative operations: ${cumulativeOps.b(cumulativeOps.a)}")
        println("- Moving average window: 5 seconds")
    }
    
    internal fun createTrafficTimeSeries(): MetaSeries<Instant, Long> {
        val now = kotlinx.datetime.Clock.System.now()
        val samples = 100
        return now j { baseTime: Instant ->
            // Simulate varying traffic over time
            val offset = (0..samples).random()
            (offset * 10).toLong()
        }
    }
    
    internal fun projectToOpsPerSecond(traffic: MetaSeries<Instant, Long>): MetaSeries<Int, Double> {
        return 60 j { second: Int ->
            // Calculate ops/second for each time window
            traffic.b(traffic.a).toDouble() / 60.0
        }
    }
    
    internal fun projectToCumulative(traffic: MetaSeries<Instant, Long>): MetaSeries<Int, Long> {
        var sum = 0L
        return 100 j { index: Int ->
            sum += traffic.b(traffic.a)
            sum
        }
    }
    
    internal fun projectToMovingAverage(
        traffic: MetaSeries<Instant, Long>,
        windowSize: Int
    ): MetaSeries<Int, Double> {
        return 95 j { index: Int ->
            // Calculate moving average over window
            val windowSum = (0 until windowSize).sumOf { 
                traffic.b(traffic.a)
            }
            windowSum.toDouble() / windowSize
        }
    }
}

/**
 * Example showing channel-based fan-in/fan-out patterns
 */
class ChannelFanPatterns {
    
    suspend fun demonstrateFanInFanOut() = coroutineScope {
        // Multiple document sources (fan-in)
        val docSources = (1..3).map { sourceId ->
            Channel<WaveDocumentOp>(10).also { channel ->
                launch {
                    repeat(20) { i ->
                        channel.send(
                            WaveDocumentOp.Insert(
                                text = "Source$sourceId-Op$i",
                                position = i * 10
                            )
                        )
                        delay(50)
                    }
                    channel.close()
                }
            }
        }
        
        // Merge channels (fan-in)
        val mergedOps = Channel<WaveDocumentOp>(30)
        launch {
            docSources.forEach { source ->
                launch {
                    for (op in source) {
                        mergedOps.send(op)
                    }
                }
            }
        }
        
        // Process and distribute (fan-out)
        val processors = (1..4).map { processorId ->
            launch {
                for (op in mergedOps) {
                    processOperation(op, processorId)
                }
            }
        }
        
        // Wait for completion
        delay(3000)
        mergedOps.close()
        processors.forEach { it.join() }
    }
    
    internal suspend fun processOperation(op: WaveDocumentOp, processorId: Int) {
        delay(20) // Simulate processing
        println("Processor $processorId handled: ${op.javaClass.simpleName}")
    }
}

/**
 * Real-world scenario: Distributed document editing session
 */
class DistributedEditingSession {
    
    suspend fun runEditingSession() = coroutineScope {
        println("\n=== Distributed Editing Session ===")
        
        // Create distributed session
        val session = DistributedWaveSession("editing-session-001")
        
        // Simulate multiple editors
        val editors = listOf(
            ParticipantId("alice"),
            ParticipantId("bob"),
            ParticipantId("charlie")
        )
        
        // Create document and wavelet
        val documentId = DocumentId("shared-doc")
        val waveletId = WaveletId("main-wavelet")
        
        // Subscribe to flake updates
        val flakeCollector = launch {
            session.flakeFlow.collect { flake ->
                println("New flake: ${flake.commitHash} by ${flake.author.value}")
                println("  Operations: ${flake.waveOps.size}")
                println("  Document size: ${flake.documentState.length} chars")
            }
        }
        
        // Simulate editing activity
        editors.forEach { editor ->
            launch {
                repeat(5) { i ->
                    delay((100..500).random().toLong())
                    
                    // Create and apply operation
                    val op = when (i % 3) {
                        0 -> WaveDocumentOp.Insert(
                            text = "${editor.value} adds text $i ",
                            position = i * 10
                        )
                        1 -> WaveDocumentOp.Retain(5)
                        else -> WaveDocumentOp.AnnotationBoundary(
                            changes = mapOf("author" to editor.value),
                            position = i * 5
                        )
                    }
                    
                    // Create flake
                    session.createFlake(documentId, waveletId, editor)
                }
            }
        }
        
        // Let editing complete
        delay(3000)
        flakeCollector.cancel()
        
        println("\nEditing session completed")
    }
}

/**
 * Example demonstrating convergence monitoring
 */
class ConvergenceMonitor {
    
    suspend fun monitorConvergence() = coroutineScope {
        val nodeCount = 5
        val simulator = WaveCRDTTrafficSimulator(nodeCount = nodeCount).initialize()
        
        // Create convergence monitoring flow
        val convergenceFlow = flow {
            var lastStates = mutableMapOf<ParticipantId, String>()
            
            while (currentCoroutineContext().isActive) {
                // Collect current states from all nodes
                val currentStates = collectNodeStates(simulator)
                
                // Check convergence
                val converged = currentStates.values.distinct().size == 1
                val changeCount = currentStates.count { (id, state) ->
                    lastStates[id] != state
                }
                
                emit(ConvergenceStatus(
                    converged = converged,
                    uniqueStates = currentStates.values.distinct().size,
                    changedNodes = changeCount,
                    timestamp = kotlinx.datetime.Clock.System.now()
                ))
                
                lastStates = currentStates.toMutableMap()
                delay(100)
            }
        }
        
        // Monitor convergence during simulation
        val monitorJob = launch {
            convergenceFlow.collect { status ->
                if (status.converged) {
                    println("✓ Converged at ${status.timestamp}")
                } else {
                    println("⟳ Diverged: ${status.uniqueStates} unique states, " +
                           "${status.changedNodes} nodes changed")
                }
            }
        }
        
        // Run simulation
        val results = simulator.simulate(
            pattern = TrafficPattern.Burst,
            duration = 5.seconds,
            scope = this
        )
        
        monitorJob.cancel()
        println("\nFinal convergence time: ${results.convergenceTime}")
    }
    
    internal suspend fun collectNodeStates(
        simulator: WaveCRDTTrafficSimulator
    ): Map<ParticipantId, String> {
        // In real implementation, would query actual document states
        return mapOf(
            ParticipantId("node-0") to "state-${(0..2).random()}",
            ParticipantId("node-1") to "state-${(0..2).random()}",
            ParticipantId("node-2") to "state-${(0..2).random()}",
            ParticipantId("node-3") to "state-${(0..2).random()}",
            ParticipantId("node-4") to "state-${(0..2).random()}"
        )
    }
}

data class ConvergenceStatus(
    val converged: Boolean,
    val uniqueStates: Int,
    val changedNodes: Int,
    val timestamp: Instant
)