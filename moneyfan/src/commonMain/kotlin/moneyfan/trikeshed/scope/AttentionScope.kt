package moneyfan.trikeshed.scope

import moneyfan.trikeshed.Series
import moneyfan.trikeshed.j // For Series construction
import moneyfan.trikeshed.emptySeries
import moneyfan.trikeshed.toSeries
import kotlin.random.Random
// import kotlin.math.max // Not used
// import kotlin.math.min // Used
// import kotlin.math.roundToInt // Used

/**
 * Defines a scope or subset of a [Series] that an operation should focus on.
 * Attention scopes allow for selective processing of series data, enabling strategies
 * like focusing on specific time ranges, random samples, or data matching certain criteria.
 *
 * This is a sealed interface, meaning all direct implementations must be declared in this file.
 *
 * @param T The type of elements in the [Series] to which this scope can be applied.
 */
sealed interface AttentionScope<T> {
    /**
     * Applies this attention scope to a given `source` [Series].
     * This method returns a new [Series] containing only the elements from the `source`
     * that fall within the criteria defined by this scope. The relative order of elements
     * from the source series is generally preserved in the resulting series.
     *
     * @param source The original [Indexed<T>] to apply the scope to.
     * @return A new [Indexed<T>] representing the focused subset. If the scope results in
     *         no elements being selected, or if the source is empty, an empty series is returned.
     */
    fun apply(source: Indexed<T>): Indexed<T>

    /**
     * Calculates and returns a [Indexed<Int>] of sorted indices that this scope would select
     * from a source series of a given `sourceSize`.
     *
     * This method is useful for understanding which elements *would be* selected by the scope
     * without needing the actual data of the source series, or for applying the scope's
     * selection logic in different contexts. The returned indices are always sorted in ascending order.
     *
     * @param sourceSize The size of the conceptual source [Series] for which to determine scoped indices.
     * @return A [Indexed<Int>] of zero-based indices, sorted in ascending order.
     *         Returns an empty series if the scope selects no indices or if `sourceSize` is 0.
     */
    fun getScopedIndices(sourceSize: Int): Indexed<Int>
}

/**
 * An [AttentionScope] that defines a focus on a fixed range of indices within a [Series].
 * The range is defined by a `startIndex` (inclusive) and an `endIndexExclusive` (exclusive).
 *
 * @param T The type of elements in the [Series]. This type parameter is nominal and not directly used
 *          in the logic of `RangeScope` itself, but ensures type compatibility when used with `AttentionScope<T>`.
 * @property startIndex The starting index of the range (inclusive). Must be non-negative.
 * @property endIndexExclusive The ending index of the range (exclusive). Must be greater than or equal to `startIndex`.
 * @throws IllegalArgumentException if `startIndex` is negative or `endIndexExclusive < startIndex`.
 */
data class RangeScope<T>(val startIndex: Int, val endIndexExclusive: Int) : AttentionScope<T> {
    init {
        require(startIndex >= 0) { "startIndex must be non-negative, got $startIndex." }
        require(endIndexExclusive >= startIndex) { "endIndexExclusive ($endIndexExclusive) must be greater than or equal to startIndex ($startIndex)." }
    }

    /**
     * Returns a [Indexed<Int>] of indices within the defined range, adjusted for the `sourceSize`.
     * The indices are contiguous and sorted.
     * For example, `RangeScope(1, 4).getScopedIndices(5)` would produce `Series[1, 2, 3]`.
     */
    override fun getScopedIndices(sourceSize: Int): Indexed<Int> {
        if (sourceSize == 0 || startIndex >= sourceSize || startIndex >= endIndexExclusive) {
            return emptySeries()
        }
        // Determine the actual start and end of the range, clamped by sourceSize.
        val actualStart = kotlin.math.min(startIndex, sourceSize -1) // Ensure start is within bounds if source is smaller than startIndex
        val actualEndExclusive = kotlin.math.min(endIndexExclusive, sourceSize)

        val count = actualEndExclusive - actualStart
        if (count <= 0) {
            return emptySeries()
        }
        return count j { i:Int -> actualStart + i }
    }

