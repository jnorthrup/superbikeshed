package borg.trikeshed.cursor

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Series

/**
 * RowVec - Single row as Series of value-meta pairs
 */
typealias RowVec = Series<Join<Any?, () -> ColumnMeta>>

/**
 * Cursor - Series of RowVec (functional composition)
 */
typealias Cursor = Series<RowVec>

/**
 * Cursor interface for platform-specific implementations
 */
interface Cursor {
    val a: Int
    val b: (Int) -> RowVec
} 