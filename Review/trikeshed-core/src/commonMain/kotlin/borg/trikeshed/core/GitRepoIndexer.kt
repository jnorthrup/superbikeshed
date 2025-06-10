package borg.trikeshed.core

import borg.trikeshed.core.Series
import borg.trikeshed.core.j
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File
import org.eclipse.jgit.api.Git // Keep Git import if used, but primary logic is with lower-level objects
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.errors.RepositoryNotFoundException // Keep this for specific handling if needed elsewhere, though findGitDir changes approach
// Potentially remove unused imports like PathFilter if not used.

// --- Data classes for the index ---
data class GitObjectInfo(val hash: String, val type: String, val size: Long)

data class IndexedCommit(
    val commitInfo: GitObjectInfo,
    val treeHash: String,
    val parentHashes: Series<String>,
    val author: String, // Consider PersonIdent for richer info if needed later
    val committer: String, // Consider PersonIdent for richer info
    val message: String
)

data class IndexedTree(
    val treeInfo: GitObjectInfo,
    val entries: Series<TreeEntryInfo>
)

data class TreeEntryInfo( // Information about an entry within a tree
    val name: String,
    val hash: String, // Hash of the object this entry points to
    val type: String, // "blob" or "tree"
    val mode: String // FileMode as a string
)

data class IndexedBlob(
    val blobInfo: GitObjectInfo
    // val content: ByteArray? = null // Content not stored by default to save memory
) {
    // Optional: override equals and hashCode if content were included and mutable
}

class GitRepoIndexer {

    private val objects = mutableMapOf<String, GitObjectInfo>()
    private val commits = mutableMapOf<String, IndexedCommit>()
    private val trees = mutableMapOf<String, IndexedTree>()
    private val blobs = mutableMapOf<String, IndexedBlob>()

    // Public accessor methods for the index data (optional, based on how TrikeShedCore will use it)
    fun getAllObjects(): Map<String, GitObjectInfo> = objects.toMap()
    fun getCommit(hash: String): IndexedCommit? = commits[hash]
    fun getTree(hash: String): IndexedTree? = trees[hash]
    fun getBlob(hash: String): IndexedBlob? = blobs[hash]


    private fun findGitDir(startingPath: String): File? {
        var currentDir = File(startingPath).absoluteFile
        // Check if the starting path itself is a .git directory
        if (currentDir.isDirectory && currentDir.name == ".git") {
            return currentDir
        }
        // Check for .git subdirectory in the starting path
        val gitDirInCurrent = File(currentDir, ".git")
        if (gitDirInCurrent.isDirectory) {
            return gitDirInCurrent
        }
        // Traverse upwards from the starting path's parent
        // This part might be problematic if startingPath is already root or invalid
        currentDir = currentDir.parentFile
        while (currentDir != null) {
            val potentialGitDir = File(currentDir, ".git")
            if (potentialGitDir.isDirectory) {
                return potentialGitDir
            }
            currentDir = currentDir.parentFile
        }
        return null // No .git directory found
    }

