package borg.trikeshed.lib

import kotlin.assert

actual fun assert(value: Boolean) = kotlin.assert(value)

@Throws(AssertionError::class)
actual fun assert(value: Boolean, lazyMessage: () -> Any) = kotlin.assert(value, lazyMessage) 