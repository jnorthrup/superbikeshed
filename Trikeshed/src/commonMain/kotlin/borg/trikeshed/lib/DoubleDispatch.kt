package borg.trikeshed.lib

// Core double dispatch types using TrikeShed patterns
typealias DoubleDispatchEntry<A, B, R> = Join<Join<(A) -> Boolean, (B) -> Boolean>, (A, B) -> R>
typealias DoubleDispatchTable<A, B, R> = Series<DoubleDispatchEntry<A, B, R>>

// Double dispatch operator - finds first matching entry and executes handler
fun <A, B, R> DoubleDispatchTable<A, B, R>.doubleDispatch(a: A, b: B): R? = 
    this.▶.firstOrNull { entry ->
        val (predicateJoin, _) = entry
        val (aPredicate, bPredicate) = predicateJoin
        aPredicate(a) && bPredicate(b)
    }?.b?.invoke(a, b)

// Operator version using Join<A,B> input  
infix fun <A, B, R> DoubleDispatchTable<A, B, R>.`**`(pair: Join<A, B>): R? = 
    doubleDispatch(pair.a, pair.b)

// Helper for common wildcard patterns
fun <T> wildcard(): (T) -> Boolean = { true }
fun <T> typeIs(type: Class<T>): (Any) -> Boolean = { type.isInstance(it) }
inline fun <reified T> typeIs(): (Any) -> Boolean = { it is T }

// Safe spill system for non-register operations
@JvmInline value class SpillLog(val joins: MutableList<Join<*, *>> = mutableListOf())

object SafeSpillManager {
    private val spillLog = SpillLog()
    
    fun <A, B> logSafeSpill(operation: String, join: Join<A, B>) {
        spillLog.joins.add(join)
        // Debug only - gets optimized out in production
        logDebug { "Safe spill in $operation: preserving ${join.a} j ${join.b}" }
    }
    
    // Retrieve spills for analysis/optimization
    fun getSpills(): Series<Join<*, *>> = spillLog.joins.toSeries()
    
    // Clear spill log
    fun clearSpills() = spillLog.joins.clear()
}

// Spill strategies for different operation types
enum class SpillStrategy { 
    PRESERVE_JOIN,    // Safe default - keep Join intact
    LOG_AND_CONTINUE, // Log but don't break processing
    REGISTER_EXACT    // Must match exactly - no spills allowed
}

// Enhanced double dispatch with spill strategy
fun <A, B, R> DoubleDispatchTable<A, B, R>.dispatchWithStrategy(
    a: A, 
    b: B, 
    strategy: SpillStrategy = SpillStrategy.PRESERVE_JOIN
): R? = when (strategy) {
    SpillStrategy.PRESERVE_JOIN -> safeDoubleDispatch(a, b)
    SpillStrategy.LOG_AND_CONTINUE -> safeDoubleDispatch(a, b) 
    SpillStrategy.REGISTER_EXACT -> exactDispatch(a, b)
}

// Safe double dispatch with Join preservation in spills
fun <A, B, R> DoubleDispatchTable<A, B, R>.safeDoubleDispatch(a: A, b: B): R? = 
    doubleDispatch(a, b) ?: handleSafeSpill(a j b)

// Exact dispatch for register operations - no spills allowed
fun <A, B, R> DoubleDispatchTable<A, B, R>.exactDispatch(a: A, b: B): R = 
    doubleDispatch(a, b) ?: throw IllegalStateException("No exact match for register operation: ${a j b}")

// Safe spill handler that preserves Join structure
fun <A, B, R> handleSafeSpill(originalJoin: Join<A, B>): R? where R : Any? {
    SafeSpillManager.logSafeSpill("double_dispatch", originalJoin)
    return null // Safe default for non-register operations
}