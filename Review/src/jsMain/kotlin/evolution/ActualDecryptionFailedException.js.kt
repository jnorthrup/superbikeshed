package evolution

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array


// For JS, we can use a standard Error or extend it.
// Kotlin/JS exceptions are Throwable.
actual class ActualDecryptionFailedException actual constructor(
    message: String,
    cause: Throwable?
) : Exception(message, cause) { // Standard Kotlin Exception
    actual constructor(message: String) : this(message, null)
}

// Helper extensions (if not already in a shared file from ActualHkdfService.js.kt)
// These should be defined once, e.g., in a common JS util file.
// Included here as per plan for this file, but ideally shared from ActualHkdfService.js.kt
// or a dedicated JsCryptoUtils.kt file within the 'evolution' package.
// If ActualHkdfService.js.kt already defines these as internal, they are scoped to that file.
// To be truly shared, they'd need to be public or in a file accessible by both.
// For this subtask, this duplication is acceptable as per instructions.

internal fun ByteArray.toUint8Array(): Uint8Array {
    return Uint8Array(this.toTypedArray())
}

internal fun ArrayBuffer.toByteArray(): ByteArray {
    return Int8Array(this).unsafeCast<ByteArray>()
}
