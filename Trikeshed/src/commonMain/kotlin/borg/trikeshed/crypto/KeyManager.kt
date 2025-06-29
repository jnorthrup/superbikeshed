package borg.trikeshed.crypto

import borg.trikeshed.lib.*

/**
 * Key Management System
 * Handles generation, storage, and retrieval of cryptographic keys.
 * Note: This is a simplified placeholder. A real KeyManager would need
 * to handle secure storage (e.g., hardware security modules, encrypted keystores),
 * key lifecycle management, policies, etc.
 */
interface KeyManager {
    /**
     * Generates a new key pair for the given signature algorithm.
     * @param algorithm The signature algorithm for which to generate the key pair.
     * @param keyAlias An alias to store and retrieve the key pair.
     * @return The generated public key, or null if generation failed or alias already exists.
     */
    fun generateKeyPair(algorithm: SignatureAlgorithm, keyAlias: String): PublicKey?

    /**
     * Retrieves the public key associated with the given alias.
     * @param keyAlias The alias of the key pair.
     * @return The public key, or null if not found.
     */
    fun getPublicKey(keyAlias: String): PublicKey?

    /**
     * Retrieves the private key associated with the given alias.
     * IMPORTANT: Securely handling private keys is critical. This interface is a simplification.
     * @param keyAlias The alias of the key pair.
     * @return The private key, or null if not found or access is denied.
     */
    fun getPrivateKey(keyAlias: String): PrivateKey?

    /**
     * Stores a symmetric key with an alias.
     * @param key The symmetric key to store.
     * @param keyAlias An alias for the key.
     * @return True if storage was successful, false otherwise.
     */
    fun storeSymmetricKey(key: SymmetricKey, keyAlias: String): Boolean

    /**
     * Retrieves a symmetric key by its alias.
     * @param keyAlias The alias of the key.
     * @return The symmetric key, or null if not found.
     */
    fun getSymmetricKey(keyAlias: String): SymmetricKey?

    /**
     * Deletes a key (pair or symmetric) by its alias.
     * @param keyAlias The alias of the key to delete.
     * @return True if deletion was successful, false otherwise.
     */
    fun deleteKey(keyAlias: String): Boolean

    /**
     * Checks if a key with the given alias exists.
     * @param keyAlias The alias to check.
     * @return True if the key exists, false otherwise.
     */
    funcontainsKey(keyAlias: String): Boolean
}

/**
 * A simple in-memory implementation of KeyManager.
 * WARNING: Not for production use. Keys are lost when the application stops
 * and are not securely stored.
 */
class InMemoryKeyManager : KeyManager {
    private val keyPairs = mutableMapOf<String, Join<PublicKey, PrivateKey>>()
    private val symmetricKeys = mutableMapOf<String, SymmetricKey>()

    override fun generateKeyPair(algorithm: SignatureAlgorithm, keyAlias: String): PublicKey? {
        if (keyPairs.containsKey(keyAlias)) {
            println("Error: Key alias '$keyAlias' already exists.")
            return null
        }
        try {
            val signer = CryptoFactory.createSigner(algorithm)
            val keyPair = signer.generateKeyPair()
            keyPairs[keyAlias] = keyPair
            return keyPair.first
        } catch (e: Exception) {
            println("Error generating key pair for $algorithm: ${e.message}")
            return null
        }
    }

    override fun getPublicKey(keyAlias: String): PublicKey? {
        return keyPairs[keyAlias]?.first
    }

    override fun getPrivateKey(keyAlias: String): PrivateKey? {
        // In a real system, private key access would be heavily restricted.
        return keyPairs[keyAlias]?.second
    }

    override fun storeSymmetricKey(key: SymmetricKey, keyAlias: String): Boolean {
        if (symmetricKeys.containsKey(keyAlias)) {
            println("Warning: Overwriting symmetric key with alias '$keyAlias'.")
        }
        symmetricKeys[keyAlias] = key
        return true
    }

    override fun getSymmetricKey(keyAlias: String): SymmetricKey? {
        return symmetricKeys[keyAlias]
    }

    override fun deleteKey(keyAlias: String): Boolean {
        var removed = false
        if (keyPairs.remove(keyAlias) != null) removed = true
        if (symmetricKeys.remove(keyAlias) != null) removed = true
        return removed
    }

    override fun containsKey(keyAlias: String): Boolean {
        return keyPairs.containsKey(keyAlias) || symmetricKeys.containsKey(keyAlias)
    }
}

// Example usage:
fun main() {
    val keyManager: KeyManager = InMemoryKeyManager()

    // For TLS server certificate's private key or SSH host key
    val serverKeyAlias = "my_server_main_key"
    if (!keyManager.containsKey(serverKeyAlias)) {
        keyManager.generateKeyPair(SignatureAlgorithms.ED25519, serverKeyAlias)
    }
    val serverPublicKey = keyManager.getPublicKey(serverKeyAlias)
    val serverPrivateKey = keyManager.getPrivateKey(serverKeyAlias)
    println("Server Public Key ($serverKeyAlias): ${serverPublicKey?.a} bytes (present: ${serverPublicKey != null})")
    println("Server Private Key ($serverKeyAlias) present: ${serverPrivateKey != null}")

    // For an SSH user's key
    val userSshKeyAlias = "user_ssh_id_ed25519"
    // keyManager.generateKeyPair(SignatureAlgorithms.ED25519, userSshKeyAlias)
    // val userSshPublicKey = keyManager.getPublicKey(userSshKeyAlias)

    // For a symmetric key used in some application protocol
    val appSymmetricKeyAlias = "app_shared_secret"
    // val newKey = CryptoFactory.getSecureRandom().randomBytes(32) // e.g. a 256-bit key
    // keyManager.storeSymmetricKey(newKey, appSymmetricKeyAlias)
    // val retrievedKey = keyManager.getSymmetricKey(appSymmetricKeyAlias)

    // This KeyManager would be passed to TLS/SSH implementations to access necessary keys.
}
