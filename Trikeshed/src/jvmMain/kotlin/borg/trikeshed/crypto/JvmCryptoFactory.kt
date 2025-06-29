@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.crypto

import borg.trikeshed.lib.*
import java.security.*
import java.security.spec.*
import javax.crypto.*
import javax.crypto.spec.*
import java.util.concurrent.ThreadLocalRandom

/**
 * JVM implementation of CryptoFactory
 */
actual object CryptoFactory {
    actual fun createHasher(algorithm: HashAlgorithm): CommonCrypto.Hasher {
        return JvmHasher(algorithm)
    }
    
    actual fun createSymmetricCipher(suite: CipherSuite): CommonCrypto.SymmetricCipher {
        return JvmSymmetricCipher(suite)
    }
    
    actual fun createKeyExchange(algorithm: KeyExchangeAlgorithm): CommonCrypto.KeyExchange {
        return JvmKeyExchange(algorithm)
    }
    
    actual fun createSigner(algorithm: SignatureAlgorithm): CommonCrypto.Signer {
        return JvmSigner(algorithm)
    }
    
    actual fun createKeyDerivation(): CommonCrypto.KeyDerivation {
        return JvmKeyDerivation()
    }
    
    actual fun getSecureRandom(): CommonCrypto {
        return JvmSecureRandom()
    }
}

// JVM implementation of Hasher
private class JvmHasher(override val algorithm: HashAlgorithm) : CommonCrypto.Hasher {
    
    override fun hash(data: PlainText): Hash {
        val digest = when (algorithm.value) {
            "sha256" -> MessageDigest.getInstance("SHA-256")
            "sha384" -> MessageDigest.getInstance("SHA-384")
            "sha512" -> MessageDigest.getInstance("SHA-512")
            "sha3-256" -> MessageDigest.getInstance("SHA3-256")
            "sha3-384" -> MessageDigest.getInstance("SHA3-384")
            "sha3-512" -> MessageDigest.getInstance("SHA3-512")
            "md5" -> MessageDigest.getInstance("MD5")
            "sha1" -> MessageDigest.getInstance("SHA-1")
            else -> throw UnsupportedOperationException("Algorithm not supported: ${algorithm.value}")
        }
        
        val bytes = ByteArray(data.size)
        for (i in 0 until data.size) {
            bytes[i] = data[i]
        }
        return digest.digest(bytes).toIdx()
    }
    
    override fun hmac(key: SymmetricKey, data: PlainText): Hash {
        val algorithmName = when (algorithm.value) {
            "sha256" -> "HmacSHA256"
            "sha384" -> "HmacSHA384"
            "sha512" -> "HmacSHA512"
            else -> throw UnsupportedOperationException("HMAC not supported for: ${algorithm.value}")
        }
        
        val mac = Mac.getInstance(algorithmName)
        val keyBytes = ByteArray(key.size)
        for (i in 0 until key.size) {
            keyBytes[i] = key[i]
        }
        mac.init(SecretKeySpec(keyBytes, algorithmName))
        
        val dataBytes = ByteArray(data.size)
        for (i in 0 until data.size) {
            dataBytes[i] = data[i]
        }
        
        return mac.doFinal(dataBytes).toIdx()
    }
}

// JVM implementation of SymmetricCipher
private class JvmSymmetricCipher(private val suite: CipherSuite) : CommonCrypto.SymmetricCipher {
    override val algorithm: String = when (suite.value) {
        0x1301 -> "AES_128_GCM_SHA256"
        0x1302 -> "AES_256_GCM_SHA384"
        0x1303 -> "CHACHA20_POLY1305_SHA256"
        else -> "UNKNOWN"
    }
    
    override val keyLength: KeyLength = when (suite.value) {
        0x1301 -> KeyLength(128)
        0x1302 -> KeyLength(256)
        0x1303 -> KeyLength(256)
        else -> KeyLength(0)
    }
    
    override val nonceLength: NonceLength = NonceLength(12)
    override val tagLength: TagLength = TagLength(16)
    
    override fun encrypt(
        key: SymmetricKey,
        nonce: Nonce,
        plaintext: PlainText,
        additionalData: Indexed<Byte>
    ): Join<CipherText, Tag> {
        val cipher = when (suite.value) {
            0x1301, 0x1302 -> Cipher.getInstance("AES/GCM/NoPadding")
            0x1303 -> Cipher.getInstance("ChaCha20-Poly1305")
            else -> throw UnsupportedOperationException("Cipher suite not supported: ${suite.value}")
        }
        
        val keyBytes = ByteArray(key.size)
        for (i in 0 until key.size) {
            keyBytes[i] = key[i]
        }
        
        val nonceBytes = ByteArray(nonce.size)
        for (i in 0 until nonce.size) {
            nonceBytes[i] = nonce[i]
        }
        
        val keySpec = SecretKeySpec(keyBytes, if (suite.value == 0x1303) "ChaCha20" else "AES")
        val paramSpec = if (suite.value == 0x1303) {
            IvParameterSpec(nonceBytes)
        } else {
            GCMParameterSpec(128, nonceBytes)
        }
        
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, paramSpec)
        
