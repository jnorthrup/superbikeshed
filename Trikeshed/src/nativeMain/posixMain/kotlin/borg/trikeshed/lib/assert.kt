package borg.trikeshed.lib

actual fun trikeAssert(value: Boolean) {
    if (!value) {
        throw AssertionError()
    }
}

actual fun trikeAssert(value: Boolean, lazyMessage: () -> Any) {
    if (!value) {
        throw AssertionError(lazyMessage().toString())
    }
} 