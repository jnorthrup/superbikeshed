package borg.trikeshed.nio

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.emptySeries
import borg.trikeshed.lib.j
import borg.trikeshed.lib.toSeries
import borg.trikeshed.lib.FilePath
import borg.trikeshed.lib.DirectoryPath
import borg.trikeshed.lib.BufferSize
import borg.trikeshed.lib.FileOffset

actual object Files {
    actual fun readAllLines(filePath: FilePath): List<String> {
        println("readAllLines ${filePath.path} (Native placeholder)")
        return emptyList()
    }

    actual fun readAllBytes(filePath: FilePath): ByteArray {
        println("readAllBytes ${filePath.path} (Native placeholder)")
        return byteArrayOf()
    }

    actual fun readString(filePath: FilePath): String {
        println("readString ${filePath.path} (Native placeholder)")
        return ""
    }

    actual fun write(filePath: FilePath, bytes: ByteArray) {
        println("write ${filePath.path} (bytes, Native placeholder)")
    }

    actual fun write(filePath: FilePath, lines: List<String>) {
        println("write ${filePath.path} (lines, Native placeholder)")
    }

    actual fun write(filePath: FilePath, string: String) {
        println("write ${filePath.path} (string, Native placeholder)")
    }

    actual fun cwd(): DirectoryPath {
        return DirectoryPath("/current/working/dir") // Placeholder for cwd
    }

    actual fun exists(filePath: FilePath): Boolean {
        println("exists ${filePath.path} (Native placeholder)")
        return false // Assume false by default for placeholder
    }

    actual fun streamLines(
        filePath: FilePath,
        bufferSize: BufferSize,
    ): Sequence<Join<FileOffset, ByteArray>> = sequence {
        println("streamLines ${filePath.path}, buffer: ${bufferSize.bytes} (Native placeholder)")
        // Yield a single mock line
        yield(FileOffset(0L) j "Mock Line 1".encodeToByteArray())
    }

    actual fun iterateLines(
        filePath: FilePath,
        bufferSize: BufferSize,
    ): Iterable<Join<FileOffset, Series<Byte>>> = Iterable {
        println("iterateLines ${filePath.path}, buffer: ${bufferSize.bytes} (Native placeholder)")
        listOf(FileOffset(0L) j "Mock Line 1".encodeToByteArray().toSeries()).iterator()
    }
}
