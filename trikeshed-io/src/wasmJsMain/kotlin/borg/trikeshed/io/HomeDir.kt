@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

/**
 * WasmJS implementation of home directory functions
 */
actual val homedirGet: String = "/home/wasm"

actual fun mktemp(): String {
    // WasmJS temp directory
    return "/tmp/wasm_${kotlin.random.Random.nextInt(1000000)}"
}

actual fun rm(path: String): Boolean {
    // WasmJS file removal - limited in browser environment
    return false
}

actual fun mkdir(path: String): Boolean {
    // WasmJS directory creation - limited in browser environment
    return false
}