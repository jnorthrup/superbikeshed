package borg.trikeshed.lib
@file:OptIn(ExperimentalUnsignedTypes::class)


import borg.trikeshed.reactor.currentTimeMillis
import kotlin.coroutines.CoroutineContext

/**
 * CCEK-driven Packing Context for controlling Join factory strategy selection
 * beyond primitive packing linear approaches.
 */

/**
 * CPU budget for packing operations - controls how much analysis we're willing to do
 */
@kotlin.jvm.JvmInline
value class CpuBudget(
    val cycles: Long,
) {
    companion object {
        val MINIMAL = CpuBudget(10) // Hot path - diagonal only
        val STANDARD = CpuBudget(100) // Warm path - basic strategies
        val AGGRESSIVE = CpuBudget(1000) // Cold path - all strategies
        val UNLIMITED = CpuBudget(Long.MAX_VALUE) // Offline/batch processing
    }
}

/**
 * Packing strategy selection based on context
 */
enum class PackingStrategy {
    /** Diagonal packing only - zero analysis cost */
    MINIMAL,

    /** Diagonal + Prefix + basic heuristics - low analysis cost */
    STANDARD,

    /** All strategies including expensive clustering - high analysis cost */
    AGGRESSIVE,

    /** Adaptive selection based on data characteristics */
    ADAPTIVE,
}

/**
 * Priority level for packing operations
 */
enum class PackingPriority {
    /** Latency critical - skip expensive analysis */
    REALTIME,

    /** Balance speed and memory efficiency */
    BALANCED,

    /** Optimize for memory at expense of CPU */
    MEMORY_OPTIMIZED,
}

/**
 * Context-driven packing configuration as CoroutineContext.Element
 * This is the CCEK that controls j factory behavior beyond primitive packing.
 */
data class PackingContext(
    val strategy: PackingStrategy = PackingStrategy.STANDARD,
    val budget: CpuBudget = CpuBudget.STANDARD,
    val priority: PackingPriority = PackingPriority.BALANCED,
    val forceSimpleThreshold: Int = 10,
    val complexThreshold: Int = 1000,
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key

    companion object Key : CoroutineContext.Key<PackingContext> {
        /** Default context for when none is specified */
        val DEFAULT = PackingContext()

        /** Hot path context - minimal overhead */
        val HOT_PATH =
            PackingContext(
                strategy = PackingStrategy.MINIMAL,
                budget = CpuBudget.MINIMAL,
                priority = PackingPriority.REALTIME,
            )

        /** Batch processing context - maximize compression */
        val BATCH =
            PackingContext(
                strategy = PackingStrategy.AGGRESSIVE,
                budget = CpuBudget.UNLIMITED,
                priority = PackingPriority.MEMORY_OPTIMIZED,
            )
    }

    /**
     * Determines if a packing strategy should be attempted based on context
     */
    fun shouldAttempt(
        strategy: PackingStrategy,
        dataSize: Int,
        estimatedCost: Long,
    ): Boolean {
        // Always skip if over budget
        if (estimatedCost > budget.cycles) return false

        // Force simple for small data
        if (dataSize < forceSimpleThreshold && strategy != PackingStrategy.MINIMAL) {
            return false
        }

        // Context-based strategy gating
        return when (this.strategy) {
            PackingStrategy.MINIMAL -> strategy == PackingStrategy.MINIMAL
            PackingStrategy.STANDARD -> strategy != PackingStrategy.AGGRESSIVE
            PackingStrategy.AGGRESSIVE -> true
            PackingStrategy.ADAPTIVE -> {
                when {
                    priority == PackingPriority.REALTIME -> strategy == PackingStrategy.MINIMAL
                    dataSize < complexThreshold -> strategy != PackingStrategy.AGGRESSIVE
                    else -> true
                }
            }
        }
    }

    /**
     * Get estimated cost for a packing strategy based on data characteristics
     */
    fun estimateCost(
        strategy: PackingStrategy,
        dataSize: Int,
    ): Long =
        when (strategy) {
            PackingStrategy.MINIMAL -> 1L // Diagonal packing - essentially free
            PackingStrategy.STANDARD -> dataSize.toLong() * 5 // Linear scan with basic analysis
            PackingStrategy.AGGRESSIVE -> dataSize.toLong() * dataSize / 100 // Quadratic analysis
            PackingStrategy.ADAPTIVE -> dataSize.toLong() * 10 // Adaptive overhead
        }
}
