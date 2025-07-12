package borg.trikeshed.io

actual object IOError {
    actual fun require(condition: Boolean, message: () -> String) {
        kotlin.require(condition, message)
    }

    actual fun check(condition: Boolean, message: () -> String) {
        kotlin.check(condition, message)
    }
}