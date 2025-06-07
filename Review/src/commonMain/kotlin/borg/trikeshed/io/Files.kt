package borg.trikeshed.io

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.FilePath
import borg.trikeshed.lib.DirectoryPath
import borg.trikeshed.lib.BufferSize
import borg.trikeshed.lib.FileOffset

/** not unlike nio.Files */
expect object Files {
    fun readAllLines(filePath: FilePath): List<String>
    fun readAllBytes(filePath: FilePath): ByteArray
    fun readString(filePath: FilePath): String
    fun write(filePath: FilePath, bytes: ByteArray)
    fun write(filePath: FilePath, lines: List<String>)
    fun write(filePath: FilePath, string: String)
    fun cwd(): DirectoryPath
    fun exists(filePath: FilePath): Boolean

    /** read offsets and lines accompanying*/
    fun streamLines(
        /**non-seekable RO file, as in a fifo  */
        filePath: FilePath,
        bufferSize: BufferSize = BufferSize(64),
    ): Sequence<Join<FileOffset, ByteArray>>

    /** read offsets and lines
     * checks if the bytes can be facade as chars or if utf8 conversion is needed (dirty)
     * @return triple ( len, bytes, dirty   )*/
    fun iterateLines(
        filePath: FilePath,
        bufferSize: BufferSize = BufferSize(12), //for testing
    ): Iterable<Join<FileOffset, Series<Byte>>>
}
