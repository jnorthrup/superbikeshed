package borg.trikeshed.lib

import borg.trikeshed.lib.j

/**
 * Slices a tensor along a specified dimension.
 *
 * @param dimension The dimension to slice along (0-indexed).
 * @param index The index within the dimension to slice.
 * @return A new tensor with rank reduced by 1.
 */
fun <T> Tensor<T>.slice(dimension: Int, index: Int): Tensor<T> {
    require(dimension >= 0 && dimension < rank) { "Dimension $dimension out of bounds for rank $rank" }
    require(index >= 0 && index < shape[dimension]) { "Index $index out of bounds for dimension $dimension (size ${shape[dimension]})" }

    // Create new shape by removing the specified dimension using TrikeShed patterns
    val newRank = rank - 1
    val newShape: Shape = newRank j { i ->
        if (i < dimension) {
            shape[i]
        } else {
            shape[i + 1]
        }
    }

    return newShape j { newCoords ->
        // Map new coordinates to original coordinates
        val originalCoords: Shape = rank j { i ->
            if (i < dimension) {
                newCoords[i]
            } else if (i == dimension) {
                index
            } else {
                newCoords[i - 1]
            }
        }
        this.accessor(originalCoords)
    }
}

/**
 * Reshapes a tensor to a new shape.
 * The total number of elements must remain the same.
 *
 * @param newShapeValues The new shape dimensions for the tensor.
 * @return A new tensor with the specified shape, sharing the same underlying data.
 */
fun <T> Tensor<T>.reshape(vararg newShapeValues: Int): Tensor<T> {
    val newTotalSize = newShapeValues.reduce(Int::times)
    require(newTotalSize == totalSize) { "New shape ${newShapeValues.toList()} must have the same total number of elements as original ($totalSize)" }

    // Create new shape
    val newShape: Shape = newShapeValues.size j { i -> newShapeValues[i] }
    
    // This is a view, so the accessor needs to map new coordinates to old
    val originalShape = this.shape
    val originalAccessor = this.accessor

    return newShape j { newCoords ->
        // Calculate linear index from newCoords
        var linearIndex = 0
        var multiplier = 1
        for (i in newCoords.size - 1 downTo 0) {
            linearIndex += newCoords[i] * multiplier
            multiplier *= newShapeValues[i]
        }

        // Map linear index back to originalCoords
        val oldCoords: Shape = originalShape.size j { i ->
            val remainingIndex = linearIndex
            var result = remainingIndex
            for (j in originalShape.size - 1 downTo i + 1) {
                result /= originalShape[j]
            }
            result % originalShape[i]
        }
        originalAccessor(oldCoords)
    }
}

/**
 * Transposes a 2D tensor.
 *
 * @return A new tensor with rows and columns swapped.
 */
fun <T> Tensor<T>.transpose(): Tensor<T> {
    require(rank == 2) { "Transpose is only supported for 2D tensors" }
    val rows = shape[0]
    val cols = shape[1]
    return TensorCursor(cols, rows) { c, r -> this.accessor(2 j { i -> if (i == 0) r else c }) }
}