package borg.trikeshed.core

import kotlin.test.*
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import java.io.File
import java.nio.file.Files
import java.nio.file.Path // Keep this import for Files.createTempDirectory

class GitRepoIndexerTest {

    private lateinit var tempRepoDir: File
    private lateinit var git: Git
    private lateinit var headCommit: RevCommit
    private var repoInitializedSuccessfully = false // Flag to track successful setup

    @BeforeTest
    fun setup() {
        try {
            // Create a temporary directory for the Git repository
            tempRepoDir = Files.createTempDirectory("jgit_test_repo_").toFile()

            // Initialize a new Git repository
            git = Git.init().setDirectory(tempRepoDir).call()
            val repository = git.repository

            // Create and commit file1.txt
            val file1 = File(tempRepoDir, "file1.txt")
            file1.writeText("Hello from file1")
            git.add().addFilepattern("file1.txt").call()
            val firstCommit = git.commit().setMessage("Initial commit with file1.txt").setAuthor("Test Author", "test@example.com").call()

            // Create and commit file2.txt
            val file2 = File(tempRepoDir, "file2.txt")
            file2.writeText("Data in file2")
            git.add().addFilepattern("file2.txt").call()
            headCommit = git.commit().setMessage("Add file2.txt").setAuthor("Test Author", "test@example.com").call()

            repoInitializedSuccessfully = true
            println("Temporary test repository created at: ${tempRepoDir.absolutePath} with .git at ${repository.directory.absolutePath}")
            println("HEAD commit: ${headCommit.name}")
        } catch (e: Exception) {
            repoInitializedSuccessfully = false
            println("Error during @BeforeTest setup: ${e.message}")
            e.printStackTrace()
            // Re-throw or handle as appropriate for the test framework to know setup failed
            throw e
        }
    }

    @AfterTest
    fun tearDown() {
        if (::git.isInitialized) { // Check if git object was initialized
             git.close() // Close the Git object
        }
        if (::tempRepoDir.isInitialized && tempRepoDir.exists()) { // Check if tempRepoDir was initialized
            // Recursively delete the temporary directory and its contents
            val deleted = tempRepoDir.deleteRecursively()
            if (deleted) {
                println("Temporary test repository deleted from: ${tempRepoDir.absolutePath}")
            } else {
                println("ERROR: Failed to delete temporary test repository from: ${tempRepoDir.absolutePath}")
            }
        }
    }

    private fun ensureRepoInitialized() {
        if (!repoInitializedSuccessfully || !::git.isInitialized || !::headCommit.isInitialized) {
            fail("Test repository was not initialized successfully. Check @BeforeTest logs.")
        }
    }

    @Test
    fun testIndexSimpleRepository() {
        ensureRepoInitialized()
        val indexer = GitRepoIndexer()
        val gitDirPath = git.repository.directory.absolutePath // Path to .git directory

        assertNotNull(gitDirPath, "Test repository .git directory should exist")

        println("Indexing repository: $gitDirPath")
        indexer.indexRepo(gitDirPath) // Pass the .git directory path

        assertTrue(indexer.getAllObjects().isNotEmpty(), "Indexed objects map should not be empty")

        val commitCount = indexer.getAllObjects().values.count { it.type == "commit" }
        assertEquals(2, commitCount, "Should have indexed 2 commits. Found $commitCount: ${indexer.getAllObjects().values.filter{it.type == "commit"}.map{it.hash}}")

        // Check for the HEAD commit
        val indexedHeadCommit = indexer.getCommit(headCommit.name)
        assertNotNull(indexedHeadCommit, "HEAD commit (${headCommit.name}) should be found in the index")
        assertEquals("Add file2.txt", indexedHeadCommit.message.trim(), "HEAD commit message mismatch")
        assertTrue(indexedHeadCommit.parentHashes.isNotEmpty(), "HEAD commit should have a parent")

        // Check for the root commit (parent of HEAD)
        val rootCommitHash = indexedHeadCommit.parentHashes.first()
        val indexedRootCommit = indexer.getCommit(rootCommitHash)
        assertNotNull(indexedRootCommit, "Root commit ($rootCommitHash) should be found")
        assertEquals("Initial commit with file1.txt", indexedRootCommit.message.trim(), "Root commit message mismatch")
        assertTrue(indexedRootCommit.parentHashes.isEmpty(), "Root commit should have no parents")

        println("Total objects indexed: ${indexer.getAllObjects().size}")
        println("Total commits indexed: $commitCount")
        println("Total trees indexed: ${indexer.getAllObjects().values.count { it.type == "tree" }}")
        println("Total blobs indexed: ${indexer.getAllObjects().values.count { it.type == "blob" }}")

        val rootTreeHash = indexedRootCommit.treeHash
        val rootTree = indexer.getTree(rootTreeHash)
        assertNotNull(rootTree, "Root tree should be indexed")
        val file1Entry = rootTree.entries.find { it.name == "file1.txt" }
        assertNotNull(file1Entry, "file1.txt should be an entry in the root tree")
        assertEquals("blob", file1Entry.type, "file1.txt entry should be a blob")
        assertNotNull(indexer.getBlob(file1Entry.hash), "Blob for file1.txt should be indexed")
    }

