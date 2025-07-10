@file:OptIn(kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

/**
 * JVM implementation of IOException
 */
actual class IOException actual constructor(message: String) : java.io.IOException(message)