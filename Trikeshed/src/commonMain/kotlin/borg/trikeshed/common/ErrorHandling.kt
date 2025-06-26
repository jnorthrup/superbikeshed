@file:Suppress("NOTHING_TO_INLINE", "FunctionName")
package borg.trikeshed.common
@file:OptIn(ExperimentalUnsignedTypes::class)


import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.*
import kotlin.jvm.JvmInline

// === ERROR HANDLING TAXONOMICAL TYPEALIASES ===

typealias ErrorCode = Int
typealias ErrorMessage = String
typealias ErrorKey = String
typealias SystemCallResult = Int

@JvmInline value class PosixError(val code: ErrorCode)
@JvmInline value class WindowsError(val code: ErrorCode)
@JvmInline value class HttpError(val code: ErrorCode)
@JvmInline value class DatabaseError(val code: ErrorCode)

// === COMMON ERROR CODES ===
object ErrorCodes {
    const val SUCCESS: ErrorCode = 0
    const val EPERM: ErrorCode = 1      // Operation not permitted
    const val ENOENT: ErrorCode = 2     // No such file or directory
    const val ESRCH: ErrorCode = 3      // No such process
    const val EINTR: ErrorCode = 4      // Interrupted system call
    const val EIO: ErrorCode = 5        // I/O error
    const val ENXIO: ErrorCode = 6      // No such device or address
    const val E2BIG: ErrorCode = 7      // Argument list too long
    const val ENOEXEC: ErrorCode = 8    // Exec format error
    const val EBADF: ErrorCode = 9      // Bad file number
    const val ECHILD: ErrorCode = 10    // No child processes
    const val EAGAIN: ErrorCode = 11    // Try again
    const val ENOMEM: ErrorCode = 12    // Out of memory
    const val EACCES: ErrorCode = 13    // Permission denied
    const val EFAULT: ErrorCode = 14    // Bad address
    const val ENOTBLK: ErrorCode = 15   // Block device required
    const val EBUSY: ErrorCode = 16     // Device or resource busy
    const val EEXIST: ErrorCode = 17    // File exists
    const val EXDEV: ErrorCode = 18     // Cross-device link
    const val ENODEV: ErrorCode = 19    // No such device
    const val ENOTDIR: ErrorCode = 20   // Not a directory
    const val EISDIR: ErrorCode = 21    // Is a directory
    const val EINVAL: ErrorCode = 22    // Invalid argument
    const val ENFILE: ErrorCode = 23    // File table overflow
    const val EMFILE: ErrorCode = 24    // Too many open files
    const val ENOTTY: ErrorCode = 25    // Not a typewriter
    const val ETXTBSY: ErrorCode = 26   // Text file busy
    const val EFBIG: ErrorCode = 27     // File too large
    const val ENOSPC: ErrorCode = 28    // No space left on device
    const val ESPIPE: ErrorCode = 29    // Illegal seek
    const val EROFS: ErrorCode = 30     // Read-only file system
    const val EMLINK: ErrorCode = 31    // Too many links
    const val EPIPE: ErrorCode = 32     // Broken pipe
    const val EDOM: ErrorCode = 33      // Math argument out of domain of func
    const val ERANGE: ErrorCode = 34    // Math result not representable
    const val EWOULDBLOCK: ErrorCode = EAGAIN  // Operation would block
}

// === ERROR CHECKING FUNCTIONS ===

/**
 * Check if a result code indicates an error (negative values)
 */
inline fun hasError(result: SystemCallResult): Boolean = result < 0

/**
 * Check if a result code indicates success (zero or positive)
 */
inline fun isSuccess(result: SystemCallResult): Boolean = result >= 0

/**
 * Expect a successful result, throw exception on error
 */
inline fun expectResult(result: SystemCallResult, operation: String = ""): SystemCallResult {
    if (hasError(result)) {
        throw SystemCallException(operation, result)
    }
    return result
}

/**
 * Expect a specific result value, throw exception if different
 */
inline fun expectValue(result: SystemCallResult, expected: SystemCallResult, operation: String = ""): SystemCallResult {
    if (result != expected) {
        throw SystemCallException("$operation: expected $expected but got $result", result)
    }
    return result
}

