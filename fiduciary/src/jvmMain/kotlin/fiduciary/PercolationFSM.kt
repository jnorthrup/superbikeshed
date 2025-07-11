package fiduciary

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.milliseconds

/**
 * Timer-based Percolation Finite State Machine
 * 
 * Handles slow percolation scenarios with timeout detection,
 * fallback processing modes, and performance escalation.
 * Integrates with concentric dispatch for adaptive processing.
 */

// FSM States for percolation performance
sealed class PercolationState {
    object Normal : PercolationState()           // Standard processing speed
    object Slow : PercolationState()             // Detected slowdown
    object Critical : PercolationState()         // Severely degraded performance
    object Fallback : PercolationState()         // Emergency processing mode
    object Recovery : PercolationState()         // Recovering from slowdown
    object Overload : PercolationState()         // System overload detected
}

// FSM Events that trigger state transitions
sealed class PercolationEvent {
    object SlowdownDetected : PercolationEvent()
    object CriticalSlowdown : PercolationEvent()
    object SystemOverload : PercolationEvent()
    object ProcessingNormal : PercolationEvent()
    object RecoveryStarted : PercolationEvent()
    object RecoveryComplete : PercolationEvent()
    object FallbackActivated : PercolationEvent()
    object TimeoutExpired : PercolationEvent()
}

// Performance metrics for state decisions
data class PercolationMetrics(
    val averageProcessingTime: Long = 0,
    val queueDepth: Int = 0,
    val throughputPerSecond: Double = 0.0,
    val failureRate: Double = 0.0,
    val agentUtilization: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)

// Timer configuration for different scenarios
data class TimerConfig(
    val normalTimeout: Long = 5000,        // 5s normal processing timeout
    val slowTimeout: Long = 15000,         // 15s slow processing timeout
    val criticalTimeout: Long = 30000,     // 30s critical timeout
    val fallbackTimeout: Long = 60000,     // 1m fallback timeout
    val recoveryTimeout: Long = 120000,    // 2m recovery timeout
    val metricsInterval: Long = 1000       // 1s metrics collection interval
)

// The Timer-based Percolation FSM
object PercolationFSM : CoroutineContext.Element, CoroutineContext.Key<PercolationFSM> {
    override val key: CoroutineContext.Key<*> get() = PercolationFSM
    
    private var currentState: PercolationState = PercolationState.Normal
    private val stateHistory = mutableListOf<Pair<PercolationState, Long>>()
    private val metrics = mutableListOf<PercolationMetrics>()
    
    private val eventChannel = Channel<PercolationEvent>(capacity = 100)
    private val stateFlow = MutableSharedFlow<PercolationState>(replay = 1)
    private val timerConfig = TimerConfig()
    
    // Performance tracking
    private var lastProcessedCount = 0L
    private var lastMetricsTime = System.currentTimeMillis()
    private var taskStartTimes = mutableMapOf<String, Long>()
    private var recentProcessingTimes = ArrayDeque<Long>()
    private val maxRecentSamples = 100
    
    suspend fun startFSM(scope: CoroutineScope) {
        println("⏱️ Starting Timer-based Percolation FSM")
        
        // Initialize state
        currentState = PercolationState.Normal
        stateFlow.emit(currentState)
        recordStateChange(currentState)
        
        // Start FSM event processor
        scope.launch { processFSMEvents() }
        
        // Start metrics collector
        scope.launch { collectMetrics() }
        
        // Start timeout monitor
        scope.launch { monitorTimeouts() }
        
        // Start adaptive controller
        scope.launch { adaptiveController() }
        
        println("✅ Percolation FSM active in ${currentState::class.simpleName} state")
    }
    
    private suspend fun processFSMEvents() {
        eventChannel.consumeAsFlow().collect { event ->
            val newState = transitionState(currentState, event)
            
            if (newState != currentState) {
                println("🔄 FSM: ${currentState::class.simpleName} → ${newState::class.simpleName} (${event::class.simpleName})")
                
                currentState = newState
                recordStateChange(newState)
                stateFlow.emit(newState)
                
                // Handle state entry actions
                handleStateEntry(newState)
            }
        }
    }
    
