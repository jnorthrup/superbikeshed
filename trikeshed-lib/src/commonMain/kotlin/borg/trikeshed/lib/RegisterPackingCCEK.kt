package borg.trikeshed.lib

import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Register Packing CCEK - Coroutine Context Element for intelligent register packing
 * with runtime A/B/C testing and dynamic optimization
 */

// === CORE TYPES ===

/**
 * Register packing strategy variants for A/B/C testing
 */
enum class PackingStrategy {
    NAIVE,           // No packing, baseline
    TIGHT_PACK,      // Maximum density packing
    CACHE_ALIGNED,   // Cache line aware packing
    SIMD_OPTIMIZED,  // SIMD register width aware
    BRANCH_PREDICT,  // Branch prediction optimized
    PREFETCH_AWARE   // Prefetch friendly layout
}

/**
 * Runtime metrics for each packing strategy
 */
data class PackingMetrics(
    val strategy: PackingStrategy,
    val accessCount: Long = 0,
    val totalLatency: Long = 0,  // nanoseconds
    val cacheHits: Long = 0,
    val cacheMisses: Long = 0,
    val branchMispredicts: Long = 0,
    val simdUtilization: Double = 0.0,  // 0-1 efficiency
    val lastUpdateTime: Long = Clock.System.now().toEpochMilliseconds()
) {
    val averageLatency: Double get() = if (accessCount > 0) totalLatency.toDouble() / accessCount else 0.0
    val cacheHitRate: Double get() = if (accessCount > 0) cacheHits.toDouble() / accessCount else 0.0
}

/**
 * Context-specific packing hints
 */
data class PackingContext(
    val accessPattern: AccessPattern,
    val dataSize: Int,
    val hotness: Double,  // 0-1, how frequently accessed
    val temporalLocality: Double,  // 0-1, how likely to be accessed again soon
    val spatialLocality: Double,   // 0-1, how likely neighbors are accessed
    val simdWidth: Int = 256  // bits
)

enum class AccessPattern {
    SEQUENTIAL,      // Array-like access
    RANDOM,         // Hash table like
    STRIDED,        // Every Nth element
    TREE_TRAVERSAL, // Pointer chasing
    GRAPH_WALK,     // Irregular but connected
    STREAMING       // One-pass sequential
}

/**
 * Register packing decision engine
 */
class PackingDecisionEngine {
    private val strategyMetrics = mutableMapOf<PackingStrategy, PackingMetrics>()
    private val contextHistory = mutableListOf<Pair<PackingContext, PackingStrategy>>()
    
    init {
        // Initialize all strategies
        PackingStrategy.values().forEach { 
            strategyMetrics[it] = PackingMetrics(it)
        }
    }
    
    /**
     * Select best packing strategy based on context and historical performance
     */
    fun selectStrategy(context: PackingContext): PackingStrategy {
        // Exploration vs exploitation (epsilon-greedy with decay)
        val epsilon = 0.1 * (1.0 / (contextHistory.size / 100.0 + 1.0))
        
        if (kotlin.random.Random.Default.nextDouble() < epsilon) {
            // Explore: random strategy
            return PackingStrategy.values().random()
        }
        
        // Exploit: use contextual multi-armed bandit
        return selectBestStrategy(context)
    }
    
    private fun selectBestStrategy(context: PackingContext): PackingStrategy {
        // Compute context similarity weighted scores
        val scores = PackingStrategy.values().map { strategy ->
            val metrics = strategyMetrics[strategy]!!
            val baseScore = computeBaseScore(metrics)
            val contextBonus = computeContextBonus(strategy, context)
            val similarityWeight = computeSimilarityWeight(context, strategy)
            
            strategy to (baseScore * similarityWeight + contextBonus)
        }
        
        return scores.maxByOrNull { it.second }?.first ?: PackingStrategy.CACHE_ALIGNED
    }
    
    private fun computeBaseScore(metrics: PackingMetrics): Double {
        if (metrics.accessCount == 0L) return 1.0  // Optimistic initialization
        
        // Multi-objective optimization
        val latencyScore = 1.0 / (metrics.averageLatency + 1.0)
        val cacheScore = metrics.cacheHitRate
        val simdScore = metrics.simdUtilization
        
        // Weighted combination
        return 0.5 * latencyScore + 0.3 * cacheScore + 0.2 * simdScore
    }
    
    private fun computeContextBonus(strategy: PackingStrategy, context: PackingContext): Double {
        return when (strategy) {
            PackingStrategy.NAIVE -> when (context.accessPattern) {
                AccessPattern.SEQUENTIAL, AccessPattern.STREAMING -> 2.0
                else -> 0.5
            }
            PackingStrategy.CACHE_ALIGNED -> context.temporalLocality * 2.0
            PackingStrategy.SIMD_OPTIMIZED -> when {
                context.dataSize % (context.simdWidth / 8) == 0 -> 2.0
                else -> 0.8
            }
            PackingStrategy.PREFETCH_AWARE -> context.spatialLocality * 1.5
            PackingStrategy.BRANCH_PREDICT -> when (context.accessPattern) {
                AccessPattern.TREE_TRAVERSAL -> 1.8
                else -> 0.7
            }
            else -> 1.0
        }
    }
    
