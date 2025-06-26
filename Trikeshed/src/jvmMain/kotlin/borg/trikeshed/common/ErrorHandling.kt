package borg.trikeshed.common

// === JVM ERROR HANDLING IMPLEMENTATION ===

/**
 * Get error message for error code on JVM
 */
actual fun errorString(code: ErrorCode): ErrorMessage {
    // On JVM, we can use system properties or native methods if available
    // For now, fallback to common messages
    return commonErrorString(code)
}

/**
 * Get error code from error key/name on JVM
 */
actual fun errorFromKey(key: ErrorKey): ErrorCode {
    // Try to get from system property first
    val sysProp = System.getProperty("error.$key")
    if (sysProp != null) {
        return sysProp.toIntOrNull() ?: commonErrorFromKey(key)
    }
    return commonErrorFromKey(key)
}

/**
 * JVM-specific platform exception
 */
actual class PlatformException actual constructor(
    operation: String,
    errorCode: ErrorCode
) : Exception("$operation failed with error code $errorCode: ${errorString(errorCode)}")

/**
 * Convert platform-specific error codes to common codes on JVM
 */
actual fun platformToCommonError(platformError: Int): ErrorCode {
    // JVM typically uses standard POSIX error codes
    return platformError
}

/**
 * Get last error code from platform on JVM
 */
actual fun getLastError(): ErrorCode {
    // JVM doesn't have a direct equivalent to errno
    // Could use ThreadLocal or native method
    return lastErrorThreadLocal.get() ?: 0
}

/**
 * Set last error code on platform on JVM
 */
actual fun setLastError(code: ErrorCode) {
    lastErrorThreadLocal.set(code)
}

// Thread-local storage for last error
private val lastErrorThreadLocal = ThreadLocal<ErrorCode>()

/**
 * JVM-specific error utilities
 */
object JvmErrors {
    /**
     * Convert IOException to error code
     */
    fun fromIOException(e: java.io.IOException): ErrorCode {
        return when {
            e is java.io.FileNotFoundException -> ErrorCodes.ENOENT
            e is java.nio.file.AccessDeniedException -> ErrorCodes.EACCES
            e is java.nio.file.NoSuchFileException -> ErrorCodes.ENOENT
            e is java.nio.file.FileAlreadyExistsException -> ErrorCodes.EEXIST
            e is java.nio.file.DirectoryNotEmptyException -> ErrorCodes.ENOTEMPTY
            e is java.nio.file.NotDirectoryException -> ErrorCodes.ENOTDIR
            e.message?.contains("Permission denied") == true -> ErrorCodes.EACCES
            e.message?.contains("No space left") == true -> ErrorCodes.ENOSPC
            e.message?.contains("Too many open files") == true -> ErrorCodes.EMFILE
            else -> ErrorCodes.EIO
        }
    }
    
    /**
     * Execute with proper error code tracking
     */
    inline fun <T> withErrorTracking(block: () -> T): T {
        setLastError(ErrorCodes.SUCCESS)
        try {
            return block()
        } catch (e: java.io.IOException) {
            val errorCode = fromIOException(e)
            setLastError(errorCode)
            throw SystemCallException("I/O operation", errorCode, e.message)
        }
    }
}