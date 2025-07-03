package borg.trikeshed.io

actual object IOError {
    actual fun require(condition: Boolean, message: () -> String) {
        if (!condition) {
            throw IOException(message())
        }
    }
    
    actual fun check(condition: Boolean, message: () -> String): Boolean {
        if (!condition) {
            throw IOException(message())
        }
        return true
    }
} 