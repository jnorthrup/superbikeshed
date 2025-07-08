@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

/**
 * JVM implementation of IOException
 */
actual class IOException actual constructor(message: String) : java.io.IOException(message)