@file:OptIn(RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

/**
 * WasmJS implementation of PlatformFile
 */
actual class PlatformFile actual constructor(internal val path: String) {
    
    actual fun exists(): Boolean {
        // WasmJS file existence check - limited in browser environment
        return false
    }
    
    actual fun isDirectory(): Boolean {
        // WasmJS directory check - limited in browser environment
        return false
    }
    
    actual fun readAllBytes(): ByteArray {
        // WasmJS file reading - limited in browser environment
        return ByteArray(0)
    }
}