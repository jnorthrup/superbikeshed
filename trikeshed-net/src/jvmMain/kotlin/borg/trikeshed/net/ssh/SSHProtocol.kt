package borg.trikeshed.net.ssh

import java.security.SecureRandom

/**
 * JVM implementation of SSH crypto functions
 */
actual fun getSecureRandom(): ByteArray = ByteArray(32).apply {
    SecureRandom().nextBytes(this)
}

actual fun randomBytes(size: Int): ByteArray = ByteArray(size).apply {
    SecureRandom().nextBytes(this)
}