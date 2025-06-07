package borg.trikeshed.reactor

import platform.posix.POLLIN
import platform.posix.POLLOUT

actual class IOOperation private constructor(actual val value: Int) {
    // Companion object contains actual implementations for expect properties.
    actual companion object {
        actual val ACCEPT: IOOperation = IOOperation(POLLIN) // Using POLLIN for accept readiness
        actual val READ: IOOperation = IOOperation(POLLIN) // Using POLLIN for read readiness
        actual val WRITE: IOOperation = IOOperation(POLLOUT) // Using POLLOUT for write readiness
        actual val CONNECT: IOOperation = IOOperation(POLLOUT) // Using POLLOUT for connect readiness (typical for non-blocking connect)
    }
}
