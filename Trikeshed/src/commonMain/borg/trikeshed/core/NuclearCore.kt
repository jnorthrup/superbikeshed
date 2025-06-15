package borg.trikeshed.core

import borg.trikeshed.lib.Join
import borg.trikeshed.cursor.ColumnMeta
import borg.trikeshed.lib.j

// From TrikeShedTensorOperations.kt
typealias Tensor<T> = Join<IntArray, (IntArray) -> T>
typealias TensorRange = Join<Int, Int>

// From TensorFirstCursor.kt
typealias CoreTensorCursor<T> = Tensor<T>
typealias CoreTensorRowVec<T> = Tensor<T>
typealias CoreTensorColumnVec<T> = Tensor<T>
typealias CursorMeta = Tensor<ColumnMeta>
typealias CoreTensorCursorWithMeta<T> = Join<CoreTensorCursor<T>, CursorMeta>

// ============================================================================
// TENSOR CORE ACCESSORS & PROPERTIES (Non-Conflicting)
// ============================================================================

inline val <T> Tensor<T>.tensorShape: IntArray get() = a
inline val <T> Tensor<T>.tensorAccessor: (IntArray) -> T get() = b
inline val <T> Tensor<T>.tensorRank: Int get() = tensorShape.size
inline val <T> Tensor<T>.tensorTotalSize: Int get() = tensorShape.fold(1, Int::times)

// Compatibility aliases
inline val <T> Tensor<T>.shape: IntArray get() = tensorShape
inline val <T> Tensor<T>.accessor: (IntArray) -> T get() = tensorAccessor
inline val <T> Tensor<T>.rank: Int get() = tensorRank
inline val <T> Tensor<T>.totalSize: Int get() = tensorTotalSize

// ============================================================================
// TENSOR CONSTRUCTION (Working Implementation)
// ============================================================================

inline fun <T> TensorConstruct(shape: IntArray, noinline accessor: (IntArray) -> T): Tensor<T> =
    shape j accessor

// 1D tensor construction
inline fun <T> TensorSeries(size: Int, noinline accessor: (Int) -> T): Tensor<T> =
    intArrayOf(size) j { coords -> accessor(coords[0]) }

// 2D tensor construction
inline fun <T> TensorCursor(rows: Int, cols: Int, noinline accessor: (Int, Int) -> T): Tensor<T> =
    intArrayOf(rows, cols) j { coords -> accessor(coords[0], coords[1]) }

// ============================================================================
// ELEMENT ACCESS OPERATORS (Non-Conflicting)
// ============================================================================

inline operator fun <T> Tensor<T>.invoke(coords: IntArray): T = tensorAccessor(coords)
inline operator fun <T> Tensor<T>.invoke(vararg coords: Int): T = tensorAccessor(coords)

inline operator fun <T> Tensor<T>.invoke(index: Int): T {
    require(tensorRank == 1) { "Single index access requires rank 1, got $tensorRank" }
    return tensorAccessor(intArrayOf(index))
}

inline operator fun <T> Tensor<T>.invoke(row: Int, col: Int): T {
    require(tensorRank == 2) { "Two index access requires rank 2, got $tensorRank" }
    return tensorAccessor(intArrayOf(row, col))
}

// ============================================================================
// CURSOR ACCESSORS (Tensor-Compatible)
// ============================================================================

// Cursor dimensions
inline val <T> CoreTensorCursor<T>.rows: Int get() = shape[0]
inline val <T> CoreTensorCursor<T>.cols: Int get() = shape[1]

// Row/column access
inline fun <T> CoreTensorCursor<T>.row(index: Int): CoreTensorRowVec<T> {
    require(index >= 0 && index < rows) { "Row index $index out of bounds [0, $rows)" }
    return TensorSeries(cols) { col -> this(index, col) }
}

inline fun <T> CoreTensorCursor<T>.col(index: Int): CoreTensorColumnVec<T> {
    require(index >= 0 && index < cols) { "Column index $index out of bounds [0, $cols)" }
    return TensorSeries(rows) { row -> this(row, index) }
}

// Element access (inherited from tensor invoke operator)
// Add specific get operator for cursors for clarity if desired,
// though invoke(row, col) already covers this.
// For strictness and minimality, we can rely on the existing invoke operator.
// However, if direct `get` is a strong convention, we can add it:
// inline operator fun <T> CoreTensorCursor<T>.get(row: Int, col: Int): T = this(row, col)
// For now, let's stick to minimality and omit the explicit get unless requested.
