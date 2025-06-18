package borg.trikeshed.isam

import borg.trikeshed.cursor.ColumnMeta
import borg.trikeshed.cursor.TypeMemento
import borg.trikeshed.isam.meta.IOMemento
import borg.trikeshed.lib.j
import borg.trikeshed.lib.Join

data class RecordMeta(
    override val name: String,
    override val type: IOMemento,
    val begin: Int = -1,
    val end: Int = -1,
    val decoder: (ByteArray) -> Any? = type.createDecoder(end - begin),
    val encoder: (Any?) -> ByteArray = type.createEncoder(end - begin),
    var child: RecordMeta? = null,
    val attributes: Map<String, String> = emptyMap()
) : ColumnMeta {
    override val a: String get() = name
    override val b: TypeMemento get() = type

    override fun toString(): String = "RecordMeta(name='$name', type=$type, begin=$begin, end=$end, attributes=$attributes, child=$child )"
}
