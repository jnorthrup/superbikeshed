package borg.trikeshed.cursor

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.TypeMemento

/**
 * Represents metadata for a column
 */
interface ColumnMeta {
    val name: String
    val type: TypeMemento
}

/**
 * Type memento for storing type information
 */
interface TypeMemento {
    val networkSize: Int?
} 