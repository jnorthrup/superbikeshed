package borg.trikeshed.isam

import borg.trikeshed.lib.ColumnMeta
import borg.trikeshed.lib.Cursor
import borg.trikeshed.lib.RowVec
import borg.trikeshed.lib.Usable

/**
 * ISAM data file interface
 */
expect interface IsamDataFile : Usable {
    val datafileFilename: String
    val datafilePath: String
    val datafileSize: Long
    val datafileRecordCount: Long
    val datafileRecordSize: Int
    val datafileColumnCount: Int
    val datafileColumns: List<ColumnMeta>
    
    override fun open()
    override fun close()

    fun write(cursor: Cursor): Unit
    fun append(row: RowVec): Unit
    fun read(): RowVec
} 