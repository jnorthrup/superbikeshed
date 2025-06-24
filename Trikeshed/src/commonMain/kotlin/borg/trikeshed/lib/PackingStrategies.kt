package borg.trikeshed.lib

/**
 * TrikeShed Packing Strategies - Built on CoreTypes.kt foundation
 * Uses existing Join<A,B> and Either<L,R> from CoreTypes.kt
 */

// === PACKING RESULT TAXONOMY ===

/**
 * A concrete result from a successful packing operation.
 * PackedResult IS a Join where 'a' is the packed data and 'b' is the metadata.
 */
sealed interface PackedResult<A, B> : Join<A, B>

/** The result of a failed packing attempt - using existing Join from CoreTypes.kt */
typealias Spill<A, B> = Join<A, B>

/** A universally applicable typealias for a packing operation's outcome */
typealias PackingEither<A, B, P_A, P_B> = Either<Spill<A, B>, PackedResult<P_A, P_B>>

/**
 * The "secret handshake" for high-performance consumers.
 * Cast to this for unified view regardless of strategy.
 */
interface PackedView {
    fun getAsLong(index: Int): Long
    val elementCount: Int
}

// === CONCRETE PACKING STRATEGY DATA CLASSES ===

// Holds cluster definitions for advanced strategies
data class ClusterInfo(val base: Long, val bitsPerOffset: Int)

// The concrete packing strategy classes - all implement PackedResult<A,B>
data class DiagonalPacked(val reg: Long) : PackedResult<Long, Nothing?> {
    override val a = reg
    override val b = null
}

data class PrefixedPacked(val reg: Long, val prefix: Byte) : PackedResult<Long, Byte> {
    override val a = reg
    override val b = prefix
}

data class RangeOffsetPacked(val regs: LongArray, val base: Long) : PackedResult<LongArray, Long> {
    override val a = regs
    override val b = base
}

data class RelativeIncrementPacked(val regs: LongArray, val base: Long) : PackedResult<LongArray, Long> {
    override val a = regs
    override val b = base
}

data class PalettePacked(val regs: LongArray, val palette: Array<*>) : PackedResult<LongArray, Array<*>> {
    override val a = regs
    override val b = palette
}

data class MultiClusterPacked(val regs: LongArray, val clusters: Array<ClusterInfo>) : PackedResult<LongArray, Array<ClusterInfo>> {
    override val a = regs
    override val b = clusters
}

// === EITHER EXTENSIONS FOR PACKING ===

/**
 * Fold operation for Either - needed for packing waterfall
 */
inline fun <L, R, T> Either<L, R>.fold(onLeft: (L) -> T, onRight: (R) -> T): T = when (this) {
    is Either.Left -> onLeft(value)
    is Either.Right -> onRight(value)
}

// === DUAL-DISPATCH PACKING ENGINE ===

/**
 * Core packing strategies with Either waterfall
 */
object Packer {
    
    // Heuristic waterfall - tries strategies in order of efficiency
    fun <A, B> tryDiagonalPack(a: A, b: B): PackingEither<A, B, Long, Nothing?> {
        // Implementation for diagonal packing strategy
        return if (canDiagonalPack(a, b)) {
            Either.right(DiagonalPacked(packDiagonal(a, b)))
        } else {
            Either.left(a j b)
        }
    }
    
    fun <A, B> tryPrefixedPack(a: A, b: B): PackingEither<A, B, Long, Byte> {
        return if (canPrefixedPack(a, b)) {
            val (packed, prefix) = packPrefixed(a, b)
            Either.right(PrefixedPacked(packed, prefix))
        } else {
            Either.left(a j b)
        }
    }
    
    fun <A, B> tryRangeOffsetPack(a: A, b: B): PackingEither<A, B, LongArray, Long> {
        return if (canRangeOffsetPack(a, b)) {
            val (regs, base) = packRangeOffset(a, b)
            Either.right(RangeOffsetPacked(regs, base))
        } else {
            Either.left(a j b)
        }
    }
    
    fun <A, B> tryRelativeIncrementPack(a: A, b: B): PackingEither<A, B, LongArray, Long> {
        return if (canRelativeIncrementPack(a, b)) {
            val (regs, base) = packRelativeIncrement(a, b)
            Either.right(RelativeIncrementPacked(regs, base))
        } else {
            Either.left(a j b)
        }
    }
    
    fun <A, B> tryPalettePack(a: A, b: B): PackingEither<A, B, LongArray, Array<*>> {
        return if (canPalettePack(a, b)) {
            val (regs, palette) = packPalette(a, b)
            Either.right(PalettePacked(regs, palette))
        } else {
            Either.left(a j b)
        }
    }
    
    fun <A, B> tryMultiClusterPack(a: A, b: B): PackingEither<A, B, LongArray, Array<ClusterInfo>> {
        return if (canMultiClusterPack(a, b)) {
            val (regs, clusters) = packMultiCluster(a, b)
            Either.right(MultiClusterPacked(regs, clusters))
        } else {
            Either.left(a j b)
        }
    }
    
