package evolution

import kotlin.js.Promise
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.Int8Array // For converting back from Uint8Array if needed
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers // Not strictly needed for JS if all ops are on main/worker
import kotlinx.coroutines.GlobalScope // For simple launch, ideally use provided scope
import kotlinx.coroutines.promise // To bridge suspend fun to Promise for JS users if service itself is directly called from JS

// WebCrypto API is available under `crypto.subtle` in modern browsers and Node.js (15+)
@JsModule("crypto") @JsNonModule
external object CommonJsCrypto {
    val subtle: SubtleCrypto
    fun getRandomValues(array: ByteArray): ByteArray // Node.js specific for crypto.getRandomValues
}

@JsName("crypto")
external object BrowserCrypto {
    val subtle: SubtleCrypto
    fun getRandomValues(array: Uint8Array): Uint8Array // Browser specific
}

interface SubtleCrypto {
    fun digest(algorithm: dynamic, data: ArrayBuffer): Promise<ArrayBuffer>
    fun importKey(format: String, keyData: dynamic, algorithm: dynamic, extractable: Boolean, keyUsages: Array<String>): Promise<CryptoKey>
    fun sign(algorithm: dynamic, key: CryptoKey, data: ArrayBuffer): Promise<ArrayBuffer>
    fun deriveBits(algorithm: dynamic, baseKey: CryptoKey, length: Int): Promise<ArrayBuffer>
    fun encrypt(algorithm: dynamic, key: CryptoKey, data: ArrayBuffer): Promise<ArrayBuffer>
    fun decrypt(algorithm: dynamic, key: CryptoKey, data: ArrayBuffer): Promise<ArrayBuffer>
    fun exportKey(format: String, key: CryptoKey): Promise<ArrayBuffer> // Added for key export
    fun generateKey(algorithm: dynamic, extractable: Boolean, keyUsages: Array<String>): Promise<dynamic> // For key pair generation
    fun verify(algorithm: dynamic, key: CryptoKey, signature: ArrayBuffer, data: ArrayBuffer): Promise<Boolean> // Added for signature verification
}

external interface CryptoKey

// Helper to get subtle crypto interface
internal val subtleCrypto: SubtleCrypto by lazy {
    try {
        // Try commonJS (Node.js) crypto module first
        CommonJsCrypto.subtle
    } catch (e: dynamic) {
        // Fallback to browser global crypto
        BrowserCrypto.subtle
    }
}

actual class HkdfService actual constructor() : CoroutineContext.Element {
    actual override val key: CoroutineContext.Key<*> = HkdfServiceKey

    // HKDF Extract: HMAC-Hash(salt, IKM)
    actual suspend fun extract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val saltKey = subtleCrypto.importKey(
            "raw",
            salt.toUint8Array().buffer, // Use salt as is for importKey
            js("{name: 'HMAC', hash: 'SHA-256'}"),
            false, // not extractable
            arrayOf("sign")
        ).await()

        val prkBuffer = subtleCrypto.sign(
            js("{name: 'HMAC'}"), // Algorithm for sign
            saltKey,
            ikm.toUint8Array().buffer
        ).await()
        return prkBuffer.toByteArray()
    }

    // HKDF Expand: HMAC-Hash(PRK, info | 0x01)
    // This is a simplified expand; a full one might need iterations for longer output.
    actual suspend fun expand(prk: ByteArray, info: ByteArray, len: Int): ByteArray {
        if (len > 32 * 255) throw IllegalArgumentException("Requested length $len is too large for simple HKDF-Expand.")

        val prkKey = subtleCrypto.importKey(
            "raw",
            prk.toUint8Array().buffer,
            js("{name: 'HMAC', hash: 'SHA-256'}"),
            false,
            arrayOf("sign")
        ).await()

        // HKDF Expand logic: T(0) = empty, T(1) = HMAC(PRK, T(0) | info | 0x01), OKM = T(1)
        // For longer outputs: T(n) = HMAC(PRK, T(n-1) | info | n), OKM = T(1) | T(2) | ...
        // This implementation provides up to Hash.length (32 bytes for SHA-256)
        // A more complete version would loop if len > hash_output_length.

        val bufferToSign = info.toMutableList()
        bufferToSign.add(0x01.toByte()) // Append counter (1)

        val okmBuffer = subtleCrypto.sign(
            js("{name: 'HMAC'}"),
            prkKey,
            bufferToSign.toByteArray().toUint8Array().buffer
        ).await()

        return okmBuffer.toByteArray().copyOf(len) // Truncate to desired length
    }
}

// Helper extensions
internal fun ByteArray.toUint8Array(): Uint8Array {
    return Uint8Array(this.toTypedArray())
}

internal fun ArrayBuffer.toByteArray(): ByteArray {
    return Int8Array(this).unsafeCast<ByteArray>()
}
