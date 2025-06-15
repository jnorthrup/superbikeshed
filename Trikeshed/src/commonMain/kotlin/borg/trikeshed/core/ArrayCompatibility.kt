@file:Suppress("NOTHING_TO_INLINE")

package borg.trikeshed.core

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
import kotlin.jvm.JvmInline

/**
 * Array Compatibility - TrikeShed/Tensor Bridge 
 * Dimension shedding for 50k+ column scale
 */

@JvmInline
value class ArraySize(val length: Int) {
    inline val size: Int get() = length
}

inline infix fun <T> ArraySize.j(noinline accessor: (Int) -> T): ArraySeries<T> = 
    ArraySeries(Join(this, accessor))

@JvmInline
value class ArraySeries<T>(val series: Join<ArraySize, (Int) -> T>) {
    inline val size: Int get() = series.a.size
    inline operator fun get(index: Int): T = series.b(index)
    inline infix fun <R> alpha(crossinline transform: (T) -> R): ArraySeries<R> = 
        ArraySeries(Join(series.a) { i -> transform(series.b(i)) })
}

@JvmInline
value class DimensionShedder<T>(val tensor: Tensor<T>) {
    fun shed1D(): SeriesView<T> = SeriesView(tensor)
    fun shed2D(): CursorView<T> = CursorView(tensor)
}

@JvmInline
value class SeriesView<T>(val tensor: Tensor<T>) {
    inline val size: Int get() = if (tensor.rank == 1) tensor.shape[0] else tensor.totalSize
    inline operator fun get(index: Int): T = 
        if (tensor.rank == 1) tensor.accessor(intArrayOf(index))
        else {
            val coords = IntArray(tensor.rank)
            var remaining = index
            var i = tensor.rank - 1
            while (i >= 0) {
                coords[i] = remaining % tensor.shape[i]
                remaining /= tensor.shape[i]
                i--
            }
            tensor.accessor(coords)
        }
}

@JvmInline  
value class CursorView<T>(val tensor: Tensor<T>) {
    inline val size: Int get() = if (tensor.rank >= 2) tensor.shape[0] else 1
    inline operator fun get(row: Int, col: Int): T = tensor.accessor(intArrayOf(row, col))
}

fun <T> Tensor<T>.shedDimensions(): DimensionShedder<T> = DimensionShedder(this)
fun <T> Array<T>.toArraySeries(): ArraySeries<T> = 
    ArraySeries(ArraySize(size) j { i: Int -> this[i] })