    private fun transitionState(current: PercolationState, event: PercolationEvent): PercolationState {
        return when (current) {
            is PercolationState.Normal -> when (event) {
                is PercolationEvent.SlowdownDetected -> PercolationState.Slow
                is PercolationEvent.SystemOverload -> PercolationState.Overload
                else -> current
            }
            
            is PercolationState.Slow -> when (event) {
                is PercolationEvent.CriticalSlowdown -> PercolationState.Critical
                is PercolationEvent.ProcessingNormal -> PercolationState.Recovery
                is PercolationEvent.SystemOverload -> PercolationState.Overload
                is PercolationEvent.TimeoutExpired -> PercolationState.Fallback
                else -> current
            }
            
            is PercolationState.Critical -> when (event) {
                is PercolationEvent.FallbackActivated -> PercolationState.Fallback
                is PercolationEvent.RecoveryStarted -> PercolationState.Recovery
                is PercolationEvent.TimeoutExpired -> PercolationState.Fallback
                else -> current
            }
            
            is PercolationState.Fallback -> when (event) {
                is PercolationEvent.RecoveryStarted -> PercolationState.Recovery
                is PercolationEvent.ProcessingNormal -> PercolationState.Recovery
                else -> current
            }
            
            is PercolationState.Recovery -> when (event) {
                is PercolationEvent.RecoveryComplete -> PercolationState.Normal
                is PercolationEvent.SlowdownDetected -> PercolationState.Slow
                is PercolationEvent.SystemOverload -> PercolationState.Overload
                else -> current
            }
            
            is PercolationState.Overload -> when (event) {
                is PercolationEvent.ProcessingNormal -> PercolationState.Recovery
                is PercolationEvent.FallbackActivated -> PercolationState.Fallback
                else -> current
            }
        }
    }
    
    private suspend fun handleStateEntry(state: PercolationState) {
        when (state) {
            is PercolationState.Slow -> {
                println("🐌 SLOW: Activating slow processing protocols")
                // Increase agent allocation to compensate
                requestAdditionalAgents(ring = ConcentricRing.TRIAD, count = 2)
            }
            
            is PercolationState.Critical -> {
                println("🚨 CRITICAL: Emergency processing mode activated")
                // Escalate to higher rings
                requestAdditionalAgents(ring = ConcentricRing.PENTAD, count = 3)
                requestAdditionalAgents(ring = ConcentricRing.DODECAD, count = 5)
            }
            
            is PercolationState.Fallback -> {
                println("⚡ FALLBACK: Emergency processing mode - bypassing complex analysis")
                // Switch to simplified processing pipeline
                activateFallbackProcessing()
            }
            
            is PercolationState.Recovery -> {
                println("🔧 RECOVERY: Gradually restoring normal processing")
                // Slowly restore normal processing
                startRecoveryProcess()
            }
            
            is PercolationState.Overload -> {
                println("💥 OVERLOAD: System capacity exceeded - emergency load shedding")
                // Drop low-priority tasks, emergency processing only
                activateLoadShedding()
            }
            
            is PercolationState.Normal -> {
                println("✅ NORMAL: Standard processing mode restored")
                // All systems normal
            }
        }
    }
    
    private suspend fun collectMetrics() {
        while (true) {
            delay(timerConfig.metricsInterval)
            
            val currentTime = System.currentTimeMillis()
            val storage = FiduciaryPercolator.getStorage()
            val currentProcessedCount = storage.size.toLong()
            
            // Calculate throughput
            val timeDelta = currentTime - lastMetricsTime
            val countDelta = currentProcessedCount - lastProcessedCount
            val throughput = if (timeDelta > 0) (countDelta * 1000.0) / timeDelta else 0.0
            
            // Calculate average processing time
            val avgProcessingTime = if (recentProcessingTimes.isNotEmpty()) {
                recentProcessingTimes.average().toLong()
            } else 0L
            
            // Calculate agent utilization
            val agentStats = ConcentricDispatcher.getAgentStats()
            val totalAgents = agentStats.values.sum()
            val utilization = if (totalAgents > 0) {
                // Simulate utilization based on queue depth and processing times
                (avgProcessingTime / 1000.0).coerceIn(0.0, 1.0)
            } else 0.0
            
            // Create metrics snapshot
            val currentMetrics = PercolationMetrics(
                averageProcessingTime = avgProcessingTime,
                queueDepth = 0, // Would need access to queue sizes
                throughputPerSecond = throughput,
                failureRate = 0.0, // Would need failure tracking
                agentUtilization = utilization,
                timestamp = currentTime
            )
            
            // Store metrics (keep last 1000 samples)
            metrics.add(currentMetrics)
            if (metrics.size > 1000) {
                metrics.removeFirst()
            }
            
            // Update tracking variables
            lastProcessedCount = currentProcessedCount
            lastMetricsTime = currentTime
            
            // Analyze metrics for performance issues
            analyzePerformance(currentMetrics)
        }
    }
    
