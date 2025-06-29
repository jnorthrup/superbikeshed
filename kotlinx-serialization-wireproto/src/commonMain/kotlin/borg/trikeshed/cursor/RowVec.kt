package borg.trikeshed.cursor

import borg.trikeshed.lib.Join

/**
 * Represents a row of data with indexed access to column values
 */
typealias RowVec = Join<Int, (Int) -> Join<Any?, () -> ColumnMeta>> 