package nexus.core.dgm.services

import nexus.core.dgm.CodeSnapshot
import nexus.core.dgm.FilePath
import nexus.core.dgm.FileContent
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.Join

// KMP expect/actual for basic file system operations
// In a full CCEK environment, this might be replaced by a CCEK NioService.Key lookup
expect object FileSystemUtils {
    fun createTempDirectory(prefix: String, context: CCEKContext): FilePath
    fun writeFile(filePath: FilePath, content: FileContent, context: CCEKContext)
    fun readFile(filePath: FilePath, context: CCEKContext): FileContent
    fun listFilesRecursively(directoryPath: FilePath, context: CCEKContext): Series<FilePath>
    fun deleteDirectoryRecursively(directoryPath: FilePath, context: CCEKContext): Boolean
    fun joinPath(base: FilePath, vararg parts: String): FilePath
    fun getAbsolutePath(path: FilePath, context: CCEKContext): FilePath
    fun fileExists(filePath: FilePath, context: CCEKContext): Boolean
    fun createDirectories(directoryPath: FilePath, context: CCEKContext)
}

// Default/common actual implementation for FileSystemUtils for commonMain.
// This is a placeholder/stub. Real implementations would be in platform-specific source sets (jvmMain, nativeMain, etc.)
// and would use actual file system APIs.
// For this exercise, we'll make them do nothing or return mock values to allow compilation.
internal actual object FileSystemUtils {
    private const val MOCK_TEMP_DIR_ROOT = "/tmp/dgm_workspaces/" // Mock base for temp dirs

    actual fun createTempDirectory(prefix: String, context: CCEKContext): FilePath {
        // In a real scenario, this would use platform APIs to create a unique temporary directory.
        val mockDirName = prefix + "_" + kotlin.random.Random.nextInt(100000, 999999)
        val mockPath = joinPath(MOCK_TEMP_DIR_ROOT, mockDirName)
        // Simulate creation for logging/tracking if needed, but no actual FS op here for commonMain stub
        println("MockFileSystemUtils: Creating temp directory at $mockPath")
        // In a real implementation, ensure the directory is actually created.
        // For this stub, we assume it's "created" and return the path.
        return mockPath
    }

    actual fun writeFile(filePath: FilePath, content: FileContent, context: CCEKContext) {
        // Real implementation: java.io.File(filePath).writeText(content) or similar
        println("MockFileSystemUtils: Writing ${content.length} chars to $filePath")
        // No actual write for this commonMain stub
    }

    actual fun readFile(filePath: FilePath, context: CCEKContext): FileContent {
        // Real implementation: java.io.File(filePath).readText() or similar
        println("MockFileSystemUtils: Reading from $filePath (returning empty content for stub)")
        return "" // Stub behavior
    }

    actual fun listFilesRecursively(directoryPath: FilePath, context: CCEKContext): Series<FilePath> {
        // Real implementation: Walk file tree and collect relative paths
        println("MockFileSystemUtils: Listing files in $directoryPath (returning empty series for stub)")
        return Series.empty() // Stub behavior
    }

    actual fun deleteDirectoryRecursively(directoryPath: FilePath, context: CCEKContext): Boolean {
        // Real implementation: Delete directory and its contents
        println("MockFileSystemUtils: Deleting directory recursively $directoryPath")
        return true // Stub behavior, assume success
    }

    actual fun joinPath(base: FilePath, vararg parts: String): FilePath {
        return (listOf(base.trimEnd('/')) + parts.map { it.trim('/') }).joinToString("/")
    }

    actual fun getAbsolutePath(path: FilePath, context: CCEKContext): FilePath {
        // Real implementation: java.io.File(path).absolutePath
        println("MockFileSystemUtils: Getting absolute path for $path (returning as-is for stub)")
        return path // Stub, assumes path is already absolute-like for mock purposes
    }

    actual fun fileExists(filePath: FilePath, context: CCEKContext): Boolean {
        println("MockFileSystemUtils: Checking existence of $filePath (returning false for stub)")
        return false // Stub behavior
    }

    actual fun createDirectories(directoryPath: FilePath, context: CCEKContext) {
        println("MockFileSystemUtils: Creating directories for $directoryPath")
        // No actual creation for this commonMain stub
    }
}


class LocalWorkspaceService : WorkspaceService {

    override suspend fun setupWorkspace(parentCode: CodeSnapshot, context: CCEKContext): FilePath {
        val workspaceRoot = FileSystemUtils.createTempDirectory("dgm_ws_", context)
        FileSystemUtils.createDirectories(workspaceRoot, context) // Ensure root exists

        parentCode.forEachIndexed { _, fileEntry ->
            val filePath = fileEntry.a // FilePath (relative to some conceptual root)
            val fileContent = fileEntry.b // FileContent

            val absoluteFilePath = FileSystemUtils.joinPath(workspaceRoot, filePath)
            val parentDir = absoluteFilePath.substringBeforeLast('/', "")

            if (parentDir.isNotEmpty() && parentDir != workspaceRoot) {
                FileSystemUtils.createDirectories(parentDir, context)
            }
            FileSystemUtils.writeFile(absoluteFilePath, fileContent, context)
        }
        return workspaceRoot
    }

    override suspend fun applyChanges(workspacePath: FilePath, changes: CodeSnapshot, context: CCEKContext): Boolean {
        // This assumes `changes` CodeSnapshot contains paths relative to the workspacePath
        changes.forEachIndexed { _, fileEntry ->
            val relativeFilePath = fileEntry.a
            val fileContent = fileEntry.b
            val absoluteFilePath = FileSystemUtils.joinPath(workspacePath, relativeFilePath)

            val parentDir = absoluteFilePath.substringBeforeLast('/', "")
            if (parentDir.isNotEmpty() && parentDir != workspacePath) {
                 FileSystemUtils.createDirectories(parentDir, context)
            }
            FileSystemUtils.writeFile(absoluteFilePath, fileContent, context)
        }
        return true // Assuming success for now, real implementation would have error handling
    }

    override suspend fun getCurrentSnapshot(workspacePath: FilePath, context: CCEKContext): CodeSnapshot {
        val allFilePathsInWorkspace = FileSystemUtils.listFilesRecursively(workspacePath, context)
        val fileEntries = mutableListOf<Join<FilePath, FileContent>>()

        allFilePathsInWorkspace.forEachIndexed { _, absoluteFilePath ->
            // We need to make file paths relative to the workspace root for the snapshot
            val relativePath = absoluteFilePath.removePrefix(workspacePath).trimStart('/')
            if (FileSystemUtils.fileExists(absoluteFilePath, context)) { // Check if it's a file not a dir
                try {
                    val content = FileSystemUtils.readFile(absoluteFilePath, context)
                    fileEntries.add(Join(relativePath, content))
                } catch (e: Exception) {
                    // Handle cases where a listed path might be a directory or unreadable
                    // Or ensure listFilesRecursively only returns files
                    println("Warning: Could not read file $absoluteFilePath: ${e.message}")
                }
            }
        }
        return Series.ofList(fileEntries)
    }

    override suspend fun cleanupWorkspace(workspacePath: FilePath, context: CCEKContext) {
        FileSystemUtils.deleteDirectoryRecursively(workspacePath, context)
    }
}
