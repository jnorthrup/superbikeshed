package borg.trikeshed.ts.kotlin.assimilation

import borg.trikeshed.lib.*

/**
 * TrikeShed TypeScript Implementation - Kotlin Port
 * Core data structures and operations
 */

/**
 * Join - The fundamental composition type (already exists in TrikeShed)
 * Using the existing Join<A, B> from borg.trikeshed.lib
 */



/**
 * Create a Series instance
 */
fun <T> createSeries(size: Int, accessor: (index: Int) -> T): Series<T> {
    return size j accessor
}

/**
 * Tensor - A multi-dimensional data structure
 */
typealias Tensor<T> = Join<Indexed<Int>, (coords: Indexed<Int>) -> T>

/**
 * Create a Tensor instance
 */
fun <T> createTensor(shape: Indexed<Int>, accessor: (coords: Indexed<Int>) -> T): Tensor<T> {
    return shape j accessor
}

/**
 * Cursor - A view into data structures
 */
typealias Cursor<T> = Join<Indexed<Int>, (coords: Indexed<Int>) -> T>

/**
 * Create a Cursor instance
 */
fun <T> createCursor(shape: Indexed<Int>, accessor: (coords: Indexed<Int>) -> T): Cursor<T> {
    return shape j accessor
}

/**
 * Alpha transform - Fundamental transformation
 */
fun <T, R> alpha(series: Series<T>, transform: (value: T) -> R): Series<R> {
    return createSeries(series.size) { i -> transform(series[i]) }
}

/**
 * Materialize a Series to a list
 */
fun <T> materialize(series: Series<T>): List<T> {
    return List(series.size) { i -> series[i] }
}

/**
 * Calculate total size of a shape
 */
fun calculateTotalSize(shape: Indexed<Int>): Int {
    var result = 1
    for (i in 0 until shape.size) {
        result *= shape[i]
    }
    return result
}

/**
 * Convert linear index to coordinates
 */
fun linearToCoords(index: Int, shape: Indexed<Int>): Indexed<Int> {
    val coords = Array(shape.size) { 0 }
    var remaining = index
    
    for (i in shape.size - 1 downTo 0) {
        coords[i] = remaining % shape[i]
        remaining /= shape[i]
    }
    
    return coords.size j { coords[it] }
}

/**
 * Convert coordinates to linear index
 */
fun coordsToLinear(coords: Indexed<Int>, shape: Indexed<Int>): Int {
    var index = 0
    var stride = 1
    
    for (i in coords.size - 1 downTo 0) {
        index += coords[i] * stride
        stride *= shape[i]
    }
    
    return index
}

/**
 * Materialize a Tensor to a list
 */
fun <T> materializeTensor(
    tensor: Tensor<T>,
    batchSize: Int = 1024
): List<T> {
    val totalSize = calculateTotalSize(tensor.a)
    val result = mutableListOf<T>()
    
    for (i in 0 until totalSize) {
        val coords = linearToCoords(i, tensor.a)
        result.add(tensor.b(coords))
    }
    
    return result
}

/**
 * Hot path materialization with batching
 */
fun <T, R> materializeHot(
    tensor: Tensor<T>,
    batchSize: Int = 1024,
    operation: (data: List<T>) -> R
): R {
    val materialized = materializeTensor(tensor)
    return operation(materialized)
} 