    private suspend fun analyzePerformance(metrics: PercolationMetrics) {
        // Detect various performance issues
        when {
            metrics.averageProcessingTime > timerConfig.criticalTimeout -> {
                eventChannel.send(PercolationEvent.CriticalSlowdown)
            }
            
            metrics.averageProcessingTime > timerConfig.slowTimeout -> {
                eventChannel.send(PercolationEvent.SlowdownDetected)
            }
            
            metrics.agentUtilization > 0.95 -> {
                eventChannel.send(PercolationEvent.SystemOverload)
            }
            
            metrics.throughputPerSecond < 0.1 && currentState != PercolationState.Normal -> {
                eventChannel.send(PercolationEvent.ProcessingNormal)
            }
            
            metrics.averageProcessingTime < timerConfig.normalTimeout && 
            currentState in listOf(PercolationState.Recovery, PercolationState.Slow) -> {
                eventChannel.send(PercolationEvent.RecoveryComplete)
            }
        }
    }
    
    private suspend fun monitorTimeouts() {
        while (true) {
            delay(1.seconds)
            
            val currentTime = System.currentTimeMillis()
            val stuckTasks = mutableListOf<String>()
            
            // Check for stuck tasks
            taskStartTimes.entries.removeAll { (taskId, startTime) ->
                val timeoutThreshold = when (currentState) {
                    is PercolationState.Normal -> timerConfig.normalTimeout
                    is PercolationState.Slow -> timerConfig.slowTimeout
                    is PercolationState.Critical -> timerConfig.criticalTimeout
                    is PercolationState.Fallback -> timerConfig.fallbackTimeout
                    is PercolationState.Recovery -> timerConfig.recoveryTimeout
                    is PercolationState.Overload -> timerConfig.fallbackTimeout
                }
                
                if (currentTime - startTime > timeoutThreshold) {
                    stuckTasks.add(taskId)
                    println("⏰ Task $taskId timed out after ${currentTime - startTime}ms")
                    true
                } else {
                    false
                }
            }
            
            // Trigger timeout events if tasks are stuck
            if (stuckTasks.isNotEmpty()) {
                eventChannel.send(PercolationEvent.TimeoutExpired)
            }
        }
    }
    
    private suspend fun adaptiveController() {
        while (true) {
            delay(5.seconds)
            
            // Adaptive control based on current state and metrics
            when (currentState) {
                is PercolationState.Slow -> {
                    // Try to speed up processing
                    adjustProcessingSpeed(1.2)
                }
                
                is PercolationState.Critical -> {
                    // Aggressive speedup attempts
                    adjustProcessingSpeed(1.5)
                }
                
                is PercolationState.Fallback -> {
                    // Maintain minimal processing
                    adjustProcessingSpeed(0.8)
                }
                
                is PercolationState.Recovery -> {
                    // Gradually increase processing
                    adjustProcessingSpeed(1.1)
                }
                
                is PercolationState.Overload -> {
                    // Reduce processing to sustainable levels
                    adjustProcessingSpeed(0.6)
                }
                
                else -> {
                    // Normal adaptive adjustments
                    val recentMetrics = metrics.takeLast(10)
                    if (recentMetrics.isNotEmpty()) {
                        val avgThroughput = recentMetrics.map { it.throughputPerSecond }.average()
                        if (avgThroughput < 1.0) {
                            adjustProcessingSpeed(1.1)
                        }
                    }
                }
            }
        }
    }
    
