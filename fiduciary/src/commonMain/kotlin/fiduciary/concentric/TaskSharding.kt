package fiduciary.concentric

import borg.trikeshed.lib.*
import borg.trikeshed.dht.kademlia.id.NUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlin.math.*

/**
 * Task Sharding for Concentric Networks
 * 
 * Implements intelligent task decomposition and distribution
 * across agent groups based on capabilities, load, and network topology.
 * 
 * Supports Patrick Devine content processing through parallel sharding.
 */

// === TASK SHARDS ===

/**
 * A shard of a larger task
 */
@Serializable
data class TaskShard(
    val shardId: NUID,
    val parentTaskId: NUID,
    val shardIndex: Int,
    val totalShards: Int,
    val content: ShardContent,
    val dependencies: Set<NUID> = emptySet(), // Other shard IDs this depends on
    val estimatedLoad: Double,
    val requiredCapabilities: Set<AgentCapability>,
    val assignedTo: NUID? = null,
    val status: ShardStatus = ShardStatus.PENDING,
    val createdAt: Instant = Clock.System.now()
)

@Serializable
sealed class ShardContent {
    @Serializable
    data class DataProcessing(
        val dataSlice: ByteArray,
        val startOffset: Long,
        val endOffset: Long,
        val processingType: ProcessingType
    ) : ShardContent()
    
    @Serializable
    data class TextAnalysis(
        val text: String,
        val analysisTypes: Set<AnalysisType>,
        val languageHint: String? = null
    ) : ShardContent()
    
    @Serializable
    data class PatrickDevineContent(
        val contentId: String,
        val contentType: PatrickContentType,
        val timeRange: Pair<Int, Int>? = null // For audio/video
    ) : ShardContent()
    
    @Serializable
    data class MapReduce(
        val mapFunction: String,
        val data: ByteArray,
        val isMapper: Boolean // true for map, false for reduce
    ) : ShardContent()
}

enum class ProcessingType {
    TRANSCRIPTION,
    NLP_ANALYSIS,
    TOPIC_MODELING,
    ENTITY_EXTRACTION,
    SENTIMENT_ANALYSIS,
    ANOMALY_DETECTION
}

enum class AnalysisType {
    LEXICAL,
    SYNTACTIC,
    SEMANTIC,
    PRAGMATIC,
    DISCOURSE
}

enum class PatrickContentType {
    SPEECH_TRANSCRIPT,
    VIDEO_SEGMENT,
    DOCUMENT_SECTION,
    CROSS_REFERENCE,
    MEMVID_CLIP
}

enum class ShardStatus {
    PENDING,
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    REASSIGNED
}

// === SHARDING STRATEGIES ===

/**
 * Strategy for decomposing tasks into shards
 */
sealed class ShardingStrategy {
    /**
     * Fixed-size sharding based on data volume
     */
    data class FixedSize(
        val shardSize: Long,
        val overlap: Long = 0 // Overlap between shards for context
    ) : ShardingStrategy()
    
    /**
     * Dynamic sharding based on content complexity
     */
    data class ComplexityBased(
        val targetComplexity: Double,
        val complexityMetric: (ByteArray) -> Double
    ) : ShardingStrategy()
    
    /**
     * Semantic sharding that preserves logical boundaries
     */
    data class SemanticBoundary(
        val boundaryDetector: (String) -> List<Int>, // Returns boundary positions
        val minShardSize: Int,
        val maxShardSize: Int
    ) : ShardingStrategy()
    
    /**
     * Capability-based sharding for heterogeneous agents
     */
    data class CapabilityAligned(
        val capabilityMap: Map<AgentCapability, Double>, // Capability to load factor
        val agentCapabilities: Map<NUID, Set<AgentCapability>>
    ) : ShardingStrategy()
    
    /**
     * Time-based sharding for streaming data
     */
    data class TimeBased(
        val windowSize: Long, // milliseconds
        val slideInterval: Long // milliseconds
    ) : ShardingStrategy()
}