    /**
     * Applies the range scope to the `source` [Series].
     * Returns a new [Series] containing elements from the `source` series
     * at indices from `startIndex` (inclusive) up to `endIndexExclusive` (exclusive),
     * respecting the bounds of the `source` series.
     */
    override fun apply(source: Indexed<T>): Indexed<T> {
        // getScopedIndices handles empty sourceSize correctly, so this check is belt-and-suspenders
        // but good for clarity if apply is called directly with an empty series.
        if (source.isEmpty()) {
            return emptySeries()
        }
        val scopedIndices = getScopedIndices(source.a)
        if (scopedIndices.isEmpty()){
            return emptySeries()
        }
        // Construct new series using the selected indices to pick elements from the source.
        return scopedIndices.a j { i:Int -> source.b(scopedIndices.b(i)) }
    }
}

/**
 * An [AttentionScope] that defines a focus on a randomly selected fractional subset of a [Series].
 * The selection aims to pick approximately `percentage * source.a` elements.
 *
 * @param T The type of elements in the [Series].
 * @property percentage The target fraction of elements to select, ranging from `0.0` (select none)
 *                      to `1.0` (select all).
 * @property seed An optional [Long] seed for the random number generator. Providing a seed ensures
 *                that the same subset of indices is selected for a given source size and percentage,
 *                allowing for reproducible random sampling. If `null`, a default [Random] instance is used.
 * @throws IllegalArgumentException if `percentage` is not between `0.0` and `1.0` (inclusive).
 */
data class FractionalScope<T>(val percentage: Double, val seed: Long? = null) : AttentionScope<T> {
    init {
        require(percentage >= 0.0 && percentage <= 1.0) {
            "percentage must be between 0.0 and 1.0 (inclusive), got $percentage."
        }
    }

    private fun getRandom(): Random = if (seed != null) Random(seed) else Random.Default

    /**
     * Returns a [Indexed<Int>] of randomly selected indices.
     * The number of indices is approximately `percentage * sourceSize`.
     * The selected indices are **sorted** to ensure that when [apply] is used,
     * the relative order of elements from the source series is maintained in the result.
     */
    override fun getScopedIndices(sourceSize: Int): Indexed<Int> {
        if (sourceSize == 0 || percentage == 0.0) {
            return emptySeries()
        }

        val targetSize = (sourceSize * percentage).roundToInt()
        if (targetSize == 0 && percentage > 0.0 && sourceSize > 0) {
            // If percentage is very small but non-zero for a small sourceSize,
            // targetSize might round to 0. Decide if we should select at least one if possible.
            // For now, if it rounds to 0, we return empty.
            return emptySeries()
        }
         if (targetSize == 0) return emptySeries()


        val allIndices = (0 until sourceSize).toList()
        val random = getRandom()

        // Shuffle all original indices and take the target number of them.
        val shuffledIndices = allIndices.shuffled(random)
        val selectedIndices = shuffledIndices.take(targetSize)

        // Crucially, sort the selected indices. This ensures that applying this scope
        // preserves the relative order of the chosen elements from the source series.
        return selectedIndices.sorted().toIndexed()
    }

    /**
     * Applies the fractional scope to the `source` [Series].
     * Returns a new [Series] containing a randomly selected subset of elements
     * from the `source`. The size of the subset is approximately `percentage * source.a`.
     * The relative order of the selected elements is preserved.
     */
    override fun apply(source: Indexed<T>): Indexed<T> {
        if (source.isEmpty() || percentage == 0.0) { // Check percentage here too for quick exit
            return emptySeries()
        }
        val scopedIndices = getScopedIndices(source.a)
        if (scopedIndices.isEmpty()){ // Handles cases where targetSize rounded to 0
            return emptySeries()
        }
        // Construct new series using the selected indices to pick elements from the source.
        return scopedIndices.a j { i:Int -> source.b(scopedIndices.b(i)) }
    }
}
