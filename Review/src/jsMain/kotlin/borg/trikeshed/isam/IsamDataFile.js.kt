package borg.trikeshed.isam

import borg.trikeshed.cursor.ColumnMeta
import borg.trikeshed.cursor.Cursor
import borg.trikeshed.cursor.RowVec
import borg.trikeshed.io.Usable
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.FilePath
import borg.trikeshed.lib.ColumnName
import borg.trikeshed.lib.CharLength

actual class IsamDataFile actual constructor(
    actual val datafileFilePath: FilePath, // Changed
    metafileFilePath: FilePath, // Changed, but not stored as direct property in expect
    actual val metafile: IsamMetaFileReader
) : Usable, Cursor {
    actual override val a: Int
        get() = TODO("JS: Not yet implemented - size of ISAM data")
    actual override val b: (Int) -> Join<Int, (Int) -> Join<Any?, () -> ColumnMeta>>
        get() = { rowIndex -> TODO("JS: Not yet implemented - row accessor for index $rowIndex") }

    // datafileFilename from expect is now datafileFilePath
    // metafile is in constructor

    actual override fun open() {
        TODO("JS: Not yet implemented - open ISAM data file ${datafileFilePath.path}")
    }

    actual override fun close() {
        TODO("JS: Not yet implemented - close ISAM data file ${datafileFilePath.path}")
    }

    actual companion object {
        actual fun write(
            cursor: Cursor,
            datafilePath: FilePath, // Changed
            varChars: Map<ColumnName, CharLength> // Changed
        ) {
            TODO("JS: Not yet implemented - write to ${datafilePath.path}, varChars count: ${varChars.size}")
        }

        actual fun append(
            msf: Iterable<RowVec>,
            datafilePath: FilePath, // Changed
            varChars: Map<ColumnName, CharLength>, // Changed
            transform: ((RowVec) -> RowVec)?
        ) {
            TODO("JS: Not yet implemented - append to ${datafilePath.path}, varChars count: ${varChars.size}")
        }
    }
}