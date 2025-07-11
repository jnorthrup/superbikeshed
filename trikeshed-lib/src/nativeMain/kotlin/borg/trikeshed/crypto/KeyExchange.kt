package borg.trikeshed.crypto

import kotlinx.cinterop.*
import platform.posix.*

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual suspend fun performDHKeyExchange(): Pair<ByteArray, ByteArray> {
    // TODO: Implement native DH key exchange
    // For now, return dummy values
    return Pair(ByteArray(32) { it.toByte() }, ByteArray(32) { (it + 1).toByte() })
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual suspend fun performECDHKeyExchange(): Pair<ByteArray, ByteArray> {
    // TODO: Implement native ECDH key exchange
    // For now, return dummy values
    return Pair(ByteArray(32) { (it + 2).toByte() }, ByteArray(32) { (it + 3).toByte() })
} 