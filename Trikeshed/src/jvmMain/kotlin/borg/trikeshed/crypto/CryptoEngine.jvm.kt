package borg.trikeshed.crypto

import borg.trikeshed.lib.*
import borg.trikeshed.lib.CZero.z
import borg.trikeshed.lib.CZero.nz
import java.security.*
import java.security.spec.*
import javax.crypto.*
import javax.crypto.spec.*
import java.util.*

/**
 * JVM-specific crypto engine implementation
 * Uses Java's built-in crypto libraries for maximum performance and security
 */
class JvmCryptoEngine : CryptoEngine {
    
    private val secureRandom = SecureRandom()
    
    override suspend fun generateKeyPair(algorithm: KeyAlgorithm): KeyPair {
        val keyPairGenerator = when (algorithm) {
            KeyAlgorithm.RSA_2048 -> KeyPairGenerator.getInstance("RSA").apply { 
                initialize(2048, secureRandom) 
            }
            KeyAlgorithm.RSA_4096 -> KeyPairGenerator.getInstance("RSA").apply { 
                initialize(4096, secureRandom) 
            }
            KeyAlgorithm.ECDSA_P256 -> KeyPairGenerator.getInstance("EC").apply {
                val ecSpec = ECGenParameterSpec("secp256r1")
                initialize(ecSpec, secureRandom)
            }
            KeyAlgorithm.ECDSA_P384 -> KeyPairGenerator.getInstance("EC").apply {
                val ecSpec = ECGenParameterSpec("secp384r1")
                initialize(ecSpec, secureRandom)
            }
            KeyAlgorithm.ECDSA_P521 -> KeyPairGenerator.getInstance("EC").apply {
                val ecSpec = ECGenParameterSpec("secp521r1")
                initialize(ecSpec, secureRandom)
            }
            KeyAlgorithm.ED25519 -> KeyPairGenerator.getInstance("Ed25519")
            KeyAlgorithm.X25519 -> KeyPairGenerator.getInstance("X25519")
        }
        
        val javaKeyPair = keyPairGenerator.generateKeyPair()
        
        return KeyPair(
            publicKey = PublicKey(
                algorithm = algorithm,
                keyData = javaKeyPair.public.encoded.size j { javaKeyPair.public.encoded[it] },
                encoded = javaKeyPair.public.encoded.size j { javaKeyPair.public.encoded[it] }
            ),
            privateKey = PrivateKey(
                algorithm = algorithm,
                keyData = javaKeyPair.private.encoded.size j { javaKeyPair.private.encoded[it] },
                encoded = javaKeyPair.private.encoded.size j { javaKeyPair.private.encoded[it] }
            )
        )
    }
    
    override suspend fun generateSymmetricKey(algorithm: SymmetricAlgorithm): SymmetricKey {
        val keySize = when (algorithm) {
            SymmetricAlgorithm.AES_128 -> 16
            SymmetricAlgorithm.AES_256 -> 32
            SymmetricAlgorithm.CHACHA20_POLY1305 -> 32
            SymmetricAlgorithm.AES_GCM_128 -> 16
            SymmetricAlgorithm.AES_GCM_256 -> 32
        }
        
        val keyBytes = ByteArray(keySize)
        secureRandom.nextBytes(keyBytes)
        
        return SymmetricKey(
            algorithm = algorithm,
            keyData = keySize j { keyBytes[it] }
        )
    }
    
    override suspend fun hash(data: Indexed<Byte>, algorithm: HashAlgorithm): Indexed<Byte> {
        val digest = when (algorithm) {
            HashAlgorithm.SHA_256 -> MessageDigest.getInstance("SHA-256")
            HashAlgorithm.SHA_384 -> MessageDigest.getInstance("SHA-384")
            HashAlgorithm.SHA_512 -> MessageDigest.getInstance("SHA-512")
            HashAlgorithm.SHA3_256 -> MessageDigest.getInstance("SHA3-256")
            HashAlgorithm.SHA3_384 -> MessageDigest.getInstance("SHA3-384")
            HashAlgorithm.SHA3_512 -> MessageDigest.getInstance("SHA3-512")
            HashAlgorithm.BLAKE2B_256 -> MessageDigest.getInstance("BLAKE2B-256")
            HashAlgorithm.BLAKE2B_512 -> MessageDigest.getInstance("BLAKE2B-512")
        }
        
        val dataArray = ByteArray(data.a) { data.b(it) }
        val hashBytes = digest.digest(dataArray)
        
        return hashBytes.size j { hashBytes[it] }
    }
    