        if (additionalData.size > 0) {
            val aadBytes = ByteArray(additionalData.size)
            for (i in 0 until additionalData.size) {
                aadBytes[i] = additionalData[i]
            }
            cipher.updateAAD(aadBytes)
        }
        
        val plaintextBytes = ByteArray(plaintext.size)
        for (i in 0 until plaintext.size) {
            plaintextBytes[i] = plaintext[i]
        }
        
        val ciphertextWithTag = cipher.doFinal(plaintextBytes)
        val ciphertext = ciphertextWithTag.sliceArray(0 until ciphertextWithTag.size - 16).toIdx()
        val tag = ciphertextWithTag.sliceArray(ciphertextWithTag.size - 16 until ciphertextWithTag.size).toIdx()
        
        return ciphertext j tag
    }
    
    override fun decrypt(
        key: SymmetricKey,
        nonce: Nonce,
        ciphertext: CipherText,
        tag: Tag,
        additionalData: Indexed<Byte>
    ): PlainText? {
        val cipher = when (suite.value) {
            0x1301, 0x1302 -> Cipher.getInstance("AES/GCM/NoPadding")
            0x1303 -> Cipher.getInstance("ChaCha20-Poly1305")
            else -> throw UnsupportedOperationException("Cipher suite not supported: ${suite.value}")
        }
        
        val keyBytes = ByteArray(key.size)
        for (i in 0 until key.size) {
            keyBytes[i] = key[i]
        }
        
        val nonceBytes = ByteArray(nonce.size)
        for (i in 0 until nonce.size) {
            nonceBytes[i] = nonce[i]
        }
        
        val keySpec = SecretKeySpec(keyBytes, if (suite.value == 0x1303) "ChaCha20" else "AES")
        val paramSpec = if (suite.value == 0x1303) {
            IvParameterSpec(nonceBytes)
        } else {
            GCMParameterSpec(128, nonceBytes)
        }
        
        cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec)
        
        if (additionalData.size > 0) {
            val aadBytes = ByteArray(additionalData.size)
            for (i in 0 until additionalData.size) {
                aadBytes[i] = additionalData[i]
            }
            cipher.updateAAD(aadBytes)
        }
        
        val ciphertextWithTag = ByteArray(ciphertext.size + tag.size)
        for (i in 0 until ciphertext.size) {
            ciphertextWithTag[i] = ciphertext[i]
        }
        for (i in 0 until tag.size) {
            ciphertextWithTag[ciphertext.size + i] = tag[i]
        }
        
        return try {
            cipher.doFinal(ciphertextWithTag).toIdx()
        } catch (e: Exception) {
            null
        }
    }
}

// JVM implementation of KeyExchange
private class JvmKeyExchange(override val algorithm: KeyExchangeAlgorithm) : CommonCrypto.KeyExchange {
    
    override fun generateKeyPair(): Join<PublicKey, PrivateKey> {
        val keyPairGen = when (algorithm.value) {
            "x25519" -> KeyPairGenerator.getInstance("X25519")
            "x448" -> KeyPairGenerator.getInstance("X448")
            "secp256r1" -> KeyPairGenerator.getInstance("EC").apply {
                initialize(ECGenParameterSpec("secp256r1"))
            }
            "secp384r1" -> KeyPairGenerator.getInstance("EC").apply {
                initialize(ECGenParameterSpec("secp384r1"))
            }
            "secp521r1" -> KeyPairGenerator.getInstance("EC").apply {
                initialize(ECGenParameterSpec("secp521r1"))
            }
            else -> throw UnsupportedOperationException("Algorithm not supported: ${algorithm.value}")
        }
        
        val keyPair = keyPairGen.generateKeyPair()
        val publicKey = keyPair.public.encoded.toIdx()
        val privateKey = keyPair.private.encoded.toIdx()
        return publicKey j privateKey
    }
    
