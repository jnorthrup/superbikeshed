package gk.kademlia.security

// Marker interface for PublicKey
interface PublicKey {
    fun getEncoded(): ByteArray
    // Optionally, a type identifier if multiple crypto systems are used
    // val type: String
}

// Marker interface for PrivateKey
interface PrivateKey {
    fun getEncoded(): ByteArray
    // Optionally, a type identifier
    // val type: String
}

data class KeyPair(val publicKey: PublicKey, val privateKey: PrivateKey)

interface CryptoService {
    /**
     * Generates a new cryptographic key pair.
     */
    fun generateKeyPair(): KeyPair

    /**
     * Computes a hash of the given data.
     * @param data The input data to hash.
     * @param algorithm The hashing algorithm to use (e.g., "SHA-256", "SHA-512").
     * @return The computed hash as a ByteArray.
     */
    fun hash(data: ByteArray, algorithm: String = "SHA-256"): ByteArray

    /**
     * Signs the given data using the provided private key.
     * @param data The data to sign.
     * @param privateKey The private key to use for signing.
     * @return The signature as a ByteArray.
     */
    fun sign(data: ByteArray, privateKey: PrivateKey): ByteArray

    /**
     * Verifies a signature against the given data and public key.
     * @param data The original data that was signed.
     * @param signature The signature to verify.
     * @param publicKey The public key to use for verification.
     * @return True if the signature is valid, false otherwise.
     */
    fun verifySignature(data: ByteArray, signature: ByteArray, publicKey: PublicKey): Boolean

    /**
     * Encodes a public key into a ByteArray for serialization or transmission.
     * @param publicKey The public key to encode.
     * @return The encoded public key as a ByteArray.
     */
    fun encodePublicKey(publicKey: PublicKey): ByteArray

    /**
     * Decodes a public key from its ByteArray representation.
     * @param encoded The ByteArray containing the encoded public key.
     * @return The decoded PublicKey object.
     * @throws IllegalArgumentException if the encoded data is invalid or unsupported.
     */
    fun decodePublicKey(encoded: ByteArray): PublicKey
}
