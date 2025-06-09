package borg.trikeshed.core.git

// Imports for testing framework (kotlin.test)
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue // For contentEquals

// Imports from our project
import borg.trikeshed.core.git.internal.GitObjectType
import borg.trikeshed.core.git.internal.ParsedGitObjectHeader
import borg.trikeshed.core.git.internal.RawGitBlob
import borg.trikeshed.core.git.internal.RawGitCommit
import borg.trikeshed.core.git.internal.RawGitTree
import borg.trikeshed.core.git.internal.RawGitTreeEntry
import borg.trikeshed.core.git.internal.GitPersonIdent // internal version
import borg.trikeshed.core.git.internal.parseGitObjectHeader
import borg.trikeshed.core.git.internal.parseGitBlob
import borg.trikeshed.core.git.internal.parseGitTree
import borg.trikeshed.core.git.internal.parseGitCommit
// RawGitTag and parseGitTag will be tested in a subsequent step

class NativeGitIndexerParserTests {

    // --- Test Data Helper ---
    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    // --- parseGitObjectHeader Tests ---
    @Test
    fun testParseHeader_Blob() {
        val rawData = "blob 12\u0000Hello World".toByteArray(Charsets.UTF_8) // NUL char is \u0000
        val header = parseGitObjectHeader(rawData)
        assertNotNull(header)
        assertEquals(GitObjectType.BLOB, header.type)
        assertEquals(12L, header.size)
    }

    @Test
    fun testParseHeader_Tree() {
        val rawData = "tree 25\u0000entry_data_here".toByteArray(Charsets.UTF_8)
        val header = parseGitObjectHeader(rawData)
        assertNotNull(header)
        assertEquals(GitObjectType.TREE, header.type)
        assertEquals(25L, header.size)
    }

    @Test
    fun testParseHeader_Commit() {
        val rawData = "commit 188\u0000tree 123...\nparent abc...\nauthor ...".toByteArray(Charsets.UTF_8)
        val header = parseGitObjectHeader(rawData)
        assertNotNull(header)
        assertEquals(GitObjectType.COMMIT, header.type)
        assertEquals(188L, header.size)
    }

    @Test
    fun testParseHeader_InvalidNoNull() {
        val rawData = "blob 12Hello World".toByteArray(Charsets.UTF_8) // No NUL
        val header = parseGitObjectHeader(rawData)
        assertNull(header, "Header parsing should fail if no NUL terminator")
    }

    @Test
    fun testParseHeader_InvalidFormat() {
        val rawData = "blob\u0000Hello World".toByteArray(Charsets.UTF_8) // Missing size
        val header = parseGitObjectHeader(rawData)
        assertNull(header, "Header parsing should fail on invalid format (missing size)")
    }

    @Test
    fun testParseHeader_InvalidSize() {
        val rawData = "blob abc\u0000content".toByteArray(Charsets.UTF_8)
        val header = parseGitObjectHeader(rawData)
        assertNull(header, "Header parsing should fail on non-numeric size")
    }

    @Test
    fun testParseHeader_UnknownType() {
        val rawData = "foobar 10\u0000content".toByteArray(Charsets.UTF_8)
        val header = parseGitObjectHeader(rawData)
        assertNull(header, "Header parsing should return null for unknown type")
    }

    // --- parseGitBlob Tests ---
    @Test
    fun testParseBlob_Valid() {
        val content = "Hello World"
        val rawData = "blob ${content.length}\u0000$content".toByteArray(Charsets.UTF_8)
        val blob = parseGitBlob(rawData)
        assertNotNull(blob)
        assertEquals(GitObjectType.BLOB, blob.header.type)
        assertEquals(content.length.toLong(), blob.header.size)
        assertEquals(content, blob.content.toString(Charsets.UTF_8))
    }

    @Test
    fun testParseBlob_NotABlob() {
        val rawData = "commit 12\u0000Hello World".toByteArray(Charsets.UTF_8)
        val blob = parseGitBlob(rawData)
        assertNull(blob)
    }

