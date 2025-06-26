@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.crypto

import borg.trikeshed.lib.*
import kotlin.jvm.JvmInline

// === CRYPTO TAXONOMICAL TYPEALIASES ===

// Key types
typealias CryptoKey = Indexed<Byte>
typealias PublicKey = CryptoKey
typealias PrivateKey = CryptoKey
typealias SymmetricKey = CryptoKey
typealias SessionKey = CryptoKey

// Crypto primitives
typealias CipherText = Indexed<Byte>
typealias PlainText = Indexed<Byte>
typealias Nonce = Indexed<Byte>
typealias Tag = Indexed<Byte>
typealias Salt = Indexed<Byte>
typealias Hash = Indexed<Byte>
typealias Signature = Indexed<Byte>

// Algorithm identifiers
@JvmInline value class CipherSuite(val value: Int)
@JvmInline value class HashAlgorithm(val value: String)
@JvmInline value class KeyExchangeAlgorithm(val value: String)
@JvmInline value class SignatureAlgorithm(val value: String)
@JvmInline value class KeyDerivationFunction(val value: String)

// Crypto parameters
@JvmInline value class KeyLength(val bits: Int)
@JvmInline value class NonceLength(val bytes: Int)
@JvmInline value class TagLength(val bytes: Int)
@JvmInline value class SaltLength(val bytes: Int)

/**
 * Common crypto interface for multiplatform use
 * Provides abstractions for TLS 1.3 cryptographic operations
 */
interface CommonCrypto {
    
    /**
     * Generate cryptographically secure random bytes
     */
    fun randomBytes(length: Int): Indexed<Byte>
    
    /**
     * Hash functions
     */
    interface Hasher {
        val algorithm: HashAlgorithm
        fun hash(data: PlainText): Hash
        fun hmac(key: SymmetricKey, data: PlainText): Hash
    }
    
    /**
     * Symmetric encryption (AEAD)
     */
    interface SymmetricCipher {
        val algorithm: String
        val keyLength: KeyLength
        val nonceLength: NonceLength
        val tagLength: TagLength
        
        fun encrypt(
            key: SymmetricKey,
            nonce: Nonce,
            plaintext: PlainText,
            additionalData: Indexed<Byte> = 0 j { 0.toByte() }
        ): Join<CipherText, Tag>
        
        fun decrypt(
            key: SymmetricKey,
            nonce: Nonce,
            ciphertext: CipherText,
            tag: Tag,
            additionalData: Indexed<Byte> = 0 j { 0.toByte() }
        ): PlainText?
    }
    
    /**
     * Key exchange algorithms
     */
    interface KeyExchange {
        val algorithm: KeyExchangeAlgorithm
        
        fun generateKeyPair(): Join<PublicKey, PrivateKey>
        fun computeSharedSecret(privateKey: PrivateKey, publicKey: PublicKey): SessionKey
    }
    
    /**
     * Digital signatures
     */
    interface Signer {
        val algorithm: SignatureAlgorithm
        
        fun generateKeyPair(): Join<PublicKey, PrivateKey>
        fun sign(privateKey: PrivateKey, message: PlainText): Signature
        fun verify(publicKey: PublicKey, message: PlainText, signature: Signature): Boolean
    }
    
    /**
     * Key derivation
     */
    interface KeyDerivation {
        val function: KeyDerivationFunction
        
        fun deriveKey(
            secret: SessionKey,
            salt: Salt,
            info: Indexed<Byte>,
            length: KeyLength
        ): SymmetricKey
        
        fun expandLabel(
            secret: SessionKey,
            label: String,
            context: Indexed<Byte>,
            length: KeyLength
        ): SymmetricKey
    }
}

/**
 * TLS 1.3 Cipher Suites (RFC 8446)
 */
object TLS13CipherSuites {
    val TLS_AES_128_GCM_SHA256 = CipherSuite(0x1301)
    val TLS_AES_256_GCM_SHA384 = CipherSuite(0x1302)
    val TLS_CHACHA20_POLY1305_SHA256 = CipherSuite(0x1303)
    val TLS_AES_128_CCM_SHA256 = CipherSuite(0x1304)
    val TLS_AES_128_CCM_8_SHA256 = CipherSuite(0x1305)
}

