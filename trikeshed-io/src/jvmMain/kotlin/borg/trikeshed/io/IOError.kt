@file:OptIn(RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

/**
 * JVM implementation of IOError
 */
actual object IOError {
    
    actual fun require(condition: Boolean, message: () -> String) {
        require(condition, message)
    }
    
    actual fun check(condition: Boolean, message: () -> String): Boolean {
        check(condition, message)
        return true
    }
}