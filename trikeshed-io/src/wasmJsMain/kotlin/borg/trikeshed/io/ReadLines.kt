@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

/**
 * WasmJS implementation of read lines functions
 */
actual fun readLinesSeq(path: String): Sequence<String> {
    // WasmJS file reading - limited in browser environment
    return emptySequence()
}

actual fun readLines(path: String): List<String> {
    // WasmJS file reading - limited in browser environment
    return emptyList()
}