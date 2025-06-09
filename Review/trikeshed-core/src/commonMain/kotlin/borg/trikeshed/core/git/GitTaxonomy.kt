package borg.trikeshed.core.git

import borg.trikeshed.core.Join // Assuming Join is in borg.trikeshed.core
import borg.trikeshed.core.Series
import borg.trikeshed.core.j // For infix Join creation: a j b
import borg.trikeshed.core.emptySeries // For empty Series

// This file defines a TrikeShed-idiomatic taxonomy for Git objects.
// The core idea is to represent Git structures using TrikeShed's fundamental
// primitives: `Join` (for composing pairs of related data) and `Series`
// (for representing sequences or collections, including optional values as series of 0 or 1).
// These typealiases and structures will serve as the target for the Git parsing DSL,
// allowing raw Git data to be transformed into this structured, composable format.

// I. Basic Git Primitives as Typealiases using TrikeShed Core Types

/** Represents a Git SHA-1 hash, typically as a 40-character hex string. */
typealias GitSha1 = String

/** Represents the mode of a tree entry (e.g., "100644", "40000") as a String. */
typealias GitMode = String

/** Represents a file or directory name within a tree. */
typealias GitName = String

/** Represents a Git object type string (e.g., "blob", "tree", "commit", "tag"). */
typealias GitObjectTypeString = String // "blob", "tree", "commit", "tag"


// II. TrikeShed-idiomatic Representations of Git Objects

// --- Blob ---
/**
 * A TrikeShed representation of a Git Blob.
 * Structure: Join<Size: Long, Content: Series<Byte>>
 * Size is the byte size of the blob's content.
 * Content is represented as a Series<Byte>, allowing for potential lazy loading or streaming.
 */
typealias TsGitBlob = Join<Long, Series<Byte>>
val TsGitBlob.size: Long get() = a
val TsGitBlob.content: Series<Byte> get() = b

// --- Tree ---
/**
 * Represents a single entry within a Git Tree.
 * Structure: Join<Join<GitMode, GitName>, GitSha1>
 *              (mode j name) j object_sha1
 */
typealias TsGitTreeEntry = Join<Join<GitMode, GitName>, GitSha1>
val TsGitTreeEntry.mode: GitMode get() = a.a
val TsGitTreeEntry.name: GitName get() = a.b
val TsGitTreeEntry.sha1: GitSha1 get() = b

/** Factory function for TsGitTreeEntry */
fun tsGitTreeEntryOf(mode: GitMode, name: GitName, sha1: GitSha1): TsGitTreeEntry = (mode j name) j sha1

/**
 * A TrikeShed representation of a Git Tree.
 * Structure: Join<Size: Long, Entries: Series<TsGitTreeEntry>>
 * Size is the raw byte size of the tree object's content (as reported in its header).
 * Entries is a Series of TsGitTreeEntry.
 */
typealias TsGitTree = Join<Long, Series<TsGitTreeEntry>>
val TsGitTree.rawContentSize: Long get() = a
val TsGitTree.entries: Series<TsGitTreeEntry> get() = b

// --- Commit ---
/**
 * Represents author or committer information.
 * Structure: Join<Join<Name: String, Email: String>, Join<TimestampSec: Long, TzOffsetMin: Int>>
 *              (name j email) j (timestamp j timezoneOffset)
 */
typealias TsGitPersonIdent = Join<Join<String, String>, Join<Long, Int>>
val TsGitPersonIdent.name: String get() = a.a
val TsGitPersonIdent.email: String get() = a.b
val TsGitPersonIdent.timestampSeconds: Long get() = b.a
val TsGitPersonIdent.timezoneOffsetMinutes: Int get() = b.b

/** Factory function for TsGitPersonIdent */
fun tsGitPersonIdentOf(name: String, email: String, timestamp: Long, tzOffset: Int): TsGitPersonIdent =
    (name j email) j (timestamp j tzOffset)

/**
 * Represents primary metadata of a commit (excluding the message).
 * Structure: Join<TreeSha1, Join<Series<ParentSha1s>, Join<TsGitAuthor, TsGitCommitter>>>
 *              treeSha1 j (parentSha1s j (author j committer))
 */
typealias TsGitCommitMetadata = Join<GitSha1, Join<Series<GitSha1>, Join<TsGitPersonIdent, TsGitPersonIdent>>>
val TsGitCommitMetadata.treeSha1: GitSha1 get() = a
val TsGitCommitMetadata.parentSha1s: Series<GitSha1> get() = b.a
val TsGitCommitMetadata.author: TsGitPersonIdent get() = b.b.a
val TsGitCommitMetadata.committer: TsGitPersonIdent get() = b.b.b

/**
 * A TrikeShed representation of a Git Commit.
 * Structure: Join<TsGitCommitMetadata, Message: String>
 */
