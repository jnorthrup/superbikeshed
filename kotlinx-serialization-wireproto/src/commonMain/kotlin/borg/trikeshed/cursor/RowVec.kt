package borg.trikeshed.cursor

import borg.trikeshed.lib.Join

/**
 * DEPRECATED: Legacy row vector - superseded by trikeshed-cursor module
 * 
 * This implementation is now CANCELLED per cursor policy update.
 * Use borg.trikeshed.cursor.RowVec from trikeshed-cursor module instead.
 * 
 * @deprecated Use borg.trikeshed.cursor.RowVec from trikeshed-cursor module
 */
@Deprecated(
    "Use borg.trikeshed.cursor.RowVec from trikeshed-cursor module",
    ReplaceWith("borg.trikeshed.cursor.RowVec", "borg.trikeshed.cursor")
)
typealias RowVec = Join<Int, (Int) -> Join<Any?, () -> ColumnMeta>> 