    override suspend fun hmac(key: Indexed<Byte>, data: Indexed<Byte>, algorithm: HashAlgorithm): Indexed<Byte> {
        val macAlgorithm = when (algorithm) {
            HashAlgorithm.SHA_256 -> "HmacSHA256"
            HashAlgorithm.SHA_384 -> "HmacSHA384"
            HashAlgorithm.SHA_512 -> "HmacSHA512"
            HashAlgorithm.SHA3_256 -> "HmacSHA3-256"
            HashAlgorithm.SHA3_384 -> "HmacSHA3-384"
            HashAlgorithm.SHA3_512 -> "HmacSHA3-512"
            HashAlgorithm.BLAKE2B_256 -> "HmacBLAKE2B-256"
            HashAlgorithm.BLAKE2B_512 -> "HmacBLAKE2B-512"
        }
        
        val mac = Mac.getInstance(macAlgorithm)
        val keySpec = SecretKeySpec(ByteArray(key.a) { key.b(it) }, macAlgorithm)
        mac.init(keySpec)
        
        val dataArray = ByteArray(data.a) { data.b(it) }
        val hmacBytes = mac.doFinal(dataArray)
        
        return hmacBytes.size j { hmacBytes[it] }
    }
    
    override suspend fun encrypt(data: Indexed<Byte>, key: SymmetricKey, mode: EncryptionMode): EncryptedData {
        val cipher = when (mode) {
            EncryptionMode.CBC -> {
                when (key.algorithm) {
                    SymmetricAlgorithm.AES_128, SymmetricAlgorithm.AES_256 -> Cipher.getInstance("AES/CBC/PKCS5Padding")
                    else -> throw IllegalArgumentException("Unsupported algorithm for CBC mode")
                }
            }
            EncryptionMode.GCM -> {
                when (key.algorithm) {
                    SymmetricAlgorithm.AES_GCM_128, SymmetricAlgorithm.AES_GCM_256 -> Cipher.getInstance("AES/GCM/NoPadding")
                    else -> throw IllegalArgumentException("Unsupported algorithm for GCM mode")
                }
            }
            EncryptionMode.CHACHA20_POLY1305 -> {
                when (key.algorithm) {
                    SymmetricAlgorithm.CHACHA20_POLY1305 -> Cipher.getInstance("ChaCha20-Poly1305")
                    else -> throw IllegalArgumentException("Unsupported algorithm for ChaCha20-Poly1305 mode")
                }
            }
        }
        
        val iv = ByteArray(12) // 96 bits for GCM/ChaCha20
        secureRandom.nextBytes(iv)
        
        val keySpec = SecretKeySpec(ByteArray(key.keyData.a) { key.keyData.b(it) }, "AES")
        val ivSpec = GCMParameterSpec(128, iv) // 128-bit tag for GCM
        
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        
        val dataArray = ByteArray(data.a) { data.b(it) }
        val encryptedBytes = cipher.doFinal(dataArray)
        
        return EncryptedData(
            ciphertext = encryptedBytes.size j { encryptedBytes[it] },
            iv = iv.size j { iv[it] },
            tag = null, // Tag is included in ciphertext for GCM/ChaCha20
            mode = mode
        )
    }
    
    override suspend fun decrypt(encryptedData: EncryptedData, key: SymmetricKey): Indexed<Byte> {
        val cipher = when (encryptedData.mode) {
            EncryptionMode.CBC -> Cipher.getInstance("AES/CBC/PKCS5Padding")
            EncryptionMode.GCM -> Cipher.getInstance("AES/GCM/NoPadding")
            EncryptionMode.CHACHA20_POLY1305 -> Cipher.getInstance("ChaCha20-Poly1305")
        }
        
        val keySpec = SecretKeySpec(ByteArray(key.keyData.a) { key.keyData.b(it) }, "AES")
        val ivSpec = GCMParameterSpec(128, ByteArray(encryptedData.iv.a) { encryptedData.iv.b(it) })
        
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        
        val encryptedArray = ByteArray(encryptedData.ciphertext.a) { encryptedData.ciphertext.b(it) }
        val decryptedBytes = cipher.doFinal(encryptedArray)
        
        return decryptedBytes.size j { decryptedBytes[it] }
    }
    
