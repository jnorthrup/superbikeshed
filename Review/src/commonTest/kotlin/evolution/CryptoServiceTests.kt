package evolution.test

import evolution.AesService
import evolution.HkdfService
import kotlin.coroutines.CoroutineContext
import kotlin.test.*

// Using kotlinx.coroutines.test for runTest
import kotlinx.coroutines.test.runTest

// Common test vectors (can be expanded)
object CryptoTestVectors {
    // HKDF Test Vectors (Illustrative - replace with actual RFC vectors if possible)
    val hkdfIkm = "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b".decodeHex()
    val hkdfSalt = "000102030405060708090a0b0c".decodeHex()
    // Expected PRK for SHA-256: 0x077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5
    val hkdfExpectedPrkSha256 = "077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5".decodeHex()
    val hkdfInfo = "f0f1f2f3f4f5f6f7f8f9".decodeHex()
    // Expected OKM for SHA-256 (L=42): 0x3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865
    val hkdfExpectedOkmSha256L42 = "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865".decodeHex()

    // AES-GCM Test Vectors (Illustrative - from a known source e.g. NIST)
    // AES-128-GCM
    val aesKey = "feffe9928665731c6d6a8f9467308308".decodeHex() // 128-bit key
    val aesIv = "cafebabebeef956209995888".decodeHex()          // 12-byte IV
    val aesPlaintext = "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a721c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b39".decodeHex()
    val aesAad = "feedfacedeadbeeffeedfacedeadbeefabaddad2".decodeHex()
    // Expected Ciphertext for AES-128-GCM (Ciphertext || Tag)
    val aesExpectedCiphertextGcm = "42831ec2217774244b7221b784d0d49ce3aa212f2c02a4e035c17e2329aca12e21d514b25466931c7d8f6a5aac84aa051ba30b396a0aac973d58e091473f5985".decodeHex()
    val aesExpectedTagGcm = "5bc94fbc3221a5db94fae95ae7121a47".decodeHex() // 16-byte tag
    // Combined Ciphertext + Tag
    val aesExpectedCiphertextWithTagGcm = aesExpectedCiphertextGcm + aesExpectedTagGcm

    // AES-ECB Test Vector (Illustrative)
    // Key: 000102030405060708090a0b0c0d0e0f
    // Plaintext: 00112233445566778899aabbccddeeff
    // Ciphertext: 69c4e0d86a7b0430d8cdb78070b4c55a
    val ecbKey = "000102030405060708090a0b0c0d0e0f".decodeHex()
    val ecbPlaintext = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff".decodeHex() // 32 bytes (2 blocks)
    val ecbExpectedCiphertext = "69c4e0d86a7b0430d8cdb78070b4c55a69c4e0d86a7b0430d8cdb78070b4c55a".decodeHex() // Expected for two blocks if PKCS5Padding is NOT used, or if input is block aligned.
                                                                                                            // If PKCS5Padding is used and input is not block aligned, output will differ.
                                                                                                            // JvmAesService uses PKCS5Padding. Native uses what's in .def (likely no padding by default for raw ECB). Js doesn't support.
                                                                                                            // For testing, it's best if ECB is tested with block-aligned data if NoPadding is intended, or specific padded vectors.
                                                                                                            // Given Jvm actual uses PKCS5Padding, the test vector should reflect that or test that ECB block alignment.
                                                                                                            // Let's use a block-aligned plaintext for a more consistent ECB test across platforms that might default to NoPadding.
}

