package evolution

import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.CoroutineContext

// --- Actual JVM HKDF Service Implementation ---
actual class JvmHkdfService actual constructor() : HkdfService { // Added actual constructor
    actual override val key: CoroutineContext.Key<*> get() = HkdfServiceKey

    actual override suspend fun extract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(salt, "HmacSHA256")
        mac.init(secretKey)
        return mac.doFinal(ikm)
    }

    actual override suspend fun expand(prk: ByteArray, info: ByteArray, len: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(prk, "HmacSHA256")

        val result = ByteArray(len)
        var bytesProduced = 0
        var t = ByteArray(0)
        var i: Byte = 1

        while (bytesProduced < len) {
            mac.init(secretKey)
            mac.update(t)
            mac.update(info)
            mac.update(i)
            t = mac.doFinal()

            val toCopy = minOf(t.size, len - bytesProduced)
            System.arraycopy(t, 0, result, bytesProduced, toCopy)
            bytesProduced += toCopy

            if (bytesProduced < len && i == Byte.MAX_VALUE) {
                throw IllegalArgumentException("Requested length $len is too large for HKDF-Expand with SHA-256 (max iterations reached)")
            }
            i++
        }
        return result
    }
}

// --- Actual JVM AES Service Implementation ---
actual class JvmAesService actual constructor() : AesService { // Added actual constructor
    actual override val key: CoroutineContext.Key<*> get() = AesServiceKey

    actual override suspend fun gcmEncrypt(keyBytes: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        require(iv.size == 12) { "IV length must be 12 bytes for GCM" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val gcmSpec = GCMParameterSpec(128, iv) // Standard GCM IV size is 12 bytes.
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    actual override suspend fun gcmDecrypt(keyBytes: ByteArray, iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        require(iv.size == 12) { "IV length must be 12 bytes for GCM" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        cipher.updateAAD(aad)
        try {
            return cipher.doFinal(ciphertext)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw DecryptionFailedException("AES-GCM decryption failed: Tag mismatch or corrupt ciphertext.", e)
        } catch (e: Exception) {
            throw DecryptionFailedException("AES-GCM decryption failed due to an unexpected error.", e)
        }
    }

    actual override suspend fun ecbEncrypt(keyBytes: ByteArray, plaintext: ByteArray): ByteArray {
        // WARNING: ECB mode is generally insecure and should be used with extreme caution.
        // It does not provide semantic security. Consider if this is truly needed.
        // PKCS5Padding is often used with ECB to handle block alignment.
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding") // Changed to PKCS5Padding for robustness
        val keySpec = SecretKeySpec(keyBytes, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        return cipher.doFinal(plaintext)
    }
}

actual class DecryptionFailedException actual constructor(actual val message: String, actual val cause: Throwable?) : Exception(message, cause)
