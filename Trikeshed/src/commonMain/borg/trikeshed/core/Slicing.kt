@file:Suppress("NOTHING_TO_INLINE")

package borg.trikeshed.core

import borg.trikeshed.lib.forEachIndexed

/**
 * Tensor slicing with varargs and range specifications
 * 
 * Inspired by TrikeShed's DelimitRange but extended for multi-dimensional tensors.
 * Provides numpy-style slicing with Kotlin elegance.
 */

/**
 * Range specification for tensor slicing
 */
sealed class TensorRangeSpec {
    /**
     * Full range (equivalent to :)
     */
    object All : TensorRangeSpec()
    
    /**
     * Single index
     */
    data class Index(val value: Int) : TensorRangeSpec()
    
    /**
     * Range with start/end/step
     */
    data class Range(val start: Int, val end: Int, val step: Int = 1) : TensorRangeSpec()
    
    /**
     * Mask-based selection
     */
    data class Mask(val indices: IntArray) : TensorRangeSpec()
    
    companion object
}

/**
 * Infix operators for elegant slicing syntax
 */
infix fun Int.until(end: Int): TensorRangeSpec = TensorRangeSpec.Range(this, end - 1)
infix fun Int.downTo(end: Int): TensorRangeSpec = TensorRangeSpec.Range(this, end, -1)
infix fun IntRange.step(step: Int): TensorRangeSpec = TensorRangeSpec.Range(first, last, step)

/**
 * Convenient range construction
 */
inline fun range(start: Int, end: Int, step: Int = 1): TensorRangeSpec =
    TensorRangeSpec.Range(start, end, step)

inline fun index(value: Int): TensorRangeSpec =
    TensorRangeSpec.Index(value)

inline val all: TensorRangeSpec get() = TensorRangeSpec.All

/**
 * Advanced slicing with TensorRangeSpec
 */
fun <T> Tensor<T>.sliceAdvanced(vararg specs: TensorRangeSpec): Tensor<T> {
    require(specs.size <= rank) { "Too many slice specifications: ${specs.size} > $rank" }
    
    // Pad with All specs if needed
    val paddingSpecs = Array(rank - specs.size) { TensorRangeSpec.All }
    val fullSpecs = specs.toList() + paddingSpecs.toList()
    
    // Calculate new shape and create mapping
    val newShape = mutableListOf<Int>()
    val indexMappers = mutableListOf<(Int) -> Int>()
    
    fullSpecs.forEachIndexed { dim, spec ->
        when (spec) {
            is TensorRangeSpec.All -> {
                newShape.add(shape[dim])
                indexMappers.add { it }
            }
            is TensorRangeSpec.Index -> {
                // Index specs collapse dimension - no new shape entry
                val index = normalizeIndex(spec.value, shape[dim])
                indexMappers.add { index }
            }
            is TensorRangeSpec.Range -> {
                val (start, end, step) = normalizeRange(spec.start, spec.end, spec.step, shape[dim])
                val size = maxOf(0, (end - start + step - 1) / step)
                newShape.add(size)
                indexMappers.add { i -> start + i * step }
            }
            is TensorRangeSpec.Mask -> {
                newShape.add(spec.indices.size)
                indexMappers.add { i -> spec.indices[i] }
            }
        }
    }
    
    return TensorConstruct(newShape.toIntArray()) { coords ->
        val originalCoords = IntArray(rank)
        var newDim = 0
        
        indexMappers.forEachIndexed { originalDim, mapper ->
            originalCoords[originalDim] = when (fullSpecs[originalDim]) {
                is TensorRangeSpec.Index -> mapper(0) // Index doesn't consume coordinate
                else -> {
                    val coord = if (newDim < coords.size) coords[newDim] else 0
                    newDim++
                    mapper(coord)
                }
            }
        }
        
        this(originalCoords)
    }
}

/**
 * Array-based slicing (numpy-style)
 */
operator fun <T> Tensor<T>.get(vararg specs: TensorRangeSpec): Tensor<T> = sliceAdvanced(*specs)

