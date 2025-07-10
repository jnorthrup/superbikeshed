@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package borg.trikeshed.io

import java.io.File

actual fun readBytes(path: String): ByteArray = File(path).readBytes()

actual fun writeBytes(path: String, data: ByteArray) {
    File(path).writeBytes(data)
}

actual fun fileExists(path: String): Boolean = File(path).exists()

actual fun createDirectory(path: String): Boolean = File(path).mkdirs()

actual fun deleteFile(path: String): Boolean = File(path).delete()