/**
 * Hash algorithms
 */
object HashAlgorithms {
    val SHA256 = HashAlgorithm("SHA-256")
    val SHA384 = HashAlgorithm("SHA-384")
    val SHA512 = HashAlgorithm("SHA-512")
}

/**
 * Key exchange algorithms
 */
object KeyExchangeAlgorithms {
    val X25519 = KeyExchangeAlgorithm("X25519")
    val X448 = KeyExchangeAlgorithm("X448")
    val SECP256R1 = KeyExchangeAlgorithm("secp256r1")
    val SECP384R1 = KeyExchangeAlgorithm("secp384r1")
    val SECP521R1 = KeyExchangeAlgorithm("secp521r1")
}

/**
 * Signature algorithms
 */
object SignatureAlgorithms {
    val RSA_PSS_RSAE_SHA256 = SignatureAlgorithm("rsa_pss_rsae_sha256")
    val RSA_PSS_RSAE_SHA384 = SignatureAlgorithm("rsa_pss_rsae_sha384")
    val RSA_PSS_RSAE_SHA512 = SignatureAlgorithm("rsa_pss_rsae_sha512")
    val ECDSA_SECP256R1_SHA256 = SignatureAlgorithm("ecdsa_secp256r1_sha256")
    val ECDSA_SECP384R1_SHA384 = SignatureAlgorithm("ecdsa_secp384r1_sha384")
    val ECDSA_SECP521R1_SHA512 = SignatureAlgorithm("ecdsa_secp521r1_sha512")
    val ED25519 = SignatureAlgorithm("ed25519")
    val ED448 = SignatureAlgorithm("ed448")
}

/**
 * Factory for creating crypto implementations
 */
expect object CryptoFactory {
    fun createHasher(algorithm: HashAlgorithm): CommonCrypto.Hasher
    fun createSymmetricCipher(suite: CipherSuite): CommonCrypto.SymmetricCipher
    fun createKeyExchange(algorithm: KeyExchangeAlgorithm): CommonCrypto.KeyExchange
    fun createSigner(algorithm: SignatureAlgorithm): CommonCrypto.Signer
    fun createKeyDerivation(): CommonCrypto.KeyDerivation
    fun getSecureRandom(): CommonCrypto
}

/**
 * TLS 1.3 specific crypto operations
 */
