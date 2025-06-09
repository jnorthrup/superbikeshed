package evolution

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import kotlin.coroutines.CoroutineContext
import kotlin.js.Promise

// Access to WebCrypto API
@JsModule("crypto")
@JsNonModule
external object WebCrypto {
    val subtle: SubtleCrypto
}

external interface SubtleCrypto {
    fun importKey(format: String, keyData: ArrayBuffer, algorithm: dynamic, extractable: Boolean, keyUsages: Array<String>): Promise<CryptoKey>
    fun sign(algorithm: dynamic, key: CryptoKey, data: ArrayBuffer): Promise<ArrayBuffer> // For HMAC used in HKDF
    fun deriveBits(algorithm: dynamic, baseKey: CryptoKey, length: Int): Promise<ArrayBuffer> // For HKDF (not directly used in this manual impl)
    fun encrypt(algorithm: dynamic, key: CryptoKey, data: ArrayBuffer): Promise<ArrayBuffer>
    fun decrypt(algorithm: dynamic, key: CryptoKey, data: ArrayBuffer): Promise<ArrayBuffer>
    fun digest(algorithm: dynamic, data: ArrayBuffer): Promise<ArrayBuffer>
}

external interface CryptoKey

// Helper to convert ByteArray to ArrayBuffer
fun ByteArray.toArrayBuffer(): ArrayBuffer = Uint8Array(this.toTypedArray()).buffer

// Helper to convert ArrayBuffer to ByteArray
fun ArrayBuffer.toByteArray(): ByteArray = Int8Array(this).unsafeCast<ByteArray>()


actual class JsHkdfService actual constructor() : HkdfService {
    actual override val key: CoroutineContext.Key<*> get() = HkdfServiceKey

    actual override suspend fun extract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val saltKey = WebCrypto.subtle.importKey(
            format = "raw",
            keyData = salt.toArrayBuffer(),
            algorithm = js("{name: 'HMAC', hash: 'SHA-256'}"),
            extractable = false,
            keyUsages = arrayOf("sign")
        ).await()

        val prkBuffer = WebCrypto.subtle.sign(
            algorithm = js("{name: 'HMAC'}"),
            key = saltKey,
            data = ikm.toArrayBuffer()
        ).await()

        return prkBuffer.toByteArray()
    }

    actual override suspend fun expand(prk: ByteArray, info: ByteArray, len: Int): ByteArray {
        val prkKey = WebCrypto.subtle.importKey(
            format = "raw",
            keyData = prk.toArrayBuffer(),
            algorithm = js("{name: 'HMAC', hash: 'SHA-256'}"),
            extractable = false,
            keyUsages = arrayOf("sign")
        ).await()

        val result = ByteArray(len)
        var bytesProduced = 0
        var t = byteArrayOf()
        var i: Byte = 1

        while (bytesProduced < len) {
            val dataToHmac = stabilitéJs.constructDataForHmac(t, info, i)

            val tBuffer = WebCrypto.subtle.sign(
                algorithm = js("{name: 'HMAC'}"),
                key = prkKey,
                data = dataToHmac.toArrayBuffer()
            ).await()

            t = tBuffer.toByteArray()

            val toCopy = minOf(t.size, len - bytesProduced)
            t.copyInto(result, bytesProduced, 0, toCopy)
            bytesProduced += toCopy

            if (bytesProduced < len && i == Byte.MAX_VALUE) {
                 throw IllegalArgumentException("Requested length $len is too large for HKDF-Expand with SHA-256 (max iterations reached)")
            }
            i++
        }
        return result
    }
}

// Helper for JS HKDF expand data construction
private object stabilitéJs {
    fun constructDataForHmac(prevT: ByteArray, info: ByteArray, counter: Byte): ByteArray {
        return prevT + info + byteArrayOf(counter)
    }
}

actual class JsAesService actual constructor() : AesService {
    actual override val key: CoroutineContext.Key<*> get() = AesServiceKey

    actual override suspend fun gcmEncrypt(keyBytes: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        require(iv.size == 12) { "IV length must be 12 bytes for GCM" }
        val cryptoKey = WebCrypto.subtle.importKey(
            format = "raw",
            keyData = keyBytes.toArrayBuffer(),
            algorithm = js("{name: 'AES-GCM'}"),
            extractable = false,
            keyUsages = arrayOf("encrypt")
        ).await()

        val algorithmParams = js("{name: 'AES-GCM', iv: iv.toArrayBuffer(), additionalData: aad.toArrayBuffer(), tagLength: 128}")

        val ciphertextBuffer = WebCrypto.subtle.encrypt(
            algorithm = algorithmParams,
            key = cryptoKey,
            data = plaintext.toArrayBuffer()
        ).await()

        return ciphertextBuffer.toByteArray()
    }

    actual override suspend fun gcmDecrypt(keyBytes: ByteArray, iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        require(iv.size == 12) { "IV length must be 12 bytes for GCM" }
        require(ciphertext.size >= 16) { "Ciphertext must be at least 16 bytes to include GCM tag."}

        val cryptoKey = WebCrypto.subtle.importKey(
            format = "raw",
            keyData = keyBytes.toArrayBuffer(),
            algorithm = js("{name: 'AES-GCM'}"),
            extractable = false,
            keyUsages = arrayOf("decrypt")
        ).await()

        val algorithmParams = js("{name: 'AES-GCM', iv: iv.toArrayBuffer(), additionalData: aad.toArrayBuffer(), tagLength: 128}")

        try {
            val plaintextBuffer = WebCrypto.subtle.decrypt(
                algorithm = algorithmParams,
                key = cryptoKey,
                data = ciphertext.toArrayBuffer()
            ).await()
            return plaintextBuffer.toByteArray()
        } catch (e: dynamic) {
            // WebCrypto throws DOMException. We capture its message.
            val message = e.message?.toString() ?: "AES-GCM decryption failed in JS (WebCrypto)"
            throw DecryptionFailedException(message, e as? Throwable)
        }
    }

    actual override suspend fun ecbEncrypt(keyBytes: ByteArray, plaintext: ByteArray): ByteArray {
        throw UnsupportedOperationException("AES-ECB is not supported by WebCrypto due to security concerns.")
    }
}

actual class DecryptionFailedException actual constructor(actual val message: String, actual val cause: Throwable?) : Exception(message, cause)