// === TASK SHARDER ===

/**
 * Main task sharding engine
 */
class TaskSharder(
    val networkManager: ConcentricNetworkManager
) {
    private val shardRegistry = mutableMapOf<NUID, TaskShard>()
    private val parentToShards = mutableMapOf<NUID, MutableSet<NUID>>()
    
    private val _shardFlow = MutableSharedFlow<ShardEvent>()
    val shardFlow: SharedFlow<ShardEvent> = _shardFlow.asSharedFlow()
    
    /**
     * Shard a task based on strategy
     */
    suspend fun shardTask(
        task: ConcentricTask,
        strategy: ShardingStrategy,
        targetAgents: List<ConcentricAgent>? = null
    ): List<TaskShard> {
        val shards = when (strategy) {
            is ShardingStrategy.FixedSize -> shardByFixedSize(task, strategy)
            is ShardingStrategy.ComplexityBased -> shardByComplexity(task, strategy)
            is ShardingStrategy.SemanticBoundary -> shardBySemantic(task, strategy)
            is ShardingStrategy.CapabilityAligned -> shardByCapability(task, strategy, targetAgents)
            is ShardingStrategy.TimeBased -> shardByTime(task, strategy)
        }
        
        // Register shards
        shards.forEach { shard ->
            shardRegistry[shard.shardId] = shard
            parentToShards.getOrPut(task.id) { mutableSetOf() }.add(shard.shardId)
        }
        
        // Emit sharding event
        _shardFlow.emit(ShardEvent.TaskSharded(task.id, shards.map { it.shardId }))
        
        return shards
    }
    
    /**
     * Fixed-size sharding implementation
     */
    private fun shardByFixedSize(
        task: ConcentricTask,
        strategy: ShardingStrategy.FixedSize
    ): List<TaskShard> {
        val shards = mutableListOf<TaskShard>()
        val data = task.payload
        val shardSize = strategy.shardSize.toInt()
        val overlap = strategy.overlap.toInt()
        
        var offset = 0
        var shardIndex = 0
        
        while (offset < data.size) {
            val endOffset = minOf(offset + shardSize, data.size)
            val startOffset = maxOf(0, offset - overlap)
            
            val shardData = data.sliceArray(startOffset until endOffset)
            
            val shard = TaskShard(
                shardId = NUID.fromSHA256(
                    task.id.toByteArray() + shardIndex.toByteArray()
                ),
                parentTaskId = task.id,
                shardIndex = shardIndex,
                totalShards = ceil(data.size.toDouble() / shardSize).toInt(),
                content = ShardContent.DataProcessing(
                    dataSlice = shardData,
                    startOffset = startOffset.toLong(),
                    endOffset = endOffset.toLong(),
                    processingType = determineProcessingType(task.type)
                ),
                estimatedLoad = shardData.size.toDouble() / 1024, // KB as load metric
                requiredCapabilities = task.requiredCapabilities
            )
            
            shards.add(shard)
            offset = endOffset
            shardIndex++
        }
        
        // Add dependencies for overlapping shards
        if (overlap > 0) {
            for (i in 1 until shards.size) {
                shards[i] = shards[i].copy(
                    dependencies = setOf(shards[i - 1].shardId)
                )
            }
        }
        
        return shards
    }
    
    /**
     * Complexity-based sharding
     */
    private fun shardByComplexity(
        task: ConcentricTask,
        strategy: ShardingStrategy.ComplexityBased
    ): List<TaskShard> {
        val shards = mutableListOf<TaskShard>()
        val data = task.payload
        
        var currentShard = mutableListOf<Byte>()
        var currentComplexity = 0.0
        var shardIndex = 0
        
        for (byte in data) {
            currentShard.add(byte)
            currentComplexity = strategy.complexityMetric(currentShard.toByteArray())
            
            if (currentComplexity >= strategy.targetComplexity) {
                shards.add(createShardFromBytes(
                    task,
                    currentShard.toByteArray(),
                    shardIndex++,
                    currentComplexity
                ))
                currentShard.clear()
                currentComplexity = 0.0
            }
        }
        
        // Add remaining data
        if (currentShard.isNotEmpty()) {
            shards.add(createShardFromBytes(
                task,
                currentShard.toByteArray(),
                shardIndex,
                currentComplexity
            ))
        }
        
        // Update total shards count
        return shards.map { it.copy(totalShards = shards.size) }
    }
    
    /**
     * Semantic boundary sharding
     */
    private fun shardBySemantic(
        task: ConcentricTask,
        strategy: ShardingStrategy.SemanticBoundary
    ): List<TaskShard> {
        val text = String(task.payload)
        val boundaries = strategy.boundaryDetector(text)
        val shards = mutableListOf<TaskShard>()
        
        var lastBoundary = 0
        var shardIndex = 0
        
        for (boundary in boundaries) {
            val shardText = text.substring(lastBoundary, boundary)
            
            if (shardText.length >= strategy.minShardSize) {
                // If too large, split further
                if (shardText.length > strategy.maxShardSize) {
                    shards.addAll(splitLargeShard(
                        task,
                        shardText,
                        shardIndex,
                        strategy.maxShardSize
                    ))
                    shardIndex += ceil(shardText.length.toDouble() / strategy.maxShardSize).toInt()
                } else {
                    shards.add(createTextShard(task, shardText, shardIndex++))
                }
                lastBoundary = boundary
            }
        }
        
        // Add remaining text
        if (lastBoundary < text.length) {
            val remainingText = text.substring(lastBoundary)
            if (remainingText.length >= strategy.minShardSize) {
                shards.add(createTextShard(task, remainingText, shardIndex))
            } else if (shards.isNotEmpty()) {
                // Append to last shard if too small
                val lastShard = shards.last()
                if (lastShard.content is ShardContent.TextAnalysis) {
                    shards[shards.size - 1] = lastShard.copy(
                        content = lastShard.content.copy(
                            text = lastShard.content.text + remainingText
                        )
                    )
                }
            }
        }
        
        return shards.map { it.copy(totalShards = shards.size) }
    }
    
    /**
     * Capability-aligned sharding
     */
    private fun shardByCapability(
        task: ConcentricTask,
        strategy: ShardingStrategy.CapabilityAligned,
        targetAgents: List<ConcentricAgent>?
    ): List<TaskShard> {
        val agents = targetAgents ?: emptyList()
        val shards = mutableListOf<TaskShard>()
        
        // Group capabilities by load factor
        val capabilityGroups = strategy.capabilityMap.entries
            .groupBy { it.value }
            .mapValues { it.value.map { entry -> entry.key }.toSet() }
        
        // Create shards for each capability group
        var shardIndex = 0
        capabilityGroups.forEach { (loadFactor, capabilities) ->
            // Find agents with these capabilities
            val capableAgents = agents.filter { agent ->
                agent.capabilities.any { it in capabilities }
            }
            
            if (capableAgents.isNotEmpty()) {
                // Create shards proportional to agent count
                val shardCount = max(1, capableAgents.size)
                val dataPerShard = task.payload.size / shardCount
                
                repeat(shardCount) { i ->
                    val startOffset = i * dataPerShard
                    val endOffset = if (i == shardCount - 1) task.payload.size else (i + 1) * dataPerShard
                    
                    shards.add(TaskShard(
                        shardId = NUID.fromSHA256(
                            task.id.toByteArray() + shardIndex.toByteArray()
                        ),
                        parentTaskId = task.id,
                        shardIndex = shardIndex++,
                        totalShards = 0, // Will update later
                        content = ShardContent.DataProcessing(
                            dataSlice = task.payload.sliceArray(startOffset until endOffset),
                            startOffset = startOffset.toLong(),
                            endOffset = endOffset.toLong(),
                            processingType = determineProcessingType(task.type)
                        ),
                        estimatedLoad = loadFactor * dataPerShard,
                        requiredCapabilities = capabilities,
                        assignedTo = capableAgents[i % capableAgents.size].id
                    ))
                }
            }
        }
        
        return shards.map { it.copy(totalShards = shards.size) }
    }
    
    /**
     * Time-based sharding for streaming
     */
    private fun shardByTime(
        task: ConcentricTask,
        strategy: ShardingStrategy.TimeBased
    ): List<TaskShard> {
        // For Patrick Devine video/audio content
        val shards = mutableListOf<TaskShard>()
        
        // Assume task payload contains timing metadata
        val duration = extractDuration(task.payload) // milliseconds
        val windowSize = strategy.windowSize
        val slideInterval = strategy.slideInterval
        
        var currentTime = 0L
        var shardIndex = 0
        
        while (currentTime < duration) {
            val endTime = minOf(currentTime + windowSize, duration)
            
            shards.add(TaskShard(
                shardId = NUID.fromSHA256(
                    task.id.toByteArray() + shardIndex.toByteArray()
                ),
                parentTaskId = task.id,
                shardIndex = shardIndex++,
                totalShards = ceil(duration.toDouble() / slideInterval).toInt(),
                content = ShardContent.PatrickDevineContent(
                    contentId = task.id.toString(),
                    contentType = PatrickContentType.VIDEO_SEGMENT,
                    timeRange = (currentTime.toInt() / 1000) to (endTime.toInt() / 1000)
                ),
                estimatedLoad = (endTime - currentTime) / 1000.0, // seconds as load
                requiredCapabilities = setOf(
                    AgentCapability.TRANSCRIPTION,
                    AgentCapability.NLP_PROCESSING
                )
            ))
            
            currentTime += slideInterval
        }
        
        return shards
    }
    
    /**
     * Assign shards to agents
     */
    suspend fun assignShards(
        shards: List<TaskShard>,
        agents: List<ConcentricAgent>
    ): Map<NUID, List<TaskShard>> {
        val assignments = mutableMapOf<NUID, MutableList<TaskShard>>()
        val agentLoads = agents.associateWith { 0.0 }.toMutableMap()
        
        // Sort shards by estimated load (descending)
        val sortedShards = shards.sortedByDescending { it.estimatedLoad }
        
        for (shard in sortedShards) {
            // Find capable agent with lowest load
            val capableAgents = agents.filter { agent ->
                shard.requiredCapabilities.all { it in agent.capabilities }
            }
            
            if (capableAgents.isNotEmpty()) {
                val selectedAgent = capableAgents.minBy { agentLoads[it] ?: 0.0 }
                
                // Assign shard
                assignments.getOrPut(selectedAgent.id) { mutableListOf() }.add(
                    shard.copy(assignedTo = selectedAgent.id, status = ShardStatus.ASSIGNED)
                )
                
                // Update load
                agentLoads[selectedAgent] = (agentLoads[selectedAgent] ?: 0.0) + shard.estimatedLoad
                
                // Update registry
                shardRegistry[shard.shardId] = shard.copy(
                    assignedTo = selectedAgent.id,
                    status = ShardStatus.ASSIGNED
                )
                
                // Emit assignment event
                _shardFlow.emit(ShardEvent.ShardAssigned(shard.shardId, selectedAgent.id))
            } else {
                // No capable agent found
                _shardFlow.emit(ShardEvent.ShardAssignmentFailed(
                    shard.shardId,
                    "No agent with required capabilities: ${shard.requiredCapabilities}"
                ))
            }
        }
        
        return assignments
    }
    
    /**
     * Reassign failed shards
     */
    suspend fun reassignFailedShards(failedShardIds: Set<NUID>, agents: List<ConcentricAgent>) {
        val failedShards = failedShardIds.mapNotNull { shardRegistry[it] }
            .filter { it.status == ShardStatus.FAILED }
        
        failedShards.forEach { shard ->
            // Mark as reassigned
            val updatedShard = shard.copy(status = ShardStatus.REASSIGNED)
            shardRegistry[shard.shardId] = updatedShard
            
            // Find new agent (excluding previous assignee)
            val availableAgents = agents.filter { it.id != shard.assignedTo }
            assignShards(listOf(updatedShard), availableAgents)
        }
    }
    
    /**
     * Merge shard results
     */
    suspend fun mergeShardResults(
        parentTaskId: NUID,
        shardResults: Map<NUID, ByteArray>
    ): ByteArray {
        val shardIds = parentToShards[parentTaskId] ?: return ByteArray(0)
        val shards = shardIds.mapNotNull { shardRegistry[it] }
            .sortedBy { it.shardIndex }
        
        // Check all shards completed
        val completedShards = shards.filter { it.status == ShardStatus.COMPLETED }
        if (completedShards.size != shards.size) {
            throw IllegalStateException(
                "Not all shards completed: ${completedShards.size}/${shards.size}"
            )
        }
        
        // Merge results in order
        val mergedResult = ByteArrayOutputStream()
        shards.forEach { shard ->
            shardResults[shard.shardId]?.let { result ->
                mergedResult.write(result)
            }
        }
        
        // Emit merge event
        _shardFlow.emit(ShardEvent.ShardsМerged(parentTaskId, shardIds))
        
        return mergedResult.toByteArray()
    }
    
    // Helper functions
    
    private fun determineProcessingType(taskType: TaskType): ProcessingType {
        return when (taskType) {
            TaskType.TRANSCRIPTION -> ProcessingType.TRANSCRIPTION
            TaskType.NLP_ANALYSIS -> ProcessingType.NLP_ANALYSIS
            TaskType.TOPIC_EXTRACTION -> ProcessingType.TOPIC_MODELING
            TaskType.ANOMALY_DETECTION -> ProcessingType.ANOMALY_DETECTION
            else -> ProcessingType.NLP_ANALYSIS
        }
    }
    
    private fun createShardFromBytes(
        task: ConcentricTask,
        data: ByteArray,
        index: Int,
        complexity: Double
    ): TaskShard {
        return TaskShard(
            shardId = NUID.fromSHA256(task.id.toByteArray() + index.toByteArray()),
            parentTaskId = task.id,
            shardIndex = index,
            totalShards = 0, // Will update later
            content = ShardContent.DataProcessing(
                dataSlice = data,
                startOffset = 0,
                endOffset = data.size.toLong(),
                processingType = determineProcessingType(task.type)
            ),
            estimatedLoad = complexity,
            requiredCapabilities = task.requiredCapabilities
        )
    }
    
    private fun createTextShard(
        task: ConcentricTask,
        text: String,
        index: Int
    ): TaskShard {
        return TaskShard(
            shardId = NUID.fromSHA256(task.id.toByteArray() + index.toByteArray()),
            parentTaskId = task.id,
            shardIndex = index,
            totalShards = 0, // Will update later
            content = ShardContent.TextAnalysis(
                text = text,
                analysisTypes = determineAnalysisTypes(task.type)
            ),
            estimatedLoad = text.length / 1000.0, // Characters/1000 as load
            requiredCapabilities = task.requiredCapabilities
        )
    }
    
    private fun splitLargeShard(
        task: ConcentricTask,
        text: String,
        startIndex: Int,
        maxSize: Int
    ): List<TaskShard> {
        val chunks = text.chunked(maxSize)
        return chunks.mapIndexed { i, chunk ->
            createTextShard(task, chunk, startIndex + i)
        }
    }
    
    private fun determineAnalysisTypes(taskType: TaskType): Set<AnalysisType> {
        return when (taskType) {
            TaskType.NLP_ANALYSIS -> setOf(
                AnalysisType.LEXICAL,
                AnalysisType.SYNTACTIC,
                AnalysisType.SEMANTIC
            )
            TaskType.TOPIC_EXTRACTION -> setOf(
                AnalysisType.SEMANTIC,
                AnalysisType.DISCOURSE
            )
            else -> setOf(AnalysisType.LEXICAL)
        }
    }
    
    private fun extractDuration(payload: ByteArray): Long {
        // Simplified - would parse actual metadata
        return 300000L // 5 minutes default
    }
    
    private fun Int.toByteArray(): ByteArray {
        return byteArrayOf(
            (this shr 24).toByte(),
            (this shr 16).toByte(),
            (this shr 8).toByte(),
            this.toByte()
        )
    }
}

