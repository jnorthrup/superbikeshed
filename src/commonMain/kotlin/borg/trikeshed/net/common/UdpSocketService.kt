package borg.trikeshed.net.common

import borg.trikeshed.lib.ByteSeries
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

class UdpSocketService(
    private val port: Int = 0,
    private val bufferSize: Int = 65507 // Maximum UDP packet size
) {
    private val socket = DatagramSocket(port)
    private val receiveBuffer = ByteArray(bufferSize)
    private val receiveChannel = Channel<Pair<ByteSeries, String>>(Channel.UNLIMITED)
    
    init {
        startReceiving()
    }
    
    private fun startReceiving() {
        Thread {
            while (!socket.isClosed) {
                try {
                    val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)
                    socket.receive(packet)
                    val data = ByteSeries(packet.data.copyOfRange(0, packet.length))
                    val address = "${packet.address.hostAddress}:${packet.port}"
                    receiveChannel.trySend(Pair(data, address))
                } catch (e: Exception) {
                    if (!socket.isClosed) {
                        // Handle error
                    }
                }
            }
        }.start()
    }
    
    fun receive(): Flow<Pair<ByteSeries, String>> = flow {
        for (data in receiveChannel) {
            emit(data)
        }
    }
    
    fun send(data: ByteSeries, address: String) {
        val (host, port) = address.split(":")
        val packet = DatagramPacket(
            data.toByteArray(),
            data.size,
            InetAddress.getByName(host),
            port.toInt()
        )
        socket.send(packet)
    }
    
    fun close() {
        socket.close()
        receiveChannel.close()
    }
} 