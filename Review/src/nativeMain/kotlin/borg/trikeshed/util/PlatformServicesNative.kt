package borg.trikeshed.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.cinterop.convert
import kotlinx.datetime.Clock // Requires kotlinx-datetime dependency
import libopenssl.RAND_bytes // Assuming OpenSSL is available and cinterop setup includes RAND_bytes
import libopenssl.ERR_get_error
import libopenssl.ERR_error_string

/**
 * Native implementation of [ClockService].
 * Uses `kotlinx.datetime.Clock` for platform-agnostic time.
 */
actual class PlatformClockService : ClockService {
    override fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
}

/**
 * Native implementation of [SecureRandomService].
 * Uses OpenSSL's `RAND_bytes` for cryptographically secure random number generation.
 */
@OptIn(ExperimentalForeignApi::class)
actual class PlatformSecureRandomService : SecureRandomService {
    override fun nextBytes(array: ByteArray) {
        if (array.isEmpty()) return

        // For Native, it's common to pin the array when passing to C functions
        // if the C function expects a stable pointer for the duration of the call.
        // RAND_bytes likely falls into this category.
        array.usePinned { pinnedArray ->
            val result = RAND_bytes(pinnedArray.addressOf(0).reinterpret(), array.size.convert())
            if (result != 1) {
                // RAND_bytes returns 1 on success, 0 on error, -1 if not supported (should not happen for OpenSSL's default).
                // Check OpenSSL error queue for more details if needed.
                val errorCode = ERR_get_error()
                val errorString = ERR_error_string(errorCode, null)?.toKString() ?: "Unknown OpenSSL error"
                // TODO: Consider fallback mechanisms if OpenSSL RAND_bytes fails critically.
                //  Examples: platform.posix.arc4random_buf (macOS, BSDs), /dev/urandom (Linux).
                //  For now, a runtime exception is thrown indicating a failure in the primary CSPRNG.
                throw RuntimeException("Failed to generate secure random bytes using OpenSSL RAND_bytes. Result: $result. Error: $errorString (Code: $errorCode)")
            }
        }
    }
}
