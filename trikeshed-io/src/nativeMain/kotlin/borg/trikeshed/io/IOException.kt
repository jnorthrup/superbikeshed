@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@file:OptIn(RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
@file:OptIn(ExperimentalForeignApi::class)


package borg.trikeshed.io

import kotlinx.cinterop.ExperimentalForeignApi

/**
 * Native implementation of IOException
 */
actual class IOException actual constructor(message: String) : Exception(message)