    override fun computeSharedSecret(privateKey: PrivateKey, publicKey: PublicKey): SessionKey {
        val keyAgreement = KeyAgreement.getInstance(
            when (algorithm.value) {
                "x25519", "x448" -> "XDH"
                else -> "ECDH"
            }
        )
        
        val privateKeyBytes = ByteArray(privateKey.size)
        for (i in 0 until privateKey.size) {
            privateKeyBytes[i] = privateKey[i]
        }
        
        val publicKeyBytes = ByteArray(publicKey.size)
        for (i in 0 until publicKey.size) {
            publicKeyBytes[i] = publicKey[i]
        }
        
        val privKey = KeyFactory.getInstance(
            when (algorithm.value) {
                "x25519", "x448" -> "XDH"
                else -> "EC"
            }
        ).generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
        
        val pubKey = KeyFactory.getInstance(
            when (algorithm.value) {
                "x25519", "x448" -> "XDH"
                else -> "EC"
            }
        ).generatePublic(X509EncodedKeySpec(publicKeyBytes))
        
        keyAgreement.init(privKey)
        keyAgreement.doPhase(pubKey, true)
        
        return keyAgreement.generateSecret().toIdx()
    }
}

// JVM implementation of Signer
private class JvmSigner(override val algorithm: SignatureAlgorithm) : CommonCrypto.Signer {
    
    override fun generateKeyPair(): Join<PublicKey, PrivateKey> {
        val keyPairGen = when (algorithm.value) {
            "rsa_pss_rsae_sha256", "rsa_pss_rsae_sha384", "rsa_pss_rsae_sha512" -> 
                KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
            "ecdsa_secp256r1_sha256" -> 
                KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
            "ecdsa_secp384r1_sha384" -> 
                KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp384r1")) }
            "ecdsa_secp521r1_sha512" -> 
                KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp521r1")) }
            "ed25519" -> KeyPairGenerator.getInstance("Ed25519")
            "ed448" -> KeyPairGenerator.getInstance("Ed448")
            else -> throw UnsupportedOperationException("Algorithm not supported: ${algorithm.value}")
        }
        
        val keyPair = keyPairGen.generateKeyPair()
        val publicKey = keyPair.public.encoded.toIdx()
        val privateKey = keyPair.private.encoded.toIdx()
        return publicKey j privateKey
    }
    
    override fun sign(privateKey: PrivateKey, message: PlainText): Signature {
        val signature = when (algorithm.value) {
            "rsa_pss_rsae_sha256" -> java.security.Signature.getInstance("SHA256withRSA/PSS")
            "rsa_pss_rsae_sha384" -> java.security.Signature.getInstance("SHA384withRSA/PSS")
            "rsa_pss_rsae_sha512" -> java.security.Signature.getInstance("SHA512withRSA/PSS")
            "ecdsa_secp256r1_sha256" -> java.security.Signature.getInstance("SHA256withECDSA")
            "ecdsa_secp384r1_sha384" -> java.security.Signature.getInstance("SHA384withECDSA")
            "ecdsa_secp521r1_sha512" -> java.security.Signature.getInstance("SHA512withECDSA")
            "ed25519" -> java.security.Signature.getInstance("Ed25519")
            "ed448" -> java.security.Signature.getInstance("Ed448")
            else -> throw UnsupportedOperationException("Algorithm not supported: ${algorithm.value}")
        }
        
        val privateKeyBytes = ByteArray(privateKey.size)
        for (i in 0 until privateKey.size) {
            privateKeyBytes[i] = privateKey[i]
        }
        
        val keyFactory = when (algorithm.value) {
            "ed25519", "ed448" -> KeyFactory.getInstance("EdDSA")
            "ecdsa_secp256r1_sha256",
            "ecdsa_secp384r1_sha384",
            "ecdsa_secp521r1_sha512" -> KeyFactory.getInstance("EC")
            else -> KeyFactory.getInstance("RSA")
        }
        
        val privKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
        signature.initSign(privKey)
        
        val messageBytes = ByteArray(message.size)
        for (i in 0 until message.size) {
            messageBytes[i] = message[i]
        }
        signature.update(messageBytes)
        
        return signature.sign().toIdx()
    }
    
