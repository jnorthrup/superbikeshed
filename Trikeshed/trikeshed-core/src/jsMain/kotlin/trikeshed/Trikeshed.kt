package trikeshed

/**
 * TrikeShed Kotlin Implementation
 * Core data structures and operations
 */

/**
 * Join - The fundamental composition type
 */
data class Join<A, B>(
    val a: A,
    val b: B
)

/**
 * Create a Join instance
 */
fun <A, B> j(a: A, b: B): Join<A, B> = Join(a, b)

/**
 * Series - A sequence type
 */
typealias Series<T> = Join<Int, (Int) -> T>

/**
 * Create a Series instance
 */
fun <T> createSeries(size: Int, accessor: (Int) -> T): Series<T> = j(size, accessor)

/**
 * Tensor - A multi-dimensional data structure
 */
typealias Tensor<T> = Join<IntArray, (IntArray) -> T>

/**
 * Create a Tensor instance
 */
fun <T> createTensor(shape: IntArray, accessor: (IntArray) -> T): Tensor<T> = j(shape, accessor)

/**
 * Cursor - A view into data structures
 */
typealias Cursor<T> = Join<IntArray, (IntArray) -> T>

/**
 * Create a Cursor instance
 */
fun <T> createCursor(shape: IntArray, accessor: (IntArray) -> T): Cursor<T> = j(shape, accessor)

/**
 * Alpha transform - Fundamental transformation
 */
fun <T, R> alpha(series: Series<T>, transform: (T) -> R): Series<R> =
    createSeries(series.a) { transform(series.b(it)) }

/**
 * Materialize a Series to an array
 */
@Suppress("UNCHECKED_CAST")
fun <T> materialize(series: Series<T>): Array<T> {
    val result = arrayOfNulls<Any>(series.a)
    for (i in 0 until series.a) {
        result[i] = series.b(i)
    }
    return result as Array<T>
}

/**
 * Calculate total size of a shape
 */
fun calculateTotalSize(shape: IntArray): Int =
    shape.fold(1) { acc, dim -> acc * dim }

/**
 * Convert linear index to coordinates
 */
fun linearToCoords(index: Int, shape: IntArray): IntArray {
    val coords = IntArray(shape.size)
    var remaining = index
    
    for (i in shape.size - 1 downTo 0) {
        coords[i] = remaining % shape[i]
        remaining /= shape[i]
    }
    
    return coords
}

/**
 * Convert coordinates to linear index
 */
fun coordsToLinear(coords: IntArray, shape: IntArray): Int {
    var index = 0
    var stride = 1
    
    for (i in coords.size - 1 downTo 0) {
        index += coords[i] * stride
        stride *= shape[i]
    }
    
    return index
}

/**
 * Materialize a Tensor to a flat array
 */
@Suppress("UNCHECKED_CAST")
fun <T> materializeTensor(tensor: Tensor<T>): Array<T> {
    val totalSize = calculateTotalSize(tensor.a)
    val result = arrayOfNulls<Any>(totalSize)
    for (i in 0 until totalSize) {
        val coords = linearToCoords(i, tensor.a)
        result[i] = tensor.b(coords)
    }
    return result as Array<T>
}

/**
 * Hot path materialization with batching
 */
fun <T, R> materializeHot(
    tensor: Tensor<T>,
    batchSize: Int = 1024,
    operation: (Array<T>) -> R
): R {
    val materialized = materializeTensor(tensor)
    return operation(materialized)
} 