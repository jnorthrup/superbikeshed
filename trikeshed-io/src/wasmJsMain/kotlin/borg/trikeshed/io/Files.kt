@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

import borg.trikeshed.lib.*

/**
 * WasmJS implementation of Files
 */
actual object Files {
    
    actual fun readAllLines(path: String): List<String> {
        // WasmJS file reading - limited in browser environment
        return emptyList()
    }
    
    actual fun readAllBytes(path: String): ByteArray {
        // WasmJS file reading - limited in browser environment
        return ByteArray(0)
    }
    
    actual fun readString(path: String): String {
        // WasmJS file reading - limited in browser environment
        return ""
    }
    
    actual fun write(path: String, content: ByteArray) {
        // WasmJS file writing - limited in browser environment
    }
    
    actual fun write(path: String, lines: List<String>) {
        // WasmJS file writing - limited in browser environment
    }
    
    actual fun write(path: String, string: String) {
        // WasmJS file writing - limited in browser environment
    }
    
    actual fun exists(path: String): Boolean {
        // WasmJS file existence check
        return false
    }
    
    actual fun cwd(): String {
        // WasmJS current working directory
        return "/wasm"
    }
    
    actual fun streamLines(fileName: String, bufsize: Int): Sequence<Join<Long, ByteArray>> {
        // WasmJS streaming - limited in browser environment
        return emptySequence()
    }
    
    actual fun iterateLines(fileName: String, bufsize: Int): Iterable<Join<Long, Indexed<Byte>>> {
        // WasmJS iteration - limited in browser environment
        return emptyList()
    }
    
    actual fun delete(path: String) {
        // WasmJS file deletion
    }
    
    actual fun readLinesSeq(path: String): Sequence<String> {
        // WasmJS file reading - limited in browser environment
        return emptySequence()
    }
    
    actual fun readLines(path: String): List<String> {
        // WasmJS file reading - limited in browser environment
        return emptyList()
    }
}