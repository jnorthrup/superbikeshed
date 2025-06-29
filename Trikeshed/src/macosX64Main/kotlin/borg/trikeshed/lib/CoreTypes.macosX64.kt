package borg.trikeshed.lib

import kotlin.assert

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
actual fun assert(value: Boolean) = kotlin.assert(value)

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
@Throws(AssertionError::class)
actual fun assert(value: Boolean, lazyMessage: () -> Any) = kotlin.assert(value, lazyMessage) 