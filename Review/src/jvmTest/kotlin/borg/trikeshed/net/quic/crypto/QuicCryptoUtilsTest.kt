package borg.trikeshed.net.quic.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QuicCryptoUtilsTest {

    @Test
    fun `computeNonce correctly XORs base IV with packet number`() {
        // Example from RFC 9001, Appendix A.2. Initial Packet
        // Packet Number: 2
        // IV: 0x0c7c8f7556785688e09c3350 (example, actual IV depends on hkdfExpandLabel)
        // For testing, let's use a known IV and PN.
        // Let base IV be: 00 01 02 03 04 05 06 07 08 09 0A 0B
        val baseIv = ByteArray(12) { it.toByte() }

        // Packet Number 0
        // Padded PN: 00 00 00 00 00 00 00 00 00 00 00 00
        // Expected Nonce: Same as baseIv
        var packetNumber = 0L
        var expectedNonce = baseIv.clone()
        var actualNonce = computeNonce(baseIv, packetNumber)
        assertContentEquals(expectedNonce, actualNonce, "Nonce for PN 0 should be same as base IV")

        // Packet Number 1
        // Padded PN: 00 00 00 00 00 00 00 00 00 00 00 01
        // Expected Nonce: baseIv with last byte XORed by 1
        packetNumber = 1L
        expectedNonce = baseIv.clone()
        expectedNonce[11] = (expectedNonce[11].toInt() xor 0x01).toByte()
        actualNonce = computeNonce(baseIv, packetNumber)
        assertContentEquals(expectedNonce, actualNonce, "Nonce for PN 1 check failed")

        // Packet Number 256 (0x0100)
        // Padded PN: 00 00 00 00 00 00 00 00 00 00 01 00
        // Expected Nonce: baseIv with byte 10 XORed by 0x01, byte 11 XORed by 0x00
        packetNumber = 256L
        expectedNonce = baseIv.clone()
        expectedNonce[10] = (expectedNonce[10].toInt() xor 0x01).toByte()
        expectedNonce[11] = (expectedNonce[11].toInt() xor 0x00).toByte() // Stays same
        actualNonce = computeNonce(baseIv, packetNumber)
        assertContentEquals(expectedNonce, actualNonce, "Nonce for PN 256 check failed")

        // Packet Number requiring more than one byte (e.g., 0x12345678)
        // Padded PN: 00 00 00 00 00 00 00 00 12 34 56 78
        packetNumber = 0x12345678L
        expectedNonce = baseIv.clone()
        expectedNonce[8] = (expectedNonce[8].toInt() xor 0x12).toByte()
        expectedNonce[9] = (expectedNonce[9].toInt() xor 0x34).toByte()
        expectedNonce[10] = (expectedNonce[10].toInt() xor 0x56).toByte()
        expectedNonce[11] = (expectedNonce[11].toInt() xor 0x78).toByte()
        actualNonce = computeNonce(baseIv, packetNumber)
        assertContentEquals(expectedNonce, actualNonce, "Nonce for PN 0x12345678 check failed")

        // Max Long value for packet number (should use last 8 bytes)
        // Padded PN: 00 00 00 00 FF FF FF FF FF FF FF FF (if PN is 0xFFFFFFFFFFFFFFFF)
        // For this test, let PN be 0x0123456789ABCDEF
        packetNumber = 0x0123456789ABCDEFL
        expectedNonce = baseIv.clone()
        expectedNonce[4] = (expectedNonce[4].toInt() xor 0x01).toByte()
        expectedNonce[5] = (expectedNonce[5].toInt() xor 0x23).toByte()
        expectedNonce[6] = (expectedNonce[6].toInt() xor 0x45).toByte()
        expectedNonce[7] = (expectedNonce[7].toInt() xor 0x67).toByte()
        expectedNonce[8] = (expectedNonce[8].toInt() xor 0x89).toByte()
        expectedNonce[9] = (expectedNonce[9].toInt() xor 0xAB).toByte()
        expectedNonce[10] = (expectedNonce[10].toInt() xor 0xCD).toByte()
        expectedNonce[11] = (expectedNonce[11].toInt() xor 0xEF).toByte()
        actualNonce = computeNonce(baseIv, packetNumber)
        assertContentEquals(expectedNonce, actualNonce, "Nonce for large PN check failed")
    }

    @Test
    fun `computeNonce should require 12-byte base IV`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            computeNonce(ByteArray(11), 0L)
        }
        assertEquals("Base IV must be 12 bytes long. Was: 11", exception.message)

        val exception2 = assertFailsWith<IllegalArgumentException> {
            computeNonce(ByteArray(13), 0L)
        }
        assertEquals("Base IV must be 12 bytes long. Was: 13", exception2.message)
    }
}
```
