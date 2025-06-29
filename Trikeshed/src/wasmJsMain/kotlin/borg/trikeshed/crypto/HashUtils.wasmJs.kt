package borg.trikeshed.crypto

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.toIndexed
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

// External declarations for Web Crypto API
// Typically, these might be in a separate file if used more broadly.
@JsModule("crypto")
@JsNonModule
external object WebCrypto { // Renamed to avoid conflict if 'crypto' is a global var by chance
    val subtle: SubtleCrypto
}

external interface SubtleCrypto {
    fun digest(algorithm: dynamic, data: ArrayBuffer): Promise<ArrayBuffer>
}

actual object HashUtils {
    actual suspend fun sha256(input: Indexed<Byte>): Indexed<Byte> {
        // Materialize Indexed<Byte> to ByteArray, then to Uint8Array for Web Crypto
        val inputArray = ByteArray(input.size) { i -> input[i] }
        val dataBuffer = Uint8Array(inputArray.toTypedArray()).buffer // Access underlying ArrayBuffer

        val digestPromise = WebCrypto.subtle.digest(
            algorithm = js("{ name: 'SHA-256' }"), // Algorithm identifier for Web Crypto
            data = dataBuffer
        )

        val digestBuffer = digestPromise.await() // Suspend until promise resolves

        // Convert the resulting ArrayBuffer (digest) back to ByteArray
        val hashInt8Array = Int8Array(digestBuffer)
        val hashBytes = ByteArray(hashInt8Array.length) { i -> hashInt8Array[i] }

        return hashBytes.toIndexed()
    }
}
