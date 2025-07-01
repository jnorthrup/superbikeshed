package borg.trikeshed.io

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.size
import kotlinx.cinterop.*
import platform.posix.*

actual class PlatformFileIOImpl : PlatformFileIO {
    actual override suspend fun readFile(path: String): Indexed<Byte>? {
        val fileDescriptor = open(path, O_RDONLY)
        if (fileDescriptor == -1) {
            perror("Error opening file for reading")
            return null
        }

        return try {
            val buffer = ByteArray(4096).toIndexed()
            val bytesRead = read(fileDescriptor, buffer.play.toCValues(), buffer.a.toULong())
            if (bytesRead > 0) {
                buffer.slice(0, bytesRead.toInt())
            } else {
                null
            }
        } catch (e: Exception) {
            println("Error reading file $path: ${e.message}")
            null
        } finally {
            close(fileDescriptor)
        }
    }

    actual override suspend fun writeFile(path: String, content: Indexed<Byte>): Boolean {
        val fileDescriptor = open(path, O_WRONLY or O_CREAT or O_TRUNC, S_IRUSR or S_IWUSR)
        if (fileDescriptor == -1) {
            perror("Error opening file for writing")
            return false
        }

        return try {
            val bytesWritten = write(fileDescriptor, content.play.toCValues(), content.a.toULong())
            bytesWritten.toInt() == content.a
        } catch (e: Exception) {
            println("Error writing file $path: ${e.message}")
            false
        } finally {
            close(fileDescriptor)
        }
    }
}