    override suspend fun sign(data: Indexed<Byte>, keyPair: KeyPair): Indexed<Byte> {
        val signature = when (keyPair.publicKey.algorithm) {
            KeyAlgorithm.RSA_2048, KeyAlgorithm.RSA_4096 -> Signature.getInstance("SHA256withRSA")
            KeyAlgorithm.ECDSA_P256, KeyAlgorithm.ECDSA_P384, KeyAlgorithm.ECDSA_P521 -> Signature.getInstance("SHA256withECDSA")
            KeyAlgorithm.ED25519 -> Signature.getInstance("Ed25519")
            KeyAlgorithm.X25519 -> throw IllegalArgumentException("X25519 is for key exchange, not signing")
        }
        
        val privateKey = when (keyPair.publicKey.algorithm) {
            KeyAlgorithm.RSA_2048, KeyAlgorithm.RSA_4096 -> {
                val keyFactory = KeyFactory.getInstance("RSA")
                val keySpec = PKCS8EncodedKeySpec(ByteArray(keyPair.privateKey.encoded.a) { keyPair.privateKey.encoded.b(it) })
                keyFactory.generatePrivate(keySpec)
            }
            KeyAlgorithm.ECDSA_P256, KeyAlgorithm.ECDSA_P384, KeyAlgorithm.ECDSA_P521 -> {
                val keyFactory = KeyFactory.getInstance("EC")
                val keySpec = PKCS8EncodedKeySpec(ByteArray(keyPair.privateKey.encoded.a) { keyPair.privateKey.encoded.b(it) })
                keyFactory.generatePrivate(keySpec)
            }
            KeyAlgorithm.ED25519 -> {
                val keyFactory = KeyFactory.getInstance("Ed25519")
                val keySpec = PKCS8EncodedKeySpec(ByteArray(keyPair.privateKey.encoded.a) { keyPair.privateKey.encoded.b(it) })
                keyFactory.generatePrivate(keySpec)
            }
            KeyAlgorithm.X25519 -> throw IllegalArgumentException("X25519 is for key exchange, not signing")
        }
        
        signature.initSign(privateKey)
        val dataArray = ByteArray(data.a) { data.b(it) }
        signature.update(dataArray)
        val signatureBytes = signature.sign()
        
        return signatureBytes.size j { signatureBytes[it] }
    }
    
    override suspend fun verify(data: Indexed<Byte>, signature: Indexed<Byte>, publicKey: PublicKey): Boolean {
        val sig = when (publicKey.algorithm) {
            KeyAlgorithm.RSA_2048, KeyAlgorithm.RSA_4096 -> Signature.getInstance("SHA256withRSA")
            KeyAlgorithm.ECDSA_P256, KeyAlgorithm.ECDSA_P384, KeyAlgorithm.ECDSA_P521 -> Signature.getInstance("SHA256withECDSA")
            KeyAlgorithm.ED25519 -> Signature.getInstance("Ed25519")
            KeyAlgorithm.X25519 -> throw IllegalArgumentException("X25519 is for key exchange, not verification")
        }
        
        val pubKey = when (publicKey.algorithm) {
            KeyAlgorithm.RSA_2048, KeyAlgorithm.RSA_4096 -> {
                val keyFactory = KeyFactory.getInstance("RSA")
                val keySpec = X509EncodedKeySpec(ByteArray(publicKey.encoded.a) { publicKey.encoded.b(it) })
                keyFactory.generatePublic(keySpec)
            }
            KeyAlgorithm.ECDSA_P256, KeyAlgorithm.ECDSA_P384, KeyAlgorithm.ECDSA_P521 -> {
                val keyFactory = KeyFactory.getInstance("EC")
                val keySpec = X509EncodedKeySpec(ByteArray(publicKey.encoded.a) { publicKey.encoded.b(it) })
                keyFactory.generatePublic(keySpec)
            }
            KeyAlgorithm.ED25519 -> {
                val keyFactory = KeyFactory.getInstance("Ed25519")
                val keySpec = X509EncodedKeySpec(ByteArray(publicKey.encoded.a) { publicKey.encoded.b(it) })
                keyFactory.generatePublic(keySpec)
            }
            KeyAlgorithm.X25519 -> throw IllegalArgumentException("X25519 is for key exchange, not verification")
        }
        
        sig.initVerify(pubKey)
        val dataArray = ByteArray(data.a) { data.b(it) }
        sig.update(dataArray)
        val signatureArray = ByteArray(signature.a) { signature.b(it) }
        
        return sig.verify(signatureArray)
    }
    
    override suspend fun deriveKey(password: Indexed<Byte>, salt: Indexed<Byte>, algorithm: KdfAlgorithm): SymmetricKey {
        val keyFactory = when (algorithm) {
            KdfAlgorithm.PBKDF2_SHA256 -> SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            KdfAlgorithm.PBKDF2_SHA512 -> SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            KdfAlgorithm.ARGON2_ID -> throw IllegalArgumentException("Argon2 not available in JVM crypto")
            KdfAlgorithm.SCRYPT -> throw IllegalArgumentException("Scrypt not available in JVM crypto")
        }
        
        val passwordArray = ByteArray(password.a) { password.b(it) }
        val saltArray = ByteArray(salt.a) { salt.b(it) }
        
        val spec = PBEKeySpec(
            passwordArray.map { it.toInt().toChar() }.toCharArray(),
            saltArray,
            100000, // iterations
            256 // key length
        )
        
        val secretKey = keyFactory.generateSecret(spec)
        val keyBytes = secretKey.encoded
        
        return SymmetricKey(
            algorithm = SymmetricAlgorithm.AES_256,
            keyData = keyBytes.size j { keyBytes[it] }
        )
    }
    
    override suspend fun generateRandomBytes(length: Int): Indexed<Byte> {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return length j { bytes[it] }
    }
    
    override suspend fun generateSecureRandom(): Long {
        return secureRandom.nextLong()
    }
} 