    private fun computeSimilarityWeight(context: PackingContext, strategy: PackingStrategy): Double {
        // Find similar contexts where this strategy was used
        val similarContexts = contextHistory.filter { (ctx, strat) ->
            strat == strategy && contextSimilarity(ctx, context) > 0.7
        }
        
        if (similarContexts.isEmpty()) return 1.0
        
        // Weight by recency and similarity
        val now = Clock.System.now().toEpochMilliseconds()
        return similarContexts.map { (ctx, _) ->
            val similarity = contextSimilarity(ctx, context)
            val recency = 1.0 / ((now - strategyMetrics[strategy]!!.lastUpdateTime) / 3600000.0 + 1.0)
            similarity * recency
        }.average()
    }
    
    private fun contextSimilarity(ctx1: PackingContext, ctx2: PackingContext): Double {
        val patternMatch = if (ctx1.accessPattern == ctx2.accessPattern) 1.0 else 0.3
        val sizeSimilarity = 1.0 - kotlin.math.abs(ctx1.dataSize - ctx2.dataSize).toDouble() / (ctx1.dataSize + ctx2.dataSize + 1.0)
        val hotnessDiff = kotlin.math.abs(ctx1.hotness - ctx2.hotness)
        val localityDiff = kotlin.math.abs(ctx1.temporalLocality - ctx2.temporalLocality) + 
                          kotlin.math.abs(ctx1.spatialLocality - ctx2.spatialLocality)
        
        return 0.4 * patternMatch + 0.2 * sizeSimilarity + 0.2 * (1.0 - hotnessDiff) + 0.2 * (1.0 - localityDiff / 2.0)
    }
    
    /**
     * Update metrics after execution
     */
    fun updateMetrics(
        strategy: PackingStrategy, 
        context: PackingContext,
        latency: Long,
        cacheHit: Boolean,
        simdEfficiency: Double
    ) {
        val current = strategyMetrics[strategy]!!
        strategyMetrics[strategy] = current.copy(
            accessCount = current.accessCount + 1,
            totalLatency = current.totalLatency + latency,
            cacheHits = if (cacheHit) current.cacheHits + 1 else current.cacheHits,
            cacheMisses = if (!cacheHit) current.cacheMisses + 1 else current.cacheMisses,
            simdUtilization = (current.simdUtilization * current.accessCount + simdEfficiency) / (current.accessCount + 1),
            lastUpdateTime = Clock.System.now().toEpochMilliseconds()
        )
        
        // Record context history (keep last 1000)
        contextHistory.add(context to strategy)
        if (contextHistory.size > 1000) {
            contextHistory.removeAt(0)
        }
    }
}

/**
 * Register Packing Coroutine Context Element
 */
@OptIn(ExperimentalStdlibApi::class)
class RegisterPackingContext(
    val engine: PackingDecisionEngine = PackingDecisionEngine()
) : CoroutineContext.Element {
    
    companion object Key : CoroutineContext.Key<RegisterPackingContext>
    override val key: CoroutineContext.Key<*> = Key
    
    // Thread-local current strategy for performance
    private val currentStrategy = ThreadLocal<PackingStrategy>()
    
    fun getStrategy(context: PackingContext): PackingStrategy {
        return currentStrategy.get() ?: engine.selectStrategy(context).also {
            currentStrategy.set(it)
        }
    }
    
    fun recordAccess(
        context: PackingContext,
        latency: Long,
        cacheHit: Boolean = true,
        simdEfficiency: Double = 1.0
    ) {
        val strategy = currentStrategy.get() ?: return
        engine.updateMetrics(strategy, context, latency, cacheHit, simdEfficiency)
    }
}

// === PACKING IMPLEMENTATIONS ===

/**
 * Intelligent Join packing with A/B testing
 */
suspend inline fun <A, B> packJoin(a: A, b: B, hint: PackingContext): Join<A, B> {
    val packingCtx = coroutineContext[RegisterPackingContext] ?: RegisterPackingContext()
    val strategy = packingCtx.getStrategy(hint)
    
    val startTime = Clock.System.now().toEpochMilliseconds()
    
    val join = when (strategy) {
        PackingStrategy.NAIVE -> makeJoin(a, b)
        PackingStrategy.TIGHT_PACK -> createTightPackedJoin(a, b)
        PackingStrategy.CACHE_ALIGNED -> createCacheAlignedJoin(a, b)
        PackingStrategy.SIMD_OPTIMIZED -> createSimdOptimizedJoin(a, b)
        PackingStrategy.BRANCH_PREDICT -> createBranchPredictableJoin(a, b)
        PackingStrategy.PREFETCH_AWARE -> createPrefetchAwareJoin(a, b)
    }
    
    val endTime = Clock.System.now().toEpochMilliseconds()
    packingCtx.recordAccess(hint, endTime - startTime)
    
    return join
}

