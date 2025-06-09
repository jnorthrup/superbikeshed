package borg.trikeshed.isam

// Common imports from borg.trikeshed.lib that are likely needed
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
import borg.trikeshed.lib.α
import borg.trikeshed.lib.FilePath
import borg.trikeshed.lib.ColumnName
import borg.trikeshed.lib.CharLength
// Specific cursor and ISAM types
import borg.trikeshed.cursor.RowVec
import borg.trikeshed.isam.meta.IsamMetaFileReader // Assuming this is refactored or compatible
import borg.trikeshed.isam.meta.RecordMeta // Assuming this is refactored or compatible
import borg.trikeshed.isam.meta.WireProto
// Posix specific file operations
import simple.PosixFile // Assuming this API expects String for paths
import simple.PosixOpenOpts
// Other imports from the original file
import borg.trikeshed.common.collections.s_ // If still used
import borg.trikeshed.isam.meta.IOMemento // If still used by RecordMeta/sanitize
import kotlinx.cinterop.* // If any cinterop is directly used here
import platform.posix.* // If any direct posix calls are made here

// Note: The constructor and overall class structure of this Posix actual class
// differ significantly from the common expect class IsamDataFile.
// It does not implement Usable or Cursor directly.
// Refactoring will apply to its existing structure, primarily focusing on types in companion object methods.

actual class IsamDataFile actual constructor(
    actual val datafilePath: FilePath, // Changed from datafilename: String
    actual val metafilePath: FilePath  // Changed from metafilename: String
    // The common expect class also takes an IsamMetaFileReader instance, which is missing here.
    // This discrepancy means this actual class cannot fully satisfy the common expect as-is.
    // For this refactoring, we only change the types of existing parameters.
) {
    actual companion object {
        // The common expect's write method has (cursor: Cursor, ...). This has (msf: Iterable<RowVec>, ...)
        // This is a signature mismatch beyond just type aliases.
        // We will refactor the existing parameters.
        actual fun write(
            msf: Iterable<RowVec>,
            datafilePath: FilePath, // Changed
            varChars: Map<ColumnName, CharLength>, // Changed
            transform: ((RowVec) -> RowVec)?
        ): Unit {
            val metafileFp = FilePath("${datafilePath.path}.meta")

            val cursor = msf.iterator()
            if (!cursor.hasNext()) return

            val firstRow = cursor.next()
            val transformedFirstRow = transform?.let { it(firstRow) } ?: firstRow

            // Assuming IsamMetaFileReader.sanitize and .write are updated or compatible
            // with Map<ColumnName, CharLength> and FilePath.
            // For sanitize, varChars needs to be converted if it expects Map<String, Int>
            val varCharsStringInt = varChars.mapKeys { it.key.name }.mapValues { it.value.count }
            val meta = IsamMetaFileReader.sanitize(transformedFirstRow.right.α { it() }, varCharsStringInt)
            val rowLen = meta.last().end // Assuming RecordMeta.end gives an Int
            val rowBuffer1 = ByteArray(rowLen)

            WireProto.writeToBuffer(transformedFirstRow, rowBuffer1, meta)

            IsamMetaFileReader.write(metafileFp.path, meta, varCharsStringInt) // Pass String path

            val data = simple.PosixFile(
                datafilePath.path, // Use .path
                PosixOpenOpts.withFlags(PosixOpenOpts.O_Creat, PosixOpenOpts.O_Trunc, PosixOpenOpts.O_Rdwr)
            )

            data.write(rowBuffer1)

            for (rowVec1 in cursor) {
                val rowVec = transform?.let { it(rowVec1) } ?: rowVec1
                WireProto.writeToBuffer(rowVec, rowBuffer1, meta)
                data.write(rowBuffer1)
            }
            data.close()
        }

        actual fun append(
            msf: Iterable<RowVec>,
            datafilePath: FilePath, // Changed
            varChars: Map<ColumnName, CharLength>, // Changed
            transform: ((RowVec) -> RowVec)?
        ): Unit {
            val metafileFp = FilePath("${datafilePath.path}.meta")

            // IsamMetaFileReader constructor expects FilePath
            val metaReader = IsamMetaFileReader(metafileFilePath = metafileFp)
            metaReader.open()
            val existingMeta = metaReader.constraints
            metaReader.close()

            val hasExistingFile = simple.PosixFile.exists(datafilePath.path) // Use .path

            var actualMeta: Series<RecordMeta> = existingMeta
            var firstRowProcessed = false
            var rowLen = 0
            lateinit var rowBuffer: ByteArray

            val dataFile = simple.PosixFile(
                datafilePath.path, // Use .path
                PosixOpenOpts.withFlags(PosixOpenOpts.O_Rdwr, PosixOpenOpts.O_Append, PosixOpenOpts.O_Creat)
            )
            try {
                msf.forEach { rowVec1: RowVec ->
                    val rowVec = transform?.let { it(rowVec1) } ?: rowVec1
                    val varCharsStringInt = varChars.mapKeys { it.key.name }.mapValues { it.value.count }

                    if (!firstRowProcessed) {
                        if (!hasExistingFile || actualMeta.isEmpty()) {
                            actualMeta = IsamMetaFileReader.sanitize(rowVec.right.α { it() }, varCharsStringInt)
                            if (!hasExistingFile || existingMeta.isEmpty()) {
                                IsamMetaFileReader.write(metafileFp.path, actualMeta, varCharsStringInt) // Pass String path
                            }
                        }
                        rowLen = actualMeta.last().end
                        rowBuffer = ByteArray(rowLen)
                        firstRowProcessed = true
                    }

                    if (rowLen != actualMeta.last().end) {
                        throw IllegalStateException("Record length changed during append, not supported for now.")
                    }

                    WireProto.writeToBuffer(rowVec, rowBuffer, actualMeta)
                    dataFile.write(rowBuffer)
                }
            } finally {
                dataFile.close()
            }
        }
    }
}
