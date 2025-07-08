@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.ssh

import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.toList

/**
 * JVM implementations of SSH file operations
 */

actual suspend fun readFile(path: String): String = withContext(Dispatchers.IO) {
    File(path).readText()
}

actual suspend fun writeFile(path: String, content: String) = withContext(Dispatchers.IO) {
    File(path).writeText(content)
}

actual suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.IO) {
    File(path).exists()
}

actual suspend fun listFiles(directory: String, pattern: Regex?): List<String> = withContext(Dispatchers.IO) {
    val dir = File(directory)
    if (!dir.exists() || !dir.isDirectory) {
        return@withContext emptyList()
    }
    
    dir.listFiles()
        ?.filter { file ->
            file.isFile && (pattern == null || pattern.matches(file.name))
        }
        ?.map { it.name }
        ?: emptyList()
}

actual fun getSSHDirectory(): String {
    return "${System.getProperty("user.home")}/.ssh"
}