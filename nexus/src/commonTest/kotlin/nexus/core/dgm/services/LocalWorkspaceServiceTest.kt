package nexus.core.dgm.services

import nexus.core.dgm.CodeSnapshot
import nexus.core.dgm.FilePath
import nexus.core.dgm.FileContent
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Join
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*
import kotlinx.coroutines.test.runTest

// Test-specific actual implementation for FileSystemUtils
// This replaces the stub in the main commonMain LocalWorkspaceService.kt for testing purposes.
internal actual object FileSystemUtils {
    val calls = mutableListOf<String>()
    private val fileContents = mutableMapOf<FilePath, FileContent>()
    private var tempDirCounter = 0
    private var mockTempBase = "/tmp/test_ws_"
    private val createdDirs = mutableSetOf<FilePath>()
    private val existingFilesForListing = mutableMapOf<FilePath, Indexed<FilePath>>() // Dir -> List of files in it
    private val filesToExist = mutableSetOf<FilePath>()


    fun reset() {
        calls.clear()
        fileContents.clear()
        tempDirCounter = 0
        createdDirs.clear()
        existingFilesForListing.clear()
        filesToExist.clear()
    }

    fun mockTempDirectoryPath(prefix: String): FilePath {
        return "$mockTempBase$prefix${tempDirCounter++}"
    }

    fun setMockBasePath(basePath: String) {
        mockTempBase = basePath
        if (!mockTempBase.endsWith("/")) mockTempBase += "/"
    }

    actual fun createTempDirectory(prefix: String, context: CCEKContext): FilePath {
        val path = mockTempDirectoryPath(prefix)
        calls.add("createTempDirectory $prefix -> $path")
        createdDirs.add(path)
        return path
    }

    actual fun writeFile(filePath: FilePath, content: FileContent, context: CCEKContext) {
        calls.add("writeFile $filePath (content: ${content.take(20)}...)")
        fileContents[filePath] = content
        filesToExist.add(filePath) // Assume write implies existence for subsequent reads/listings in mock
    }

    actual fun readFile(filePath: FilePath, context: CCEKContext): FileContent {
        calls.add("readFile $filePath")
        if (!filesToExist.contains(filePath) && !fileContents.containsKey(filePath)) {
            throw RuntimeException("Mock File Not Found: $filePath") // Simulate file not found
        }
        return fileContents[filePath] ?: ""
    }

    fun mockListFiles(directoryPath: FilePath, files: List<FilePath>) {
        existingFilesForListing[directoryPath] = Indexed.ofList(files.map { joinPath(directoryPath, it) })
    }

    actual fun listFilesRecursively(directoryPath: FilePath, context: CCEKContext): Indexed<FilePath> {
        calls.add("listFilesRecursively $directoryPath")
        // Return files explicitly mocked for this directory, or all files under this path from fileContents for simplicity
        return existingFilesForListing[directoryPath] ?: run {
            val filesInDir = fileContents.keys
                .filter { it.startsWith(directoryPath) && it != directoryPath }
                .map { it } // Return absolute paths as if a real FS would
            Indexed.ofList(filesInDir)
        }
    }

    actual fun deleteDirectoryRecursively(directoryPath: FilePath, context: CCEKContext): Boolean {
        calls.add("deleteDirectoryRecursively $directoryPath")
        // Simulate deletion by removing relevant entries
        val pathsToRemove = fileContents.keys.filter { it.startsWith(directoryPath) }
        pathsToRemove.forEach { fileContents.remove(it) }
        createdDirs.remove(directoryPath)
        existingFilesForListing.remove(directoryPath)
        filesToExist.removeAll(pathsToRemove.toSet())
        return true
    }

    actual fun joinPath(base: FilePath, vararg parts: String): FilePath {
        return (listOf(base.trimEnd('/')) + parts.map { it.trim('/') }).joinToString("/")
    }

    actual fun getAbsolutePath(path: FilePath, context: CCEKContext): FilePath {
        calls.add("getAbsolutePath $path")
        return if (path.startsWith("/")) path else "/abs/path/to/$path" // Mock absolute path
    }

    actual fun fileExists(filePath: FilePath, context: CCEKContext): Boolean {
        calls.add("fileExists $filePath")
        // Check if explicitly set to exist (e.g. after writeFile) or if content is available
        return filesToExist.contains(filePath) || fileContents.containsKey(filePath)
    }

    actual fun createDirectories(directoryPath: FilePath, context: CCEKContext) {
        calls.add("createDirectories $directoryPath")
        createdDirs.add(directoryPath)
    }
}


class LocalWorkspaceServiceTest {

    private lateinit var workspaceService: LocalWorkspaceService
    private val testContext = EmptyCoroutineContext

    @BeforeTest
    fun setup() {
        FileSystemUtils.reset()
        FileSystemUtils.setMockBasePath("/testroot/ws_") // Ensure predictable workspace roots for tests
        workspaceService = LocalWorkspaceService()
    }

