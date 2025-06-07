package borg.trikeshed.io

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.toSeries
import borg.trikeshed.lib.FilePath
import borg.trikeshed.lib.DirectoryPath
import borg.trikeshed.lib.BufferSize
import borg.trikeshed.lib.FileOffset
import java.nio.file.Paths
import java.nio.file.Files as JFiles

actual object Files {
    actual fun readAllLines(filePath: FilePath): List<String> =
        JFiles.readAllLines(Paths.get(filePath.path))

    actual fun readAllBytes(filePath: FilePath): ByteArray =
        JFiles.readAllBytes(Paths.get(filePath.path))

    actual fun readString(filePath: FilePath): String =
        JFiles.readString(Paths.get(filePath.path))

    actual fun write(filePath: FilePath, bytes: ByteArray) {
        JFiles.write(Paths.get(filePath.path), bytes)
    }

    actual fun write(filePath: FilePath, lines: List<String>) {
        JFiles.write(Paths.get(filePath.path), lines)
    }

    actual fun write(filePath: FilePath, string: String) {
        JFiles.writeString(Paths.get(filePath.path), string)
    }

    actual fun cwd(): DirectoryPath =
        DirectoryPath(Paths.get("").toAbsolutePath().toString())

    actual fun exists(filePath: FilePath): Boolean =
        JFiles.exists(Paths.get(filePath.path))

    actual fun streamLines(
        filePath: FilePath,
        bufferSize: BufferSize // Parameter bufferSize.bytes can be used if needed by underlying API
    ): Sequence<Join<FileOffset, ByteArray>> = sequence {
        val path = Paths.get(filePath.path)
        // JFiles.newBufferedReader doesn't directly use bufferSize in this simple form,
        // but it's available if a more custom reader were constructed.
        JFiles.newBufferedReader(path).useLines { lines ->
            var currentOffset = 0L
            lines.forEach { line ->
                val lineBytes = line.toByteArray()
                // This FileOffset is a conceptual start of the line.
                // For true byte offsets, one would need to sum byte lengths.
                // Here, using line index as a proxy if exact byte offset isn't critical,
                // or more complex logic if it is. For this example, let's use a running offset.
                yield(Join(FileOffset(currentOffset), lineBytes))
                currentOffset += lineBytes.size + 1 // +1 for newline, simplistic
            }
        }
    }

    actual fun iterateLines(
        filePath: FilePath,
        bufferSize: BufferSize // Parameter bufferSize.bytes can be used if needed
    ): Iterable<Join<FileOffset, Series<Byte>>> = Iterable {
        val path = Paths.get(filePath.path)
        val lines = JFiles.readAllLines(path) // Reads all lines, less ideal for large files
        var currentOffset = 0L
        lines.map { line ->
            val lineBytes = line.toByteArray()
            val entry = Join(FileOffset(currentOffset), lineBytes.toSeries())
            currentOffset += lineBytes.size + 1 // Simplistic offset tracking
            entry
        }.iterator()
    }
}