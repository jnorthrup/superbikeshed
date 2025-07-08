package borg.trikeshed.crypto

// Expect declarations for platform-specific crypto implementations
expect suspend fun performDHKeyExchange(): Pair<ByteArray, ByteArray>
expect suspend fun performECDHKeyExchange(): Pair<ByteArray, ByteArray>