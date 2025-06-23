package borg.trikeshed.lib

actual fun getCurrentTimeMillis(): Long {
    // Use JavaScript's Date.now() for WASM/JS
    return js("Date.now()").unsafeCast<Long>()
}

actual fun getSystemProperty(key: String): String? {
    // For WASM/JS platforms, we'll return null for now
    // This can be enhanced with JavaScript property access if needed
    return null
} 