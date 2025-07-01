package borg.trikeshed.io

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.size
import java.io.File
import java.io.IOException

actual class PlatformFileIOImpl : PlatformFileIO {
    actual override suspend fun readFile(path: String): Indexed<Byte>? {
        return try {
            val file = File(path)
            if (file.exists() && file.isFile) {
                file.readBytes().size j { i -> file.readBytes()[i] }
            } else {
                null
            }
        } catch (e: IOException) {
            println("Error reading file $path: ${e.message}")
            null
        }
    }

    actual override suspend fun writeFile(path: String, content: Indexed<Byte>): Boolean {
        return try {
            val file = File(path)
            file.writeBytes(content.play.toByteArray())
            true
        } catch (e: IOException) {
            println("Error writing file $path: ${e.message}")
            false
        }
    }
}
