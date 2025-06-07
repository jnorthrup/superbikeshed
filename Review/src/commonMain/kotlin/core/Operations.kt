@file:Suppress("NOTHING_TO_INLINE")

package core

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j

/**
 * TrikeShed-style functional operations adapted for tensors
 * 
 * α-conversion - The fundamental transformation
 * (λx.M[x]) → (λy.M[y]) - lambda calculus α-conversion
 * Recycled from TrikeShed with zero-cost abstraction focus.
 */
inline infix fun <X, C> Tensor<X>.α(crossinline transform: (X) -> C): Tensor<C> = 
    shape j { coords -> transform(this.accessor(coords)) }

/**
 * TrikeShed's infinite property - bounds-safe access
 */
inline val <T> Tensor<T>.infinite: Tensor<T>
    get() = shape j { coords ->
        val safeCoords = IntArray(coords.size) { i ->
            when {
                coords[i] < 0 -> 0
                coords[i] >= shape[i] -> shape[i] - 1
                else -> coords[i]
            }
        }
        this(safeCoords)
    }

/**
 * TrikeShed's ▶ operator - materialization boundary
 */
operator fun <T> Tensor<T>.rangeTo(other: Unit): Array<T> = materialize()

/**
 * Materialize tensor to Array (explicit performance boundary)
 */
fun <T> Tensor<T>.materialize(): Array<T> {
    @Suppress("UNCHECKED_CAST")
    val result = arrayOfNulls<Any?>(totalSize) as Array<T>
    var i = 0
    while (i < totalSize) {
        val coords = linearToCoords(i)
        result[i] = this(coords)
        i++
    }
    return result
}

/**
 * Hot path materialization with aligned memory
 */
inline fun <T, R> Tensor<T>.materializeHot(
    batchSize: Int = 1024,
    crossinline operation: (Array<T>) -> R
): R {
    // For now, simple materialization - future: SIMD-aligned allocation
    val materialized = materialize()
    return operation(materialized)
}

/**
 * Pairwise operations - Core of line-centric construction
 */

/**
 * zip - Fundamental pairwise combination
 * Combines two tensors element-wise with broadcasting
 */
fun <A, B> Tensor<A>.zip(other: Tensor<B>): Tensor<Join<A, B>> {
    val resultShape = broadcastShapes(this.shape, other.shape)
    return resultShape j { coords ->
        val thisCoords = coords.broadcastTo(this.shape)
        val otherCoords = coords.broadcastTo(other.shape)
        this(thisCoords) j other(otherCoords)
    }
}

/**
 * combine - Pairwise reduction
 */
inline fun <A, B, C> Tensor<A>.combine(
    other: Tensor<B>,
    crossinline operation: (A, B) -> C
): Tensor<C> = zip(other) α { (a, b) -> operation(a, b) }

/**
 * Broadcasting support
 */
fun broadcastShapes(shape1: IntArray, shape2: IntArray): IntArray {
    val maxRank = maxOf(shape1.size, shape2.size)
    val result = IntArray(maxRank)
    
    var i = 0
    while (i < maxRank) {
        val dim1 = if (i < shape1.size) shape1[shape1.size - 1 - i] else 1
        val dim2 = if (i < shape2.size) shape2[shape2.size - 1 - i] else 1
        
        result[maxRank - 1 - i] = when {
            dim1 == dim2 -> dim1
            dim1 == 1 -> dim2
            dim2 == 1 -> dim1
            else -> error("Cannot broadcast shapes: ${shape1.contentToString()} and ${shape2.contentToString()}")
        }
        i++
    }
    
    return result
}

fun IntArray.broadcastTo(targetShape: IntArray): IntArray {
    val result = IntArray(targetShape.size)
    val offset = targetShape.size - this.size
    
    for (i in result.indices) {
        result[i] = if (i < offset) 0 else {
            val sourceIndex = i - offset
            if (sourceIndex < this.size && this[sourceIndex] < targetShape[i]) {
                this[sourceIndex]
            } else 0
        }
    }
    
    return result
}

/**
 * Reduction operations
 */
inline fun <T, R> Tensor<T>.fold(initial: R, crossinline operation: (R, T) -> R): R {
    var result = initial
    var i = 0
    while (i < totalSize) {
        val coords = linearToCoords(i)
        result = operation(result, this(coords))
        i++
    }
    return result
}

/**
 * Linear indexing support (for when stdlib iterator needed)
 */
fun <T> Tensor<T>.linearToCoords(linearIndex: Int): IntArray {
    val coords = IntArray(rank)
    var remaining = linearIndex
    
    var i = rank - 1
    while (i >= 0) {
        coords[i] = remaining % shape[i]
        remaining /= shape[i]
        i--
    }
    
    return coords
}

fun <T> Tensor<T>.coordsToLinear(coords: IntArray): Int {
    var result = 0
    var stride = 1
    
    var i = rank - 1
    while (i >= 0) {
        result += coords[i] * stride
        stride *= shape[i]
        i--
    }
    
    return result
}