/**
 * Expect zero (success), throw exception on any other value
 */
inline fun expectZero(result: SystemCallResult, operation: String = ""): SystemCallResult {
    return expectValue(result, 0, operation)
}

/**
 * Convert error result to null, pass through success values
 */
inline fun <T> errorToNull(result: SystemCallResult, value: T): T? {
    return if (hasError(result)) null else value
}

/**
 * Map error code to Result<T>
 */
inline fun <T> errorToResult(result: SystemCallResult, value: T): Result<T> {
    return if (hasError(result)) {
        Result.failure(SystemCallException("System call failed", result))
    } else {
        Result.success(value)
    }
}

// === ERROR MESSAGE MAPPING ===

/**
 * Get error message for error code
 * Platform-specific implementations provide actual messages
 */
expect fun errorString(code: ErrorCode): ErrorMessage

/**
 * Get error code from error key/name
 */
expect fun errorFromKey(key: ErrorKey): ErrorCode

/**
 * Common error messages for fallback
 */
fun commonErrorString(code: ErrorCode): ErrorMessage = when (code) {
    ErrorCodes.SUCCESS -> "Success"
    ErrorCodes.EPERM -> "Operation not permitted"
    ErrorCodes.ENOENT -> "No such file or directory"
    ErrorCodes.ESRCH -> "No such process"
    ErrorCodes.EINTR -> "Interrupted system call"
    ErrorCodes.EIO -> "I/O error"
    ErrorCodes.ENXIO -> "No such device or address"
    ErrorCodes.E2BIG -> "Argument list too long"
    ErrorCodes.ENOEXEC -> "Exec format error"
    ErrorCodes.EBADF -> "Bad file number"
    ErrorCodes.ECHILD -> "No child processes"
    ErrorCodes.EAGAIN -> "Try again"
    ErrorCodes.ENOMEM -> "Out of memory"
    ErrorCodes.EACCES -> "Permission denied"
    ErrorCodes.EFAULT -> "Bad address"
    ErrorCodes.ENOTBLK -> "Block device required"
    ErrorCodes.EBUSY -> "Device or resource busy"
    ErrorCodes.EEXIST -> "File exists"
    ErrorCodes.EXDEV -> "Cross-device link"
    ErrorCodes.ENODEV -> "No such device"
    ErrorCodes.ENOTDIR -> "Not a directory"
    ErrorCodes.EISDIR -> "Is a directory"
    ErrorCodes.EINVAL -> "Invalid argument"
    ErrorCodes.ENFILE -> "File table overflow"
    ErrorCodes.EMFILE -> "Too many open files"
    ErrorCodes.ENOTTY -> "Not a typewriter"
    ErrorCodes.ETXTBSY -> "Text file busy"
    ErrorCodes.EFBIG -> "File too large"
    ErrorCodes.ENOSPC -> "No space left on device"
    ErrorCodes.ESPIPE -> "Illegal seek"
    ErrorCodes.EROFS -> "Read-only file system"
    ErrorCodes.EMLINK -> "Too many links"
    ErrorCodes.EPIPE -> "Broken pipe"
    ErrorCodes.EDOM -> "Math argument out of domain of func"
    ErrorCodes.ERANGE -> "Math result not representable"
    else -> "Unknown error $code"
}

/**
 * Common error key mapping
 */
