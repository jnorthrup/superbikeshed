package borg.trikeshed.lib

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

    val newShape = shape.filterIndexed { i, _ -> i != dimension }.toIntArray()

    return newShape j { newCoords ->
        val originalCoords = IntArray(rank)
        var newCoordIndex = 0
        for (i in 0 until rank) {
            if (i == dimension) {
                originalCoords[i] = index
            } else {
                originalCoords[i] = newCoords[newCoordIndex++]
            }
        }
        this(originalCoords)
    }
}

/**
 * Reshapes a tensor to a new shape.
 * The total number of elements must remain the same.
 *
 * @param newShape The new shape for the tensor.
 * @return A new tensor with the specified shape, sharing the same underlying data.
 */
fun <T> Tensor<T>.reshape(vararg newShape: Int): Tensor<T> {
    val newTotalSize = newShape.reduce { acc, i -> acc * i }
    require(newTotalSize == totalSize) { "New shape $newShape must have the same total number of elements as original ($totalSize)" }

    // This is a view, so the accessor needs to map new coordinates to old
    val originalShape = this.shape
    val originalAccessor = this.accessor

    return (newShape to { newCoords: IntArray ->
        // Calculate linear index from newCoords
        var linearIndex = 0
        var multiplier = 1
        for (i in newCoords.size - 1 downTo 0) {
            linearIndex += newCoords[i] * multiplier
            multiplier *= newShape[i]
        }

        // Map linear index back to originalCoords
        val oldCoords = IntArray(originalShape.size)
        var remainingIndex = linearIndex
        for (i in originalShape.size - 1 downTo 0) {
            oldCoords[i] = remainingIndex % originalShape[i]
            remainingIndex /= originalShape[i]
        }
        originalAccessor(oldCoords)
    }) as Tensor<T>
}

/**
 * Transposes a 2D tensor.
 *
 * @return A new tensor with rows and columns swapped.
 */
fun <T> Tensor<T>.transpose(): Tensor<T> {
    require(rank == 2) { "Transpose is only supported for 2D tensors" }
    val (rows, cols) = shape
    return TensorCursor(cols, rows) { c, r -> this(r, c) }
}