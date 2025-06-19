package borg.trikeshed.lib

import borg.trikeshed.lib.j // Explicitly import the j infix function

typealias Tensor<T> = Join<IntArray, (IntArray) -> T>

inline val <T> Tensor<T>.shape: IntArray get() = a
inline val <T> Tensor<T>.accessor: (IntArray) -> T get() = b
inline val <T> Tensor<T>.rank: Int get() = shape.size
inline val <T> Tensor<T>.totalSize: Int get() = shape.reduce { acc, i -> acc * i }

inline operator fun <T> Tensor<T>.invoke(vararg coords: Int): T {
    require(coords.size == rank) { "Invalid number of coordinates for tensor of rank $rank" }
    coords.forEachIndexed { index, coord ->
        require(coord >= 0 && coord < shape[index]) { "Coordinate $coord at dimension $index out of bounds [0, ${shape[index]})" }
    }
    return accessor(coords)
}

// For 1D tensors (Series-like)
inline operator fun <T> Tensor<T>.invoke(index: Int): T {
    require(rank == 1) { "This tensor is not 1D (rank $rank)" }
    require(index >= 0 && index < shape[0]) { "Index $index out of bounds [0, ${shape[0]})" }
    return accessor(intArrayOf(index))
}