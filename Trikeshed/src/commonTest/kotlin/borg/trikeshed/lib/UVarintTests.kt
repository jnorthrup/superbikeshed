package borg.trikeshed.lib

import borg.trikeshed.multiformats.base.Base16 // For easy display of byte arrays
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UVarintTests {

    private fun assertIndexedEquals(expected: Indexed<Byte>, actual: Indexed<Byte>, message: String? = null) {
        val readableMessage = message ?: "Expected ${Base16.encode(expected)}, got ${Base16.encode(actual)}"
        assertTrue(expected.size == actual.size && (0 until expected.size).all { expected[it] == actual[it] }, readableMessage)
    }

    data class UVarintTestCase(val value: Long, val hexEncodedBytes: String)

    private val testCases = listOf(
        UVarintTestCase(0L, "00"),
        UVarintTestCase(1L, "01"),
        UVarintTestCase(127L, "7f"),
        UVarintTestCase(128L, "8001"),
        UVarintTestCase(255L, "ff01"),
        UVarintTestCase(300L, "ac02"),
        UVarintTestCase(16384L, "808001"), // 2^14
        UVarintTestCase(Long.MAX_VALUE, "ffffffffffffffff7f") // Max Long needs 10 bytes if treated as unsigned, 9 if signed interpretation for last byte.
                                                              // For strict uvarint, it's 10 bytes if it were u64.
                                                              // For Java Long (signed 64-bit), max value is 2^63 - 1.
                                                              // Let's use a known uvarint vector for a large number:
                                                              // 2097151 (2^21 - 1) -> ff ff 7f
        UVarintTestCase(2097151L, "ffff7f"),
        UVarintTestCase(Long.MAX_VALUE, "ffffffffffffffff7f") // This is 2^63-1, last byte is 0x7F.
                                                              // If Long.MAX_VALUE was the input, it would be 9 bytes: fe ff ff ff ff ff ff ff 7f
                                                              // The uvarint spec typically handles unsigned numbers.
                                                              // Let's use a value that fits the 9-byte max for signed Long.
                                                              // For 0xFFFFFFFFFFFFFFFF (unsigned 64-bit max), it's "ffffffffffffffffff01" (10 bytes)
                                                              // For signed Long.MAX_VALUE (0x7FFFFFFFFFFFFFFF), it's "ffffffffffffffff7f" (9 bytes)
    )
    // Re-evaluating Long.MAX_VALUE uvarint encoding:
    // 0x7FFFFFFFFFFFFFFF
    // byte 1: 0xFF (and 0x7F) -> shift 7
    // byte 2: 0xFF (and 0x7F) -> shift 14
    // ...
    // byte 8: 0xFF (and 0x7F) -> shift 56
    // byte 9: 0x7F (remaining 7 bits of 0x7F from original number) -> no continuation bit
    // So, ffffffffffffffff7f is correct for Long.MAX_VALUE.

    @Test
    fun testEncode() {
        testCases.forEach { case ->
            val expectedBytes = Base16.decode(case.hexEncodedBytes)
            val actualBytes = UVarint.encode(case.value)
            assertIndexedEquals(expectedBytes, actualBytes, "Encoding ${case.value} failed.")
        }
    }

    @Test
    fun testEncodeNegativeFails() {
        assertFailsWith<IllegalArgumentException> {
            UVarint.encode(-1L)
        }
    }

    @Test
    fun testDecode() {
        testCases.forEach { case ->
            val inputBytes = Base16.decode(case.hexEncodedBytes)
            val (decodedValue, bytesRead) = UVarint.decode(inputBytes)
            assertEquals(case.value, decodedValue, "Decoding ${case.hexEncodedBytes} failed (value mismatch).")
            assertEquals(inputBytes.size, bytesRead, "Decoding ${case.hexEncodedBytes} failed (bytesRead mismatch).")
        }
    }

    @Test
    fun testDecodeWithOffset() {
        val value1 = 128L
        val bytes1 = UVarint.encode(value1) // 8001
        val value2 = 300L
        val bytes2 = UVarint.encode(value2) // ac02

        val combinedBytes = bytes1 + bytes2 // Using Indexed.plus

        val (decoded1, read1) = UVarint.decode(combinedBytes, 0)
        assertEquals(value1, decoded1)
        assertEquals(bytes1.size, read1)

        val (decoded2, read2) = UVarint.decode(combinedBytes, bytes1.size)
        assertEquals(value2, decoded2)
        assertEquals(bytes2.size, read2)
    }


    @Test
    fun testDecodeUnterminatedFails() {
        val malformedBytes = Base16.decode("808080") // Unterminated sequence
        assertFailsWith<IllegalArgumentException> {
            UVarint.decode(malformedBytes)
        }
    }

    @Test
    fun testDecodeOverflowFails() {
        // This sequence would result in a shift > 63 bits for a Long
        val overflowBytes = Base16.decode("ffffffffffffffff80") // 9 bytes with continuation
        // Actually, 10 bytes with continuation is the clearer overflow for 64-bit.
        val overflowBytes10 = Base16.decode("8080808080808080808001") // 10 bytes, value is 2^70
        assertFailsWith<IllegalArgumentException>("Should fail due to overflow") {
            UVarint.decode(overflowBytes10)
        }

        val overflowBytesMax = Base16.decode("ffffffffffffffffff81") // Leads to > Long.MAX_VALUE if not careful
         assertFailsWith<IllegalArgumentException>("Should fail due to overflow for Long") {
            UVarint.decode(overflowBytesMax)
        }
    }

    @Test
    fun testDecodeEmptyInputAtOffsetFails() {
        assertFailsWith<IllegalArgumentException> {
            UVarint.decode(emptyIndexed(), 0)
        }
         assertFailsWith<IllegalArgumentException> {
            UVarint.decode(Base16.decode("0102"), 2) // Offset at end of valid data
        }
    }
}
