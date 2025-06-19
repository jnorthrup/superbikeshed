package borg.trikeshed.cursor

import borg.trikeshed.isam.meta.IOMemento
import borg.trikeshed.isam.meta.*
import kotlin.jvm.JvmInline

/**
 * Column metadata for cursor operations.
 * Provides information about a column in a cursor/dataset.
 */
@JvmInline
value class ColumnMeta(val memento: IOMemento) {
    val name: String get() = memento.name ?: "unnamed"
    val type: String get() = memento.type ?: "unknown"
    val size: Int get() = memento.width ?: 0
    val nullable: Boolean get() = memento.nullable ?: true
    
    companion object {
        fun create(name: String, type: String, size: Int = 0, nullable: Boolean = true): ColumnMeta {
            return ColumnMeta(IOMemento().apply {
                this.name = name
                this.type = type
                this.width = size
                this.nullable = nullable
            })
        }
    }
}