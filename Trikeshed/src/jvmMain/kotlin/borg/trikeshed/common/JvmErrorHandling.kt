@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.common

import java.io.IOException
import java.nio.file.*

/**
 * Get error string for error code on JVM
 */
actual fun errorString(code: ErrorCode): ErrorMessage = when (code) {
    ErrorCodes.SUCCESS -> "Success"
    ErrorCodes.EPERM -> "Operation not permitted"
    ErrorCodes.ENOENT -> "No such file or directory"
    ErrorCodes.ESRCH -> "No such process"
    ErrorCodes.EINTR -> "Interrupted system call"
    ErrorCodes.EIO -> "Input/output error"
    ErrorCodes.ENXIO -> "Device not configured"
    ErrorCodes.E2BIG -> "Argument list too long"
    ErrorCodes.ENOEXEC -> "Exec format error"
    ErrorCodes.EBADF -> "Bad file descriptor"
    ErrorCodes.ECHILD -> "No child processes"
    ErrorCodes.EAGAIN -> "Resource temporarily unavailable"
    ErrorCodes.ENOMEM -> "Cannot allocate memory"
    ErrorCodes.EACCES -> "Permission denied"
    ErrorCodes.EFAULT -> "Bad address"
    ErrorCodes.ENOTBLK -> "Block device required"
    ErrorCodes.EBUSY -> "Device or resource busy"
    ErrorCodes.EEXIST -> "File exists"
    ErrorCodes.EXDEV -> "Cross-device link"
    ErrorCodes.ENODEV -> "Operation not supported by device"
    ErrorCodes.ENOTDIR -> "Not a directory"
    ErrorCodes.EISDIR -> "Is a directory"
    ErrorCodes.EINVAL -> "Invalid argument"
    ErrorCodes.ENFILE -> "Too many open files in system"
    ErrorCodes.EMFILE -> "Too many open files"
    ErrorCodes.ENOTTY -> "Inappropriate ioctl for device"
    ErrorCodes.ETXTBSY -> "Text file busy"
    ErrorCodes.EFBIG -> "File too large"
    ErrorCodes.ENOSPC -> "No space left on device"
    ErrorCodes.ESPIPE -> "Illegal seek"
    ErrorCodes.EROFS -> "Read-only file system"
    ErrorCodes.EMLINK -> "Too many links"
    ErrorCodes.EPIPE -> "Broken pipe"
    ErrorCodes.EDOM -> "Numerical argument out of domain"
    ErrorCodes.ERANGE -> "Result too large"
    ErrorCodes.ENOTEMPTY -> "Directory not empty"
    else -> "Unknown error: $code"
}

/**
 * Map error key to error code on JVM
 */
actual fun errorFromKey(key: ErrorKey): ErrorCode = when (key) {
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
    "EAGAIN", "EWOULDBLOCK" -> ErrorCodes.EAGAIN
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
    "ENOTEMPTY" -> ErrorCodes.ENOTEMPTY
    else -> -1
}

/**
 * Platform-specific exception class for JVM
 */
actual class PlatformException actual constructor(
    operation: String,
    errorCode: ErrorCode
) : Exception("$operation failed with error $errorCode: ${errorString(errorCode)}")

/**
 * Convert platform error codes to common error codes on JVM
 */
actual fun platformToCommonError(platformError: Int): ErrorCode = when (platformError) {
    // On JVM, we don't have direct errno access, so we just pass through
    in 0..255 -> platformError
    else -> -1
}

// Thread-local storage for last error
private val lastErrorThreadLocal = ThreadLocal.withInitial { 0 }

/**
 * Get last error code on JVM
 */
actual fun getLastError(): ErrorCode = lastErrorThreadLocal.get()

/**
 * Set last error code on JVM
 */
actual fun setLastError(code: ErrorCode) {
    lastErrorThreadLocal.set(code)
}