package borg.trikeshed.core.git

import borg.trikeshed.core.Series
import borg.trikeshed.core.j // For infix Join creation: a j b
import borg.trikeshed.core.emptySeries // For creating empty series for optional lists like parents
import borg.trikeshed.core.git.internal.RawGitBlob
import borg.trikeshed.core.git.internal.RawGitTree
import borg.trikeshed.core.git.internal.RawGitTreeEntry
import borg.trikeshed.core.git.internal.RawGitCommit
import borg.trikeshed.core.git.internal.GitPersonIdent as RawGitPersonIdent // Alias to avoid name clash
import borg.trikeshed.core.git.internal.RawGitTag
import borg.trikeshed.core.git.internal.GitObjectType as RawGitObjectType // Alias for clarity

// This file will house the DSL (Domain Specific Language) functions for transforming
// "raw" Git objects (parsed by GitObjectParser.kt from Phase 1) into the
// TrikeShed-idiomatic Git taxonomy (defined in GitTaxonomy.kt from Phase 2).
//
// The general approach involves:
// 1. Extension functions on `RawGit*` types (e.g., `RawGitBlob.toTsGitBlob()`).
// 2. These functions will map the fields of `RawGit*` objects to the
//    `Join`-based structures defined in the `TsGit*` taxonomy.
// 3. Helper functions might be used internally if transformations are complex,
//    but the primary interface will be these extension functions.
// 4. The focus is on clear, composable transformations that bridge the gap
//    between the raw parsed data and the structured, TrikeShed-native representation.

/**
 * Transforms a RawGitBlob (parsed from raw object data) into a TsGitBlob.
 * A TsGitBlob represents a blob's size (Long) and its content as a Series<Byte>.
 */
fun RawGitBlob.toTsGitBlob(): TsGitBlob {
    val contentByteArray = this.content
    val contentSeries = object : Series<Byte> {
        override val a: Int get() = contentByteArray.size
        override val b: (Int) -> Byte get() = { index ->
            if (index < 0 || index >= contentByteArray.size) {
                throw IndexOutOfBoundsException("Index $index out of bounds for blob content of size ${contentByteArray.size}")
            }
            contentByteArray[index]
        }
    }
    return this.header.size j contentSeries
}

/**
 * Transforms a RawGitTreeEntry into a TsGitTreeEntry.
 * A TsGitTreeEntry is Join<Join<GitMode, GitName>, GitSha1 (hex string)>.
 */
fun RawGitTreeEntry.toTsGitTreeEntry(): TsGitTreeEntry {
    val objectSha1Hex = this.sha1.toHexString()
    return (this.mode j this.name) j objectSha1Hex
}

/**
 * Transforms a RawGitTree into a TsGitTree.
 * A TsGitTree is Join<Long, Series<TsGitTreeEntry>> (rawContentSize j entriesSeries).
 */
fun RawGitTree.toTsGitTree(): TsGitTree {
    val rawSize = this.header.size
    val rawEntriesList = this.entries
    val tsEntriesSeries = object : Series<TsGitTreeEntry> {
        override val a: Int get() = rawEntriesList.size
        override val b: (Int) -> TsGitTreeEntry get() = { index ->
            if (index < 0 || index >= rawEntriesList.size) {
                throw IndexOutOfBoundsException("Index $index out of bounds for tree entries size ${rawEntriesList.size}")
            }
            rawEntriesList[index].toTsGitTreeEntry()
        }
    }
    return rawSize j tsEntriesSeries
}

/**
 * Transforms a RawGitPersonIdent (from internal parsing) into a TsGitPersonIdent.
 * A TsGitPersonIdent is Join<Join<Name: String, Email: String>, Join<TimestampSec: Long, TzOffsetMin: Int>>.
 */
fun RawGitPersonIdent.toTsGitPersonIdent(): TsGitPersonIdent {
    return (this.name j this.email) j (this.timestamp j this.timezoneOffsetMinutes)
}

/**
 * Transforms a RawGitCommit into a TsGitCommit.
 * A TsGitCommit is Join<TsGitCommitMetadata, String /* message */>.
 */
fun RawGitCommit.toTsGitCommit(): TsGitCommit {
    val tsAuthor = this.author.toTsGitPersonIdent()
    val tsCommitter = this.committer.toTsGitPersonIdent()

    val parentList = this.parentSha1HexList
    val tsParentSha1s: Series<GitSha1> = if (parentList.isEmpty()) {
        emptySeries()
    } else {
        object : Series<GitSha1> {
            override val a: Int get() = parentList.size
            override val b: (Int) -> GitSha1 get() = { index ->
                if (index < 0 || index >= parentList.size) {
                    throw IndexOutOfBoundsException("Index $index out of bounds for parent SHAs size ${parentList.size}")
                }
                parentList[index]
            }
        }
    }
    val tsMetadata = this.treeSha1Hex j (tsParentSha1s j (tsAuthor j tsCommitter))
    return tsMetadata j this.message
}

/**
 * Transforms a RawGitTag into a TsGitTag.
 * TsGitTag is Join<Join<TsGitTagCoreInfo, TsGitTagDetails>, String /* message */>
 */
fun RawGitTag.toTsGitTag(): TsGitTag {
    // 1. Map RawGitObjectType enum to GitObjectTypeString typealias
    val objectTypeString: GitObjectTypeString = when (this.objectType) {
        RawGitObjectType.BLOB -> "blob"
        RawGitObjectType.TREE -> "tree"
        RawGitObjectType.COMMIT -> "commit"
        RawGitObjectType.TAG -> "tag"
        RawGitObjectType.UNKNOWN -> "unknown" // Or handle as an error/default more explicitly
    }

    // 2. Create TsGitTagCoreInfo: Join<GitSha1, GitObjectTypeString>
    val tsCoreInfo: TsGitTagCoreInfo = this.objectSha1Hex j objectTypeString

    // 3. Transform tagger (RawGitPersonIdent?) to Series<TsGitPersonIdent>
    val tsTaggerSeries: Series<TsGitPersonIdent> = this.tagger?.let { rawTagger ->
        val tsTagger = rawTagger.toTsGitPersonIdent() // Use existing helper
        // Create a single-element Series
        object : Series<TsGitPersonIdent> {
            override val a: Int get() = 1
            override val b: (Int) -> TsGitPersonIdent get() = { index ->
                if (index == 0) tsTagger else throw IndexOutOfBoundsException("Index $index out of bounds for single tagger series")
            }
        }
    } ?: emptySeries<TsGitPersonIdent>() // Use emptySeries if tagger is null

    // 4. Create TsGitTagDetails: Join<GitName, Series<TsGitPersonIdent>>
    val tsDetails: TsGitTagDetails = this.tagName j tsTaggerSeries

    // 5. Assemble TsGitTag: Join<Join<TsGitTagCoreInfo, TsGitTagDetails>, String /* message */>
    return (tsCoreInfo j tsDetails) j this.message
}


// --- Utility Functions ---

/**
 * Converts a ByteArray (typically a SHA1 hash) into its hexadecimal string representation.
 */
private fun ByteArray.toHexString(): String {
    return this.joinToString("") { byte -> "%02x".format(byte) }
}

// End of DSL functions. All four object types are now covered.
