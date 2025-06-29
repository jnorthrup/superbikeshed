package borg.trikeshed.multiformats.base

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.emptyIndexed
import borg.trikeshed.lib.toIndexed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


class Base16Tests {

    private fun assertIndexedEquals(expected: Indexed<Byte>, actual: Indexed<Byte>, message: String? = null) {
        assertTrue(expected.size == actual.size && (0 until expected.size).all { expected[it] == actual[it] }, message)
    }

    @Test
    fun testEncodeEmpty() {
        assertEquals("", Base16.encode(emptyIndexed()))
    }

    @Test
    fun testDecodeEmpty() {
        assertIndexedEquals(emptyIndexed(), Base16.decode(""))
    }

    @Test
    fun testEncodeSimple() {
        // "f" -> 66
        // "fo" -> 666f
        // "foo" -> 666f6f
        // "foob" -> 666f6f62
        // "fooba" -> 666f6f6261
        // "foobar" -> 666f6f626172
        assertEquals("66", Base16.encode("f".encodeToByteArray().toIndexed()))
        assertEquals("666f", Base16.encode("fo".encodeToByteArray().toIndexed()))
        assertEquals("666f6f", Base16.encode("foo".encodeToByteArray().toIndexed()))
        assertEquals("666f6f62", Base16.encode("foob".encodeToByteArray().toIndexed()))
        assertEquals("666f6f6261", Base16.encode("fooba".encodeToByteArray().toIndexed()))
        assertEquals("666f6f626172", Base16.encode("foobar".encodeToByteArray().toIndexed()))
    }

    @Test
    fun testDecodeSimple() {
        assertIndexedEquals("f".encodeToByteArray().toIndexed(), Base16.decode("66"))
        assertIndexedEquals("fo".encodeToByteArray().toIndexed(), Base16.decode("666f"))
        assertIndexedEquals("foo".encodeToByteArray().toIndexed(), Base16.decode("666f6f"))
        assertIndexedEquals("foob".encodeToByteArray().toIndexed(), Base16.decode("666f6f62"))
        assertIndexedEquals("fooba".encodeToByteArray().toIndexed(), Base16.decode("666f6f6261"))
        assertIndexedEquals("foobar".encodeToByteArray().toIndexed(), Base16.decode("666f6f626172"))
    }

    @Test
    fun testDecodeUppercase() {
        assertIndexedEquals("foobar".encodeToByteArray().toIndexed(), Base16.decode("666F6F626172"))
    }

    @Test
    fun testEncodeAllBytes() {
        val allBytes = ByteArray(256) { it.toByte() }
        val encoded = Base16.encode(allBytes.toIndexed())
        val decoded = Base16.decode(encoded)
        assertIndexedEquals(allBytes.toIndexed(), decoded, "Encode/decode mismatch for all byte values")
    }

    @Test
    fun testDecodeInvalidLength() {
        assertFailsWith<IllegalArgumentException>("Odd length string should fail") {
            Base16.decode("666")
        }
        assertFailsWith<IllegalArgumentException>("Odd length string should fail") {
            Base16.decode("a")
        }
    }

    @Test
    fun testDecodeInvalidCharacters() {
        assertFailsWith<IllegalArgumentException>("Invalid char 'g'") {
            Base16.decode("6g")
        }
        assertFailsWith<IllegalArgumentException>("Invalid char ' '") {
            Base16.decode("66 6f")
        }
        assertFailsWith<IllegalArgumentException>("Invalid char '-'") {
            Base16.decode("ff-ff")
        }
    }
}