    // Integration functions with existing systems
    
    suspend fun recordTaskStart(taskId: String) {
        taskStartTimes[taskId] = System.currentTimeMillis()
    }
    
    suspend fun recordTaskComplete(taskId: String, processingTime: Long) {
        taskStartTimes.remove(taskId)
        
        // Add to recent processing times
        recentProcessingTimes.addLast(processingTime)
        if (recentProcessingTimes.size > maxRecentSamples) {
            recentProcessingTimes.removeFirst()
        }
    }
    
    private suspend fun requestAdditionalAgents(ring: ConcentricRing, count: Int) {
        println("🚀 FSM: Requesting $count additional agents from ${ring.name} ring")
        // Would integrate with AgentFactory to spawn more agents
        // AgentFactory.spawnAdditionalAgents(ring, count)
    }
    
    private suspend fun activateFallbackProcessing() {
        println("⚡ FSM: Activating fallback processing - simplified pipeline")
        // Switch to simplified processing mode
        // - Skip complex analysis
        // - Use basic validation only
        // - Prioritize throughput over accuracy
    }
    
    private suspend fun startRecoveryProcess() {
        println("🔧 FSM: Starting recovery process")
        // Gradually restore normal processing capabilities
        delay(10.seconds)
        eventChannel.send(PercolationEvent.RecoveryComplete)
    }
    
    private suspend fun activateLoadShedding() {
        println("💥 FSM: Activating emergency load shedding")
        // Drop low priority tasks
        // Process only critical items
        // Notify upstream systems of capacity issues
    }
    
    private suspend fun adjustProcessingSpeed(factor: Double) {
        println("⚙️ FSM: Adjusting processing speed by factor ${"%.2f".format(factor)}")
        // Adjust concentric dispatch priorities
        // Modify timeout values
        // Scale agent allocation
    }
    
    private fun recordStateChange(state: PercolationState) {
        stateHistory.add(Pair(state, System.currentTimeMillis()))
        
        // Keep last 100 state changes
        if (stateHistory.size > 100) {
            stateHistory.removeFirst()
        }
    }
    
    // Public API
    fun getCurrentState(): PercolationState = currentState
    
    fun getStateFlow(): SharedFlow<PercolationState> = stateFlow.asSharedFlow()
    
    fun getMetrics(): List<PercolationMetrics> = metrics.toList()
    
    fun getStateHistory(): List<Pair<PercolationState, Long>> = stateHistory.toList()
    
    suspend fun forceEvent(event: PercolationEvent) {
        eventChannel.send(event)
    }
    
    fun getPerformanceSummary(): String {
        val recentMetrics = metrics.takeLast(10)
        if (recentMetrics.isEmpty()) return "No metrics available"
        
        val avgProcessingTime = recentMetrics.map { it.averageProcessingTime }.average()
        val avgThroughput = recentMetrics.map { it.throughputPerSecond }.average()
        val avgUtilization = recentMetrics.map { it.agentUtilization }.average()
        
        return """
            🎯 Percolation FSM Performance Summary:
            Current State: ${currentState::class.simpleName}
            Avg Processing Time: ${"%.0f".format(avgProcessingTime)}ms
            Avg Throughput: ${"%.2f".format(avgThroughput)} items/sec
            Avg Agent Utilization: ${"%.1f".format(avgUtilization * 100)}%
            State Changes: ${stateHistory.size} recorded
        """.trimIndent()
    }
}

// Extension function to integrate FSM with main percolator
suspend fun FiduciaryPercolator.withFSMMonitoring(taskId: String, block: suspend () -> Unit) {
    PercolationFSM.recordTaskStart(taskId)
    val startTime = System.currentTimeMillis()
    
    try {
        block()
        val processingTime = System.currentTimeMillis() - startTime
        PercolationFSM.recordTaskComplete(taskId, processingTime)
    } catch (e: Exception) {
        // Record failure
        PercolationFSM.recordTaskComplete(taskId, System.currentTimeMillis() - startTime)
        throw e
    }
}