package borg.trikeshed.io

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Indexed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

actual val platformFileIO: PlatformFileIO = PlatformFileIOImpl()

actual object Files {
    actual fun readAllLines(path: String): List<String> {
        println("Files.readAllLines not implemented for macosArm64")
        return emptyList()
    }

    actual fun readAllBytes(path: String): ByteArray {
        println("Files.readAllBytes not implemented for macosArm64")
        return ByteArray(0)
    }

    actual fun readString(path: String): String {
        println("Files.readString not implemented for macosArm64")
        return ""
    }

    actual fun write(path: String, content: ByteArray) {
        println("Files.write(ByteArray) not implemented for macosArm64")
    }

    actual fun write(path: String, lines: List<String>) {
        println("Files.write(List<String>) not implemented for macosArm64")
    }

    actual fun write(path: String, string: String) {
        println("Files.write(String) not implemented for macosArm64")
    }

    actual fun exists(path: String): Boolean {
        println("Files.exists not implemented for macosArm64")
        return false
    }

    actual fun cwd(): String {
        println("Files.cwd not implemented for macosArm64")
        return ""
    }

    actual fun streamLines(fileName: String, bufsize: Int): Sequence<Join<Long, ByteArray>> {
        println("Files.streamLines not implemented for macosArm64")
        return emptySequence()
    }

    actual fun iterateLines(fileName: String, bufsize: Int): Iterable<Join<Long, Indexed<Byte>>> {
        println("Files.iterateLines not implemented for macosArm64")
        return emptyList()
    }

    actual fun delete(path: String) {
        println("Files.delete not implemented for macosArm64")
    }

    actual fun readLinesSeq(path: String): Sequence<String> {
        println("Files.readLinesSeq not implemented for macosArm64")
        return emptySequence()
    }

    actual fun readLines(path: String): List<String> {
        println("Files.readLines not implemented for macosArm64")
        return emptyList()
    }
}