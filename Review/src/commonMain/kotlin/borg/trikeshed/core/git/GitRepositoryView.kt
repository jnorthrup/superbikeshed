package borg.trikeshed.core.git

import borg.trikeshed.core.git.internal.GitObjectType as RawGitObjectType
import borg.trikeshed.core.git.internal.RawGitBlob
import borg.trikeshed.core.git.internal.RawGitCommit
import borg.trikeshed.core.git.internal.RawGitTag
import borg.trikeshed.core.git.internal.RawGitTree
import borg.trikeshed.core.git.internal.parseGitBlob
import borg.trikeshed.core.git.internal.parseGitCommit
import borg.trikeshed.core.git.internal.parseGitObjectHeader // Corrected from parseGitHeader
import borg.trikeshed.core.git.internal.parseGitTag
import borg.trikeshed.core.git.internal.parseGitTree
import borg.trikeshed.core.git.internal.readLooseObjectRaw

// Platform functions expected to be in borg.trikeshed.core.git.internal
// These are now defined in GitObjectParser.kt (common) and PlatformSpecific.kt (jvm)
import borg.trikeshed.core.git.internal.readPlatformTextFile
import borg.trikeshed.core.git.internal.listPlatformDirectory
import borg.trikeshed.core.git.internal.platformIsFile
import borg.trikeshed.core.git.internal.platformIsDirectory
import borg.trikeshed.core.git.internal.platformJoinPath

// Assuming Series and its `▶` operator are available from borg.trikeshed.core
import borg.trikeshed.core.Series
import borg.trikeshed.core.`▶` // Import the extension property


class GitRepositoryView(private val dotGitPath: String) {

    // In-memory caches for the TrikeShed-idiomatic Git objects
    private val tsCommits = mutableMapOf<GitSha1, TsGitCommit>()
    private val tsTrees = mutableMapOf<GitSha1, TsGitTree>()
    private val tsBlobs = mutableMapOf<GitSha1, TsGitBlob>()
    private val tsTags = mutableMapOf<GitSha1, TsGitTag>() // For annotated tags

    // Set to keep track of processed SHAs to avoid redundant work and cycles
    private val processedShas = mutableSetOf<GitSha1>()
    // Queue for SHAs to be processed
    private val processingQueue = ArrayDeque<GitSha1>()

    // Public accessors for the indexed objects (immutable views)
    fun getCommit(sha1: GitSha1): TsGitCommit? = tsCommits[sha1]
    fun getTree(sha1: GitSha1): TsGitTree? = tsTrees[sha1]
    fun getBlob(sha1: GitSha1): TsGitBlob? = tsBlobs[sha1]
    fun getTag(sha1: GitSha1): TsGitTag? = tsTags[sha1] // For annotated tags

    fun indexRepository() {
        println("Starting repository indexing for: $dotGitPath")
        tsCommits.clear()
        tsTrees.clear()
        tsBlobs.clear()
        tsTags.clear()
        processedShas.clear()
        processingQueue.clear()

        discoverReferences()

        while (processingQueue.isNotEmpty()) {
            val sha1ToProcess = processingQueue.removeFirst() // Using removeFirst for FIFO queue behavior
            if (sha1ToProcess in processedShas) {
                continue
            }
            processObject(sha1ToProcess)
            // Note: processObject might add more items to the queue.
            // Mark as processed only after successful processing or terminal failure for that object.
            // For simplicity now, marking it here. If processObject fails and we want to retry, this needs adjustment.
            processedShas.add(sha1ToProcess)
        }
        println("Repository indexing complete.")
        println("Indexed ${tsCommits.size} commits, ${tsTrees.size} trees, ${tsBlobs.size} blobs, ${tsTags.size} annotated tags.")
    }

    private fun objectPathFromSha1(sha1: GitSha1): String {
        if (sha1.length < 2) throw IllegalArgumentException("SHA1 string too short: $sha1")
        val dir = sha1.substring(0, 2)
        val file = sha1.substring(2)
        // Corrected usage of platformJoinPath for multiple segments
        return platformJoinPath(dotGitPath, "objects", dir, file)
    }

    private fun processObject(sha1: GitSha1) {
        val objectFilePath = objectPathFromSha1(sha1)
        val rawData = readLooseObjectRaw(objectFilePath) // From Phase 1 GitObjectParser
        if (rawData == null) {
            println("Failed to read object: $sha1 at $objectFilePath")
            return
        }

        val header = parseGitObjectHeader(rawData) // From Phase 1
        if (header == null) {
            println("Failed to parse header for object: $sha1")
            return
        }

        try {
            when (header.type) {
                RawGitObjectType.BLOB -> {
                    val rawBlob = parseGitBlob(rawData)
                    rawBlob?.toTsGitBlob()?.let { tsBlob ->
                        tsBlobs[sha1] = tsBlob
                        // Blobs don't typically point to other Git objects to queue
                    } ?: println("Failed to parse or transform blob: $sha1")
                }
                RawGitObjectType.TREE -> {
                    val rawTree = parseGitTree(rawData)
                    rawTree?.toTsGitTree()?.let { tsTree ->
                        tsTrees[sha1] = tsTree
                        tsTree.entries.`▶`.forEach { entry ->
                            if (entry.sha1 !in processedShas && entry.sha1 !in processingQueue) { // Avoid re-queueing
                                processingQueue.addLast(entry.sha1)
                            }
                        }
                    } ?: println("Failed to parse or transform tree: $sha1")
                }
                RawGitObjectType.COMMIT -> {
                    val rawCommit = parseGitCommit(rawData)
                    rawCommit?.toTsGitCommit()?.let { tsCommit ->
                        tsCommits[sha1] = tsCommit
                        if (tsCommit.treeSha1 !in processedShas && tsCommit.treeSha1 !in processingQueue) {
                            processingQueue.addLast(tsCommit.treeSha1)
                        }
                        tsCommit.parentSha1s.`▶`.forEach { parentSha1 ->
                            if (parentSha1 !in processedShas && parentSha1 !in processingQueue) {
                                processingQueue.addLast(parentSha1)
                            }
                        }
                    } ?: println("Failed to parse or transform commit: $sha1")
                }
                RawGitObjectType.TAG -> { // Annotated Tag
                    val rawTag = parseGitTag(rawData)
                    rawTag?.toTsGitTag()?.let { tsTag ->
                        tsTags[sha1] = tsTag
                        if (tsTag.objectSha1 !in processedShas && tsTag.objectSha1 !in processingQueue) {
                            processingQueue.addLast(tsTag.objectSha1)
                        }
                    } ?: println("Failed to parse or transform tag: $sha1")
                }
                RawGitObjectType.UNKNOWN -> {
                    println("Cannot process UNKNOWN object type for SHA: $sha1 with header type ${header.type}")
                }
            }
        } catch (e: Exception) {
            println("Exception during processing or transformation of object $sha1: ${e.message}")
            // e.printStackTrace() // For detailed debugging
        }
    }

