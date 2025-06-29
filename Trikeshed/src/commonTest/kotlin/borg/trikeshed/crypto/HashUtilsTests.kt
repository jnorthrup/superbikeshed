package borg.trikeshed.crypto

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.emptyIndexed
import borg.trikeshed.lib.toIndexed
import borg.trikeshed.multiformats.base.Base16
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HashUtilsTests {

    private fun assertIndexedEquals(expected: Indexed<Byte>, actual: Indexed<Byte>, message: String? = null) {
        assertTrue(expected.size == actual.size && (0 until expected.size).all { expected[it] == actual[it] }, message)
    }

    @Test
    fun testSha256_emptyInput() = runBlocking {
        // SHA256 hash of an empty string:
        // e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        val expectedHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val expectedDigest = Base16.decode(expectedHex)

        val actualDigest = HashUtils.sha256(emptyIndexed())
        assertIndexedEquals(expectedDigest, actualDigest, "SHA-256 of empty input mismatch")
    }

    @Test
    fun testSha256_simpleInput() = runBlocking {
        // SHA256 hash of "hello"
        // 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        val inputString = "hello"
        val inputIndexed = inputString.encodeToByteArray().toIndexed()

        val expectedHex = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        val expectedDigest = Base16.decode(expectedHex)

        val actualDigest = HashUtils.sha256(inputIndexed)
        assertIndexedEquals(expectedDigest, actualDigest, "SHA-256 of 'hello' mismatch")
    }

    @Test
    fun testSha256_longerInput() = runBlocking {
        // SHA256 hash of "The quick brown fox jumps over the lazy dog"
        // d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592
        val inputString = "The quick brown fox jumps over the lazy dog"
        val inputIndexed = inputString.encodeToByteArray().toIndexed()

        val expectedHex = "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592"
        val expectedDigest = Base16.decode(expectedHex)

        val actualDigest = HashUtils.sha256(inputIndexed)
        assertIndexedEquals(expectedDigest, actualDigest, "SHA-256 of 'The quick brown fox...' mismatch")
    }
}
