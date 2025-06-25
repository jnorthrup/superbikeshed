package borg.trikeshed.nio

expect class PlatformDatagramSocket() {
    fun connect(address: PlatformInetSocketAddress)
    fun send(packet: PlatformDatagramPacket)
    fun receive(packet: PlatformDatagramPacket)
    val isConnected: Boolean
    val isClosed: Boolean
    fun close()
    companion object {
        fun create(): PlatformDatagramSocket
    }
} 