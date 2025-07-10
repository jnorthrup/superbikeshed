@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

import java.io.File

/**
 * JVM implementation of PlatformFile
 */
actual class PlatformFile actual constructor(internal val path: String) {
    internal val file = File(path)
    
    actual fun exists(): Boolean {
        return file.exists()
    }
    
    actual fun isDirectory(): Boolean {
        return file.isDirectory
    }
    
    actual fun readAllBytes(): ByteArray {
        return file.readBytes()
    }
}