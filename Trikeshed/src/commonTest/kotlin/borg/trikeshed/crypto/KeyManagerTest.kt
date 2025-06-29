package borg.trikeshed.crypto

import kotlin.test.*

class KeyManagerTest {

    @Test
    fun testInMemoryKeyManager_KeyPairOperations() {
        val keyManager: KeyManager = InMemoryKeyManager()
        val alias = "testKeyEd25519"
        val algorithm = SignatureAlgorithms.ED25519

        // 1. Generate Key Pair
        assertFalse(keyManager.containsKey(alias))
        val publicKey = keyManager.generateKeyPair(algorithm, alias)
        assertNotNull(publicKey, "Public key should be generated")
        assertTrue(keyManager.containsKey(alias), "Key should exist after generation")

        // 2. Retrieve Public Key
        val retrievedPublicKey = keyManager.getPublicKey(alias)
        assertNotNull(retrievedPublicKey)
        // TODO: Compare publicKey and retrievedPublicKey if Indexed<Byte> supports contentEquals
        // assertEquals(publicKey, retrievedPublicKey)
        assertTrue(publicKey!!.a > 0, "Public key should not be empty")


        // 3. Retrieve Private Key
        val privateKey = keyManager.getPrivateKey(alias)
        assertNotNull(privateKey, "Private key should be retrievable")
        assertTrue(privateKey.a > 0, "Private key should not be empty")


        // 4. Try generating again with same alias (should fail or return existing)
        // InMemoryKeyManager's generateKeyPair returns null if alias exists.
        val newPublicKey = keyManager.generateKeyPair(algorithm, alias)
        assertNull(newPublicKey, "Generating with existing alias should fail or return null for this impl")

        // 5. Delete Key
        assertTrue(keyManager.deleteKey(alias))
        assertFalse(keyManager.containsKey(alias), "Key should not exist after deletion")
        assertNull(keyManager.getPublicKey(alias))
        assertNull(keyManager.getPrivateKey(alias))
    }

    @Test
    fun testInMemoryKeyManager_SymmetricKeyOperations() {
        val keyManager: KeyManager = InMemoryKeyManager()
        val alias = "testSymmetricKey"

        // Create a dummy symmetric key (e.g., 32 bytes)
        val crypto = CryptoFactory.getSecureRandom() // Assuming this is available for tests
        val symmetricKey = crypto.randomBytes(32)

        // 1. Store Symmetric Key
        assertFalse(keyManager.containsKey(alias))
        assertTrue(keyManager.storeSymmetricKey(symmetricKey, alias))
        assertTrue(keyManager.containsKey(alias))

        // 2. Retrieve Symmetric Key
        val retrievedKey = keyManager.getSymmetricKey(alias)
        assertNotNull(retrievedKey)
        assertEquals(symmetricKey.a, retrievedKey.a)
        // TODO: Compare content if Indexed<Byte> supports contentEquals
        // assertTrue(symmetricKey.contentEquals(retrievedKey))


        // 3. Overwrite Symmetric Key
        val newSymmetricKey = crypto.randomBytes(32)
        assertTrue(keyManager.storeSymmetricKey(newSymmetricKey, alias))
        val overwrittenKey = keyManager.getSymmetricKey(alias)
        assertNotNull(overwrittenKey)
        // TODO: Check if overwrittenKey is indeed newSymmetricKey
        // assertFalse(symmetricKey.contentEquals(overwrittenKey))
        // assertTrue(newSymmetricKey.contentEquals(overwrittenKey))


        // 4. Delete Key
        assertTrue(keyManager.deleteKey(alias))
        assertFalse(keyManager.containsKey(alias))
        assertNull(keyManager.getSymmetricKey(alias))
    }

    @Test
    fun testDeleteNonExistentKey() {
        val keyManager: KeyManager = InMemoryKeyManager()
        val alias = "nonExistentKey"
        assertFalse(keyManager.deleteKey(alias))
    }
}