    @Test
    fun testLoadGitIndexFunction_Success() {
        ensureRepoInitialized()
        val gitDirPath = git.repository.directory.absolutePath
        val indexer = loadGitIndex(gitDirPath) // Test the function from TrikeShedCore.kt
        assertNotNull(indexer, "loadGitIndex should return a non-null indexer for a valid repo")
        assertTrue(indexer.getAllObjects().isNotEmpty(), "Indexer from loadGitIndex should have objects")
        val indexedHeadCommit = indexer.getCommit(headCommit.name)
        assertNotNull(indexedHeadCommit, "HEAD commit should be found via loadGitIndex")
    }

    @Test
    fun testLoadGitIndexFunction_NonExistentRepo() {
        // No need for ensureRepoInitialized() here as we are testing a failure case independent of the setup
        val nonExistentPath = File(tempRepoDir, "non_existent_repo_${System.currentTimeMillis()}").absolutePath // Ensure unique name
        val indexer = loadGitIndex(nonExistentPath)
        assertNull(indexer, "loadGitIndex should return null for a non-existent repo path. Got: $indexer and its objects: ${indexer?.getAllObjects()?.keys}")
    }

    @Test
    fun testIndexEmptyRepository() {
        // No need for ensureRepoInitialized() from the main setup
        val emptyRepoDirLocal = Files.createTempDirectory("jgit_empty_repo_").toFile()
        var emptyGitLocal: Git? = null
        try {
            emptyGitLocal = Git.init().setDirectory(emptyRepoDirLocal).call()
            val emptyGitPath = emptyGitLocal.repository.directory.absolutePath

            val indexer = GitRepoIndexer()
            println("Indexing empty repository at: $emptyGitPath")
            indexer.indexRepo(emptyGitPath)

            // An empty repository after init still might have an initial empty commit object or specific refs.
            // JGit's `git init` creates a .git directory but no commits.
            // Our indexer walks from refs. An empty repo has a HEAD ref pointing to 'refs/heads/master' but master doesn't exist yet.
            // So, no commits should be found.
            if (indexer.getAllObjects().isNotEmpty()) {
                 println("Objects found in empty repo index: ${indexer.getAllObjects()}")
            }
            assertTrue(indexer.getAllObjects().isEmpty(), "Index of an empty repo (no commits) should have no objects. Found: ${indexer.getAllObjects().size}")
            assertTrue(indexer.getCommit("HEAD") == null, "No HEAD commit object in empty repo")
            assertEquals(0, indexer.getAllObjects().values.count { it.type == "commit" }, "Should be 0 commits")
            assertEquals(0, indexer.getAllObjects().values.count { it.type == "tree" }, "Should be 0 trees")
            assertEquals(0, indexer.getAllObjects().values.count { it.type == "blob" }, "Should be 0 blobs")

        } finally {
            emptyGitLocal?.close()
            emptyRepoDirLocal.deleteRecursively()
            println("Cleaned up empty test repository: ${emptyRepoDirLocal.absolutePath}")
        }
    }
}