    @Test
    fun testParseBlob_SizeMismatchInHeader() {
        // Header says 10, actual content "Hello" (5 bytes) matches rawData's implied content length
        // parseGitObjectHeader has a check: size != expectedContentSize.toLong()
        // If parseGitObjectHeader is strict and returns null, parseGitBlob will also return null.
        // If parseGitObjectHeader is lenient on this, parseGitBlob's own check will catch it.
        val rawDataMismatch = "blob 10\u0000Hello".toByteArray(Charsets.UTF_8)
        val blob = parseGitBlob(rawDataMismatch)
        // This test depends on how strictly parseGitObjectHeader checks content size.
        // The current parseGitObjectHeader prints a warning but doesn't return null for this specific mismatch.
        // However, parseGitBlob has its own check: contentBytes.size.toLong() != header.size
        // Here, header.size = 10, contentBytes.size = 5. So this should be null.
        assertNull(blob, "Blob parsing should fail if header size (10) doesn't match actual content size (5)")

        // Header says 3, actual content "Hello" (5 bytes)
        val rawDataMismatch2 = "blob 3\u0000Hello".toByteArray(Charsets.UTF_8)
        val blob2 = parseGitBlob(rawDataMismatch2)
        // header.size = 3, contentBytes.size = 5. Should be null.
        assertNull(blob2, "Blob parsing should fail if header size (3) doesn't match actual content size (5)")
    }

    // --- parseGitTree Tests ---
    @Test
    fun testParseTree_ValidOneEntry() {
        val entryName = "test.txt"
        val entryMode = "100644"
        val entrySha1Hex = "1234567890abcdef1234567890abcdef12345678"
        val entrySha1Bytes = entrySha1Hex.decodeHex()

        val entryBytes = "$entryMode $entryName\u0000".toByteArray(Charsets.UTF_8) + entrySha1Bytes
        val rawData = "tree ${entryBytes.size}\u0000".toByteArray(Charsets.UTF_8) + entryBytes

        val tree = parseGitTree(rawData)
        assertNotNull(tree)
        assertEquals(GitObjectType.TREE, tree.header.type)
        assertEquals(1, tree.entries.size)
        val entry = tree.entries[0]
        assertEquals(entryMode, entry.mode)
        assertEquals(entryName, entry.name)
        assertTrue(entrySha1Bytes.contentEquals(entry.sha1), "SHA1 mismatch for ${entry.name}")
    }

    @Test
    fun testParseTree_ValidTwoEntries() {
        val entry1Name = "file1.txt"
        val entry1Mode = "100644"
        val entry1Sha1Bytes = "01".repeat(20).decodeHex()
        val entry1Bytes = "$entry1Mode $entry1Name\u0000".toByteArray(Charsets.UTF_8) + entry1Sha1Bytes

        val entry2Name = "subdir"
        val entry2Mode = "40000"
        val entry2Sha1Bytes = "02".repeat(20).decodeHex()
        val entry2Bytes = "$entry2Mode $entry2Name\u0000".toByteArray(Charsets.UTF_8) + entry2Sha1Bytes

        val treeContentBytes = entry1Bytes + entry2Bytes
        val rawData = "tree ${treeContentBytes.size}\u0000".toByteArray(Charsets.UTF_8) + treeContentBytes

        val tree = parseGitTree(rawData)
        assertNotNull(tree)
        assertEquals(2, tree.entries.size)
        assertEquals(entry1Name, tree.entries[0].name)
        assertTrue(entry1Sha1Bytes.contentEquals(tree.entries[0].sha1))
        assertEquals(entry2Name, tree.entries[1].name)
        assertTrue(entry2Sha1Bytes.contentEquals(tree.entries[1].sha1))
    }

    @Test
    fun testParseTree_EmptyTree() {
        val rawData = "tree 0\u0000".toByteArray(Charsets.UTF_8)
        val tree = parseGitTree(rawData)
        assertNotNull(tree)
        assertEquals(0, tree.entries.size)
        assertEquals(0L, tree.header.size)
    }

    @Test
    fun testParseTree_MalformedEntryMissingSha1() {
        val entryBytes = "100644 test.txt\u0000".toByteArray(Charsets.UTF_8) // SHA1 is missing
        val rawData = "tree ${entryBytes.size}\u0000".toByteArray(Charsets.UTF_8) + entryBytes
        val tree = parseGitTree(rawData)
        assertNull(tree, "Tree parsing should fail if an entry is malformed (missing SHA1)")
    }

