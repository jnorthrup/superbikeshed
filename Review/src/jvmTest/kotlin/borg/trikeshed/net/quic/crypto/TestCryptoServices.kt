package borg.trikeshed.net.quic.crypto

import evolution.AesService
import evolution.HkdfService
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A test implementation of HkdfService.
 * For testing purposes, it might produce predictable outputs or log interactions.
 */
class TestHkdfService : HkdfService {
    override suspend fun extract(salt: ByteArray, ikm: ByteArray): ByteArray {
        // This is NOT a real HKDF extract. For testing, it might just concatenate or XOR.
        // For more realistic tests, a proper HKDF library or a more complete mock would be needed.
        // Example: Simple concatenation for predictability in tests.
        println("TestHkdfService.extract called with salt: ${salt.take(4).joinToString()}, ikm: ${ikm.take(4).joinToString()}")
        if (salt.isEmpty()) return ikm // Simplified behavior if salt is empty
        val result = salt + ikm
        return if (result.size > 32) result.sliceArray(0..31) else result // Max 32 bytes for typical PRK
    }

    override suspend fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        // This is NOT a real HKDF expand.
        println("TestHkdfService.expand called with prk: ${prk.take(4).joinToString()}, info: ${info.take(4).joinToString()}, length: $length")
        val combined = prk + info
        val result = ByteArray(length)
        for (i in 0 until length) {
            result[i] = combined.getOrElse(i % combined.size) { 0.toByte() } // Simple repeating pattern
        }
        return result
    }
}

/**
 * A test implementation of AesService for JVM.
 * This provides a basic, functional AES-GCM and AES-ECB for testing purposes.
 * It does not handle all error cases robustly as a production service might.
 */
class TestAesService : AesService {
    private val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
    private val AES_ECB_NO_PADDING = "AES/ECB/NoPadding" // For header protection

    override suspend fun gcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray? {
        println("TestAesService.gcmEncrypt called. Key: ${key.size}b, IV: ${iv.size}b, PT: ${plaintext.size}b, AAD: ${aad.size}b")
        return try {
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            val keySpec = SecretKeySpec(key, "AES")
            val gcmSpec = GCMParameterSpec(128, iv) // 128-bit auth tag
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            cipher.updateAAD(aad)
            cipher.doFinal(plaintext)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun gcmDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray? {
        println("TestAesService.gcmDecrypt called. Key: ${key.size}b, IV: ${iv.size}b, CT: ${ciphertext.size}b, AAD: ${aad.size}b")
        return try {
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            val keySpec = SecretKeySpec(key, "AES")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            cipher.updateAAD(aad)
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            // javax.crypto.AEADBadTagException is common here if key/tag mismatch
            e.printStackTrace()
            null
        }
    }

    override suspend fun ecbEncrypt(key: ByteArray, plaintext: ByteArray): ByteArray? {
         println("TestAesService.ecbEncrypt called for header protection. Key: ${key.size}b, PT: ${plaintext.size}b")
        return try {
            val cipher = Cipher.getInstance(AES_ECB_NO_PADDING)
            val keySpec = SecretKeySpec(key, "AES")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
            cipher.doFinal(plaintext)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
