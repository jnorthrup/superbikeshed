package evolution

import kotlin.js.Promise
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.Int8Array
import kotlin.coroutines.CoroutineContext

// Assuming SubtleCrypto, CryptoKey, and helpers are available (defined in ActualHkdfService.js.kt or a shared file)
// subtleCrypto is already defined in ActualHkdfService.js.kt in the same package.

actual class AesService actual constructor() : CoroutineContext.Element {
    actual override val key: CoroutineContext.Key<*> = AesServiceKey

    actual suspend fun gcmEncrypt(keyBytes: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val cryptoKey = subtleCrypto.importKey(
            "raw",
            keyBytes.toUint8Array().buffer,
            js("{name: 'AES-GCM'}"),
            false,
            arrayOf("encrypt")
        ).await()

        val algorithmParams = js("{name: 'AES-GCM', iv: iv.toUint8Array()}")
        if (aad.isNotEmpty()) {
            algorithmParams.additionalData = aad.toUint8Array().buffer
        }

        val ciphertextBuffer = subtleCrypto.encrypt(
            algorithmParams,
            cryptoKey,
            plaintext.toUint8Array().buffer
        ).await()
        return ciphertextBuffer.toByteArray() // This includes the tag appended by WebCrypto
    }

    actual suspend fun gcmDecrypt(keyBytes: ByteArray, iv: ByteArray, ciphertextAndTag: ByteArray, aad: ByteArray): ByteArray {
        val cryptoKey = subtleCrypto.importKey(
            "raw",
            keyBytes.toUint8Array().buffer,
            js("{name: 'AES-GCM'}"),
            false,
            arrayOf("decrypt")
        ).await()

        val algorithmParams = js("{name: 'AES-GCM', iv: iv.toUint8Array()}")
         if (aad.isNotEmpty()) {
            algorithmParams.additionalData = aad.toUint8Array().buffer
        }

        try {
            val plaintextBuffer = subtleCrypto.decrypt(
                algorithmParams,
                cryptoKey,
                ciphertextAndTag.toUint8Array().buffer // WebCrypto expects ciphertext + tag combined
            ).await()
            return plaintextBuffer.toByteArray()
        } catch (e: dynamic) {
            // WebCrypto throws a DOMException for decryption failures (e.g. tag mismatch)
            throw ActualDecryptionFailedException("AES-GCM decryption failed: " + (e.message ?: "Unknown error"), e)
        }
    }

    actual suspend fun ecbEncrypt(keyBytes: ByteArray, plaintext: ByteArray): ByteArray {
         val cryptoKey = subtleCrypto.importKey(
            "raw",
            keyBytes.toUint8Array().buffer,
            js("{name: 'AES-ECB'}"), // AES-ECB might not be universally supported or recommended
            false,
            arrayOf("encrypt")
        ).await()

        // AES-ECB does not use an IV. Padding is handled by WebCrypto (typically PKCS#7).
        val algorithmParams = js("{name: 'AES-ECB'}")

        val ciphertextBuffer = subtleCrypto.encrypt(
            algorithmParams,
            cryptoKey,
            plaintext.toUint8Array().buffer
        ).await()
        return ciphertextBuffer.toByteArray()
    }
}
// ByteArray.toUint8Array() and ArrayBuffer.toByteArray() assumed from ActualHkdfService.js.kt or a shared util file.
