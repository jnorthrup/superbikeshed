package borg.trikeshed.crypto

actual suspend fun performDHKeyExchange(): Pair<ByteArray, ByteArray> = throw NotImplementedError("DH key exchange not implemented for JS")
actual suspend fun performECDHKeyExchange(): Pair<ByteArray, ByteArray> = throw NotImplementedError("ECDH key exchange not implemented for JS") 