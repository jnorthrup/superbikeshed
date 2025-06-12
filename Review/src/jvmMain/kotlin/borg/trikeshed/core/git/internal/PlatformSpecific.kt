package borg.trikeshed.core.git.internal

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.Inflater
import java.nio.charset.StandardCharsets

actual fun decompressZlib(compressedData: ByteArray): ByteArray {
    val inflater = Inflater()
    val outputStream = ByteArrayOutputStream()
    val buffer = ByteArray(1024)

    inflater.setInput(compressedData)
    try {
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0) {
                if (inflater.needsInput()) {
                     // This case implies truncated or corrupt data if no more input is available.
                    throw IOException("Zlib inflation error: needs input but none available.")
                }
                // If count is 0 and no input is needed, it might mean finished, or buffer too small (though Inflater handles this).
                // Loop should terminate if inflater.finished() is true.
                if (!inflater.finished()) {
                    // If not finished and count is 0, this is an unexpected state.
                    throw IOException("Zlib inflation error: count is 0 but not finished and no input needed.")
                }
                // if finished, break
                 break;
            }
            outputStream.write(buffer, 0, count)
        }
    } finally {
        inflater.end()
    }
    return outputStream.toByteArray()
}

actual fun readPlatformFile(filePath: String): ByteArray? {
    return try {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            // println("Binary file does not exist or is not a regular file: $filePath") // Less noisy for binary reads
            return null
        }
        file.readBytes()
    } catch (e: IOException) {
        println("IOException while reading binary file '$filePath': ${e.message}")
        null
    } catch (e: SecurityException) {
        println("SecurityException: Permission denied for binary file '$filePath': ${e.message}")
        null
    }
}

// --- New actual platform functions ---

actual fun readPlatformTextFile(filePath: String): String? {
    return try {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            // println("Text file does not exist or is not a regular file: $filePath") // Can be noisy
            return null
        }
        file.readText(StandardCharsets.UTF_8) // Assume UTF-8 for ref files and other text files in .git
    } catch (e: IOException) {
        println("IOException while reading text file '$filePath': ${e.message}")
        null
    } catch (e: SecurityException) {
        println("SecurityException: Permission denied for text file '$filePath': ${e.message}")
        null
    } catch (e: Exception) { // Catch any other exception during text file reading
        println("Unexpected exception while reading text file '$filePath': ${e.message}")
        null
    }
}

actual fun listPlatformDirectory(dirPath: String): List<String>? {
    return try {
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory) {
            // println("Directory does not exist or is not a directory: $dirPath") // Can be noisy
            return null
        }
        dir.list()?.toList()
    } catch (e: SecurityException) {
        println("SecurityException: Permission denied for directory '$dirPath': ${e.message}")
        null
    } catch (e: Exception) {
        println("Unexpected exception while listing directory '$dirPath': ${e.message}")
        null
    }
}

actual fun platformIsFile(filePath: String): Boolean {
    return try {
        File(filePath).isFile
    } catch (e: SecurityException) {
        println("SecurityException checking if file: '$filePath': ${e.message}")
        false
    } catch (e: Exception) {
        println("Unexpected exception checking if file: '$filePath': ${e.message}")
        false
    }
}

actual fun platformIsDirectory(filePath: String): Boolean {
    return try {
        File(filePath).isDirectory
    } catch (e: SecurityException) {
        println("SecurityException checking if directory: '$filePath': ${e.message}")
        false
    } catch (e: Exception) {
        println("Unexpected exception checking if directory: '$filePath': ${e.message}")
        false
    }
}

actual fun platformJoinPath(base: String, vararg children: String): String {
    var current = File(base)
    for (child in children) {
        current = File(current, child)
    }
    return current.path
}
