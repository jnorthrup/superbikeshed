package borg.trikeshed.common

/**
 * WasmJs implementation of error handling functions
 * Simplified stub implementations for compilation
 */

actual fun errorString(code: ErrorCode): ErrorMessage {
    return "Error code: $code"
}

actual fun errorFromKey(key: ErrorKey): ErrorCode {
    return 0
}

actual class PlatformException actual constructor(
    operation: String,
    errorCode: ErrorCode
) : Exception("$operation failed with error code: $errorCode")

actual fun platformToCommonError(platformError: Int): ErrorCode {
    return platformError
}

actual fun getLastError(): ErrorCode {
    return 0
}

actual fun setLastError(code: ErrorCode) {
    // No-op for stub
}