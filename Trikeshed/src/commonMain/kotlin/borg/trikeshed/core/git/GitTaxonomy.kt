package borg.trikeshed.core.git

import kotlinx.serialization.Serializable

@Serializable
sealed class GitObject {
    abstract val hash: String
    abstract val type: GitObjectType
}

@Serializable
enum class GitObjectType {
    BLOB, TREE, COMMIT, TAG
}

@Serializable
data class GitBlob(
    override val hash: String,
    val content: ByteArray,
    override val type: GitObjectType = GitObjectType.BLOB
) : GitObject()

@Serializable
data class GitTree(
    override val hash: String,
    val entries: List<GitTreeEntry>,
    override val type: GitObjectType = GitObjectType.TREE
) : GitObject()

@Serializable
data class GitTreeEntry(
    val mode: String,
    val name: String,
    val hash: String
)

@Serializable
data class GitCommit(
    override val hash: String,
    val tree: String,
    val parents: List<String>,
    val author: GitSignature,
    val committer: GitSignature,
    val message: String,
    override val type: GitObjectType = GitObjectType.COMMIT
) : GitObject()

@Serializable
data class GitSignature(
    val name: String,
    val email: String,
    val timestamp: Long,
    val timezone: String
)

@Serializable
data class GitTag(
    override val hash: String,
    val objectHash: String,
    val objectType: GitObjectType,
    val tag: String,
    val tagger: GitSignature,
    val message: String,
    override val type: GitObjectType = GitObjectType.TAG
) : GitObject()

@Serializable
data class GitReference(
    val name: String,
    val target: String,
    val symbolic: Boolean = false
)

@Serializable
data class GitRepository(
    val objects: Map<String, GitObject>,
    val refs: Map<String, GitReference>,
    val config: GitConfig
)

@Serializable
data class GitConfig(
    val core: GitCoreConfig,
    val remote: Map<String, GitRemoteConfig>
)

@Serializable
data class GitCoreConfig(
    val repositoryFormatVersion: Int = 0,
    val fileMode: Boolean = true,
    val bare: Boolean = false
)

@Serializable
data class GitRemoteConfig(
    val url: String,
    val fetch: List<String>
) 