class TLS13Crypto(
    private val cipherSuite: CipherSuite,
    private val crypto: CommonCrypto = CryptoFactory.getSecureRandom()
) {
    private val hasher: CommonCrypto.Hasher
    private val cipher: CommonCrypto.SymmetricCipher
    private val kdf: CommonCrypto.KeyDerivation
    
    init {
        val (hashAlg, _) = getCipherSuiteParams(cipherSuite)
        hasher = CryptoFactory.createHasher(hashAlg)
        cipher = CryptoFactory.createSymmetricCipher(cipherSuite)
        kdf = CryptoFactory.createKeyDerivation()
    }
    
    /**
     * Derive handshake secrets (RFC 8446 Section 7.1)
     */
    fun deriveHandshakeSecrets(
        sharedSecret: SessionKey,
        clientHello: Indexed<Byte>,
        serverHello: Indexed<Byte>
    ): HandshakeSecrets {
        // Early Secret = HKDF-Extract(0, 0)
        val earlySecret = kdf.deriveKey(
            secret = 0 j { 0.toByte() },
            salt = 0 j { 0.toByte() },
            info = 0 j { 0.toByte() },
            length = KeyLength(hasher.algorithm.value.substringAfterLast("-").toInt() / 8)
        )
        
        // Derive-Secret(Secret, Label, Messages)
        val derivedSecret = kdf.expandLabel(
            secret = earlySecret,
            label = "derived",
            context = hasher.hash(0 j { 0.toByte() }),
            length = KeyLength(hasher.algorithm.value.substringAfterLast("-").toInt() / 8)
        )
        
        // Handshake Secret = HKDF-Extract(Derive-Secret(...), shared_secret)
        val handshakeSecret = kdf.deriveKey(
            secret = sharedSecret,
            salt = derivedSecret,
            info = 0 j { 0.toByte() },
            length = KeyLength(hasher.algorithm.value.substringAfterLast("-").toInt() / 8)
        )
        
        // Calculate handshake hash
        val handshakeHash = hasher.hash(
            (clientHello.a + serverHello.a) j { i: Int ->
                if (i < clientHello.a) clientHello[i]
                else serverHello[i - clientHello.a]
            }
        )
        
        // Client Handshake Traffic Secret
        val clientHandshakeTrafficSecret = kdf.expandLabel(
            secret = handshakeSecret,
            label = "c hs traffic",
            context = handshakeHash,
            length = KeyLength(hasher.algorithm.value.substringAfterLast("-").toInt() / 8)
        )
        
        // Server Handshake Traffic Secret
        val serverHandshakeTrafficSecret = kdf.expandLabel(
            secret = handshakeSecret,
            label = "s hs traffic",
            context = handshakeHash,
            length = KeyLength(hasher.algorithm.value.substringAfterLast("-").toInt() / 8)
        )
        
        return HandshakeSecrets(
            clientSecret = clientHandshakeTrafficSecret,
            serverSecret = serverHandshakeTrafficSecret,
            handshakeSecret = handshakeSecret
        )
    }
    
    /**
     * Derive application traffic secrets
     */
    fun deriveApplicationSecrets(
        handshakeSecret: SessionKey,
        handshakeHash: Hash
    ): ApplicationSecrets {
        // Derive-Secret(., "derived", "")
        val derivedSecret = kdf.expandLabel(
            secret = handshakeSecret,
            label = "derived",
            context = hasher.hash(0 j { 0.toByte() }),
            length = KeyLength(hasher.algorithm.value.substringAfterLast("-").toInt() / 8)
        )
        
        // Master Secret = HKDF-Extract(Derive-Secret(...), 0)
        val masterSecret = kdf.deriveKey(
            secret = 0 j { 0.toByte() },
            salt = derivedSecret,
            info = 0 j { 0.toByte() },
            length = KeyLength(hasher.algorithm.value.substringAfterLast("-").toInt() / 8)
        )
        
        // Client Application Traffic Secret
        val clientAppTrafficSecret = kdf.expandLabel(
            secret = masterSecret,
            label = "c ap traffic",
            context = handshakeHash,
            length = KeyLength(hasher.algorithm.value.substringAfterLast("-").toInt() / 8)
        )
        
        // Server Application Traffic Secret
        val serverAppTrafficSecret = kdf.expandLabel(
            secret = masterSecret,
            label = "s ap traffic",
            context = handshakeHash,
            length = KeyLength(hasher.algorithm.value.substringAfterLast("-").toInt() / 8)
        )
        
        // Exporter Master Secret
        val exporterMasterSecret = kdf.expandLabel(
            secret = masterSecret,
            label = "exp master",
            context = handshakeHash,
            length = KeyLength(hasher.algorithm.value.substringAfterLast("-").toInt() / 8)
        )
        
        // Resumption Master Secret
        val resumptionMasterSecret = kdf.expandLabel(
            secret = masterSecret,
            label = "res master",
            context = handshakeHash,
            length = KeyLength(hasher.algorithm.value.substringAfterLast("-").toInt() / 8)
        )
        
        return ApplicationSecrets(
            clientSecret = clientAppTrafficSecret,
            serverSecret = serverAppTrafficSecret,
            exporterSecret = exporterMasterSecret,
            resumptionSecret = resumptionMasterSecret
        )
    }
    
    /**
     * Derive key and IV from traffic secret
     */
    fun deriveKeyAndIV(trafficSecret: SessionKey): Join<SymmetricKey, Nonce> {
        val key = kdf.expandLabel(
            secret = trafficSecret,
            label = "key",
            context = 0 j { 0.toByte() },
            length = cipher.keyLength
        )
        
        val iv = kdf.expandLabel(
            secret = trafficSecret,
            label = "iv",
            context = 0 j { 0.toByte() },
            length = KeyLength(12 * 8) // 12 bytes for GCM/ChaCha20
        )
        
        return Join(key, iv)
    }
    
    /**
     * Get cipher suite parameters
     */
    private fun getCipherSuiteParams(suite: CipherSuite): Join<HashAlgorithm, Int> {
        return when (suite) {
            TLS13CipherSuites.TLS_AES_128_GCM_SHA256 -> Join(HashAlgorithms.SHA256, 16)
            TLS13CipherSuites.TLS_AES_256_GCM_SHA384 -> Join(HashAlgorithms.SHA384, 32)
            TLS13CipherSuites.TLS_CHACHA20_POLY1305_SHA256 -> Join(HashAlgorithms.SHA256, 32)
            TLS13CipherSuites.TLS_AES_128_CCM_SHA256 -> Join(HashAlgorithms.SHA256, 16)
            TLS13CipherSuites.TLS_AES_128_CCM_8_SHA256 -> Join(HashAlgorithms.SHA256, 16)
            else -> Join(HashAlgorithms.SHA256, 16)
        }
    }
}