fun commonErrorFromKey(key: ErrorKey): ErrorCode = when (key.uppercase()) {
    "SUCCESS" -> ErrorCodes.SUCCESS
    "EPERM" -> ErrorCodes.EPERM
    "ENOENT" -> ErrorCodes.ENOENT
    "ESRCH" -> ErrorCodes.ESRCH
    "EINTR" -> ErrorCodes.EINTR
    "EIO" -> ErrorCodes.EIO
    "ENXIO" -> ErrorCodes.ENXIO
    "E2BIG" -> ErrorCodes.E2BIG
    "ENOEXEC" -> ErrorCodes.ENOEXEC
    "EBADF" -> ErrorCodes.EBADF
    "ECHILD" -> ErrorCodes.ECHILD
    "EAGAIN" -> ErrorCodes.EAGAIN
    "ENOMEM" -> ErrorCodes.ENOMEM
    "EACCES" -> ErrorCodes.EACCES
    "EFAULT" -> ErrorCodes.EFAULT
    "ENOTBLK" -> ErrorCodes.ENOTBLK
    "EBUSY" -> ErrorCodes.EBUSY
    "EEXIST" -> ErrorCodes.EEXIST
    "EXDEV" -> ErrorCodes.EXDEV
    "ENODEV" -> ErrorCodes.ENODEV
    "ENOTDIR" -> ErrorCodes.ENOTDIR
    "EISDIR" -> ErrorCodes.EISDIR
    "EINVAL" -> ErrorCodes.EINVAL
    "ENFILE" -> ErrorCodes.ENFILE
    "EMFILE" -> ErrorCodes.EMFILE
    "ENOTTY" -> ErrorCodes.ENOTTY
    "ETXTBSY" -> ErrorCodes.ETXTBSY
    "EFBIG" -> ErrorCodes.EFBIG
    "ENOSPC" -> ErrorCodes.ENOSPC
    "ESPIPE" -> ErrorCodes.ESPIPE
    "EROFS" -> ErrorCodes.EROFS
    "EMLINK" -> ErrorCodes.EMLINK
    "EPIPE" -> ErrorCodes.EPIPE
    "EDOM" -> ErrorCodes.EDOM
    "ERANGE" -> ErrorCodes.ERANGE
    "EWOULDBLOCK" -> ErrorCodes.EWOULDBLOCK
    else -> -1
}

// === EXCEPTION TYPES ===

/**
 * Exception thrown when a system call fails
 */
class SystemCallException(
    val operation: String,
    val errorCode: ErrorCode,
    message: String? = null
) : Exception(message ?: "$operation failed: ${errorString(errorCode)} (error $errorCode)")

/**
 * Exception with platform-specific error information
 */
expect class PlatformException(
    operation: String,
    errorCode: ErrorCode
) : Exception

// === UTILITY FUNCTIONS ===

/**
 * Execute block and convert exceptions to error codes
 */
inline fun <T> trySystemCall(block: () -> T): Join<SystemCallResult, T?> {
    return try {
        val result = block()
        Join(ErrorCodes.SUCCESS, result)
    } catch (e: SystemCallException) {
        Join(e.errorCode, null)
    } catch (e: Exception) {
        Join(-1, null)
    }
}

/**
 * Retry a system call on EINTR
 */
inline fun <T> retryOnInterrupt(maxRetries: Int = 3, block: () -> T): T {
    var retries = 0
    while (retries < maxRetries) {
        try {
            return block()
        } catch (e: SystemCallException) {
            if (e.errorCode == ErrorCodes.EINTR && retries < maxRetries - 1) {
                retries++
                continue
            }
            throw e
        }
    }
    throw SystemCallException("Max retries exceeded", ErrorCodes.EINTR)
}

/**
 * Convert platform-specific error codes to common codes
 */
expect fun platformToCommonError(platformError: Int): ErrorCode

/**
 * Get last error code from platform
 */
expect fun getLastError(): ErrorCode

/**
 * Set last error code on platform
 */
expect fun setLastError(code: ErrorCode)

// === ERROR CODE BUILDERS ===

/**
 * Build error result with code
 */
inline fun errorResult(code: ErrorCode): SystemCallResult = -code

/**
 * Build success result with value
 */
inline fun successResult(value: Int = 0): SystemCallResult = value

/**
 * Chain multiple system calls, stop on first error
 */
inline fun chainSystemCalls(vararg calls: () -> SystemCallResult): SystemCallResult {
    for (call in calls) {
        val result = call()
        if (hasError(result)) {
            return result
        }
    }
    return ErrorCodes.SUCCESS
}

/**
 * Execute cleanup even if main block fails
 */
inline fun <T> withCleanup(
    cleanup: () -> Unit,
    block: () -> T
): T {
    try {
        return block()
    } finally {
        cleanup()
    }
}