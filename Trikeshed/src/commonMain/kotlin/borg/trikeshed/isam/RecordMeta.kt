package borg.trikeshed.isam

import borg.trikeshed.isam.meta.IOMemento

/**
 * RecordMeta is a data class that describes a column of an Isam record
 *
 * @param name the name of the column
 * @param type the type of the column
 * @param begin the byte offset of the beginning of the column
 * @param end the byte offset of the end of the column
 * @param decoder a lambda that converts a byte[] to downstream, often but not necessarily the IoMemento utility
 * @param encoder a lambda that produces a byte[] for marshalling to disk or elsewhere
 * @param child a child RecordMeta for a child record, for instance, CSV conversion to ISAM might define two RecordMetas for two steps
 */
class RecordMeta(
    val name: String,
    /** enum-resident Type describing byte marshalling strategies - a specialization of TypeMemento */
    val type: IOMemento,
    /** context-specific byte offset beginning*/
    val begin: Int = -1,
    /** context-specific byte offset ending*/
    val end: Int = -1,
    /** a lambda that converts a byte[] to downstream, often but not necessarily the IoMemento utility */
    val decoder: (ByteArray) -> Any? = { byteArray -> byteArray }, // Placeholder decoder
    /** a lambda that produces a byte[] for marshalling to disk or elsewhere */
    val encoder: (Any?) -> ByteArray = { value -> value.toString().encodeToByteArray() }, // Placeholder encoder
    /** open to interpretation, for instance, CSV conversion to ISAM might define two RecordMetas for two steps*/
    var child: RecordMeta? = null,
) {
    override fun toString(): String = "RecordMeta(name='$name', type=$type, begin=$begin, end=$end, decoder=$decoder, encoder=$encoder, child=$child)"
} 