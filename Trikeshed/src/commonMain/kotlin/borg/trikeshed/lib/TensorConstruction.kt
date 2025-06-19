package borg.trikeshed.lib

import kotlin.math.min
import borg.trikeshed.lib.j // Explicitly import the j infix function from this package

/**
 * TensorSeries - Creates a 1D Tensor (equivalent to Series)
 */
inline fun <T> TensorSeries(size: Int, noinline accessor: (Int) -> T): Tensor<T> {
    require(size >= 0) { "Size must be non-negative" }
    return intArrayOf(size) borg.trikeshed.lib.j { coords -> accessor(coords[0]) }
}

/**
 * TensorCursor - Creates a 2D Tensor (equivalent to Cursor)
 */
inline fun <T> TensorCursor(rows: Int, cols: Int, noinline accessor: (Int, Int) -> T): Tensor<T> {
    require(rows >= 0 && cols >= 0) { "Rows and columns must be non-negative" }
    return intArrayOf(rows, cols) borg.trikeshed.lib.j { coords -> accessor(coords[0], coords[1]) }
}

/**
 * Creates a tensor from a 2D list.
 */
fun <T> List<List<T>>.toTensor(): Tensor<T> {
    if (isEmpty()) return TensorCursor(0, 0) { _, _ -> null as T } // Empty tensor

    val rows = size
    val cols = first().size

    // Ensure all rows have the same number of columns
    require(all { it.size == cols }) { "All rows in the list must have the same number of columns" }

    return TensorCursor(rows, cols) { row, col -> this[row][col] }
}

/**
 * Creates a 1D tensor from a list.
 */
fun <T> List<T>.toTensor1D(): Tensor<T> {
    val size = this.size
    return TensorSeries(size) { i -> this[i] }
}

/**
 * Creates a tensor filled with a single value.
 */
inline fun <T> fillTensor(shape: IntArray, value: T): Tensor<T> {
    return shape borg.trikeshed.lib.j { value }
}

/**
 * Creates a tensor with a specified shape and initializes elements using a generator function.
 */
inline fun <T> generateTensor(shape: IntArray, noinline generator: (IntArray) -> T): Tensor<T> {
    return shape borg.trikeshed.lib.j generator
}