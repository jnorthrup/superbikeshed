@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

/**
 * WasmJS implementation of IOError
 */
actual object IOError {
    
    actual fun require(condition: Boolean, message: () -> String) {
        if (!condition) {
            throw IllegalArgumentException(message())
        }
    }
    
    actual fun check(condition: Boolean, message: () -> String): Boolean {
        if (!condition) {
            throw IllegalStateException(message())
        }
        return true
    }
}