package borg.trikeshed.cursor

import borg.trikeshed.lib.ColumnMeta
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.RowVec
import borg.trikeshed.lib.Series

/**
 * Cursor - Series of RowVec (functional composition)
 * Note: RowVec and ColumnMeta are already defined in CoreTypes.kt
 */
typealias Cursor = Series<RowVec>

/**
 * CursorInterface - Platform-specific cursor implementations
 * Renamed from Cursor to avoid naming conflict with the typealias
 */
interface CursorInterface {
    val size: Int
    operator fun get(index: Int): RowVec
}