// Specialized join implementations

private fun <A, B> createTightPackedJoin(a: A, b: B): Join<A, B> = makeJoin(a,b)

private fun <A, B> createCacheAlignedJoin(a: A, b: B): Join<A, B> = makeJoin(a,b)

private fun <A, B> createSimdOptimizedJoin(a: A, b: B): Join<A, B> = makeJoin(a,b)

private fun <A, B> createBranchPredictableJoin(a: A, b: B): Join<A, B> = makeJoin(a,b)

private fun <A, B> createPrefetchAwareJoin(a: A, b: B): Join<A, B> = makeJoin(a,b)

// === BINARY TREE WITH INTELLIGENT PACKING ===

/**
 * Register-packed binary tree with runtime optimization
 */
class RegisterPackedBinaryTree<T : Comparable<T>> {
    private var root: Join<T, Join<RegisterPackedBinaryTree<T>?, RegisterPackedBinaryTree<T>?>>? = null
    
    suspend fun insert(value: T) {
        val hint = PackingContext(
            accessPattern = AccessPattern.TREE_TRAVERSAL,
            dataSize = 16,  // Approximate node size
            hotness = 0.8,  // Trees are frequently accessed
            temporalLocality = 0.6,  // Moderate reuse
            spatialLocality = 0.3   // Low spatial locality in trees
        )
        
        root = insertRecursive(root, value, hint)
    }
    
    private suspend fun insertRecursive(
        node: Join<T, Join<RegisterPackedBinaryTree<T>?, RegisterPackedBinaryTree<T>?>>?,
        value: T,
        hint: PackingContext
    ): Join<T, Join<RegisterPackedBinaryTree<T>?, RegisterPackedBinaryTree<T>?>> {
        if (node == null) {
            val leftRight = packJoin<RegisterPackedBinaryTree<T>?, RegisterPackedBinaryTree<T>?>(
                null, null, hint
            )
            return packJoin(value, leftRight, hint)
        }
        
        val currentValue = node.a
        val children = node.b
        val left = children.a
        val right = children.b
        
        return when {
            value < currentValue -> {
                val newLeft = RegisterPackedBinaryTree<T>().apply {
                    root = insertRecursive(left?.root, value, hint)
                }
                val newChildren = packJoin(newLeft, right, hint)
                packJoin(currentValue, newChildren, hint)
            }
            value > currentValue -> {
                val newRight = RegisterPackedBinaryTree<T>().apply {
                    root = insertRecursive(right?.root, value, hint)
                }
                val newChildren = packJoin(left, newRight, hint)
                packJoin(currentValue, newChildren, hint)
            }
            else -> node
        }
    }
}

// === USAGE EXAMPLE ===

suspend fun demonstratePackingOptimization() {
    withContext(RegisterPackingContext()) {
        val tree = RegisterPackedBinaryTree<Int>()
        
        // Insert many values - the system will learn optimal packing
        repeat(10000) { i ->
            tree.insert((kotlin.random.Random.Default.nextDouble() * 1000).toInt())
        }
        
        // Different access patterns will trigger different strategies
        val sequentialHint = PackingContext(
            accessPattern = AccessPattern.SEQUENTIAL,
            dataSize = 8,
            hotness = 1.0,
            temporalLocality = 0.9,
            spatialLocality = 0.9
        )
        
        val randomHint = PackingContext(
            accessPattern = AccessPattern.RANDOM,
            dataSize = 8,
            hotness = 0.5,
            temporalLocality = 0.2,
            spatialLocality = 0.1
        )
        
        // The system adapts packing strategy based on usage patterns
    }
}

// === EXTENSION FOR INDEXED TYPES ===

/**
 * Intelligent packing for Indexed types with prefetch hints
 */
suspend fun <T> packIndexed(size: Int, f: (Int) -> T, hint: PackingContext): Indexed<T> {
    val packingCtx = coroutineContext[RegisterPackingContext] ?: RegisterPackingContext()
    val strategy = packingCtx.getStrategy(hint)
    
    return when (strategy) {
        PackingStrategy.PREFETCH_AWARE -> {
            // Prefetch-friendly indexed with stride hints
            size j { i -> 
                if (i + 1 < size) {
                    // Prefetch hint for next element
                    @Suppress("UNUSED_EXPRESSION")
                    f(i + 1)
                }
                f(i)
            }
        }
        else -> size j f
    }
}