// === SHARD EVENTS ===

/**
 * Events related to task sharding
 */
sealed class ShardEvent {
    data class TaskSharded(
        val parentTaskId: NUID,
        val shardIds: List<NUID>
    ) : ShardEvent()
    
    data class ShardAssigned(
        val shardId: NUID,
        val agentId: NUID
    ) : ShardEvent()
    
    data class ShardAssignmentFailed(
        val shardId: NUID,
        val reason: String
    ) : ShardEvent()
    
    data class ShardCompleted(
        val shardId: NUID,
        val result: ByteArray
    ) : ShardEvent()
    
    data class ShardFailed(
        val shardId: NUID,
        val error: String
    ) : ShardEvent()
    
    data class ShardsМerged(
        val parentTaskId: NUID,
        val shardIds: Set<NUID>
    ) : ShardEvent()
}

// === SHARDING OPTIMIZER ===

/**
 * Optimizes sharding strategies based on performance metrics
 */
class ShardingOptimizer {
    private val performanceHistory = mutableMapOf<String, ShardingPerformance>()
    
    /**
     * Select optimal sharding strategy based on task characteristics
     */
    fun selectOptimalStrategy(
        task: ConcentricTask,
        availableAgents: List<ConcentricAgent>
    ): ShardingStrategy {
        return when (task.type) {
            TaskType.TRANSCRIPTION, TaskType.CONTENT_INGESTION -> {
                // Time-based for media content
                ShardingStrategy.TimeBased(
                    windowSize = 60000, // 1 minute windows
                    slideInterval = 30000 // 30 second slides
                )
            }
            TaskType.NLP_ANALYSIS, TaskType.TOPIC_EXTRACTION -> {
                // Semantic boundaries for text
                ShardingStrategy.SemanticBoundary(
                    boundaryDetector = ::detectParagraphBoundaries,
                    minShardSize = 500,
                    maxShardSize = 5000
                )
            }
            TaskType.ANOMALY_DETECTION -> {
                // Complexity-based for pattern detection
                ShardingStrategy.ComplexityBased(
                    targetComplexity = 0.7,
                    complexityMetric = ::calculateDataComplexity
                )
            }
            else -> {
                // Default to fixed size
                ShardingStrategy.FixedSize(
                    shardSize = 1024 * 1024, // 1MB shards
                    overlap = 1024 * 10 // 10KB overlap
                )
            }
        }
    }
    
