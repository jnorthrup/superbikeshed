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

/**
 * Interface for a compositional packing strategy.
 * Each strategy can determine if it applies and then perform the packing.
 */
interface PackerStrategy<A, B> {
    /** Determines if this strategy can be applied to the given data. */
    fun canPack(
        a: A,
        b: B,
        context: PackingContext,
    ): Boolean

    /** Executes the packing operation. */
    fun pack(
        a: A,
        b: B,
    ): PackingEither<A, B, *, *>
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
inline fun <L, R, T> Either<L, R>.fold(
    onLeft: (L) -> T,
    onRight: (R) -> T,
): T =
    when (this) {
        is Either.Left -> onLeft(value)
        is Either.Right -> onRight(value)
    }

// === DUAL-DISPATCH PACKING ENGINE ===

/**
 * Core packing strategies with Either waterfall
 */
object Packer {
    // A declarative, compositional list of strategies. Extensible.
    private val strategies: List<PackerStrategy<Any?, Any?>> =
        listOf(
            object : PackerStrategy<Any?, Any?> {
                override fun canPack(
                    a: Any?,
                    b: Any?,
                    context: PackingContext,
                ) = canDiagonalPack(a, b)

                override fun pack(
                    a: Any?,
                    b: Any?,
                ) = Either.right(DiagonalPacked(packDiagonal(a, b)))
            },
            object : PackerStrategy<Any?, Any?> {
                override fun canPack(
                    a: Any?,
                    b: Any?,
                    context: PackingContext,
                ) = canPrefixedPack(a, b)

                override fun pack(
                    a: Any?,
                    b: Any?,
                ) = packPrefixed(a, b).let { Either.right(PrefixedPacked(it.first, it.second)) }
            },
            object : PackerStrategy<Any?, Any?> {
                override fun canPack(
                    a: Any?,
                    b: Any?,
                    context: PackingContext,
                ) = context.strategy >= PackingStrategy.STANDARD && canRangeOffsetPack(a, b)

                override fun pack(
                    a: Any?,
                    b: Any?,
                ) = packRangeOffset(a, b).let { Either.right(RangeOffsetPacked(it.first, it.second)) }
            },
            object : PackerStrategy<Any?, Any?> {
                override fun canPack(
                    a: Any?,
                    b: Any?,
                    context: PackingContext,
                ) = context.strategy >= PackingStrategy.STANDARD && canRelativeIncrementPack(a, b)

                override fun pack(
                    a: Any?,
                    b: Any?,
                ) = packRelativeIncrement(
                    a,
                    b,
                ).let { Either.right(RelativeIncrementPacked(it.first, it.second)) }
            },
            object : PackerStrategy<Any?, Any?> {
                override fun canPack(
                    a: Any?,
                    b: Any?,
                    context: PackingContext,
                ) = context.strategy >= PackingStrategy.AGGRESSIVE && canPalettePack(a, b)

                override fun pack(
                    a: Any?,
                    b: Any?,
                ) = packPalette(a, b).let { Either.right(PalettePacked(it.first, it.second)) }
            },
            object : PackerStrategy<Any?, Any?> {
                override fun canPack(
                    a: Any?,
                    b: Any?,
                    context: PackingContext,
                ) = context.strategy >= PackingStrategy.AGGRESSIVE && canMultiClusterPack(a, b)

                override fun pack(
                    a: Any?,
                    b: Any?,
                ) = packMultiCluster(a, b).let { Either.right(MultiClusterPacked(it.first, it.second)) }
            },
        )

    // Context-aware dual-dispatch mechanism with jk/kj pattern
    fun <A, B> pack(
        a: A,
        b: B,
        context: PackingContext = PackingContext.DEFAULT,
    ): Join<A, B> {
        // Register fastlane - try primitive packing first (0-1 cycles)
        if (context.shouldAttempt(PackingStrategy.MINIMAL, 1, 1)) {
            // Try generic primitive packing first
            RegisterFastlane.tryPrimitivePack(a, b)?.let { return it as Join<A, B> }

            // Try specialized token packing if we have tokens
            if (a is borg.trikeshed.parse.Token && b is borg.trikeshed.parse.Token) {
                RegisterFastlane.tryTokenPack(a, b)?.let { return it as Join<A, B> }
            }
        }

        // Iterate through the compositional strategies
        for (strategy in strategies) {
            try {
                if (strategy.canPack(a, b, context)) {
                    val result = strategy.pack(a, b)
                    if (result is Either.Right) {
                        return result.value as Join<A, B>
                    }
                }
            } catch (e: ClassCastException) {
                // This strategy doesn't apply to these types, continue to the next one.
            }
        }

        // Fall back to simple Join if no packing strategy worked
        return a j b
    }

    // Data size estimation for context decisions
    private fun <A, B> estimateDataSize(
        a: A,
        b: B,
    ): Int {
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
    private fun <A, B> canDiagonalPack(
        a: A,
        b: B,
    ): Boolean {
        // Placeholder - implement actual heuristics
        return false
    }

    private fun <A, B> canPrefixedPack(
        a: A,
        b: B,
    ): Boolean {
        // Placeholder - implement actual heuristics
        return false
    }

    private fun <A, B> canRangeOffsetPack(
        a: A,
        b: B,
    ): Boolean {
        // Placeholder - implement actual heuristics
        return false
    }

    private fun <A, B> canRelativeIncrementPack(
        a: A,
        b: B,
    ): Boolean {
        // Placeholder - implement actual heuristics
        return false
    }

    private fun <A, B> canPalettePack(
        a: A,
        b: B,
    ): Boolean {
        // Placeholder - implement actual heuristics
        return false
    }

    private fun <A, B> canMultiClusterPack(
        a: A,
        b: B,
    ): Boolean {
        // Placeholder - implement actual heuristics
        return false
    }

    // Packing implementation methods
    private fun <A, B> packDiagonal(
        a: A,
        b: B,
    ): Long {
        // Placeholder - implement actual packing
        return 0L
    }

    private fun <A, B> packPrefixed(
        a: A,
        b: B,
    ): Pair<Long, Byte> {
        // Placeholder - implement actual packing
        return Pair(0L, 0.toByte())
    }

    private fun <A, B> packRangeOffset(
        a: A,
        b: B,
    ): Pair<LongArray, Long> {
        // Placeholder - implement actual packing
        return Pair(longArrayOf(), 0L)
    }

    private fun <A, B> packRelativeIncrement(
        a: A,
        b: B,
    ): Pair<LongArray, Long> {
        // Placeholder - implement actual packing
        return Pair(longArrayOf(), 0L)
    }

    private fun <A, B> packPalette(
        a: A,
        b: B,
    ): Pair<LongArray, Array<*>> {
        // Placeholder - implement actual packing
        return Pair(longArrayOf(), arrayOf<Any>())
    }

    private fun <A, B> packMultiCluster(
        a: A,
        b: B,
    ): Pair<LongArray, Array<ClusterInfo>> {
        // Placeholder - implement actual packing
        return Pair(longArrayOf(), arrayOf<ClusterInfo>())
    }
}

// === USER-FACING OPERATORS ===

/**
 * Enhanced j operator that triggers automatic packing with default context
 */
inline infix fun <A, B> A.jj(b: B): Join<A, B> = Packer.pack(this, b)

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
fun <A, B> A.jp(
    b: B,
    context: PackingContext,
): Join<A, B> = Packer.pack(this, b, context)

/**
 * Forced non-packing j operator for when you want raw Join
 */
infix fun <A, B> A.jn(b: B): Join<A, B> = this j b
