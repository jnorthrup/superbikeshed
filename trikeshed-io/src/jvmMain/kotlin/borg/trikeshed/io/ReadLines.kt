package borg.trikeshed.io

import java.io.File

/**
 * JVM implementation of read lines functions
 */
actual fun readLinesSeq(path: String): Sequence<String> {
    return File(path).bufferedReader().lineSequence()
}

actual fun readLines(path: String): List<String> {
    return File(path).readLines()
}