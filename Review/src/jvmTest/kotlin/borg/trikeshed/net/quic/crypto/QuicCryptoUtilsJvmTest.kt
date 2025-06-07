package borg.trikeshed.net.quic.crypto // Matching the location of TestCryptoServices

import borg.trikeshed.net.quic.tls.TLS_VERSION_1_3 // For potential context construction
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class QuicCryptoUtilsJvmTest {

    @Test
    fun testHkdfExpandLabel() = runBlocking {
        val hkdfService = TestHkdfService() // Using the TestHkdfService from the same (jvmTest) source set
        val secret = "test_secret".encodeToByteArray()
        val labelString = "test label"
        val context = "test_context".encodeToByteArray()
        val length = 32

        val result = hkdfExpandLabel(hkdfService, secret, labelString, context, length)

        // Based on TestHkdfService.expand: prk + info + length_bytes (where prk is 'secret' here)
        // And HkdfExpandLabel constructs info as: length_bytes (2) + "tls13 " + label_bytes (len-prefixed) + context_bytes (len-prefixed)

        val tlsLabelPrefix = "tls13 "
        val fullLabel = tlsLabelPrefix + labelString
        val labelBytes = fullLabel.encodeToByteArray()

        val expectedInfo = ByteArray(2 + 1 + labelBytes.size + 1 + context.size)
        var offset = 0
        expectedInfo[offset++] = (length shr 8).toByte()
        expectedInfo[offset++] = length.toByte()
        expectedInfo[offset++] = labelBytes.size.toByte()
        labelBytes.copyInto(expectedInfo, offset); offset += labelBytes.size
        expectedInfo[offset++] = context.size.toByte()
        context.copyInto(expectedInfo, offset)

        // TestHkdfService.expand behavior is: secret + expectedInfo, then padded/truncated to 'length'
        val tempExpected = secret + expectedInfo
        val expectedResult = ByteArray(length)
        for (i in 0 until length) {
            expectedResult[i] = tempExpected.getOrElse(i % tempExpected.size) { 0.toByte() }
        }

        assertContentEquals(expectedResult, result, "hkdfExpandLabel output mismatch")
    }

    @Test
    fun testHkdfExpandLabel_EmptyContext() = runBlocking {
        val hkdfService = TestHkdfService()
        val secret = "another_secret".encodeToByteArray()
        val labelString = "another label"
        val context = byteArrayOf() // Empty context
        val length = 16

        val result = hkdfExpandLabel(hkdfService, secret, labelString, context, length)

        val tlsLabelPrefix = "tls13 "
        val fullLabel = tlsLabelPrefix + labelString
        val labelBytes = fullLabel.encodeToByteArray()

        val expectedInfo = ByteArray(2 + 1 + labelBytes.size + 1 + context.size)
        var offset = 0
        expectedInfo[offset++] = (length shr 8).toByte()
        expectedInfo[offset++] = length.toByte()
        expectedInfo[offset++] = labelBytes.size.toByte()
        labelBytes.copyInto(expectedInfo, offset); offset += labelBytes.size
        expectedInfo[offset++] = context.size.toByte()
        // context.copyInto not called as context is empty

        val tempExpected = secret + expectedInfo
        val expectedResult = ByteArray(length)
        for (i in 0 until length) {
            expectedResult[i] = tempExpected.getOrElse(i % tempExpected.size) { 0.toByte() }
        }

        assertContentEquals(expectedResult, result, "hkdfExpandLabel with empty context output mismatch")
    }


    @Test
    fun testHmacSha256() = runBlocking {
        val hkdfService = TestHkdfService() // Uses TestHkdfService.extract
        val key = "test_key".encodeToByteArray()
        val data = "test_data".encodeToByteArray()

        val result = hmacSha256(hkdfService, key, data)

        // TestHkdfService.extract behavior is: salt + ikm (key + data here), then truncated if > 32
        var expectedResult = key + data
        if (expectedResult.size > 32) {
            expectedResult = expectedResult.sliceArray(0..31)
        }

        assertContentEquals(expectedResult, result, "hmacSha256 output mismatch based on TestHkdfService.extract")
    }
}
