package borg.trikeshed.git

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant

/**
 * Git Repository Manager
 */
class GitRepositoryManager(
    private val baseDir: File
) {
    private val objectStore = FileGitObjectStore(File(baseDir, "objects"))
    private val refsDir = File(baseDir, "refs")
    private val configFile = File(baseDir, "config")
    
    init {
        baseDir.mkdirs()
        refsDir.mkdirs()
    }
    
    /**
     * Initialize a new Git repository
     */
    suspend fun init(bare: Boolean = false): GitRepository {
        val config = GitConfig(
            core = CoreConfig(
                repositoryFormatVersion = 0,
                fileMode = true,
                bare = bare,
                logAllRefUpdates = true,
                symlinks = true,
                ignorecase = false
            )
        )
        
        saveConfig(config)
        
        return GitRepository(
            path = baseDir.absolutePath,
            config = config,
            refs = emptyMap(),
            objects = emptyMap()
        )
    }
    
    /**
     * Open an existing Git repository
     */
    suspend fun open(): GitRepository? {
        if (!isGitRepository()) return null
        
        val config = loadConfig() ?: return null
        val refs = loadRefs()
        val objects = loadObjects()
        
        return GitRepository(
            path = baseDir.absolutePath,
            config = config,
            refs = refs,
            objects = objects
        )
    }
    
    /**
     * Check if a directory is a Git repository
     */
    fun isGitRepository(): Boolean {
        return baseDir.exists() &&
               File(baseDir, "objects").exists() &&
               File(baseDir, "refs").exists() &&
               configFile.exists()
    }
    
    /**
     * Create a new commit
     */
    suspend fun createCommit(
        tree: String,
        parents: List<String>,
        author: GitSignature,
        committer: GitSignature,
        message: String
    ): String {
        val commit = GitCommit(
            hash = "",
            size = 0,
            tree = tree,
            parents = parents,
            author = author,
            committer = committer,
            message = message,
            content = ByteArray(0) // Will be set by object store
        )
        
        return objectStore.store(commit)
    }
    
    /**
     * Create a new tree
     */
    suspend fun createTree(entries: List<GitTreeEntry>): String {
        val tree = GitTree(
            hash = "",
            size = 0,
            entries = entries,
            content = ByteArray(0) // Will be set by object store
        )
        
        return objectStore.store(tree)
    }
    
    /**
     * Create a new blob
     */
    suspend fun createBlob(content: ByteArray): String {
        val blob = GitBlob(
            hash = "",
            size = content.size,
            content = content
        )
        
        return objectStore.store(blob)
    }
    
    /**
     * Create a new tag
     */
    suspend fun createTag(
        objectHash: String,
        objectType: GitObjectType,
        tag: String,
        tagger: GitSignature,
        message: String
    ): String {
        val tagObject = GitTag(
            hash = "",
            size = 0,
            objectHash = objectHash,
            objectType = objectType,
            tag = tag,
            tagger = tagger,
            message = message,
            content = ByteArray(0) // Will be set by object store
        )
        
        return objectStore.store(tagObject)
    }
    
    /**
     * Update a reference
     */
    suspend fun updateRef(name: String, target: String, isSymbolic: Boolean = false) {
        val refFile = getRefFile(name)
        refFile.parentFile.mkdirs()
        refFile.writeText(target)
    }
    
    /**
     * Delete a reference
     */
    suspend fun deleteRef(name: String): Boolean {
        val refFile = getRefFile(name)
        return if (refFile.exists()) {
            refFile.delete()
        } else {
            false
        }
    }
    
    /**
     * Get a reference
     */
    suspend fun getRef(name: String): GitReference? {
        val refFile = getRefFile(name)
        if (!refFile.exists()) return null
        
        val target = refFile.readText().trim()
        return GitReference(name, target)
    }
    
    /**
     * List all references
     */
    fun listRefs(): Flow<GitReference> = flow {
        refsDir.walk()
            .filter { it.isFile }
            .forEach { file ->
                val name = file.relativeTo(refsDir).path
                val target = file.readText().trim()
                emit(GitReference(name, target))
            }
    }
    
    private fun getRefFile(name: String): File {
        return File(refsDir, name)
    }
    
    private suspend fun saveConfig(config: GitConfig) {
        val configContent = buildString {
            appendLine("[core]")
            appendLine("repositoryformatversion = ${config.core.repositoryFormatVersion}")
            appendLine("filemode = ${config.core.fileMode}")
            appendLine("bare = ${config.core.bare}")
            appendLine("logallrefupdates = ${config.core.logAllRefUpdates}")
            appendLine("symlinks = ${config.core.symlinks}")
            appendLine("ignorecase = ${config.core.ignorecase}")
            
            config.remote.forEach { (name, remote) ->
                appendLine()
                appendLine("[remote \"$name\"]")
                appendLine("url = ${remote.url}")
                remote.fetch.forEach { ref ->
                    appendLine("fetch = $ref")
                }
                remote.pushurl?.let { url ->
                    appendLine("pushurl = $url")
                }
                remote.push.forEach { ref ->
                    appendLine("push = $ref")
                }
            }
            
            config.branch.forEach { (name, branch) ->
                appendLine()
                appendLine("[branch \"$name\"]")
                branch.remote?.let { remote ->
                    appendLine("remote = $remote")
                }
                branch.merge?.let { merge ->
                    appendLine("merge = $merge")
                }
                appendLine("rebase = ${branch.rebase}")
            }
        }
        
        configFile.writeText(configContent)
    }
    
    private suspend fun loadConfig(): GitConfig? {
        if (!configFile.exists()) return null
        
        val configContent = configFile.readText()
        val lines = configContent.lines()
        
        var currentSection = ""
        val coreConfig = mutableMapOf<String, String>()
        val remoteConfigs = mutableMapOf<String, MutableMap<String, String>>()
        val branchConfigs = mutableMapOf<String, MutableMap<String, String>>()
        
        for (line in lines) {
            when {
                line.startsWith("[") && line.endsWith("]") -> {
                    currentSection = line.substring(1, line.length - 1)
                }
                line.contains("=") -> {
                    val (key, value) = line.split("=", limit = 2).map { it.trim() }
                    when {
                        currentSection == "core" -> coreConfig[key] = value
                        currentSection.startsWith("remote \"") -> {
                            val name = currentSection.substring(8, currentSection.length - 1)
                            remoteConfigs.getOrPut(name) { mutableMapOf() }[key] = value
                        }
                        currentSection.startsWith("branch \"") -> {
                            val name = currentSection.substring(8, currentSection.length - 1)
                            branchConfigs.getOrPut(name) { mutableMapOf() }[key] = value
                        }
                    }
                }
            }
        }
        
        return GitConfig(
            core = CoreConfig(
                repositoryFormatVersion = coreConfig["repositoryformatversion"]?.toIntOrNull() ?: 0,
                fileMode = coreConfig["filemode"]?.toBoolean() ?: true,
                bare = coreConfig["bare"]?.toBoolean() ?: false,
                logAllRefUpdates = coreConfig["logallrefupdates"]?.toBoolean() ?: true,
                symlinks = coreConfig["symlinks"]?.toBoolean() ?: true,
                ignorecase = coreConfig["ignorecase"]?.toBoolean() ?: false
            ),
            remote = remoteConfigs.mapValues { (_, config) ->
                RemoteConfig(
                    url = config["url"] ?: "",
                    fetch = config.filterKeys { it == "fetch" }.values.toList(),
                    pushurl = config["pushurl"],
                    push = config.filterKeys { it == "push" }.values.toList()
                )
            },
            branch = branchConfigs.mapValues { (_, config) ->
                BranchConfig(
                    remote = config["remote"],
                    merge = config["merge"],
                    rebase = config["rebase"]?.toBoolean() ?: false
                )
            }
        )
    }
    
    private suspend fun loadRefs(): Map<String, GitReference> {
        val refs = mutableMapOf<String, GitReference>()
        listRefs().collect { ref ->
            refs[ref.name] = ref
        }
        return refs
    }
    
    private suspend fun loadObjects(): Map<String, GitObject> {
        val objects = mutableMapOf<String, GitObject>()
        objectStore.listObjects().collect { object ->
            objects[object.hash] = object
        }
        return objects
    }
} 