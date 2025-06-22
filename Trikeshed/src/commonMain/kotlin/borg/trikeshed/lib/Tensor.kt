package borg.trikeshed.lib

import borg.trikeshed.lib.j // Explicitly import the j infix function

// Tensor<T> is already defined in CoreTypes.kt as MetaSeries<Shape, T>
// This file provides additional operations for the Tensor type

inline val <T> Tensor<T>.shape: Shape get() = a
inline val <T> Tensor<T>.accessor: (Shape) -> T get() = b
inline val <T> Tensor<T>.rank: Int get() = shape.size
inline val <T> Tensor<T>.totalSize: Int get() = shape.play.fold(1) { acc, i -> acc * i }

inline operator fun <T> Tensor<T>.invoke(vararg coords: Int): T {
    require(coords.size == rank) { "Invalid number of coordinates for tensor of rank $rank" }
    coords.forEachIndexed { index, coord ->
        require(coord >= 0 && coord < shape[index]) { "Coordinate $coord at dimension $index out of bounds [0, ${shape[index]})" }
    }
    val coordsShape: Shape = coords.size j { i: Int -> coords[i] }
    return accessor(coordsShape)
}

// For 1D tensors (Series-like)
inline operator fun <T> Tensor<T>.invoke(index: Int): T {
    require(rank == 1) { "This tensor is not 1D (rank $rank)" }
    require(index >= 0 && index < shape[0]) { "Index $index out of bounds [0, ${shape[0]})" }
    val indexShape: Shape = 1 j { _: Int -> index }
    return accessor(indexShape)
}