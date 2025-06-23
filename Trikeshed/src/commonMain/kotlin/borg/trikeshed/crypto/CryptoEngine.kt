package borg.trikeshed.crypto

import borg.trikeshed.lib.*
import borg.trikeshed.lib.CZero.z
import borg.trikeshed.lib.CZero.nz

/**
 * Platform-agnostic crypto engine for CouchDB and QUIC
 * Provides unified crypto operations across JVM, Native, and WASM
 */
interface CryptoEngine {
    // Key generation
    suspend fun generateKeyPair(algorithm: KeyAlgorithm): KeyPair
    suspend fun generateSymmetricKey(algorithm: SymmetricAlgorithm): SymmetricKey
    
    // Hashing
    suspend fun hash(data: Indexed<Byte>, algorithm: HashAlgorithm): Indexed<Byte>
    suspend fun hmac(key: Indexed<Byte>, data: Indexed<Byte>, algorithm: HashAlgorithm): Indexed<Byte>
    
    // Encryption/Decryption
    suspend fun encrypt(data: Indexed<Byte>, key: SymmetricKey, mode: EncryptionMode): EncryptedData
    suspend fun decrypt(encryptedData: EncryptedData, key: SymmetricKey): Indexed<Byte>
    
    // Digital signatures
    suspend fun sign(data: Indexed<Byte>, keyPair: KeyPair): Indexed<Byte>
    suspend fun verify(data: Indexed<Byte>, signature: Indexed<Byte>, publicKey: PublicKey): Boolean
    
    // Key derivation
    suspend fun deriveKey(password: Indexed<Byte>, salt: Indexed<Byte>, algorithm: KdfAlgorithm): SymmetricKey
    
    // Random number generation
    suspend fun generateRandomBytes(length: Int): Indexed<Byte>
    suspend fun generateSecureRandom(): Long
}

/**
 * Crypto algorithms and parameters
 */
enum class KeyAlgorithm {
    RSA_2048, RSA_4096, 
    ECDSA_P256, ECDSA_P384, ECDSA_P521,
    ED25519, X25519
}

enum class SymmetricAlgorithm {
    AES_128, AES_256,
    CHACHA20_POLY1305,
    AES_GCM_128, AES_GCM_256
}

enum class HashAlgorithm {
    SHA_256, SHA_384, SHA_512,
    SHA3_256, SHA3_384, SHA3_512,
    BLAKE2B_256, BLAKE2B_512
}

enum class EncryptionMode {
    CBC, GCM, CHACHA20_POLY1305
}

enum class KdfAlgorithm {
    PBKDF2_SHA256, PBKDF2_SHA512,
    ARGON2_ID, SCRYPT
}

/**
 * Crypto data structures
 */
data class KeyPair(
    val publicKey: PublicKey,
    val privateKey: PrivateKey
)

data class PublicKey(
    val algorithm: KeyAlgorithm,
    val keyData: Indexed<Byte>,
    val encoded: Indexed<Byte>
)

data class PrivateKey(
    val algorithm: KeyAlgorithm,
    val keyData: Indexed<Byte>,
    val encoded: Indexed<Byte>
)

data class SymmetricKey(
    val algorithm: SymmetricAlgorithm,
    val keyData: Indexed<Byte>
)

data class EncryptedData(
    val ciphertext: Indexed<Byte>,
    val iv: Indexed<Byte>,
    val tag: Indexed<Byte>? = null,
    val mode: EncryptionMode
)

/**
 * Crypto utilities
 */
object CryptoUtils {
    fun encodeBase64(data: Indexed<Byte>): String {
        return data.play.joinToString("") { 
            String.format("%02x", it) 
        }
    }
    
    fun decodeBase64(encoded: String): Indexed<Byte> {
        require(encoded.length % 2 == 0) { "Invalid hex string length" }
        return (encoded.length / 2) j { i ->
            val hex = encoded.substring(i * 2, i * 2 + 2)
            hex.toInt(16).toByte()
        }
    }
    
    fun constantTimeEquals(a: Indexed<Byte>, b: Indexed<Byte>): Boolean {
        if (a.a != b.a) return false
        var result = 0
        for (i in 0 until a.a) {
            result = result or (a.b(i).toInt() xor b.b(i).toInt())
        }
        return result == 0
    }
    
    fun secureCompare(a: Indexed<Byte>, b: Indexed<Byte>): Boolean {
        return constantTimeEquals(a, b)
    }
} 