    fun indexRepo(repoPath: String) {
        val gitDir = findGitDir(repoPath)
        if (gitDir == null) {
            println("Error: Could not find .git directory for path: $repoPath")
            return
        }

        println("Using .git directory: ${gitDir.absolutePath}")

        try {
            FileRepositoryBuilder().setGitDir(gitDir).readEnvironment().findGitDir().build().use { repository ->
                println("Successfully opened repository: ${repository.directory}")

                // Clear previous index data
                objects.clear()
                commits.clear()
                trees.clear()
                blobs.clear()

                repository.newObjectReader().use { reader ->
                    RevWalk(reader).use { revWalk ->
                        // Index all reachable commits from all local references (branches, tags)
                        val allRefs = repository.refDatabase.refs
                        var hasCommitsToProcess = false
                        for (ref in allRefs) {
                            if (ref.objectId == null) continue
                            try {
                                val peeled = revWalk.peel(revWalk.parseAny(ref.objectId))
                                if (peeled is RevCommit) {
                                    revWalk.markStart(peeled)
                                    hasCommitsToProcess = true
                                } else if (peeled is TaggedObject && peeled.objectType == Constants.OBJ_COMMIT){
                                     // If it's a tag object pointing to a commit, parse that commit
                                     val commitId = peeled.id // This should be the commit id
                                     revWalk.markStart(revWalk.parseCommit(commitId))
                                     hasCommitsToProcess = true
                                } else if (ref.isPeeled && ref.peeledObjectId != null && revWalk.lookupCommit(ref.peeledObjectId) != null) {
                                    // For tags that are already peeled and point to a commit
                                    revWalk.markStart(revWalk.parseCommit(ref.peeledObjectId))
                                    hasCommitsToProcess = true
                                }
                                // else {
                                //    println("Ref ${ref.name} (peeled: ${peeled.type}, ${peeled.id.name()}) does not point to a commit.")
                                // }
                            } catch (e: Exception) {
                                 println("Could not parse or peel ref ${ref.name} (ObjectId: ${ref.objectId.name()}): ${e.message}. Skipping this ref.")
                            }
                        }

                        if (!hasCommitsToProcess) {
                             println("No commits found to index after checking all references. The repository might be empty or have no local branches/tags pointing to commits.")
                             return@use // a bit before original: return@use
                        }

                        for (commit in revWalk) { // This iterator will now walk from all marked starting points
                            indexCommit(repository, reader, revWalk, commit)
                        }
                    }
                }

                println("--- Indexing Complete ---")
                println("Indexed ${commits.size} commits.")
                println("Indexed ${trees.size} trees.")
                println("Indexed ${blobs.size} blobs.")
                println("Total ${objects.size} unique Git objects indexed.")
            }
        } catch (e: RepositoryNotFoundException) {
            println("Error: Repository not found at ${gitDir.absolutePath}. Please ensure this is a valid .git directory.")
            // e.printStackTrace()
        }
        catch (e: Exception) {
            println("Error during repository indexing (${gitDir.absolutePath}): ${e.message}")
            e.printStackTrace() // For more detailed debugging during development
        }
    }

    private fun indexCommit(repository: Repository, reader: ObjectReader, revWalk: RevWalk, commit: RevCommit) {
        val commitHash = commit.id.name
        if (commits.containsKey(commitHash)) {
            return // Already processed
        }

        val commitSize = reader.getObjectSize(commit.id, Constants.OBJ_COMMIT)
        val objInfo = GitObjectInfo(commitHash, Constants.typeString(commit.type), commitSize)
        objects[commitHash] = objInfo

        val indexedCommit = IndexedCommit(
            commitInfo = objInfo,
            treeHash = commit.tree.name, // commit.getTree().getId().name()
            parentHashes = commit.parents.size j { i -> commit.parents[i].id.name },
            author = commit.authorIdent.toExternalString(),
            committer = commit.committerIdent.toExternalString(),
            message = commit.fullMessage
        )
        commits[commitHash] = indexedCommit

        // Process the tree associated with this commit
        // commit.tree is already a RevTree if parsed with RevWalk
        indexTree(repository, reader, revWalk, commit.tree.id) // Pass ObjectId of the tree
    }

