package borg.trikeshed.cursor

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.TypeMemento

/**
 * DEPRECATED: Legacy column metadata - superseded by trikeshed-cursor module
 * 
 * This implementation is now CANCELLED per cursor policy update.
 * Use borg.trikeshed.cursor.ColumnMeta from trikeshed-cursor module instead.
 * 
 * @deprecated Use borg.trikeshed.cursor.ColumnMeta from trikeshed-cursor module
 */
@Deprecated(
    "Use borg.trikeshed.cursor.ColumnMeta from trikeshed-cursor module",
    ReplaceWith("borg.trikeshed.cursor.ColumnMeta", "borg.trikeshed.cursor")
)
interface ColumnMeta {
    val name: String
    val type: TypeMemento
}

/**
 * DEPRECATED: Legacy type memento - superseded by trikeshed-cursor module
 * 
 * @deprecated Use borg.trikeshed.cursor.IOMemento from trikeshed-cursor module
 */
@Deprecated(
    "Use borg.trikeshed.cursor.IOMemento from trikeshed-cursor module",
    ReplaceWith("borg.trikeshed.cursor.IOMemento", "borg.trikeshed.cursor")
)
interface TypeMemento {
    val networkSize: Int?
} 