    // Context-aware dual-dispatch mechanism with jk/kj pattern
    fun <A, B> pack(a: A, b: B, context: PackingContext = PackingContext.DEFAULT): Join<A, B> {
        // Register fastlane - try primitive packing first (0-1 cycles)
        if (context.shouldAttempt(PackingStrategy.MINIMAL, 1, 1)) {
            when {
                a is borg.trikeshed.parse.Token && b is borg.trikeshed.parse.Token -> {
                    RegisterFastlane.tryTokenPack(a, b)?.let { return it as Join<A, B> }
                }
                a is Int && b is Int -> {
                    val packed = (a.toLong() shl 32) or (b.toLong() and 0xFFFFFFFF)
                    return DiagonalPacked(packed) as Join<A, B>
                }
                a is Boolean && b is Boolean -> {
                    val packed = (if (a) 1L else 0L) or (if (b) 2L else 0L)
                    return DiagonalPacked(packed) as Join<A, B>
                }
                a is Byte && b is Byte -> {
                    val packed = (a.toLong() shl 8) or (b.toLong() and 0xFF)
                    return DiagonalPacked(packed) as Join<A, B>
                }
            }
        }
        
        // Estimate data size for context decisions
        val dataSize = estimateDataSize(a, b)
        
        // Try diagonal packing (always allowed - zero cost)
        if (context.shouldAttempt(PackingStrategy.MINIMAL, dataSize, 1)) {
            tryDiagonalPack(a, b).fold(
                onLeft = { },
                onRight = { return it as Join<A, B> }
            )
        }
        
        // Try prefix packing if context allows
        if (context.shouldAttempt(PackingStrategy.STANDARD, dataSize, context.estimateCost(PackingStrategy.STANDARD, dataSize))) {
            tryPrefixedPack(a, b).fold(
                onLeft = { },
                onRight = { return it as Join<A, B> }
            )
        }
        
        // Try range offset packing if context allows
        if (context.shouldAttempt(PackingStrategy.STANDARD, dataSize, context.estimateCost(PackingStrategy.STANDARD, dataSize))) {
            tryRangeOffsetPack(a, b).fold(
                onLeft = { },
                onRight = { return it as Join<A, B> }
            )
        }
        
        // Try relative increment packing if context allows
        if (context.shouldAttempt(PackingStrategy.STANDARD, dataSize, context.estimateCost(PackingStrategy.STANDARD, dataSize))) {
            tryRelativeIncrementPack(a, b).fold(
                onLeft = { },
                onRight = { return it as Join<A, B> }
            )
        }
        
        // Try expensive strategies only if context allows
        if (context.shouldAttempt(PackingStrategy.AGGRESSIVE, dataSize, context.estimateCost(PackingStrategy.AGGRESSIVE, dataSize))) {
            tryPalettePack(a, b).fold(
                onLeft = { },
                onRight = { return it as Join<A, B> }
            )
            
            tryMultiClusterPack(a, b).fold(
                onLeft = { },
                onRight = { return it as Join<A, B> }
            )
        }
        
        // Fall back to simple Join if no packing strategy worked
        return a j b
    }
    
    // Data size estimation for context decisions
    private fun <A, B> estimateDataSize(a: A, b: B): Int {
        return when {
            a is Collection<*> -> a.size
            b is Collection<*> -> b.size
            a is Array<*> -> a.size
            b is Array<*> -> b.size
            a is String -> a.length
            b is String -> b.length
            else -> 1 // Single primitive values
        }
    }
    
    // Strategy detection methods
    private fun <A, B> canDiagonalPack(a: A, b: B): Boolean {
        // Placeholder - implement actual heuristics
        return false
    }
    
    private fun <A, B> canPrefixedPack(a: A, b: B): Boolean {
        // Placeholder - implement actual heuristics  
        return false
    }
    
    private fun <A, B> canRangeOffsetPack(a: A, b: B): Boolean {
        // Placeholder - implement actual heuristics
        return false
    }
    
    private fun <A, B> canRelativeIncrementPack(a: A, b: B): Boolean {
        // Placeholder - implement actual heuristics
        return false
    }
    
    private fun <A, B> canPalettePack(a: A, b: B): Boolean {
        // Placeholder - implement actual heuristics
        return false
    }
    
    private fun <A, B> canMultiClusterPack(a: A, b: B): Boolean {
        // Placeholder - implement actual heuristics
        return false
    }
    
    // Packing implementation methods
    private fun <A, B> packDiagonal(a: A, b: B): Long {
        // Placeholder - implement actual packing
        return 0L
    }
    
    private fun <A, B> packPrefixed(a: A, b: B): Pair<Long, Byte> {
        // Placeholder - implement actual packing
        return Pair(0L, 0.toByte())
    }
    
    private fun <A, B> packRangeOffset(a: A, b: B): Pair<LongArray, Long> {
        // Placeholder - implement actual packing
        return Pair(longArrayOf(), 0L)
    }
    
    private fun <A, B> packRelativeIncrement(a: A, b: B): Pair<LongArray, Long> {
        // Placeholder - implement actual packing
        return Pair(longArrayOf(), 0L)
    }
    
    private fun <A, B> packPalette(a: A, b: B): Pair<LongArray, Array<*>> {
        // Placeholder - implement actual packing
        return Pair(longArrayOf(), arrayOf<Any>())
    }
    
    private fun <A, B> packMultiCluster(a: A, b: B): Pair<LongArray, Array<ClusterInfo>> {
        // Placeholder - implement actual packing
        return Pair(longArrayOf(), arrayOf<ClusterInfo>())
    }
}

// === USER-FACING OPERATORS ===

/**
 * Enhanced j operator that triggers automatic packing with default context
 */
infix fun <A, B> A.jj(b: B): Join<A, B> = Packer.pack(this, b)

/**
 * Context-aware packing operator - uses PackingContext from coroutine context if available
 */
suspend fun <A, B> A.jc(b: B): Join<A, B> {
    val context = kotlin.coroutines.coroutineContext[PackingContext.Key] ?: PackingContext.DEFAULT
    return Packer.pack(this, b, context)
}

/**
 * Explicit context packing operator
 */
fun <A, B> A.jp(b: B, context: PackingContext): Join<A, B> = Packer.pack(this, b, context)

/**
 * Forced non-packing j operator for when you want raw Join
 */
infix fun <A, B> A.jn(b: B): Join<A, B> = this j b