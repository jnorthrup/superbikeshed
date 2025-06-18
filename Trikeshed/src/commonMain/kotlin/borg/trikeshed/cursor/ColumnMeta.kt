package borg.trikeshed.cursor

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.Series2

typealias ColumnMeta = Join<String, TypeMemento>

//mix-in for name
val ColumnMeta.name: String get() = this.a

//mix-in for type
val ColumnMeta.type: TypeMemento get() = this.b

// Integrated Cursor and RowVec definitions
typealias RowVec = Series2<Any?, () -> ColumnMeta>
typealias Cursor = Series<RowVec>