    /**
     * Update performance metrics
     */
    fun recordPerformance(
        strategy: ShardingStrategy,
        taskType: TaskType,
        performance: ShardingPerformance
    ) {
        val key = "${strategy::class.simpleName}_${taskType.name}"
        val existing = performanceHistory[key]
        
        performanceHistory[key] = if (existing != null) {
            // Exponential moving average
            ShardingPerformance(
                avgProcessingTime = 0.7 * existing.avgProcessingTime + 0.3 * performance.avgProcessingTime,
                successRate = 0.7 * existing.successRate + 0.3 * performance.successRate,
                avgLoadBalance = 0.7 * existing.avgLoadBalance + 0.3 * performance.avgLoadBalance
            )
        } else {
            performance
        }
    }
    
    private fun detectParagraphBoundaries(text: String): List<Int> {
        val boundaries = mutableListOf<Int>()
        var index = 0
        
        while (index < text.length) {
            val nextNewline = text.indexOf("\n\n", index)
            if (nextNewline != -1) {
                boundaries.add(nextNewline)
                index = nextNewline + 2
            } else {
                break
            }
        }
        
        boundaries.add(text.length)
        return boundaries
    }
    
    private fun calculateDataComplexity(data: ByteArray): Double {
        // Simple entropy-based complexity
        val frequencies = IntArray(256)
        data.forEach { byte ->
            frequencies[byte.toInt() and 0xFF]++
        }
        
        var entropy = 0.0
        val total = data.size.toDouble()
        
        frequencies.forEach { count ->
            if (count > 0) {
                val probability = count / total
                entropy -= probability * log2(probability)
            }
        }
        
        return entropy / 8.0 // Normalize to 0-1
    }
}