    @Test
    fun setupWorkspaceCreatesDirectoryAndWritesFiles() = runTest {
        val file1 = Join<FilePath, FileContent>("src/main.kt", "fun main() {}")
        val file2 = Join<FilePath, FileContent>("README.md", "# Test Project")
        val parentCode = Indexed.of(file1, file2)

        val workspaceRoot = workspaceService.setupWorkspace(parentCode, testContext)
        val expectedWorkspaceRoot = FileSystemUtils.mockTempBasePath("dgm_ws_0")

        assertEquals(expectedWorkspaceRoot, workspaceRoot)
        assertTrue(FileSystemUtils.calls.contains("createTempDirectory dgm_ws_ -> $expectedWorkspaceRoot"))
        assertTrue(FileSystemUtils.calls.contains("createDirectories $expectedWorkspaceRoot")) // Initial root creation

        // Check for directory creation for src folder
        val srcDir = FileSystemUtils.joinPath(expectedWorkspaceRoot, "src")
        assertTrue(FileSystemUtils.calls.contains("createDirectories $srcDir"))
        assertTrue(FileSystemUtils.calls.contains("writeFile ${FileSystemUtils.joinPath(expectedWorkspaceRoot, "src/main.kt")} (content: fun main() {}...)"))

        // Check for README (no separate subdir creation needed beyond root)
        assertTrue(FileSystemUtils.calls.contains("writeFile ${FileSystemUtils.joinPath(expectedWorkspaceRoot, "README.md")} (content: # Test Project...)"))
    }

    @Test
    fun applyChangesWritesFilesToWorkspace() = runTest {
        val workspacePath = "/testroot/ws_apply_test"
        FileSystemUtils.createdDirs.add(workspacePath) // Simulate workspace already exists

        val change1 = Join<FilePath, FileContent>("lib/utils.kt", "fun util() = true")
        val change2 = Join<FilePath, FileContent>("data/config.json", """{"key":"value"}""")
        val changes = Indexed.of(change1, change2)

        val result = workspaceService.applyChanges(workspacePath, changes, testContext)
        assertTrue(result)

        val libDir = FileSystemUtils.joinPath(workspacePath, "lib")
        val dataDir = FileSystemUtils.joinPath(workspacePath, "data")

        assertTrue(FileSystemUtils.calls.contains("createDirectories $libDir"))
        assertTrue(FileSystemUtils.calls.contains("writeFile ${FileSystemUtils.joinPath(workspacePath, "lib/utils.kt")} (content: fun util() = true...)"))
        assertTrue(FileSystemUtils.calls.contains("createDirectories $dataDir"))
        assertTrue(FileSystemUtils.calls.contains("writeFile ${FileSystemUtils.joinPath(workspacePath, "data/config.json")} (content: {\"key\":\"value\"}...)"))
    }

    @Test
    fun getCurrentSnapshotReadsFilesFromWorkspace() = runTest {
        val workspacePath = "/testroot/ws_snapshot_test"
        val fileAPath = "fileA.txt"
        val fileBPath = "subdir/fileB.txt"
        val absFileAPath = FileSystemUtils.joinPath(workspacePath, fileAPath)
        val absFileBPath = FileSystemUtils.joinPath(workspacePath, fileBPath)

        // Mock that these files will be listed
        FileSystemUtils.mockListFiles(workspacePath, listOf(absFileAPath, absFileBPath))

        // Mock content for these files
        FileSystemUtils.fileContents[absFileAPath] = "Content of A"
        FileSystemUtils.fileContents[absFileBPath] = "Content of B"
        FileSystemUtils.filesToExist.add(absFileAPath) // Mark as existing for fileExists check
        FileSystemUtils.filesToExist.add(absFileBPath)


        val snapshot = workspaceService.getCurrentSnapshot(workspacePath, testContext)

        assertTrue(FileSystemUtils.calls.contains("listFilesRecursively $workspacePath"))
        assertTrue(FileSystemUtils.calls.contains("fileExists $absFileAPath"))
        assertTrue(FileSystemUtils.calls.contains("readFile $absFileAPath"))
        assertTrue(FileSystemUtils.calls.contains("fileExists $absFileBPath"))
        assertTrue(FileSystemUtils.calls.contains("readFile $absFileBPath"))

        assertEquals(2, snapshot.size)
        val snapshotMap = snapshot.associate { it.a to it.b }
        assertEquals("Content of A", snapshotMap[fileAPath])
        assertEquals("Content of B", snapshotMap[fileBPath])
    }

    @Test
    fun getCurrentSnapshotHandlesEmptyDirectory() = runTest {
        val workspacePath = "/testroot/ws_empty_snapshot_test"
        FileSystemUtils.mockListFiles(workspacePath, emptyList())

        val snapshot = workspaceService.getCurrentSnapshot(workspacePath, testContext)
        assertTrue(snapshot.isEmpty())
        assertTrue(FileSystemUtils.calls.contains("listFilesRecursively $workspacePath"))
    }


    @Test
    fun cleanupWorkspaceDeletesDirectory() = runTest {
        val workspacePath = "/testroot/ws_cleanup_test"
        workspaceService.cleanupWorkspace(workspacePath, testContext)
        assertTrue(FileSystemUtils.calls.contains("deleteDirectoryRecursively $workspacePath"))
    }
}
