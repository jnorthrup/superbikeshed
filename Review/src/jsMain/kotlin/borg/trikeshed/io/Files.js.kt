package borg.trikeshed.io

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.FilePath
import borg.trikeshed.lib.DirectoryPath
import borg.trikeshed.lib.BufferSize
import borg.trikeshed.lib.FileOffset

actual object Files {
    actual fun readAllLines(filePath: FilePath): List<String> {
        TODO("Not yet implemented for JS. FilePath: ${filePath.path}")
    }

    actual fun readAllBytes(filePath: FilePath): ByteArray {
        TODO("Not yet implemented for JS. FilePath: ${filePath.path}")
    }

    actual fun readString(filePath: FilePath): String {
        TODO("Not yet implemented for JS. FilePath: ${filePath.path}")
    }

    actual fun write(filePath: FilePath, bytes: ByteArray) {
        // TODO("Not yet implemented for JS. FilePath: ${filePath.path}")
    }

    actual fun write(filePath: FilePath, lines: List<String>) {
        // TODO("Not yet implemented for JS. FilePath: ${filePath.path}")
    }

    actual fun write(filePath: FilePath, string: String) {
        // TODO("Not yet implemented for JS. FilePath: ${filePath.path}")
    }

    actual fun cwd(): DirectoryPath {
        TODO("Not yet implemented for JS. Should return DirectoryPath.")
        // return DirectoryPath("/mock/js/cwd") // Example placeholder return
    }

    actual fun exists(filePath: FilePath): Boolean {
        TODO("Not yet implemented for JS. FilePath: ${filePath.path}")
        // return false // Example placeholder return
    }

    actual fun streamLines(
        filePath: FilePath,
        bufferSize: BufferSize,
    ): Sequence<Join<FileOffset, ByteArray>> {
        TODO("Not yet implemented for JS. FilePath: ${filePath.path}, BufferSize: ${bufferSize.bytes}")
    }

    actual fun iterateLines(
        filePath: FilePath,
        bufferSize: BufferSize,
    ): Iterable<Join<FileOffset, Series<Byte>>> {
        TODO("Not yet implemented for JS. FilePath: ${filePath.path}, BufferSize: ${bufferSize.bytes}")
    }
}