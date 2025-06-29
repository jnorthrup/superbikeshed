package borg.trikeshed.isam

import borg.trikeshed.cursor.ColumnMeta
import borg.trikeshed.cursor.Cursor
import borg.trikeshed.cursor.RowVec
import borg.trikeshed.lib.Join
import borg.trikeshed.io.Usable

interface IsamDataFile : Usable {
    val datafileFilename: String
    val metafile: IsamMetaFileReader
    
    fun open()
    fun close()

    companion object {
        fun write(cursor: Cursor, datafilename: String, varChars: Map<String, Int> = emptyMap())

        fun append(
            msf: Iterable<RowVec>,
            datafilename: String,
            varChars: Map<String, Int> = emptyMap(),
            transform: ((RowVec) -> RowVec)? = null,
        )
    }
} 