@Serializable
data class ShardingPerformance(
    val avgProcessingTime: Double,
    val successRate: Double,
    val avgLoadBalance: Double
)

// === PATRICK DEVINE SHARDING ===

/**
 * Specialized sharding for Patrick Devine content
 */
object PatrickDevineSharding {
    
    /**
     * Create sharding strategy for Patrick Devine videos
     */
    fun videoShardingStrategy(): ShardingStrategy {
        return ShardingStrategy.TimeBased(
            windowSize = 120000, // 2 minute windows
            slideInterval = 60000 // 1 minute slides for context overlap
        )
    }
    
    /**
     * Create sharding strategy for Patrick Devine transcripts
     */
    fun transcriptShardingStrategy(): ShardingStrategy {
        return ShardingStrategy.SemanticBoundary(
            boundaryDetector = { text ->
                // Detect speaker turns or topic shifts
                val boundaries = mutableListOf<Int>()
                val speakerPattern = Regex("\\n[A-Z][A-Za-z]+:")
                
                speakerPattern.findAll(text).forEach { match ->
                    boundaries.add(match.range.first)
                }
                
                if (boundaries.isEmpty()) {
                    // Fall back to paragraph boundaries
                    text.split("\n\n").fold(0) { acc, paragraph ->
                        boundaries.add(acc + paragraph.length + 2)
                        acc + paragraph.length + 2
                    }
                }
                
                boundaries
            },
            minShardSize = 1000,
            maxShardSize = 10000
        )
    }
    
