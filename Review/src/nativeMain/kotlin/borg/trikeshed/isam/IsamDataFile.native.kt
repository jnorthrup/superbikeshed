package borg.trikeshed.isam

import borg.trikeshed.cursor.ColumnMeta
import borg.trikeshed.cursor.Cursor
import borg.trikeshed.cursor.RowVec
import borg.trikeshed.io.Usable
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.emptySeries
import borg.trikeshed.lib.j
import borg.trikeshed.lib.FilePath
import borg.trikeshed.lib.ColumnName
import borg.trikeshed.lib.CharLength

actual class IsamDataFile actual constructor(
    actual val datafileFilePath: FilePath, // Changed from datafileFilename: String
    metafileFilePath: FilePath, // Used in constructor, but not stored as a direct property in expect
    actual val metafile: IsamMetaFileReader,
) : Usable, Cursor {
    // Placeholder properties
    actual override val a: Int = 0 // size
    actual override val b: (Int) -> Join<Int, (Int) -> Join<Any?, () -> ColumnMeta>> =
        { _ -> 0 j { _ -> "".to("").to(::ColumnMeta) } } // Placeholder accessor, ColumnMeta part is complex for a simple placeholder

    // datafileFilename property from expect is now datafileFilePath
    // metafile property is already in constructor

    actual override fun open() {
        println("IsamDataFile.open() called for ${datafileFilePath.path} (Native placeholder)")
        // No actual file opening for placeholder
    }

    actual override fun close() {
        println("IsamDataFile.close() called for ${datafileFilePath.path} (Native placeholder)")
        // No actual file closing for placeholder
    }

    actual companion object {
        actual fun write(
            cursor: Cursor,
            datafilePath: FilePath, // Changed from datafilename: String
            varChars: Map<ColumnName, CharLength>, // Changed Map value type
        ) {
            println("IsamDataFile.write() called for ${datafilePath.path} (Native placeholder). VarChars count: ${varChars.size}")
        }

        actual fun append(
            msf: Iterable<RowVec>,
            datafilePath: FilePath, // Changed from datafilename: String
            varChars: Map<ColumnName, CharLength>, // Changed Map value type
            transform: ((RowVec) -> RowVec)?,
        ) {
            println("IsamDataFile.append() called for ${datafilePath.path} (Native placeholder). VarChars count: ${varChars.size}")
        }
    }
}