    private fun discoverReferences() {
        // 1. HEAD
        val headFilePath = platformJoinPath(dotGitPath, "HEAD")
        val headContent = readPlatformTextFile(headFilePath)
        if (headContent != null) {
            if (headContent.startsWith("ref: ")) {
                val refPath = headContent.substring("ref: ".length).trim()
                val resolvedSha1 = resolveSymbolicRef(refPath)
                if (resolvedSha1 != null) {
                     println("HEAD points to $refPath -> $resolvedSha1")
                     if (resolvedSha1 !in processedShas && resolvedSha1 !in processingQueue) processingQueue.addLast(resolvedSha1)
                } else {
                    println("Could not resolve symbolic HEAD ref: $refPath")
                }
            } else if (headContent.matches(Regex("[0-9a-fA-F]{40}"))) { // Detached HEAD
                println("HEAD is detached, points to commit: $headContent")
                val sha1 = headContent.trim()
                if (sha1 !in processedShas && sha1 !in processingQueue) processingQueue.addLast(sha1)
            } else {
                println("Malformed HEAD content: $headContent")
            }
        } else {
            println("Warning: Could not read HEAD file at $headFilePath. Repository might be empty or invalid.")
        }

        // 2. Branch Heads (.git/refs/heads/)
        val headsPath = platformJoinPath(dotGitPath, "refs", "heads")
        recursivelyDiscoverRefsInPath(headsPath, "refs/heads/")

        // 3. Tags (.git/refs/tags/) - both lightweight and annotated tags
        val tagsPath = platformJoinPath(dotGitPath, "refs", "tags")
        recursivelyDiscoverRefsInPath(tagsPath, "refs/tags/")

        // TODO: Handle .git/packed-refs file
        // This file can contain many refs and their SHA1s, especially after `git gc`.
        // Format: # pack-refs with: <attributes> \n <SHA1> <ref_name> \n ...
        // Also, peeled tags might have a line like: ^<commit_SHA1> for an annotated tag.
        // For now, skipping packed-refs.
    }

    private fun recursivelyDiscoverRefsInPath(currentPath: String, logicalBasePath: String) {
        val items = listPlatformDirectory(currentPath)
        if (items == null) {
            // println("Cannot list directory or directory does not exist: $currentPath") // Can be noisy
            return
        }

        items.forEach { itemName ->
            val itemFullPath = platformJoinPath(currentPath, itemName)
            val logicalFullName = if (logicalBasePath.endsWith("/")) "$logicalBasePath$itemName" else "$logicalBasePath/$itemName"

            if (platformIsDirectory(itemFullPath)) {
                recursivelyDiscoverRefsInPath(itemFullPath, "$logicalFullName/")
            } else if (platformIsFile(itemFullPath)) {
                val sha1 = readPlatformTextFile(itemFullPath)?.trim()
                if (sha1 != null && sha1.matches(Regex("[0-9a-fA-F]{40}"))) {
                    println("Discovered ref: $logicalFullName -> $sha1")
                    if (sha1 !in processedShas && sha1 !in processingQueue) processingQueue.addLast(sha1)
                } else {
                     println("Warning: Could not read or invalid SHA1 in ref file: $itemFullPath (content: '$sha1')")
                }
            }
        }
    }

    private fun resolveSymbolicRef(symbolicRefPath: String, depth: Int = 0): GitSha1? {
        if (depth > 10) { // Max recursion depth to prevent infinite loops from malformed refs
            println("Error: Exceeded max recursion depth resolving symbolic ref: $symbolicRefPath")
            return null
        }
        val filePath = platformJoinPath(dotGitPath, symbolicRefPath)
        val content = readPlatformTextFile(filePath)?.trim()

        if (content != null) {
            if (content.startsWith("ref: ")) {
                return resolveSymbolicRef(content.substring("ref: ".length).trim(), depth + 1)
            } else if (content.matches(Regex("[0-9a-fA-F]{40}"))) {
                return content
            }
        }
        println("Warning: Could not read or resolve symbolic ref: $symbolicRefPath at $filePath (content: '$content')")
        return null
    }
}

// Note: The `▶` operator for Series needs to be accessible.
// It's typically defined in TrikeShedCore as an extension:
// internal inline val <T> Series<T>.`▶`: IterableSeries<T> get() = IterableSeries(this)
// Ensure borg.trikeshed.core.`▶` is imported or accessible.
