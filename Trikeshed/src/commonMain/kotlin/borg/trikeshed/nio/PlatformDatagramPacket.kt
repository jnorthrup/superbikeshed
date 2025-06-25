package borg.trikeshed.nio

expect class PlatformDatagramPacket(data: ByteArray, length: Int, address: PlatformInetSocketAddress) {
    val data: ByteArray
    val length: Int
    val address: PlatformInetSocketAddress
} 