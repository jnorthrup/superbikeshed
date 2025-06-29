@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package borg.trikeshed.common

import kotlinx.cinterop.*
import platform.posix.*

// === NATIVE ERROR HANDLING IMPLEMENTATION ===

/**
 * Get error message for error code on Native platforms
 */
actual fun errorString(code: ErrorCode): ErrorMessage {
    // Use strerror to get platform-specific error message
    val message = strerror(code)
    return if (message != null) {
        message.toKString()
    } else {
        commonErrorString(code)
    }
}

/**
 * Get error code from error key/name on Native platforms
 */
actual fun errorFromKey(key: ErrorKey): ErrorCode {
    // First try common mapping
    val commonCode = commonErrorFromKey(key)
    if (commonCode != -1) return commonCode
    
    // Platform-specific mappings could be added here
    return when (key.uppercase()) {
        "ENOTEMPTY" -> 39  // Directory not empty (POSIX)
        "ENETDOWN" -> 50   // Network is down
        "ENETUNREACH" -> 51 // Network is unreachable
        "ENETRESET" -> 52  // Network dropped connection on reset
        "ECONNABORTED" -> 53 // Software caused connection abort
        "ECONNRESET" -> 54 // Connection reset by peer
        "ENOBUFS" -> 55    // No buffer space available
        "EISCONN" -> 56    // Socket is already connected
        "ENOTCONN" -> 57   // Socket is not connected
        "ESHUTDOWN" -> 58  // Can't send after socket shutdown
        "ETIMEDOUT" -> 60  // Connection timed out
        "ECONNREFUSED" -> 61 // Connection refused
        "ELOOP" -> 62      // Too many levels of symbolic links
        "ENAMETOOLONG" -> 63 // File name too long
        "EHOSTDOWN" -> 64  // Host is down
        "EHOSTUNREACH" -> 65 // No route to host
        else -> -1
    }
}

/**
 * Native platform exception
 */
actual class PlatformException actual constructor(
    operation: String,
    errorCode: ErrorCode
) : Exception("$operation failed: ${strerror(errorCode)?.toKString() ?: "Unknown error $errorCode"} (errno=$errorCode)")

/**
 * Convert platform-specific error codes to common codes on Native
 */
actual fun platformToCommonError(platformError: Int): ErrorCode {
    // On POSIX systems, error codes are already standardized
    return platformError
}

/**
 * Get last error code from platform on Native
 */
actual fun getLastError(): ErrorCode {
    return if (lastErrorCode != 0) lastErrorCode else platform.posix.errno
}

/**
 * Set last error code on platform on Native
 */
actual fun setLastError(code: ErrorCode) {
    // errno is read-only in Kotlin/Native, store in thread-local instead
    lastErrorCode = code
}

// Thread-local error storage for native
internal var lastErrorCode: ErrorCode = 0

/**
 * Native-specific error utilities
 */
object NativeErrors {
    /**
     * Execute system call and check result
     */
    internal inline fun <T> systemCall(
        operation: String = "",
        block: () -> T
    ): T {
        lastErrorCode = 0 // Clear last error before call
        val result = block()
        
        // Check if result indicates error (typically -1 for int returns)
        if (result is Int && result == -1 && platform.posix.errno != 0) {
            lastErrorCode = platform.posix.errno
            throw SystemCallException(operation, platform.posix.errno)
        }
        
        return result
    }
    
    /**
     * Execute system call that returns pointer, check for NULL
     */
    internal inline fun <T : CPointer<*>?> systemCallPtr(
        operation: String = "",
        block: () -> T
    ): T {
        lastErrorCode = 0 // Clear last error before call
        val result = block()
        
        if (result == null && platform.posix.errno != 0) {
            lastErrorCode = platform.posix.errno
            throw SystemCallException(operation, platform.posix.errno)
        }
        
        return result
    }
    
    /**
     * Check if errno indicates a temporary error that should be retried
     */
    fun isTemporaryError(code: ErrorCode = errno): Boolean {
        return when (code) {
            EINTR,     // Interrupted system call
            EAGAIN,    // Resource temporarily unavailable
            EWOULDBLOCK -> true // Operation would block
            else -> false
        }
    }
    
    /**
     * Check if errno indicates a resource error
     */
    fun isResourceError(code: ErrorCode = errno): Boolean {
        return when (code) {
            EMFILE,    // Too many open files
            ENFILE,    // Too many open files in system
            ENOBUFS,   // No buffer space available
            ENOMEM,    // Out of memory
            ENOSPC,    // No space left on device
            EDQUOT -> true // Disk quota exceeded
            else -> false
        }
    }
    
    /**
     * Platform-specific error code definitions
     */
    object Codes {
        const val ENOTEMPTY = 39
        const val ENETDOWN = 50
        const val ENETUNREACH = 51
        const val ENETRESET = 52
        const val ECONNABORTED = 53
        const val ECONNRESET = 54
        const val ENOBUFS = 55
        const val EISCONN = 56
        const val ENOTCONN = 57
        const val ESHUTDOWN = 58
        const val ETIMEDOUT = 60
        const val ECONNREFUSED = 61
        const val ELOOP = 62
        const val ENAMETOOLONG = 63
        const val EHOSTDOWN = 64
        const val EHOSTUNREACH = 65
        const val EDQUOT = 69
    }
}