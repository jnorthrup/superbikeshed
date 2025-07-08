@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package k2script.platform

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import borg.trikeshed.io.PlatformFileIO
import borg.trikeshed.io.PlatformFile
import borg.trikeshed.io.Files as TrikeshedFiles
import borg.trikeshed.io.Files as TrikeshedFiles

typealias File = PlatformFile

// Data class for command execution results
data class CommandResult(val stdout: String, val stderr: String, val exitCode: Int)

// Interface for file system operations
interface FileSystemOperations {
    val platformFileIO: PlatformFileIO
    suspend fun createTempDir(prefix: String): File
    suspend fun copyFile(source: File, destination: File, overwrite: Boolean = false)
    suspend fun deleteRecursively(file: File)
    suspend fun fileExists(file: File): Boolean = TrikeshedFiles.exists(file.absolutePath)
    suspend fun isDirectory(file: File): Boolean = file.isDirectory()
    suspend fun readText(file: File): String = TrikeshedFiles.readString(file.absolutePath)
    suspend fun writeText(file: File, text: String) = TrikeshedFiles.write(file.absolutePath, text)
    suspend fun getFileSize(file: File): Long
}

// Interface for process execution
interface ProcessExecutor {
    suspend fun runCommand(command: String, workingDir: File? = null): CommandResult
}

// CCEK Key for FileSystemOperations
class FileSystemOperationsKey(val fileSystemOperations: FileSystemOperations) : AbstractCoroutineContextElement(FileSystemOperationsKey) {
    companion object Key : CoroutineContext.Key<FileSystemOperationsKey>
}

// CCEK Key for ProcessExecutor
class ProcessExecutorKey(val processExecutor: ProcessExecutor) : AbstractCoroutineContextElement(ProcessExecutorKey) {
    companion object Key : CoroutineContext.Key<ProcessExecutorKey>
}

// Helper extensions to retrieve from CoroutineContext
val CoroutineContext.fileSystemOperations: FileSystemOperations
    get() = get(FileSystemOperationsKey)?.fileSystemOperations ?: error("FileSystemOperations not found in CoroutineContext")

val CoroutineContext.processExecutor: ProcessExecutor
    get() = get(ProcessExecutorKey)?.processExecutor ?: error("ProcessExecutor not found in CoroutineContext")
