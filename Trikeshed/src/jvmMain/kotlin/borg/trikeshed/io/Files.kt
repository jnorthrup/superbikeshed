package borg.trikeshed.io

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.toIdx
import java.nio.file.Files as JFiles
import java.nio.file.Paths

actual object Files {
    actual fun readAllLines(path: String): List<String> =
        JFiles.readAllLines(Paths.get(path))

    actual fun readAllBytes(path: String): ByteArray =
        JFiles.readAllBytes(Paths.get(path))

    actual fun readString(filename: String): String =
        JFiles.readString(Paths.get(filename))

    actual fun write(filename: String, bytes: ByteArray) {
        JFiles.write(Paths.get(filename), bytes)
    }

    actual fun write(filename: String, lines: List<String>) {
        JFiles.write(Paths.get(filename), lines)
    }

    actual fun write(filename: String, string: String) {
        JFiles.writeString(Paths.get(filename), string)
    }

    actual fun cwd(): String =
        Paths.get("").toAbsolutePath().toString()

    actual fun exists(filename: String): Boolean =
        JFiles.exists(Paths.get(filename))

    actual fun delete(path: String) {
        JFiles.deleteIfExists(Paths.get(path))
    }

    actual fun readLinesSeq(path: String): Sequence<String> = sequence {
        JFiles.newBufferedReader(Paths.get(path)).useLines { lines ->
            lines.forEach { yield(it) }
        }
    }

    actual fun readLines(path: String): List<String> =
        JFiles.readAllLines(Paths.get(path))

    actual fun streamLines(fileName: String, bufsize: Int ): Sequence<Join<Long, ByteArray>> = sequence {
        val path = Paths.get(fileName)
        JFiles.newBufferedReader(path).useLines { lines ->
            lines.forEachIndexed { index, line ->
                yield(Join(index.toLong(), line.toByteArray()))
            }
        }
    }

    actual fun iterateLines(fileName: String, bufsize: Int ): Iterable<Join<Long, Indexed<Byte>>> = Iterable {
        val path = Paths.get(fileName)
        val lines = JFiles.readAllLines(path)
        lines.mapIndexed { index: Int, line: String ->
            Join(index.toLong(), (line.toByteArray()).toIdx())
        }.iterator()
    }
}