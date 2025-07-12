package borg.trikeshed.net.ssh

actual fun getSecureRandom(): ByteArray = ByteArray(16) { it.toByte() } // TODO: Implement actual secure random
actual fun randomBytes(size: Int): ByteArray = ByteArray(size) { it.toByte() } // TODO: Implement actual random bytes
