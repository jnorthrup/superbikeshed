package borg.trikeshed.git

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.security.MessageDigest

/**
 * Interface for Git object storage
 */
interface GitObjectStore {
    /**
     * Store a Git object
     */
    suspend fun store(object: GitObject): String
    
    /**
     * Retrieve a Git object by its hash
     */
    suspend fun retrieve(hash: String): GitObject?
    
    /**
     * Check if an object exists
     */
    suspend fun exists(hash: String): Boolean
    
    /**
     * Delete an object
     */
    suspend fun delete(hash: String): Boolean
    
    /**
     * List all objects
     */
    fun listObjects(): Flow<GitObject>
}

/**
 * File-based implementation of GitObjectStore
 */
class FileGitObjectStore(
    private val objectsDir: File
) : GitObjectStore {
    init {
        objectsDir.mkdirs()
    }
    
    override suspend fun store(object: GitObject): String {
        val hash = calculateHash(object)
        val objectFile = getObjectFile(hash)
        
        if (!objectFile.exists()) {
            objectFile.parentFile.mkdirs()
            objectFile.writeBytes(object.content)
        }
        
        return hash
    }
    
    override suspend fun retrieve(hash: String): GitObject? {
        val objectFile = getObjectFile(hash)
        if (!objectFile.exists()) return null
        
        val content = objectFile.readBytes()
        return parseObject(content)
    }
    
    override suspend fun exists(hash: String): Boolean {
        return getObjectFile(hash).exists()
    }
    
    override suspend fun delete(hash: String): Boolean {
        val objectFile = getObjectFile(hash)
        return if (objectFile.exists()) {
            objectFile.delete()
        } else {
            false
        }
    }
    
    override fun listObjects(): Flow<GitObject> = flow {
        objectsDir.walk()
            .filter { it.isFile }
            .forEach { file ->
                val content = file.readBytes()
                val object = parseObject(content)
                if (object != null) {
                    emit(object)
                }
            }
    }
    
    private fun getObjectFile(hash: String): File {
        val dir = objectsDir.resolve(hash.substring(0, 2))
        return dir.resolve(hash.substring(2))
    }
    
    private fun calculateHash(object: GitObject): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val header = "${object.type.name.lowercase()} ${object.size}\u0000"
        digest.update(header.toByteArray())
        digest.update(object.content)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    
    private fun parseObject(content: ByteArray): GitObject? {
        val headerEnd = content.indexOf(0)
        if (headerEnd == -1) return null
        
        val header = content.copyOfRange(0, headerEnd).toString(Charsets.UTF_8)
        val parts = header.split(" ", limit = 2)
        if (parts.size != 2) return null
        
        val type = parts[0]
        val size = parts[1].toIntOrNull() ?: return null
        val objectContent = content.copyOfRange(headerEnd + 1, content.size)
        
        return when (type) {
            "blob" -> GitBlob("", size, objectContent)
            "tree" -> parseTree(objectContent)
            "commit" -> parseCommit(objectContent)
            "tag" -> parseTag(objectContent)
            else -> null
        }
    }
    
    private fun parseTree(content: ByteArray): GitTree? {
        val entries = mutableListOf<GitTreeEntry>()
        var offset = 0
        
        while (offset < content.size) {
            val modeEnd = content.indexOf(' '.toByte(), offset)
            if (modeEnd == -1) break
            
            val nameEnd = content.indexOf(0.toByte(), modeEnd)
            if (nameEnd == -1) break
            
            val mode = content.copyOfRange(offset, modeEnd).toString(Charsets.UTF_8)
            val name = content.copyOfRange(modeEnd + 1, nameEnd).toString(Charsets.UTF_8)
            val hash = content.copyOfRange(nameEnd + 1, nameEnd + 21)
                .joinToString("") { "%02x".format(it) }
            
            entries.add(GitTreeEntry(mode, name, hash))
            offset = nameEnd + 21
        }
        
        return GitTree("", content.size, entries, content)
    }
    
    private fun parseCommit(content: ByteArray): GitCommit? {
        val contentStr = content.toString(Charsets.UTF_8)
        val lines = contentStr.split("\n")
        
        var tree = ""
        val parents = mutableListOf<String>()
        var author: GitSignature? = null
        var committer: GitSignature? = null
        val message = StringBuilder()
        var inMessage = false
        
        for (line in lines) {
            when {
                line.startsWith("tree ") -> tree = line.substring(5)
                line.startsWith("parent ") -> parents.add(line.substring(7))
                line.startsWith("author ") -> author = parseSignature(line.substring(7))
                line.startsWith("committer ") -> committer = parseSignature(line.substring(10))
                line.isEmpty() -> inMessage = true
                inMessage -> message.append(line).append("\n")
            }
        }
        
        if (tree.isEmpty() || author == null || committer == null) return null
        
        return GitCommit(
            hash = "",
            size = content.size,
            tree = tree,
            parents = parents,
            author = author,
            committer = committer,
            message = message.toString().trim(),
            content = content
        )
    }
    
    private fun parseTag(content: ByteArray): GitTag? {
        val contentStr = content.toString(Charsets.UTF_8)
        val lines = contentStr.split("\n")
        
        var objectHash = ""
        var objectType: GitObjectType? = null
        var tag = ""
        var tagger: GitSignature? = null
        val message = StringBuilder()
        var inMessage = false
        
        for (line in lines) {
            when {
                line.startsWith("object ") -> objectHash = line.substring(7)
                line.startsWith("type ") -> objectType = GitObjectType.valueOf(line.substring(5).uppercase())
                line.startsWith("tag ") -> tag = line.substring(4)
                line.startsWith("tagger ") -> tagger = parseSignature(line.substring(7))
                line.isEmpty() -> inMessage = true
                inMessage -> message.append(line).append("\n")
            }
        }
        
        if (objectHash.isEmpty() || objectType == null || tag.isEmpty() || tagger == null) return null
        
        return GitTag(
            hash = "",
            size = content.size,
            objectHash = objectHash,
            objectType = objectType,
            tag = tag,
            tagger = tagger,
            message = message.toString().trim(),
            content = content
        )
    }
    
    private fun parseSignature(signature: String): GitSignature? {
        val parts = signature.split(" ", limit = 3)
        if (parts.size != 3) return null
        
        val name = parts[0]
        val email = parts[1].removeSurrounding("<", ">")
        val timestamp = parts[2].toLongOrNull()?.let { 
            java.time.Instant.ofEpochSecond(it)
        } ?: return null
        
        return GitSignature(name, email, timestamp)
    }
} 