@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package k2script.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File as JvmFile
import borg.trikeshed.io.PlatformFileIO
import borg.trikeshed.io.PlatformFileIOImpl
import borg.trikeshed.io.Files as TrikeshedFiles

// JVM implementation of FileSystemOperations
class JvmFileSystemOperations(override val platformFileIO: PlatformFileIO) : FileSystemOperations {
    override suspend fun createTempDir(prefix: String): File = withContext(Dispatchers.IO) {
        val tempDir = JvmFile.createTempFile(prefix, "")
        tempDir.delete()
        tempDir.mkdir()
        File(tempDir.absolutePath)
    }

    override suspend fun copyFile(source: File, destination: File, overwrite: Boolean) = withContext(Dispatchers.IO) {
        JvmFile(source.absolutePath).copyTo(JvmFile(destination.absolutePath), overwrite)
    }

    override suspend fun deleteRecursively(file: File) = withContext(Dispatchers.IO) {
        JvmFile(file.absolutePath).deleteRecursively()
    }

    override suspend fun fileExists(file: File): Boolean = withContext(Dispatchers.IO) {
        TrikeshedFiles.exists(file.absolutePath)
    }

    override suspend fun isDirectory(file: File): Boolean = withContext(Dispatchers.IO) {
        JvmFile(file.absolutePath).isDirectory()
    }

    override suspend fun readText(file: File): String = withContext(Dispatchers.IO) {
        TrikeshedFiles.readString(file.absolutePath)
    }

    override suspend fun writeText(file: File, text: String) = withContext(Dispatchers.IO) {
        TrikeshedFiles.write(file.absolutePath, text)
    }

    override suspend fun getFileSize(file: File): Long = withContext(Dispatchers.IO) {
        JvmFile(file.absolutePath).length()
    }
}

// JVM implementation of ProcessExecutor
class JvmProcessExecutor : ProcessExecutor {
    override suspend fun runCommand(command: String, workingDir: File?): CommandResult = withContext(Dispatchers.IO) {
        val processBuilder = ProcessBuilder(*command.split(" ").toTypedArray())
        workingDir?.let { processBuilder.directory(JvmFile(it.absolutePath)) }
        processBuilder.redirectErrorStream(true) // Combine stdout and stderr

        val process = processBuilder.start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        CommandResult(output, "", exitCode)
    }
}