typealias TsGitCommit = Join<TsGitCommitMetadata, String>
val TsGitCommit.metadata: TsGitCommitMetadata get() = a
val TsGitCommit.message: String get() = b
// Accessors for convenience
val TsGitCommit.treeSha1: GitSha1 get() = metadata.treeSha1
val TsGitCommit.parentSha1s: Series<GitSha1> get() = metadata.parentSha1s
val TsGitCommit.author: TsGitPersonIdent get() = metadata.author
val TsGitCommit.committer: TsGitPersonIdent get() = metadata.committer


// --- Annotated Tag ---
/**
 * Core information for an annotated tag, pointing to the tagged object.
 * Structure: Join<TaggedObjectSha1, TaggedObjectTypeString>
 */
typealias TsGitTagCoreInfo = Join<GitSha1, GitObjectTypeString> // objectSha1 j objectType
val TsGitTagCoreInfo.objectSha1: GitSha1 get() = a
val TsGitTagCoreInfo.objectType: GitObjectTypeString get() = b

/**
 * Details specific to the tag itself, like its name and the tagger (if any).
 * Structure: Join<TagName, Series<TsGitPersonIdent>>
 * Tagger is represented as a Series of TsGitPersonIdent (0 or 1 element) for optionality.
 */
typealias TsGitTagDetails = Join<GitName, Series<TsGitPersonIdent>> // tagName j tagger (Series for optionality)
val TsGitTagDetails.tagName: GitName get() = a
/** The tagger information, as a Series containing zero or one [TsGitPersonIdent]. */
val TsGitTagDetails.tagger: Series<TsGitPersonIdent> get() = b

/**
 * A TrikeShed representation of an annotated Git Tag.
 * Structure: Join<Join<TsGitTagCoreInfo, TsGitTagDetails>, Message: String>
 *              (coreInfo j details) j message
 */
typealias TsGitTag = Join<Join<TsGitTagCoreInfo, TsGitTagDetails>, String /* message */>
val TsGitTag.coreInfo: TsGitTagCoreInfo get() = a.a
val TsGitTag.details: TsGitTagDetails get() = a.b
val TsGitTag.message: String get() = b
// Convenience accessors
val TsGitTag.objectSha1: GitSha1 get() = coreInfo.objectSha1
val TsGitTag.objectType: GitObjectTypeString get() = coreInfo.objectType
val TsGitTag.tagName: GitName get() = details.tagName
/** The tagger information for the tag, as a Series containing zero or one [TsGitPersonIdent]. */
val TsGitTag.tagger: Series<TsGitPersonIdent> get() = details.tagger


// III. Generic Git Object Representation (Conceptual)
// This section outlines potential future directions or considerations for representing
// Git objects in a more generalized way using TrikeShed primitives.
// For now, the concrete types (TsGitBlob, TsGitTree, etc.) are the primary focus.
// A sum type (sealed interface/class) is a common Kotlin idiom for such cases,
// but this taxonomy emphasizes Join-based composition.

/**
 * Placeholder for a generic Git object identifier. Currently identical to GitSha1,
 * but could be expanded to include type information if needed for disambiguation in
 * some contexts.
 */
typealias TsGitObjectId = GitSha1

// Example of how one might represent a lookup result for a generic Git object:
// typealias TsGitObjectLookupResult = Join<TsGitObjectId, Series<Any /* Specific TsGitObject type*/>>
// The 'Any' here would require type casting, highlighting a challenge with purely Join-based sum types.
// A more type-safe approach would involve dedicated structures or a sealed hierarchy if strict
// type safety without casting is paramount for these generic cases.

// Helper for creating an empty Series, useful for optional fields like tagger.
// `emptySeries<Type>()` from `borg.trikeshed.core` should be used directly.
// Example usage for an optional field:
// val noTagger: Series<TsGitPersonIdent> = emptySeries()
// val someTagger: Series<TsGitPersonIdent> = tsGitPersonIdentOf(...) j emptySeries() // Incorrect use of j for Series construction
// Correct construction for a Series of one:
// val singleElementSeries: Series<TsGitPersonIdent> = 1 j { index -> /* return TsGitPersonIdent instance */ }
// Or, more simply, if the value is already constructed:
// fun <T> T.toSingletonSeries(): Series<T> = 1 j { _ -> this }
// val someTaggerSeries: Series<TsGitPersonIdent> = someActualTaggerIdent.toSingletonSeries()
// val noTaggerSeries: Series<TsGitPersonIdent> = emptySeries()

// End of GitTaxonomy.kt
// Additional comments on design philosophy are included at the top of the file.
// The primary goal is to provide a composable, Join/Series-based representation
// of Git objects that can be targeted by a parsing DSL.
```