    /**
     * Create cross-reference sharding strategy
     */
    fun crossReferenceShardingStrategy(
        referenceMap: Map<String, Set<String>>
    ): ShardingStrategy {
        return ShardingStrategy.CapabilityAligned(
            capabilityMap = mapOf(
                AgentCapability.ENTITY_EXTRACTION to 0.5,
                AgentCapability.FACT_CHECKING to 1.0,
                AgentCapability.CONSISTENCY_VALIDATION to 0.8
            ),
            agentCapabilities = emptyMap() // Will be provided at runtime
        )
    }
}

/**
 * Task sharding wrapper for compatibility with WorkerPoolEvolution
 */
class TaskSharding(
    private val subnetId: NUID,
    private val quicProtocol: QuicConcentricProtocol
) {
    private val networkManager = ConcentricNetworkManager(subnetId)
    private val taskSharder = TaskSharder(networkManager)
    
    suspend fun shardTask(
        task: ConcentricTask,
        strategy: ShardingStrategy,
        targetAgents: List<ConcentricAgent>? = null
    ): List<TaskShard> {
        return taskSharder.shardTask(task, strategy, targetAgents)
    }
    
    fun getShardRegistry() = taskSharder.shardRegistry
    fun getShardFlow() = taskSharder.shardFlow
}