package borg.trikeshed.io

import borg.trikeshed.lib.*
import java.nio.file.Files as JFiles
import java.nio.file.Paths

actual object Files {
    actual fun readAllLines(path: String): List<String> =
        JFiles.readAllLines(Paths.get(path))

    actual fun readAllBytes(path: String): ByteArray =
        JFiles.readAllBytes(Paths.get(path))

    actual fun readString(path: String): String =
        JFiles.readString(Paths.get(path))

    actual fun write(path: String, content: ByteArray) {
        JFiles.write(Paths.get(path), content)
    }

    actual fun write(path: String, lines: List<String>) {
        JFiles.write(Paths.get(path), lines)
    }

    actual fun write(path: String, string: String) {
        JFiles.writeString(Paths.get(path), string)
    }

    actual fun cwd(): String =
        Paths.get("").toAbsolutePath().toString()

    actual fun exists(path: String): Boolean =
        JFiles.exists(Paths.get(path))

    actual fun streamLines(fileName: String, bufsize: Int ): Sequence<Join<Long, ByteArray>> = sequence {
        val p = java.nio.file.Paths.get(fileName)
        java.nio.file.Files.newBufferedReader(p).useLines { lines ->
            lines.forEachIndexed { index, line ->
                yield(Join(index.toLong(), line.toByteArray()))
            }
        }
    }

    actual fun iterateLines(fileName: String, bufsize: Int ): Iterable<Join<Long, Indexed<Byte>>> = Iterable {
        val p = java.nio.file.Paths.get(fileName)
        val lines = java.nio.file.Files.readAllLines(p)
        lines.mapIndexed { index: Int, line: String ->
            Join(index.toLong(), Indexed(line.toByteArray(), index))
        }.iterator()
    }

    actual fun delete(path: String) {
        java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(path))
    }

    actual fun readLinesSeq(path: String): Sequence<String> =
        java.nio.file.Files.lines(java.nio.file.Paths.get(path)).asSequence()

    actual fun readLines(path: String): List<String> =
        java.nio.file.Files.readAllLines(java.nio.file.Paths.get(path))
}