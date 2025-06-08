package evolution

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith

// Expect function to get the actual service instance for testing
expect fun getTestAesService(testContext: CoroutineContext): AesService

class AesServiceTest {
    private val key = ByteArray(16) { 0x01.toByte() } // 16-byte key
    private val iv = ByteArray(12) { 0x02.toByte() }  // 12-byte IV for GCM
    private val aad = ByteArray(8) { 0x03.toByte() }  // Additional Authenticated Data
    private val plaintext = "Hello QUIC World! This is a test message.".encodeToByteArray()

    @Test
    fun testAesGcmEncryptDecryptRoundtrip() = runTest {
        val service = getTestAesService(this.coroutineContext + EmptyCoroutineContext)
        assertNotNull(service, "AesService should be available for testing")

        val ciphertext = service.gcmEncrypt(key, iv, plaintext, aad)
        assertNotNull(ciphertext, "Ciphertext should not be null")

        val decryptedText = service.gcmDecrypt(key, iv, ciphertext, aad)
        assertNotNull(decryptedText, "Decrypted text should not be null")

        assertContentEquals(plaintext, decryptedText, "Decrypted text should match original plaintext")
    }

    @Test
    fun testAesGcmDecryptFailureWithWrongKey() = runTest {
         val service = getTestAesService(this.coroutineContext + EmptyCoroutineContext)
        assertNotNull(service, "AesService should be available for testing")
        val wrongKey = ByteArray(16) { 0xAA.toByte() }

        val ciphertext = service.gcmEncrypt(key, iv, plaintext, aad)

        assertFailsWith<Exception> ("Decryption with wrong key should fail (expecting DecryptionFailedException or similar)") {
            // The actual exception type might be platform-specific (e.g. ActualDecryptionFailedException)
            // For commonTest, checking generic Exception or a common base class if defined.
            service.gcmDecrypt(wrongKey, iv, ciphertext, aad)
        }
    }

    @Test
    fun testAesGcmDecryptFailureWithWrongAad() = runTest {
        val service = getTestAesService(this.coroutineContext + EmptyCoroutineContext)
        assertNotNull(service, "AesService should be available for testing")
        val wrongAad = ByteArray(8) { 0xBB.toByte() }

        val ciphertext = service.gcmEncrypt(key, iv, plaintext, aad)

        assertFailsWith<Exception>("Decryption with wrong AAD should fail") {
            service.gcmDecrypt(key, iv, ciphertext, wrongAad)
        }
    }

    @Test
    fun testAesEcbEncrypt() = runTest {
        val service = getTestAesService(this.coroutineContext + EmptyCoroutineContext)
        assertNotNull(service, "AesService should be available for testing")

        // ECB needs plaintext to be multiple of block size for no padding, or padding occurs.
        // WebCrypto and OpenSSL EVP handle PKCS#7 padding by default.
        val ecbPlaintext = "This is 16 bytes".encodeToByteArray() // 16 bytes, one block
        val ecbKey = ByteArray(16) {0x0C.toByte()}

        val ciphertext = service.ecbEncrypt(ecbKey, ecbPlaintext)
        assertNotNull(ciphertext)
        // For ECB, if plaintext is block-aligned and no padding, ciphertext length might equal plaintext.
        // If padding occurs, ciphertext will be longer (e.g., 32 bytes for 16-byte input if padded to 2 blocks).
        // Exact ciphertext bytes are hard to predict without knowing padding scheme and running one impl.
        // A simple check is that it runs and produces output.
        // A roundtrip decrypt test would be better if ecbDecrypt were available.
        kotlin.test.assertTrue(ciphertext.isNotEmpty(), "ECB ciphertext should not be empty")
    }
}
