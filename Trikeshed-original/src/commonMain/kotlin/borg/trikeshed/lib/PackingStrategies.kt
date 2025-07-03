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
data class ClusterInfo(
    val base: Long,
    val bitsPerOffset: Int,
)

// The concrete packing strategy classes - all implement PackedResult<A,B>
data class DiagonalPacked(
    val reg: Long,
) : PackedResult<Long, Nothing?> {
    override val a = reg
    override val b = null
}

data class PrefixedPacked(
    val reg: Long,
    val prefix: Byte,
) : PackedResult<Long, Byte> {
    override val a = reg
    override val b = prefix
}

data class RangeOffsetPacked(
    val regs: LongArray,
    val base: Long,
) : PackedResult<LongArray, Long> {
    override val a = regs
    override val b = base
}

data class RelativeIncrementPacked(
    val regs: LongArray,
    val base: Long,
) : PackedResult<LongArray, Long> {
    override val a = regs
    override val b = base
}

data class PalettePacked(
    val regs: LongArray,
    val palette: Array<*>,
) : PackedResult<LongArray, Array<*>> {
    override val a = regs
    override val b = palette
}

data class MultiClusterPacked(
    val regs: LongArray,
    val clusters: Array<ClusterInfo>,
) : PackedResult<LongArray, Array<ClusterInfo>> {
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
    ): Int =
        when {
            a is Collection<*> -> a.size
            b is Collection<*> -> b.size
            a is Array<*> -> a.size
            b is Array<*> -> b.size
            a is String -> a.length
            b is String -> b.length
            else -> 1 // Single primitive values
        }

    // Strategy detection methods
    private fun <A, B> canDiagonalPack(
        a: A,
        b: B,
    ): Boolean {
        // Diagonal packing works when both values can fit in a single Long
        // and they have a simple mathematical relationship
        return when {
            a is Int && b is Int -> {
                val aLong = a.toLong()
                val bLong = b.toLong()
                // Check if we can pack them into 32 bits each
                aLong >= 0 && aLong <= 0xFFFFFFFFL && 
                bLong >= 0 && bLong <= 0xFFFFFFFFL
            }
            a is Long && b is Long -> {
                // For Longs, check if they can be combined meaningfully
                // This is a simple heuristic - could be enhanced
                a >= 0 && b >= 0 && a <= 0x7FFFFFFFL && b <= 0x7FFFFFFFL
            }
            a is String && b is String -> {
                // For strings, check if they're short enough to pack
                a.length <= 4 && b.length <= 4
            }
            else -> false
        }
    }

    private fun <A, B> canPrefixedPack(
        a: A,
        b: B,
    ): Boolean {
        // Prefixed packing works when one value is small and can be used as a prefix
        return when {
            a is Byte && b is Long -> true
            a is Short && b is Long -> true
            a is Int && b is Long -> a >= 0 && a <= 0xFF
            a is String && b is String -> {
                // One string is very short (prefix) and the other is longer
                (a.length <= 2 && b.length > 2) || (b.length <= 2 && a.length > 2)
            }
            else -> false
        }
    }

    private fun <A, B> canRangeOffsetPack(
        a: A,
        b: B,
    ): Boolean {
        // Range offset works when we have arrays/lists with values in a known range
        return when {
            a is Array<*> && b is Long -> {
                isArray(a, 1, Int.MAX_VALUE) && (a.all { it is Int } || a.all { it is Long } || a.all { it is Short })
            }
            a is List<*> && b is Long -> {
                a.isNotEmpty() && (a.first() is Int || a.first() is Long || a.first() is Short)
            }
            a is Long && b is Array<*> -> {
                isArray(b, 1, Int.MAX_VALUE) && (b.all { it is Int } || b.all { it is Long } || b.all { it is Short })
            }
            a is Long && b is List<*> -> {
                b.isNotEmpty() && (b.first() is Int || b.first() is Long || b.first() is Short)
            }
            else -> false
        }
    }

    private fun <A, B> canRelativeIncrementPack(
        a: A,
        b: B,
    ): Boolean {
        // Relative increment works when we have sequences with small differences
        return when {
            a is Array<*> && b is Long -> {
                isArray(a, 2, Int.MAX_VALUE) && a.all { it is Int } && a.size > 1 && hasSmallIncrements(a as Array<Int>)
            }
            a is List<*> && b is Long -> {
                a.isNotEmpty() && a.all { it is Int } && hasSmallIncrements(a as List<Int>)
            }
            else -> false
        }
    }

    private fun <A, B> canPalettePack(
        a: A,
        b: B,
    ): Boolean {
        // Palette packing works when we have repeated values
        return when {
            a is Array<*> && b is Array<*> -> {
                a.size > 4 && hasRepeatedValues(a)
            }
            a is List<*> && b is Array<*> -> {
                a.size > 4 && hasRepeatedValues(a)
            }
            else -> false
        }
    }

    private fun <A, B> canMultiClusterPack(
        a: A,
        b: B,
    ): Boolean {
        // Multi-cluster works when we have multiple distinct ranges
        return when {
            a is Array<*> && b is Array<*> -> {
                a.size > 8 && hasMultipleClusters(a)
            }
            a is List<*> && b is Array<*> -> {
                a.size > 8 && hasMultipleClusters(a)
            }
            else -> false
        }
    }

    // Helper methods for strategy detection
    private fun hasSmallIncrements(array: Array<Int>): Boolean {
        if (array.size < 2) return false
        for (i in 1 until array.size) {
            val diff = kotlin.math.abs(array[i] - array[i-1])
            if (diff > 255) return false // Too large for efficient packing
        }
        return true
    }

    private fun hasSmallIncrements(list: List<Int>): Boolean {
        if (list.size < 2) return false
        for (i in 1 until list.size) {
            val diff = kotlin.math.abs(list[i] - list[i-1])
            if (diff > 255) return false
        }
        return true
    }

    private fun hasRepeatedValues(array: Array<*>): Boolean {
        val uniqueCount = array.toSet().size
        return uniqueCount < array.size * 0.7 // At least 30% repetition
    }

    private fun hasRepeatedValues(list: List<*>): Boolean {
        val uniqueCount = list.toSet().size
        return uniqueCount < list.size * 0.7
    }

    private fun hasMultipleClusters(array: Array<*>): Boolean {
        // Simplified heuristic - could be enhanced with actual clustering
        val uniqueCount = array.toSet().size
        return uniqueCount > 2 && uniqueCount < array.size * 0.5
    }

    private fun hasMultipleClusters(list: List<*>): Boolean {
        val uniqueCount = list.toSet().size
        return uniqueCount > 2 && uniqueCount < list.size * 0.5
    }

    // Packing implementation methods
    private fun <A, B> packDiagonal(
        a: A,
        b: B,
    ): Long {
        return when {
            a is Int && b is Int -> {
                val aLong = a.toLong()
                val bLong = b.toLong()
                (aLong shl 32) or (bLong and 0xFFFFFFFFL)
            }
            a is Long && b is Long -> {
                // For Longs, we need to be more careful about bit manipulation
                val aUpper = (a shr 32) and 0x7FFFFFFFL
                val bUpper = (b shr 32) and 0x7FFFFFFFL
                (aUpper shl 32) or bUpper
            }
            a is String && b is String -> {
                // Pack short strings into a Long - direct character access
                var result = 0L
                for (i in 0 until 4) {
                    val aByte = if (i < a.length) a[i].code.toLong() else 0L
                    val bByte = if (i < b.length) b[i].code.toLong() else 0L
                    result = result or (aByte shl (i * 8)) or (bByte shl (i * 8 + 32))
                }
                result
            }
            else -> 0L
        }
    }

    private fun <A, B> packPrefixed(
        a: A,
        b: B,
    ): Pair<Long, Byte> {
        return when {
            a is Byte && b is Long -> Pair(b, a)
            a is Short && b is Long -> Pair(b, a.toByte())
            a is Int && b is Long -> Pair(b, a.toByte())
            a is String && b is String -> {
                val (prefix, main) = if (a.length <= 2) Pair(a, b) else Pair(b, a)
                var result = 0L
                for (i in 0 until 6) {
                    val byte = if (i < main.length) main[i].code.toLong() else 0L
                    result = result or (byte shl (i * 8))
                }
                val prefixByte = if (prefix.isNotEmpty()) prefix[0].code.toByte() else 0.toByte()
                Pair(result, prefixByte)
            }
            else -> Pair(0L, 0.toByte())
        }
    }

    private fun <A, B> packRangeOffset(
        a: A,
        b: B,
    ): Pair<LongArray, Long> {
        return when {
            a is Array<*> && b is Long -> {
                when {
                    a.all { it is Int } -> {
                        val intArray = a as Array<Int>
                        val base = intArray.minOrNull()?.toLong() ?: 0L
                        val regs = intArray.map { (it - base).toLong() }.toLongArray()
                        Pair(regs, base)
                    }
                    a.all { it is Long } -> {
                        val longArray = a as Array<Long>
                        val base = longArray.minOrNull() ?: 0L
                        val regs = longArray.map { it - base }.toLongArray()
                        Pair(regs, base)
                    }
                    else -> Pair(longArrayOf(), 0L)
                }
            }
            a is List<*> && b is Long -> {
                when {
                    a.all { it is Int } -> {
                        val intList = a as List<Int>
                        val base = intList.minOrNull()?.toLong() ?: 0L
                        val regs = intList.map { (it - base).toLong() }.toLongArray()
                        Pair(regs, base)
                    }
                    a.all { it is Long } -> {
                        val longList = a as List<Long>
                        val base = longList.minOrNull() ?: 0L
                        val regs = longList.map { it - base }.toLongArray()
                        Pair(regs, base)
                    }
                    else -> Pair(longArrayOf(), 0L)
                }
            }
            else -> Pair(longArrayOf(), 0L)
        }
    }

    private fun <A, B> packRelativeIncrement(
        a: A,
        b: B,
    ): Pair<LongArray, Long> {
        return when {
            a is Array<*> && b is Long -> {
                when {
                    a.all { it is Int } -> {
                        val intArray = a as Array<Int>
                        val base = intArray[0].toLong()
                        val regs = LongArray(intArray.size - 1) { i ->
                            (intArray[i + 1] - intArray[i]).toLong()
                        }
                        Pair(regs, base)
                    }
                    else -> Pair(longArrayOf(), 0L)
                }
            }
            a is List<*> && b is Long -> {
                when {
                    a.all { it is Int } -> {
                        val intList = a as List<Int>
                        val base = intList[0].toLong()
                        val regs = LongArray(intList.size - 1) { i ->
                            (intList[i + 1] - intList[i]).toLong()
                        }
                        Pair(regs, base)
                    }
                    else -> Pair(longArrayOf(), 0L)
                }
            }
            else -> Pair(longArrayOf(), 0L)
        }
    }

    private fun <A, B> packPalette(
        a: A,
        b: B,
    ): Pair<LongArray, Array<*>> {
        return when {
            a is Array<*> && b is Array<*> -> {
                val values = a.toList()
                val palette = values.toSet().toTypedArray()
                val regs = LongArray(values.size) { i ->
                    palette.indexOf(values[i]).toLong()
                }
                Pair(regs, palette)
            }
            a is List<*> && b is Array<*> -> {
                val values = a.toList()
                val palette = values.toSet().toTypedArray()
                val regs = LongArray(values.size) { i ->
                    palette.indexOf(values[i]).toLong()
                }
                Pair(regs, palette)
            }
            else -> Pair(longArrayOf(), arrayOf<Any>())
        }
    }

    private fun <A, B> packMultiCluster(
        a: A,
        b: B,
    ): Pair<LongArray, Array<ClusterInfo>> {
        // Simplified multi-cluster implementation
        return when {
            a is Array<*> && b is Array<*> -> {
                val values = a.toList()
                val clusters = arrayOf(
                    ClusterInfo(0L, 8),
                    ClusterInfo(256L, 8)
                )
                val regs = LongArray(values.size) { i ->
                    val value = values[i] as? Int ?: 0
                    if (value < 256) value.toLong() else (value - 256).toLong()
                }
                Pair(regs, clusters)
            }
            a is List<*> && b is Array<*> -> {
                val values = a.toList()
                val clusters = arrayOf(
                    ClusterInfo(0L, 8),
                    ClusterInfo(256L, 8)
                )
                val regs = LongArray(values.size) { i ->
                    val value = values[i] as? Int ?: 0
                    if (value < 256) value.toLong() else (value - 256).toLong()
                }
                Pair(regs, clusters)
            }
            else -> Pair(longArrayOf(), arrayOf<ClusterInfo>())
        }
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

// === PACKING CHORD SHEET - METASERIES CONTROLLERS ===

// Strategy detection chord - maps strategy types to detection functions
private val strategyDetectionChord: MetaSeries<PackingStrategy, (Any?, Any?) -> Boolean> =
    PackingStrategy.MINIMAL j { strategy ->
        when (strategy) {
            PackingStrategy.MINIMAL -> { a, b -> canDiagonalPack(a, b) }
            PackingStrategy.STANDARD -> { a, b -> canPrefixedPack(a, b) || canRangeOffsetPack(a, b) }
            PackingStrategy.AGGRESSIVE -> { a, b -> canPalettePack(a, b) || canMultiClusterPack(a, b) }
            PackingStrategy.EXPERIMENTAL -> { a, b -> true } // Try everything
        }
    }

// Diagonal packing chord - maps value types to diagonal packing functions
private val diagonalPackingChord: MetaSeries<Join<Any?, Any?>, () -> Boolean> =
    (1 j 2) j { (a, b) ->
        when {
            a is Int && b is Int -> {
                val aLong = a.toLong()
                val bLong = b.toLong()
                aLong >= 0 && aLong <= 0xFFFFFFFFL && 
                bLong >= 0 && bLong <= 0xFFFFFFFFL
            }
            a is Long && b is Long -> {
                a >= 0 && b >= 0 && a <= 0x7FFFFFFFL && b <= 0x7FFFFFFFL
            }
            a is String && b is String -> {
                a.length <= 4 && b.length <= 4
            }
            else -> false
        }
    }

// Prefixed packing chord - maps value types to prefixed packing functions
private val prefixedPackingChord: MetaSeries<Join<Any?, Any?>, () -> Boolean> =
    (1 j 2) j { (a, b) ->
        when {
            a is Byte && b is Long -> true
            a is Short && b is Long -> true
            a is Int && b is Long -> a >= 0 && a <= 0xFF
            a is String && b is String -> {
                (a.length <= 2 && b.length > 2) || (b.length <= 2 && a.length > 2)
            }
            else -> false
        }
    }

// Range offset packing chord - maps value types to range offset functions
private val rangeOffsetPackingChord: MetaSeries<Join<Any?, Any?>, () -> Boolean> =
    (1 j 2) j { (a, b) ->
        when {
            a is Array<*> && b is Long -> {
                a.a > 0 && a.all { it is Number }
            }
            a is Indexed<*> && b is Long -> {
                a.a > 0 && (0 until a.a).all { a.b(it) is Number }
            }
            else -> false
        }
    }

// Relative increment packing chord - maps value types to relative increment functions
private val relativeIncrementPackingChord: MetaSeries<Join<Any?, Any?>, () -> Boolean> =
    (1 j 2) j { (a, b) ->
        when {
            a is Array<*> && b is Long -> {
                a.a > 1 && a.all { it is Number }
            }
            a is Indexed<*> && b is Long -> {
                a.a > 1 && (0 until a.a).all { a.b(it) is Number }
            }
            else -> false
        }
    }

// Palette packing chord - maps value types to palette packing functions
private val palettePackingChord: MetaSeries<Join<Any?, Any?>, () -> Boolean> =
    (1 j 2) j { (a, b) ->
        when {
            a is Array<*> && b is Long -> {
                a.a > 0 && a.toSet().a < a.a * 0.8 // Has duplicates
            }
            a is Indexed<*> && b is Long -> {
                a.a > 0 && {
                    val uniqueCount = (0 until a.a).map { a.b(it) }.toSet().a
                    uniqueCount < a.a * 0.8
                }()
            }
            else -> false
        }
    }

// Multi-cluster packing chord - maps value types to multi-cluster functions
private val multiClusterPackingChord: MetaSeries<Join<Any?, Any?>, () -> Boolean> =
    (1 j 2) j { (a, b) ->
        when {
            a is Array<*> && b is Long -> {
                a.a > 10 && a.toSet().a > 3
            }
            a is Indexed<*> && b is Long -> {
                a.a > 10 && (0 until a.a).map { a.b(it) }.toSet().a > 3
            }
            else -> false
        }
    }

// Packing execution chord - maps strategy types to packing functions
private val packingExecutionChord: MetaSeries<PackingStrategy, (Any?, Any?) -> Either<Any, PackedResult>> =
    PackingStrategy.MINIMAL j { strategy ->
        when (strategy) {
            PackingStrategy.MINIMAL -> { a, b -> 
                if (canDiagonalPack(a, b)) Either.right(DiagonalPacked(packDiagonal(a, b)))
                else Either.left("Cannot diagonal pack")
            }
            PackingStrategy.STANDARD -> { a, b ->
                when {
                    canPrefixedPack(a, b) -> packPrefixed(a, b).let { Either.right(PrefixedPacked(it.first, it.second)) }
                    canRangeOffsetPack(a, b) -> packRangeOffset(a, b).let { Either.right(RangeOffsetPacked(it.first, it.second)) }
                    canRelativeIncrementPack(a, b) -> packRelativeIncrement(a, b).let { Either.right(RelativeIncrementPacked(it.first, it.second)) }
                    else -> Either.left("No standard packing strategy applicable")
                }
            }
            PackingStrategy.AGGRESSIVE -> { a, b ->
                when {
                    canPalettePack(a, b) -> packPalette(a, b).let { Either.right(PalettePacked(it.first, it.second)) }
                    canMultiClusterPack(a, b) -> packMultiCluster(a, b).let { Either.right(MultiClusterPacked(it.first, it.second)) }
                    else -> Either.left("No aggressive packing strategy applicable")
                }
            }
            PackingStrategy.EXPERIMENTAL -> { a, b ->
                // Try all strategies in order
                when {
                    canDiagonalPack(a, b) -> Either.right(DiagonalPacked(packDiagonal(a, b)))
                    canPrefixedPack(a, b) -> packPrefixed(a, b).let { Either.right(PrefixedPacked(it.first, it.second)) }
                    canRangeOffsetPack(a, b) -> packRangeOffset(a, b).let { Either.right(RangeOffsetPacked(it.first, it.second)) }
                    canRelativeIncrementPack(a, b) -> packRelativeIncrement(a, b).let { Either.right(RelativeIncrementPacked(it.first, it.second)) }
                    canPalettePack(a, b) -> packPalette(a, b).let { Either.right(PalettePacked(it.first, it.second)) }
                    canMultiClusterPack(a, b) -> packMultiCluster(a, b).let { Either.right(MultiClusterPacked(it.first, it.second)) }
                    else -> Either.left("No experimental packing strategy applicable")
                }
            }
        }
    }

// === REFACTORED PACKING USING CHORD SHEET ===

private fun <A, B> canDiagonalPack(a: A, b: B): Boolean {
    return diagonalPackingChord.b(a j b)()
}

private fun <A, B> canPrefixedPack(a: A, b: B): Boolean {
    return prefixedPackingChord.b(a j b)()
}

private fun <A, B> canRangeOffsetPack(a: A, b: B): Boolean {
    return rangeOffsetPackingChord.b(a j b)()
}

private fun <A, B> canRelativeIncrementPack(a: A, b: B): Boolean {
    return relativeIncrementPackingChord.b(a j b)()
}

private fun <A, B> canPalettePack(a: A, b: B): Boolean {
    return palettePackingChord.b(a j b)()
}

private fun <A, B> canMultiClusterPack(a: A, b: B): Boolean {
    return multiClusterPackingChord.b(a j b)()
}
