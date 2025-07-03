package borg.trikeshed.io

import java.io.File

actual class PlatformFile actual constructor(val path: String) {
    private val file: File = File(path)

    actual fun exists(): Boolean = file.exists()
    actual fun isDirectory(): Boolean = file.isDirectory
    actual fun readAllBytes(): ByteArray = file.readBytes()
}