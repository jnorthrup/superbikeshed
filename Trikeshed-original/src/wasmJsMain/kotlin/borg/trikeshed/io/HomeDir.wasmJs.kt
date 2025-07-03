package borg.trikeshed.io

// WASM-JS doesn't have traditional file system access
actual val homedirGet: String = "/wasm-storage" // Virtual path for WASM context

actual fun mktemp(): String {
    // Generate a random temporary path for WASM
    val randomId = kotlin.random.Random.nextInt(100000, 999999)
    return "/tmp/wasm_temp_$randomId"
}

actual fun rm(path: String): Boolean {
    // In WASM context, this would interact with host-provided file APIs
    // For now, return success stub
    return true
}

actual fun mkdir(path: String): Boolean {
    // In WASM context, this would create storage structures
    // For now, return success stub
    return true
}