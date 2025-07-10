@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File as JvmFile
import borg.trikeshed.io.PlatformFileIO
import borg.trikeshed.io.PlatformFileIOImpl
import borg.trikeshed.io.PlatformFile
import borg.trikeshed.io.Files as TrikeshedFiles

// JVM implementation of FileSystemOperations
class JvmFileSystemOperations(override val platformFileIO: PlatformFileIO) : FileSystemOperations {
    override suspend fun createTempDir(prefix: String): PlatformFile = withContext(Dispatchers.IO) {
        val tempDir = JvmFile.createTempFile(prefix, "")
        tempDir.delete()
        tempDir.mkdir()
        PlatformFile(tempDir.absolutePath)
    }

    override suspend fun copyFile(source: File, destination: File, overwrite: Boolean) = withContext(Dispatchers.IO) {
        val srcFile = if (source is PlatformFile) JvmFile(source.path) else JvmFile(source.toString())
        val destFile = if (destination is PlatformFile) JvmFile(destination.path) else JvmFile(destination.toString())
        srcFile.copyTo(destFile, overwrite)
    }

    override suspend fun deleteRecursively(file: File) = withContext(Dispatchers.IO) {
        val jvmFile = if (file is PlatformFile) JvmFile(file.path) else JvmFile(file.toString())
        jvmFile.deleteRecursively()
    }

    override suspend fun fileExists(file: File): Boolean = withContext(Dispatchers.IO) {
        val path = if (file is PlatformFile) file.path else file.toString()
        TrikeshedFiles.exists(path)
    }

    override suspend fun isDirectory(file: File): Boolean = withContext(Dispatchers.IO) {
        file.isDirectory()
    }

    override suspend fun readText(file: File): String = withContext(Dispatchers.IO) {
        val path = if (file is PlatformFile) file.path else file.toString()
        TrikeshedFiles.readString(path)
    }

    override suspend fun writeText(file: File, text: String) = withContext(Dispatchers.IO) {
        val path = if (file is PlatformFile) file.path else file.toString()
        TrikeshedFiles.write(path, text)
    }

    override suspend fun getFileSize(file: File): Long = withContext(Dispatchers.IO) {
        val jvmFile = if (file is PlatformFile) JvmFile(file.path) else JvmFile(file.toString())
        jvmFile.length()
    }
}

// JVM implementation of ProcessExecutor
class JvmProcessExecutor : ProcessExecutor {
    override suspend fun runCommand(command: String, workingDir: File?): CommandResult = withContext(Dispatchers.IO) {
        val processBuilder = ProcessBuilder(*command.split(" ").toTypedArray())
        workingDir?.let { 
            val dirPath = if (it is PlatformFile) it.path else it.toString()
            processBuilder.directory(JvmFile(dirPath)) 
        }
        processBuilder.redirectErrorStream(true) // Combine stdout and stderr

        val process = processBuilder.start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        CommandResult(output, "", exitCode)
    }
}