    private fun indexTree(repository: Repository, reader: ObjectReader, revWalk: RevWalk, treeId: ObjectId) {
        val treeHash = treeId.name
        if (trees.containsKey(treeHash)) {
            return // Already processed
        }

        // It's generally better to parse the tree using the revWalk if it might already have it,
        // or if you need it as a RevTree object for further rev operations.
        // However, for direct reading of entries, ObjectReader + TreeWalk is fine.
        // val revTree = revWalk.parseTree(treeId) // This ensures it's a RevTree

        val treeSize = reader.getObjectSize(treeId, Constants.OBJ_TREE) // Use reader for size
        val objInfo = GitObjectInfo(treeHash, Constants.typeString(Constants.OBJ_TREE), treeSize)
        objects[treeHash] = objInfo

        // Collect entries using TrikeShed functional approach
        val entriesData = mutableListOf<TreeEntryInfo>()
        val subObjectIds = mutableListOf<Pair<ObjectId, Int>>() // Store for processing after entries collection

        TreeWalk(reader).use { treeWalk -> // Use the passed ObjectReader
            treeWalk.addTree(treeId)
            treeWalk.isRecursive = false // We handle recursion manually to store each tree object

            while (treeWalk.next()) {
                val entryObjectId = treeWalk.getObjectId(0)
                val entryName = treeWalk.nameString
                val entryFileMode = treeWalk.fileMode
                // Determine entry type ("blob" or "tree") from FileMode
                val entryTypeString = when (entryFileMode.objectType) {
                    Constants.OBJ_BLOB -> "blob"
                    Constants.OBJ_TREE -> "tree"
                    Constants.OBJ_COMMIT -> "commit" // For submodules
                    else -> "unknown"
                }

                entriesData.add(TreeEntryInfo(entryName, entryObjectId.name, entryTypeString, entryFileMode.bits.toString(8)))
                subObjectIds.add(entryObjectId to entryFileMode.objectType)
            }
        }

        // Create Series from collected data using TrikeShed patterns
        val entriesSeries = entriesData.size j { i -> entriesData[i] }
        val currentIndexedTree = IndexedTree(objInfo, entriesSeries)
        trees[treeHash] = currentIndexedTree

        // Process sub-objects after tree creation
        for ((entryObjectId, objectType) in subObjectIds) {
            when (objectType) {
                Constants.OBJ_BLOB -> indexBlob(repository, reader, revWalk, entryObjectId)
                Constants.OBJ_TREE -> {
                    // Recursive call for sub-trees
                    indexTree(repository, reader, revWalk, entryObjectId)
                }
                // Constants.OBJ_COMMIT represents a submodule. For now, we just record its TreeEntryInfo.
                // Deeper indexing of submodules could be a future enhancement.
            }
        }
    }

    private fun indexBlob(repository: Repository, reader: ObjectReader, revWalk: RevWalk, blobId: ObjectId) {
        val blobHash = blobId.name
        if (blobs.containsKey(blobHash)) {
            return // Already processed
        }

        // Use ObjectReader to open the blob and get its size and type
        val objectLoader = reader.open(blobId, Constants.OBJ_BLOB) // Specify expected type for safety
        val blobSize = objectLoader.size
        val objInfo = GitObjectInfo(blobHash, Constants.typeString(objectLoader.type), blobSize)

        objects[blobHash] = objInfo
        blobs[blobHash] = IndexedBlob(objInfo)
        // Blob content (objectLoader.bytes) is not read or stored.
    }
}

// // Comment out or remove the main function if present, as this is part of a larger library
// // fun main() {
// //     val indexer = GitRepoIndexer()
// //     // Create a dummy .git repo for testing
// //     val tempDir = kotlin.io.path.createTempDirectory("testGitRepo").toFile()
// //     val gitDir = File(tempDir, ".git") // JGit refers to .git as the repository
// //     try {
// //         println("Creating dummy repository for testing at: ${tempDir.absolutePath}")
// //         org.eclipse.jgit.api.Git.init().setDirectory(tempDir).setBare(false).call().use { git ->
// //             File(tempDir, "test.txt").writeText("Hello world for indexing test.")
// //             git.add().addFilepattern("test.txt").call()
// //             val firstCommit = git.commit().setMessage("Initial commit for indexing").setAuthor("Test Indexer", "indexer@example.com").call()
// //             println("Dummy repository created. Initial commit: ${firstCommit.name}")

// //             File(tempDir, "another.txt").writeText("Another file.")
// //             git.add().addFilepattern("another.txt").call()
// //             git.commit().setMessage("Second commit").call()

// //             git.tag().setName("v1.0").setObjectId(firstCommit).call() // Create a tag

// //             println("Repository setup complete at: ${gitDir.absolutePath}")
// //         }
// //         // Test indexing the dummy repository by passing the path to the WORKTREE (containing .git)
// //         indexer.indexRepo(tempDir.absolutePath)
// //     } catch (e: Exception) {
// //         println("Error in main test setup: ${e.message}")
// //         e.printStackTrace()
// //     } finally {
// //         // Clean up the dummy repository
// //         // tempDir.deleteRecursively() // Be careful with deleteRecursively
// //         // println("Cleaned up dummy repository: ${tempDir.absolutePath}")
// //     }
// // }
