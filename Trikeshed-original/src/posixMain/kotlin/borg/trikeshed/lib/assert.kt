package borg.trikeshed.lib

actual fun assert(value: Boolean) {
    if (!value) {
        throw AssertionError()
    }
}

actual fun assert(value: Boolean, lazyMessage: () -> Any) {
    if (!value) {
        throw AssertionError(lazyMessage().toString())
    }
} 