package org.trikeshed.net

import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.DatagramPacket

@JvmInline
value class QuicConnection(private val socket: DatagramSocket) {
    fun send(data: ByteArray) {
        socket.send(DatagramPacket(data, data.size, InetSocketAddress("localhost", 443)))
    }

    fun receive(): ByteArray {
        val buffer = ByteArray(1024)
        val packet = DatagramPacket(buffer, buffer.size)
        socket.receive(packet)
        return buffer.sliceArray(0 until packet.length)
    }
}