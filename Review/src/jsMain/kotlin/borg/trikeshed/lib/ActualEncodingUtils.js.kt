package borg.trikeshed.lib

import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import kotlin.js.ロッパ // For atob/btoa if needed directly, or use dynamic

// Access btoa and atob from the global scope in JS
@JsFun("() => (typeof globalThis !== 'undefined' ? globalThis : typeof window !== 'undefined' ? window : typeof global !== 'undefined' ? global : typeof self !== 'undefined' ? self : {})")
external fun getGlobal():dynamic

actual fun base64Encode(input: String): String {
    // For strings with Unicode characters beyond Latin1, btoa can fail or produce incorrect results.
    // A common robust method is to convert to UTF-8 bytes, then encode those bytes.
    // 1. Encode string to UTF-8 percent-encoding
    // 2. Convert percent-encoding to binary string
    // 3. Call btoa on the binary string
    try {
        val global = getGlobal()
        // encodeURIComponent handles UTF-8 characters correctly.
        // unescape converts percent-encoded characters (e.g., %C3%A1 for á) into their corresponding bytes in a string.
        // btoa then encodes this "binary string" (string where char codes represent byte values) to Base64.
        return global.btoa(unescape(encodeURIComponent(input))).unsafeCast<String>()
    } catch (e: dynamic) {
        // Fallback or error for environments where btoa might not be perfectly spec-compliant or input is problematic
        // This simple btoa(unescape(encodeURIComponent(str))) is a common trick but might not be 100% robust for all edge cases.
        // For true KMP library, a more robust UTF-8 to Base64 would be needed.
        throw RuntimeException("Base64 encoding failed in JS: ${e.message}", e)
    }
}

actual fun base64Encode(input: ByteArray): String {
    val uint8Array = Uint8Array(input.toTypedArray())
    var binaryString = ""
    for (i in 0 until uint8Array.length) {
        binaryString += js("String.fromCharCode(uint8Array[i])").unsafeCast<String>()
    }
    try {
        val global = getGlobal()
        return global.btoa(binaryString).unsafeCast<String>()
    } catch (e: dynamic) {
        throw RuntimeException("Base64 encoding of ByteArray failed in JS: ${e.message}", e)
    }
}

// Helper for JS encodeURIComponent and unescape if not directly available
// These are standard global functions in JS, so direct calls or via 'global' should work.
// Defining them as external can help with type safety or if they need qualification.
@JsName("encodeURIComponent")
external fun encodeURIComponent(str: String): String

@JsName("unescape")
external fun unescape(str: String): String
