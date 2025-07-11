@file:OptIn(kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

/**
 * Expect declaration for IOException
 */
expect class IOException(message: String) : Exception 