/**
 * Container for handshake secrets
 */
data class HandshakeSecrets(
    val clientSecret: SessionKey,
    val serverSecret: SessionKey,
    val handshakeSecret: SessionKey
)

/**
 * Container for application traffic secrets
 */
data class ApplicationSecrets(
    val clientSecret: SessionKey,
    val serverSecret: SessionKey,
    val exporterSecret: SessionKey,
    val resumptionSecret: SessionKey
)

/**
 * Transcript hash for TLS handshake
 */
class TranscriptHash(
    private val hasher: CommonCrypto.Hasher
) {
    private val messages = mutableListOf<Byte>()
    
    fun update(message: Indexed<Byte>) {
        for (i in 0 until message.a) {
            messages.add(message[i])
        }
    }
    
    fun getCurrentHash(): Hash {
        return hasher.hash(messages.size j { i: Int -> messages[i] })
    }
}

/**
 * TLS 1.3 Key Schedule (RFC 8446 Section 7.1)
 * 
 *             0
 *             |
 *             v
 *   PSK ->  HKDF-Extract = Early Secret
 *             |
 *             +-----> Derive-Secret(., "ext binder" | "res binder", "")
 *             |                     = binder_key
 *             |
 *             +-----> Derive-Secret(., "c e traffic", ClientHello)
 *             |                     = client_early_traffic_secret
 *             |
 *             +-----> Derive-Secret(., "e exp master", ClientHello)
 *             |                     = early_exporter_master_secret
 *             v
 *       Derive-Secret(., "derived", "")
 *             |
 *             v
 *   (EC)DHE -> HKDF-Extract = Handshake Secret
 *             |
 *             +-----> Derive-Secret(., "c hs traffic",
 *             |                     ClientHello...ServerHello)
 *             |                     = client_handshake_traffic_secret
 *             |
 *             +-----> Derive-Secret(., "s hs traffic",
 *             |                     ClientHello...ServerHello)
 *             |                     = server_handshake_traffic_secret
 *             v
 *       Derive-Secret(., "derived", "")
 *             |
 *             v
 *   0 -> HKDF-Extract = Master Secret
 *             |
 *             +-----> Derive-Secret(., "c ap traffic",
 *             |                     ClientHello...server Finished)
 *             |                     = client_application_traffic_secret_0
 *             |
 *             +-----> Derive-Secret(., "s ap traffic",
 *             |                     ClientHello...server Finished)
 *             |                     = server_application_traffic_secret_0
 *             |
 *             +-----> Derive-Secret(., "exp master",
 *             |                     ClientHello...server Finished)
 *             |                     = exporter_master_secret
 *             |
 *             +-----> Derive-Secret(., "res master",
 *                                   ClientHello...client Finished)
 *                                   = resumption_master_secret
 */