// Helper to decode hex strings to ByteArray
internal fun String.decodeHex(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

// Common tests for HkdfService
abstract class AbstractHkdfServiceTests {
    protected abstract val hkdfService: HkdfService

    @Test
    fun testHkdfExtractSha256() = runTest {
        val prk = hkdfService.extract(CryptoTestVectors.hkdfSalt, CryptoTestVectors.hkdfIkm)
        assertContentEquals(CryptoTestVectors.hkdfExpectedPrkSha256, prk, "HKDF-Extract PRK does not match expected value.")
    }

    @Test
    fun testHkdfExpandSha256() = runTest {
        val okm = hkdfService.expand(CryptoTestVectors.hkdfExpectedPrkSha256, CryptoTestVectors.hkdfInfo, 42)
        assertContentEquals(CryptoTestVectors.hkdfExpectedOkmSha256L42, okm, "HKDF-Expand OKM does not match expected value.")
    }
}

// Common tests for AesService
abstract class AbstractAesServiceTests {
    protected abstract val aesService: AesService
    protected open val supportsEcb: Boolean = true // Overridden by JS tests

    @Test
    fun testAesGcmEncryptDecrypt() = runTest {
        val ciphertextWithTag = aesService.gcmEncrypt(
            CryptoTestVectors.aesKey,
            CryptoTestVectors.aesIv,
            CryptoTestVectors.aesPlaintext,
            CryptoTestVectors.aesAad
        )
        // Verify against known ciphertext+tag if available, or just ensure decryption works
        assertContentEquals(CryptoTestVectors.aesExpectedCiphertextWithTagGcm, ciphertextWithTag, "AES-GCM Encrypted output does not match test vector.")

        val decryptedPlaintext = aesService.gcmDecrypt(
            CryptoTestVectors.aesKey,
            CryptoTestVectors.aesIv,
            ciphertextWithTag, // Pass combined ciphertext and tag
            CryptoTestVectors.aesAad
        )
        assertContentEquals(CryptoTestVectors.aesPlaintext, decryptedPlaintext, "AES-GCM Decrypted output does not match original plaintext.")
    }

    @Test
    fun testAesGcmDecryptKnownVector() = runTest {
         val decryptedPlaintext = aesService.gcmDecrypt(
            CryptoTestVectors.aesKey,
            CryptoTestVectors.aesIv,
            CryptoTestVectors.aesExpectedCiphertextWithTagGcm, // Known good ciphertext with tag
            CryptoTestVectors.aesAad
        )
        assertContentEquals(CryptoTestVectors.aesPlaintext, decryptedPlaintext, "AES-GCM Decryption of known vector failed.")
    }

    @Test
    fun testAesGcmDecryptFailsWithBadTag() = runTest {
        val badCiphertextWithAlteredTag = CryptoTestVectors.aesExpectedCiphertextWithTagGcm.copyOf()
        badCiphertextWithAlteredTag[badCiphertextWithAlteredTag.size - 1] = badCiphertextWithAlteredTag[badCiphertextWithAlteredTag.size - 1].inc() // Flip a bit in the tag

        assertFailsWith<Exception>("AES-GCM decryption should fail with altered tag.") {
            aesService.gcmDecrypt(
                CryptoTestVectors.aesKey,
                CryptoTestVectors.aesIv,
                badCiphertextWithAlteredTag,
                CryptoTestVectors.aesAad
            )
        }
    }

    @Test
    fun testAesEcbEncrypt() = runTest {
        if (!supportsEcb) {
            assertFailsWith<UnsupportedOperationException>("AES-ECB should throw UnsupportedOperationException.") {
                aesService.ecbEncrypt(CryptoTestVectors.ecbKey, CryptoTestVectors.ecbPlaintext.copyOfRange(0,16)) // Test with one block
            }
            return@runTest
        }

        // Test with a single block first, as ECB with NoPadding (common in C libraries) requires block alignment.
        // JvmAesService uses PKCS5Padding, so it can handle non-block aligned data.
        // For a common test, using block-aligned data is safer.
        val singleBlockPlaintext = CryptoTestVectors.ecbPlaintext.copyOfRange(0, 16)
        val expectedSingleBlockCiphertext = CryptoTestVectors.ecbExpectedCiphertext.copyOfRange(0, 16)

        val ciphertext = aesService.ecbEncrypt(CryptoTestVectors.ecbKey, singleBlockPlaintext)
        assertContentEquals(expectedSingleBlockCiphertext, ciphertext, "AES-ECB encryption output for single block does not match test vector.")

        // Optional: Test two blocks if platforms handle padding consistently or if input is always block-aligned.
        // Due to Jvm using PKCS5Padding and Native potentially not, this might be flaky without careful vector selection.
        // If testing PKCS5Padding: plaintext "00112233445566778899aabbccddeeff" (16 bytes) with key "0001..."
        // results in "69c4e0d86a7b0430d8cdb78070b4c55a"
        // If plaintext is "00112233445566778899aabbccddeeff01" (17 bytes), PKCS5Padding adds 15 bytes of 0x0f.
        // This level of detail is for specific padding scheme tests.
    }
}
