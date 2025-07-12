@file:OptIn(kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

/**
 * JVM implementation of platformFileIO
 */
actual val platformFileIO: PlatformFileIO = PlatformFileIOImpl()