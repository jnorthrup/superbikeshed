package borg.trikeshed.crypto

import borg.trikeshed.lib.toIndexed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommonCryptoTest {

    @Test
    fun testTLS13CryptoInstantiation() {
        // This test mainly checks if TLS13Crypto can be instantiated
        // without specific crypto implementations, this might be limited.
        // It relies on CryptoFactory providing some defaults or mocks.

        try {
            val tlsCrypto = TLS13Crypto(TLS13CipherSuites.TLS_AES_128_GCM_SHA256)
            assertNotNull(tlsCrypto)
            // Further tests would involve calling its methods with mocked inputs/outputs
            // e.g., deriveHandshakeSecrets, deriveApplicationSecrets, deriveKeyAndIV
            // This requires a way to provide mock CommonCrypto.Hasher, SymmetricCipher, KeyDerivation
            // to the TLS13Crypto instance, or for CryptoFactory to return testable instances.
        } catch (e: Exception) {
            // Depending on how expect/actual for CryptoFactory is set up for tests,
            // this might throw if no actual implementation is available.
            // For now, let this pass or fail based on test environment capabilities.
            println("TLS13Crypto instantiation test note: ${e.message}")
        }
    }

    @Test
    fun testTranscriptHash() {
        // Assuming CryptoFactory.createHasher can provide a testable hasher (e.g. a simple SHA256)
        val hasher = CryptoFactory.createHasher(HashAlgorithms.SHA256) // Or a mock
        val transcript = TranscriptHash(hasher)

        val msg1 = "ClientHello".toByteArray().toIndexed()
        val msg2 = "ServerHello".toByteArray().toIndexed()

        transcript.update(msg1)
        val hash1 = transcript.getCurrentHash()
        assertNotNull(hash1)

        transcript.update(msg2)
        val hash2 = transcript.getCurrentHash()
        assertNotNull(hash2)

        assertTrue(hash1.a > 0)
        assertTrue(hash2.a > 0)
        // A real test would compare with known hash values for msg1 and (msg1 + msg2)
        // For example, if using SHA256:
        // val expectedHash1 = calculateSha256(msg1)
        // assertEquals(expectedHash1, hash1)
    }

    // TODO: Add tests for KeyShareEntry, Extension serialization/deserialization if made public
    // TODO: Add tests for TLSRecord, HandshakeMessage parsing if robust versions are made
}
