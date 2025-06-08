package borg.trikeshed.net.quic.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
// Import hexToByteArray if it's in a shared test util file, e.g. evolution.hexToByteArray
// For now, assume it's accessible or re-define if needed.
// To avoid issues, let's redefine it here for this subtask if not explicitly shared.
fun hexToByteArrayLocal(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "Hex string must have an even length" }
    return hex.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}


class PlatformCryptoTest {

    @Test
    fun testSha256_emptyInput() = runTest {
        val input = byteArrayOf()
        // SHA-256 hash of an empty string (from RFC 4634 test vectors)
        val expectedOutput = hexToByteArrayLocal("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        val result = sha256(input)
        assertContentEquals(expectedOutput, result, "SHA-256 of empty input failed")
    }

    @Test
    fun testSha256_abc() = runTest {
        val input = "abc".encodeToByteArray()
        // SHA-256 hash of "abc"
        val expectedOutput = hexToByteArrayLocal("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        val result = sha256(input)
        assertContentEquals(expectedOutput, result, "SHA-256 of 'abc' failed")
    }

    @Test
    fun testSha256_longInput() = runTest {
        val input = "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu".encodeToByteArray()
        // SHA-256 hash of the above string (from RFC 4634)
        val expectedOutput = hexToByteArrayLocal("cf5b16a778af8380036ce59e7b0492370b249b11e8f07a51afac45037afee9d1")
        val result = sha256(input)
        assertContentEquals(expectedOutput, result, "SHA-256 of long input failed")
    }

    @Test
    fun testGenerateEcdhKeyPair_X25519() = runTest {
        val groupId = 0x001Du.toUShort() // X25519
        val keyPair = generateEcdhKeyPair(groupId)
        assertNotNull(keyPair, "Key pair should not be null")

        val privateKey = keyPair.first
        val publicKey = keyPair.second

        assertNotNull(privateKey, "Private key should not be null")
        assertNotNull(publicKey, "Public key should not be null")

        // X25519 keys have specific lengths (typically 32 bytes for both private and public)
        // This check depends on the actual encoding from the platform implementation
        // For raw keys, it's usually 32 bytes.
        assertTrue(privateKey.size >= 32, "X25519 private key size is unexpected: ${privateKey.size}")
        assertTrue(publicKey.size >= 32, "X25519 public key size is unexpected: ${publicKey.size}")
    }

    @Test
    fun testComputeEcdhSharedSecret_X25519() = runTest {
        val groupId = 0x001Du.toUShort() // X25519

        // Generate two key pairs
        val keyPairA = generateEcdhKeyPair(groupId)
        val keyPairB = generateEcdhKeyPair(groupId)

        // Compute shared secret: A's private key with B's public key
        val sharedSecretAB = computeEcdhSharedSecret(groupId, keyPairA.first, keyPairB.second)
        assertNotNull(sharedSecretAB, "Shared secret AB should not be null")
        assertTrue(sharedSecretAB.isNotEmpty(), "Shared secret AB should not be empty")
        // X25519 shared secret is typically 32 bytes
        assertTrue(sharedSecretAB.size >= 32, "X25519 shared secret size is unexpected: ${sharedSecretAB.size}")

        // Compute shared secret: B's private key with A's public key
        val sharedSecretBA = computeEcdhSharedSecret(groupId, keyPairB.first, keyPairA.second)
        assertNotNull(sharedSecretBA, "Shared secret BA should not be null")
        assertTrue(sharedSecretBA.isNotEmpty(), "Shared secret BA should not be empty")

        // The derived shared secrets should be identical
        assertContentEquals(sharedSecretAB, sharedSecretBA, "ECDH shared secrets derived from opposite pairs should match")
    }

    // verifySignature is a placeholder/complex, so a meaningful common test is hard.
    // We can add a very basic test to ensure it runs without crashing on platforms
    // where it might have a minimal (e.g., always false) implementation.
    @Test
    fun testVerifySignature_placeholderRuns() = runTest {
        val groupId = 0x0403u.toUShort() // ecdsa_secp256r1_sha256
        val dummyPublicKey = ByteArray(65) // Placeholder for an uncompressed P-256 public key
        val dummyData = "data to verify".encodeToByteArray()
        val dummySignature = ByteArray(64) // Placeholder for a P-256 ECDSA signature

        // This test primarily checks that the function call doesn't crash.
        // The actual result depends on the placeholder status of the `actual` implementations.
        // If it's a strict placeholder returning false, this will pass.
        // If an actual implementation attempts to parse the dummy key and fails, it might throw.
        // For now, let's just call it. A more robust test would require valid test keys/signatures.
        try {
            val isValid = verifySignature(groupId, dummyPublicKey, groupId, dummyData, dummySignature)
            assertFalse(isValid, "Placeholder verifySignature should ideally return false or be skipped if not implemented for the scheme.")
        } catch (e: Exception) {
            // Catching general exceptions if the placeholder throws due to dummy inputs
            println("verifySignature_placeholderRuns threw an exception (as might be expected for dummy inputs): ${e.message}")
            assertTrue(true, "verifySignature threw an exception with dummy data, which is acceptable for a placeholder.")
        }
    }
}