/**
 * Utility functions
 */
private fun normalizeIndex(index: Int, size: Int): Int =
    if (index < 0) size + index else index

private fun normalizeRange(start: Int, end: Int, step: Int, size: Int): Triple<Int, Int, Int> {
    val normalizedStart = if (start < 0) size + start else start
    val normalizedEnd = if (end < 0) size + end else end
    return Triple(normalizedStart, normalizedEnd, step)
}

/**
 * Slice tensor along specific dimensions
 */
fun <T> Tensor<T>.slice(vararg ranges: IntRange): Tensor<T> {
    require(ranges.size <= rank) { "Too many slice dimensions" }
    
    val newShape = IntArray(rank) { i ->
        if (i < ranges.size) {
            ranges[i].last - ranges[i].first + 1
        } else shape[i]
    }
    
    return TensorConstruct(newShape) { coords ->
        val originalCoords = IntArray(rank) { i ->
            if (i < ranges.size) {
                ranges[i].first + coords[i]
            } else coords[i]
        }
        this(originalCoords)
    }
}

/**
 * Select specific columns (2D tensors)
 */
fun <T> Tensor<T>.selectColumns(vararg columnIndices: Int): Tensor<T> {
    require(rank == 2) { "Column selection only for 2D tensors" }
    
    return TensorCursor(shape[0], columnIndices.size) { row, col ->
        this(row, columnIndices[col])
    }
}

/**
 * Select specific rows (2D tensors)
 */
fun <T> Tensor<T>.selectRows(vararg rowIndices: Int): Tensor<T> {
    require(rank == 2) { "Row selection only for 2D tensors" }
    
    return TensorCursor(rowIndices.size, shape[1]) { row, col ->
        this(rowIndices[row], col)
    }
}

/**
 * Take first N elements along dimension
 */
fun <T> Tensor<T>.take(n: Int, axis: Int = 0): Tensor<T> {
    require(axis < rank) { "Axis $axis out of bounds for rank $rank" }
    require(n <= shape[axis]) { "Cannot take $n elements from dimension of size ${shape[axis]}" }
    
    val newShape = shape.copyOf()
    newShape[axis] = n
    
    return TensorConstruct(newShape) { coords ->
        this(coords)
    }
}

/**
 * Drop first N elements along dimension
 */
fun <T> Tensor<T>.drop(n: Int, axis: Int = 0): Tensor<T> {
    require(axis < rank) { "Axis $axis out of bounds for rank $rank" }
    require(n < shape[axis]) { "Cannot drop $n elements from dimension of size ${shape[axis]}" }
    
    val newShape = shape.copyOf()
    newShape[axis] = shape[axis] - n
    
    return TensorConstruct(newShape) { coords ->
        val originalCoords = coords.copyOf()
        originalCoords[axis] += n
        this(originalCoords)
    }
}

/**
 * Transpose (2D)
 */
fun <T> Tensor<T>.transpose(): Tensor<T> {
    require(rank == 2) { "Transpose only defined for 2D tensors" }
    
    return TensorCursor(shape[1], shape[0]) { i, j ->
        this(j, i)
    }
}

/**
 * Reshape tensor (total size must match)
 */
fun <T> Tensor<T>.reshape(vararg newShape: Int): Tensor<T> {
    val newSize = newShape.fold(1, Int::times)
    require(newSize == totalSize) { 
        "Cannot reshape tensor of size $totalSize to ${newShape.contentToString()}" 
    }
    
    return TensorConstruct(newShape) { coords ->
        val linearIndex = coordsToLinear(coords, newShape)
        val originalCoords = this.linearToCoords(linearIndex)
        this(originalCoords)
    }
}

/**
 * Helper for reshape
 */
private fun coordsToLinear(coords: IntArray, shape: IntArray): Int {
    var result = 0
    var stride = 1
    
    var i = shape.size - 1
    while (i >= 0) {
        result += coords[i] * stride
        stride *= shape[i]
        i--
    }
    
    return result
}