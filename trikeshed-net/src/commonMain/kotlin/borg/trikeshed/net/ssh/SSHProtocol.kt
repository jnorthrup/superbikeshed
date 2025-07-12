package borg.trikeshed.net.ssh

/**
 * Minimal SSH crypto functions for expect/actual compatibility
 */
expect fun getSecureRandom(): ByteArray
expect fun randomBytes(size: Int): ByteArray