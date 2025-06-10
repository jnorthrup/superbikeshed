package borg.trikeshed.isam

import borg.trikeshed.cursor.ColumnMeta
import borg.trikeshed.cursor.Cursor
import borg.trikeshed.cursor.RowVec
import borg.trikeshed.lib.Join
import borg.trikeshed.io.Usable
import borg.trikeshed.lib.FilePath
import borg.trikeshed.lib.ColumnName
import borg.trikeshed.lib.CharLength

expect class IsamDataFile(
    datafileFilePath: FilePath,
    metafileFilePath: FilePath = FilePath("${datafileFilePath.path}.meta"),
    metafile: IsamMetaFileReader = IsamMetaFileReader(metafileFilePath),
) : Usable, Cursor {
    override val a: Int
    override val b: (Int) -> Join<Int, (Int) -> Join<Any?, () -> ColumnMeta>>
    val datafileFilePath: FilePath
    val metafile: IsamMetaFileReader

    override fun open()
    override fun close()

    companion object {
        fun write(cursor: Cursor, datafilePath: FilePath, varChars: Map<ColumnName, CharLength> = emptyMap())

           fun append(
               msf: Iterable<RowVec>,
               datafilePath: FilePath,
               varChars: Map<ColumnName, CharLength> = emptyMap(),
               transform: ((RowVec) -> RowVec)? = null,
           )
    }
}
