package borg.trikeshed.nio

import java.net.DatagramSocket
import java.net.InetSocketAddress

actual class PlatformDatagramSocket actual constructor() {
    private val socket = DatagramSocket()
    private var connected = false
    actual fun connect(address: PlatformInetSocketAddress) {
        socket.connect(InetSocketAddress(address.host, address.port))
        connected = true
    }
    actual fun send(packet: PlatformDatagramPacket) {
        val dp = java.net.DatagramPacket(packet.data, packet.length, InetSocketAddress(packet.address.host, packet.address.port))
        socket.send(dp)
    }
    actual fun receive(packet: PlatformDatagramPacket) {
        val dp = java.net.DatagramPacket(packet.data, packet.length)
        socket.receive(dp)
    }
    actual val isConnected: Boolean get() = connected
    actual val isClosed: Boolean get() = socket.isClosed
    actual fun close() { socket.close() }
    actual companion object {
        actual fun create(): PlatformDatagramSocket = PlatformDatagramSocket()
    }
} 