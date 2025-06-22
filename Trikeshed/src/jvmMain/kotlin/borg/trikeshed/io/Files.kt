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

    actual fun exists(path: String): Boolean =
        JFiles.exists(Paths.get(path))

    actual fun cwd(): String =
        Paths.get("").toAbsolutePath().toString()

    actual fun streamLines(fileName: String, bufsize: Int): Sequence<Join<Long, ByteArray>> = sequence {
        val path = Paths.get(fileName)
        JFiles.newBufferedReader(path).useLines { lines ->
            lines.forEachIndexed { index, line ->
                yield(Join(index.toLong(), line.toByteArray()))
            }
        }
    }

    actual fun iterateLines(fileName: String, bufsize: Int): Iterable<Join<Long, Indexed<Byte>>> = Iterable {
        val path = Paths.get(fileName)
        val lines = JFiles.readAllLines(path)
        lines.mapIndexed { index: Int, line: String ->
            Join(index.toLong(), line.toByteArray().toList().toIdx())
        }.iterator()
    }

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
}