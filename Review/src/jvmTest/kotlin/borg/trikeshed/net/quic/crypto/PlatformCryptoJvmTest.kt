package borg.trikeshed.net.quic.crypto

import borg.trikeshed.net.quic.tls.P256_GROUP
import borg.trikeshed.net.quic.tls.TlsSignatureScheme
import borg.trikeshed.net.quic.tls.X25519_GROUP
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlatformCryptoJvmTest {

    @Test
    fun testSha256_Jvm_EmptyString() {
        val data = "".encodeToByteArray()
        val expectedHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val actualHash = sha256(data)
        assertEquals(expectedHash, actualHash.joinToString("") { "%02x".format(it) }, "SHA-256 of empty string mismatch")
    }

    @Test
    fun testSha256_Jvm_abc() {
        val data = "abc".encodeToByteArray()
        val expectedHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        val actualHash = sha256(data)
        assertEquals(expectedHash, actualHash.joinToString("") { "%02x".format(it) }, "SHA-256 of 'abc' mismatch")
    }

    @Test
    fun testGenerateEcdhKeyPair_X25519() {
        val keyPair = generateEcdhKeyPair(X25519_GROUP)
        assertNotNull(keyPair, "X25519 key pair should not be null")
        val (privateKey, publicKey) = keyPair

        assertNotNull(privateKey, "X25519 private key should not be null")
        assertNotNull(publicKey, "X25519 public key should not be null")

        // Standard X25519 keys are 32 bytes
        assertEquals(32, privateKey.size, "X25519 private key should be 32 bytes")
        assertEquals(32, publicKey.size, "X25519 public key should be 32 bytes")
        println("X25519 Private Key (first 4 bytes): ${privateKey.take(4).joinToString("") { "%02x".format(it) }}...")
        println("X25519 Public Key (first 4 bytes): ${publicKey.take(4).joinToString("") { "%02x".format(it) }}...")
    }

    @Test
    fun testGenerateEcdhKeyPair_P256() {
        val keyPair = generateEcdhKeyPair(P256_GROUP)
        assertNotNull(keyPair, "P-256 key pair should not be null")
        val (privateKey, publicKey) = keyPair

        assertNotNull(privateKey, "P-256 private key should not be null")
        assertNotNull(publicKey, "P-256 public key should not be null")

        // Lengths for P-256 keys can vary depending on encoding (PKCS#8 for private, X.509 for public)
        // but they should be non-empty and within expected ranges.
        assertTrue(privateKey.isNotEmpty(), "P-256 private key should not be empty")
        assertTrue(publicKey.isNotEmpty(), "P-256 public key should not be empty")
        // A typical uncompressed P-256 public key is 65 bytes (0x04 + x + y)
        // X.509 encoded public key for P-256 (secp256r1) is typically 91 bytes.
        // PKCS#8 encoded private key for P-256 is longer.
        println("P-256 Private Key length: ${privateKey.size} bytes")
        println("P-256 Public Key length: ${publicKey.size} bytes (X.509: ~91 bytes, Uncompressed point: 65 bytes)")
         assertTrue(publicKey.size > 60 && publicKey.size < 100, "P-256 public key has unexpected length: ${publicKey.size}")

    }

    @Test
    fun testComputeEcdhSharedSecret_RoundTrip_X25519() {
        val keyPairA = generateEcdhKeyPair(X25519_GROUP)
        val keyPairB = generateEcdhKeyPair(X25519_GROUP)

        val sharedSecretAB = computeEcdhSharedSecret(X25519_GROUP, keyPairA.first, keyPairB.second)
        val sharedSecretBA = computeEcdhSharedSecret(X25519_GROUP, keyPairB.first, keyPairA.second)

        assertNotNull(sharedSecretAB, "Shared secret AB (X25519) should not be null")
        assertNotNull(sharedSecretBA, "Shared secret BA (X25519) should not be null")
        assertTrue(sharedSecretAB.isNotEmpty(), "Shared secret AB (X25519) should not be empty")
        assertEquals(32, sharedSecretAB.size, "X25519 shared secret should be 32 bytes")


        assertContentEquals(sharedSecretAB, sharedSecretBA, "X25519 shared secrets should be identical")
        println("X25519 Shared Secret (first 4 bytes): ${sharedSecretAB.take(4).joinToString("") { "%02x".format(it) }}...")
    }

    @Test
    fun testComputeEcdhSharedSecret_RoundTrip_P256() {
        val keyPairA = generateEcdhKeyPair(P256_GROUP)
        val keyPairB = generateEcdhKeyPair(P256_GROUP)

        val sharedSecretAB = computeEcdhSharedSecret(P256_GROUP, keyPairA.first, keyPairB.second)
        val sharedSecretBA = computeEcdhSharedSecret(P256_GROUP, keyPairB.first, keyPairA.second)

        assertNotNull(sharedSecretAB, "Shared secret AB (P-256) should not be null")
        assertNotNull(sharedSecretBA, "Shared secret BA (P-256) should not be null")
        assertTrue(sharedSecretAB.isNotEmpty(), "Shared secret AB (P-256) should not be empty")
        // P-256 shared secret (x-coordinate) is 32 bytes
        assertEquals(32, sharedSecretAB.size, "P-256 shared secret should be 32 bytes")

        assertContentEquals(sharedSecretAB, sharedSecretBA, "P-256 shared secrets should be identical")
        println("P-256 Shared Secret (first 4 bytes): ${sharedSecretAB.take(4).joinToString("") { "%02x".format(it) }}...")
    }

    @Test
    fun testVerifySignature_Placeholder() {
        val dummyPublicKey = Random.nextBytes(32)
        val dummyData = "data to verify".encodeToByteArray()
        val dummySignature = Random.nextBytes(64)

        val result = verifySignature(
            groupId = X25519_GROUP, // Or any other group, doesn't matter for placeholder
            publicKeyBytes = dummyPublicKey,
            signatureScheme = TlsSignatureScheme.ECDSA_SECP256R1_SHA256, // Example scheme
            dataToVerify = dummyData,
            signature = dummySignature
        )
        assertTrue(result, "Placeholder verifySignature should return true")
    }
}
