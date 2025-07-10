@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
@file:OptIn(ExperimentalForeignApi::class)


package borg.trikeshed.io

import kotlinx.cinterop.ExperimentalForeignApi

/**
 * Native implementation of IOError
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