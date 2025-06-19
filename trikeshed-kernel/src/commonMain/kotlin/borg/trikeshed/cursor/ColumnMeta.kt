package borg.trikeshed.cursor
 
import borg.trikeshed.lib.*
 
typealias ColumnMeta = Join<String, TypeMemento>
typealias RowVec = Series2<Any?, () -> ColumnMeta>
typealias Cursor = Series<RowVec>

//mix-in for name
val ColumnMeta.name: String get() = this.a

//mix-in for type
val ColumnMeta.type: TypeMemento get() = this.b
 