    @Test
    fun testParseTree_MalformedEntryMissingNulAfterName() {
        val entrySha1Bytes = "01".repeat(20).decodeHex()
        // NUL after name is missing, space before SHA1 is also effectively part of name now.
        val entryBytes = "100644 test.txt".toByteArray(Charsets.UTF_8) + entrySha1Bytes
        val rawData = "tree ${entryBytes.size}\u0000".toByteArray(Charsets.UTF_8) + entryBytes
        val tree = parseGitTree(rawData)
        assertNull(tree, "Tree parsing should fail if an entry is malformed (missing NUL after name)")
    }

    // --- parseGitCommit Tests ---
    @Test
    fun testParseCommit_ValidMinimal() {
        val treeSha = "1234567890abcdef1234567890abcdef12345678"
        val author = "Author Name <author@example.com> 1600000000 +0000"
        val committer = "Committer Name <committer@example.com> 1600000001 +0000"
        val message = "Initial commit"
        val commitContent = "tree $treeSha\nauthor $author\ncommitter $committer\n\n$message"
        val rawData = "commit ${commitContent.toByteArray(Charsets.UTF_8).size}\u0000$commitContent".toByteArray(Charsets.UTF_8)

        val commit = parseGitCommit(rawData)
        assertNotNull(commit)
        assertEquals(treeSha, commit.treeSha1Hex)
        assertEquals("Author Name", commit.author.name)
        assertEquals("author@example.com", commit.author.email)
        assertEquals(1600000000L, commit.author.timestamp)
        assertEquals(0, commit.author.timezoneOffsetMinutes)
        assertEquals("Committer Name", commit.committer.name)
        assertEquals(message, commit.message)
        assertEquals(0, commit.parentSha1HexList.size)
    }

    @Test
    fun testParseCommit_WithParentsAndMultilineMessage() {
        val treeSha = "t1".repeat(20) // Becomes 40 char hex
        val parent1Sha = "p1".repeat(20)
        val parent2Sha = "p2".repeat(20)
        val author = "Test User <test@user.com> 1234567890 -0700"
        val committer = "Test User <test@user.com> 1234567891 -0700"
        val message = "Merge commit.\n\nWith a detailed explanation."
        val commitContent = "tree $treeSha\nparent $parent1Sha\nparent $parent2Sha\nauthor $author\ncommitter $committer\n\n$message"
        val rawData = "commit ${commitContent.toByteArray(Charsets.UTF_8).size}\u0000$commitContent".toByteArray(Charsets.UTF_8)

        val commit = parseGitCommit(rawData)
        assertNotNull(commit)
        assertEquals(treeSha, commit.treeSha1Hex)
        assertEquals(2, commit.parentSha1HexList.size)
        assertEquals(parent1Sha, commit.parentSha1HexList[0])
        assertEquals(parent2Sha, commit.parentSha1HexList[1])
        assertEquals("Test User", commit.author.name)
        assertEquals(-420, commit.author.timezoneOffsetMinutes) // -0700
        assertEquals(message, commit.message)
    }

    @Test
    fun testParseCommit_MissingTree() {
        val author = "Author Name <author@example.com> 1600000000 +0000"
        val committer = "Committer Name <committer@example.com> 1600000001 +0000"
        val message = "Initial commit"
        val commitContent = "author $author\ncommitter $committer\n\n$message" // Missing tree
        val rawData = "commit ${commitContent.toByteArray(Charsets.UTF_8).size}\u0000$commitContent".toByteArray(Charsets.UTF_8)
        val commit = parseGitCommit(rawData)
        assertNull(commit, "Commit parsing should fail if tree is missing")
    }

    @Test
    fun testParseCommit_MalformedPersonIdent() {
        val treeSha = "t1".repeat(20)
        val author = "Author Name author@example.com> 1600000000 +0000" // Missing < for email
        val committer = "Committer Name <committer@example.com> 1600000001 +0000"
        val message = "Initial commit"
        val commitContent = "tree $treeSha\nauthor $author\ncommitter $committer\n\n$message"
        val rawData = "commit ${commitContent.toByteArray(Charsets.UTF_8).size}\u0000$commitContent".toByteArray(Charsets.UTF_8)
        val commit = parseGitCommit(rawData)
        assertNull(commit, "Commit parsing should fail if PersonIdent is malformed")
    }
}
