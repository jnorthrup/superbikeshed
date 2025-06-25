package borg.trikeshed.nio

actual class PlatformDatagramPacket actual constructor(
    actual val data: ByteArray,
    actual val length: Int,
    actual val address: PlatformInetSocketAddress
) 