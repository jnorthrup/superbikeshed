@file:OptIn(RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

import borg.trikeshed.lib.*
import java.io.File
import java.nio.file.Files as NioFiles
import java.nio.file.Paths

/**
 * JVM implementation of Files
 */
actual object Files {
    
    actual fun readAllLines(path: String): List<String> {
        return File(path).readLines()
    }
    
    actual fun readAllBytes(path: String): ByteArray {
        return File(path).readBytes()
    }
    
    actual fun readString(path: String): String {
        return File(path).readText()
    }
    
    actual fun write(path: String, content: ByteArray) {
        File(path).writeBytes(content)
    }
    
    actual fun write(path: String, lines: List<String>) {
        File(path).writeText(lines.joinToString("\n"))
    }
    
    actual fun write(path: String, string: String) {
        File(path).writeText(string)
    }
    
    actual fun exists(path: String): Boolean {
        return File(path).exists()
    }
    
    actual fun cwd(): String {
        return System.getProperty("user.dir")
    }
    
    actual fun streamLines(fileName: String, bufsize: Int): Sequence<Join<Long, ByteArray>> {
        var lineNumber = 0L
        return File(fileName).bufferedReader(bufferSize = bufsize).lineSequence().map { line ->
            lineNumber++ j line.toByteArray()
        }
    }
    
    actual fun iterateLines(fileName: String, bufsize: Int): Iterable<Join<Long, Indexed<Byte>>> {
        return streamLines(fileName, bufsize).map { (lineNum: Long, bytes: ByteArray) ->
            lineNum j (bytes.size j { i: Int -> bytes[i] })
        }.asIterable()
    }
    
    actual fun delete(path: String) {
        File(path).delete()
    }
    
    actual fun readLinesSeq(path: String): Sequence<String> {
        return File(path).bufferedReader().lineSequence()
    }
    
    actual fun readLines(path: String): List<String> {
        return File(path).readLines()
    }
}