    override fun verify(publicKey: PublicKey, message: PlainText, signature: Signature): Boolean {
        val sig = when (algorithm.value) {
            "rsa_pss_rsae_sha256" -> java.security.Signature.getInstance("SHA256withRSA/PSS")
            "rsa_pss_rsae_sha384" -> java.security.Signature.getInstance("SHA384withRSA/PSS")
            "rsa_pss_rsae_sha512" -> java.security.Signature.getInstance("SHA512withRSA/PSS")
            "ecdsa_secp256r1_sha256" -> java.security.Signature.getInstance("SHA256withECDSA")
            "ecdsa_secp384r1_sha384" -> java.security.Signature.getInstance("SHA384withECDSA")
            "ecdsa_secp521r1_sha512" -> java.security.Signature.getInstance("SHA512withECDSA")
            "ed25519" -> java.security.Signature.getInstance("Ed25519")
            "ed448" -> java.security.Signature.getInstance("Ed448")
            else -> throw UnsupportedOperationException("Algorithm not supported: ${algorithm.value}")
        }
        
        val publicKeyBytes = ByteArray(publicKey.size)
        for (i in 0 until publicKey.size) {
            publicKeyBytes[i] = publicKey[i]
        }
        
        val keyFactory = when (algorithm.value) {
            "ed25519", "ed448" -> KeyFactory.getInstance("EdDSA")
            "ecdsa_secp256r1_sha256",
            "ecdsa_secp384r1_sha384",
            "ecdsa_secp521r1_sha512" -> KeyFactory.getInstance("EC")
            else -> KeyFactory.getInstance("RSA")
        }
        
        val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
        sig.initVerify(pubKey)
        
        val messageBytes = ByteArray(message.size)
        for (i in 0 until message.size) {
            messageBytes[i] = message[i]
        }
        sig.update(messageBytes)
        
        val signatureBytes = ByteArray(signature.size)
        for (i in 0 until signature.size) {
            signatureBytes[i] = signature[i]
        }
        
        return sig.verify(signatureBytes)
    }
}

// JVM implementation of KeyDerivation
private class JvmKeyDerivation : CommonCrypto.KeyDerivation {
    override val function = KeyDerivationFunction("hkdf-sha256")
    
    override fun deriveKey(
        secret: SessionKey,
        salt: Salt,
        info: Indexed<Byte>,
        length: KeyLength
    ): SymmetricKey {
        // HKDF implementation
        val hmac = Mac.getInstance("HmacSHA256")
        
        // Extract
        val saltBytes = ByteArray(salt.size)
        for (i in 0 until salt.size) {
            saltBytes[i] = salt[i]
        }
        hmac.init(SecretKeySpec(saltBytes, "HmacSHA256"))
        
        val secretBytes = ByteArray(secret.size)
        for (i in 0 until secret.size) {
            secretBytes[i] = secret[i]
        }
        val prk = hmac.doFinal(secretBytes)
        
        // Expand
        val lengthInBytes = length.bits / 8
        val output = ByteArray(lengthInBytes)
        var outputOffset = 0
        var counter = 1.toByte()
        
        hmac.init(SecretKeySpec(prk, "HmacSHA256"))
        
        val infoBytes = ByteArray(info.size)
        for (i in 0 until info.size) {
            infoBytes[i] = info[i]
        }
        
        var previousBlock = ByteArray(0)
        while (outputOffset < lengthInBytes) {
            hmac.update(previousBlock)
            hmac.update(infoBytes)
            hmac.update(counter)
            val block = hmac.doFinal()
            previousBlock = block
            
            val copyLength = minOf(block.size, lengthInBytes - outputOffset)
            System.arraycopy(block, 0, output, outputOffset, copyLength)
            outputOffset += copyLength
            counter++
        }
        
        return output.toIdx()
    }
    
    override fun expandLabel(
        secret: SessionKey,
        label: String,
        context: Indexed<Byte>,
        length: KeyLength
    ): SymmetricKey {
        // TLS 1.3 style label expansion
        val labelBytes = "tls13 $label".toByteArray()
        val lengthBytes = byteArrayOf((length.bits / 8).toByte())
        
        val hkdfLabel = lengthBytes.size + labelBytes.size + 1 + context.size
        val info = ByteArray(hkdfLabel)
        var offset = 0
        
        // Length
        System.arraycopy(lengthBytes, 0, info, offset, lengthBytes.size)
        offset += lengthBytes.size
        
        // Label
        info[offset++] = labelBytes.size.toByte()
        System.arraycopy(labelBytes, 0, info, offset, labelBytes.size)
        offset += labelBytes.size
        
        // Context
        info[offset++] = context.size.toByte()
        for (i in 0 until context.size) {
            info[offset++] = context[i]
        }
        
        return deriveKey(secret, ByteArray(32).toIdx(), info.toIdx(), length)
    }
}

// JVM implementation of SecureRandom
private class JvmSecureRandom : CommonCrypto {
    private val random = SecureRandom()
    
    override fun randomBytes(length: Int): Indexed<Byte> {
        val bytes = ByteArray(length)
        random.nextBytes(bytes)
        return bytes.toIdx()
    }
}