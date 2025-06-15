package borg.trikeshed.git

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Git Object Types
 */
enum class GitObjectType {
    BLOB,
    TREE,
    COMMIT,
    TAG
}

/**
 * Base class for all Git objects
 */
@Serializable
sealed class GitObject {
    abstract val type: GitObjectType
    abstract val hash: String
    abstract val size: Int
    abstract val content: ByteArray
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        return hash == (other as GitObject).hash
    }
    
    override fun hashCode(): Int = hash.hashCode()
}

/**
 * Git Blob - represents file content
 */
@Serializable
data class GitBlob(
    override val hash: String,
    override val size: Int,
    override val content: ByteArray
) : GitObject() {
    override val type = GitObjectType.BLOB
    
    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) return false
        return content.contentEquals((other as GitBlob).content)
    }
    
    override fun hashCode(): Int = 31 * super.hashCode() + content.contentHashCode()
}

/**
 * Git Tree Entry - represents a file or directory in a tree
 */
@Serializable
data class GitTreeEntry(
    val mode: String,
    val name: String,
    val hash: String
)

/**
 * Git Tree - represents a directory
 */
@Serializable
data class GitTree(
    override val hash: String,
    override val size: Int,
    val entries: List<GitTreeEntry>,
    override val content: ByteArray
) : GitObject() {
    override val type = GitObjectType.TREE
    
    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) return false
        return entries == (other as GitTree).entries
    }
    
    override fun hashCode(): Int = 31 * super.hashCode() + entries.hashCode()
}

/**
 * Git Commit - represents a commit
 */
@Serializable
data class GitCommit(
    override val hash: String,
    override val size: Int,
    val tree: String,
    val parents: List<String>,
    val author: GitSignature,
    val committer: GitSignature,
    val message: String,
    override val content: ByteArray
) : GitObject() {
    override val type = GitObjectType.COMMIT
    
    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) return false
        return tree == (other as GitCommit).tree &&
               parents == other.parents &&
               author == other.author &&
               committer == other.committer &&
               message == other.message
    }
    
    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + tree.hashCode()
        result = 31 * result + parents.hashCode()
        result = 31 * result + author.hashCode()
        result = 31 * result + committer.hashCode()
        result = 31 * result + message.hashCode()
        return result
    }
}

/**
 * Git Tag - represents a tag
 */
@Serializable
data class GitTag(
    override val hash: String,
    override val size: Int,
    val objectHash: String,
    val objectType: GitObjectType,
    val tag: String,
    val tagger: GitSignature,
    val message: String,
    override val content: ByteArray
) : GitObject() {
    override val type = GitObjectType.TAG
    
    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) return false
        return objectHash == (other as GitTag).objectHash &&
               objectType == other.objectType &&
               tag == other.tag &&
               tagger == other.tagger &&
               message == other.message
    }
    
    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + objectHash.hashCode()
        result = 31 * result + objectType.hashCode()
        result = 31 * result + tag.hashCode()
        result = 31 * result + tagger.hashCode()
        result = 31 * result + message.hashCode()
        return result
    }
}

/**
 * Git Signature - represents author/committer information
 */
@Serializable
data class GitSignature(
    val name: String,
    val email: String,
    val timestamp: Instant
)

/**
 * Git Reference - represents a reference (branch, tag, etc.)
 */
@Serializable
data class GitReference(
    val name: String,
    val target: String,
    val isSymbolic: Boolean = false
)

/**
 * Git Repository - represents a Git repository
 */
@Serializable
data class GitRepository(
    val path: String,
    val config: GitConfig,
    val refs: Map<String, GitReference>,
    val objects: Map<String, GitObject>
)

/**
 * Git Config - represents Git configuration
 */
@Serializable
data class GitConfig(
    val core: CoreConfig = CoreConfig(),
    val remote: Map<String, RemoteConfig> = emptyMap(),
    val branch: Map<String, BranchConfig> = emptyMap()
)

/**
 * Core Git Configuration
 */
@Serializable
data class CoreConfig(
    val repositoryFormatVersion: Int = 0,
    val fileMode: Boolean = true,
    val bare: Boolean = false,
    val logAllRefUpdates: Boolean = true,
    val symlinks: Boolean = true,
    val ignorecase: Boolean = false
)

/**
 * Remote Git Configuration
 */
@Serializable
data class RemoteConfig(
    val url: String,
    val fetch: List<String> = emptyList(),
    val pushurl: String? = null,
    val push: List<String> = emptyList()
)

/**
 * Branch Git Configuration
 */
@Serializable
data class BranchConfig(
    val remote: String? = null,
    val merge: String? = null,
    val rebase: Boolean = false
) 