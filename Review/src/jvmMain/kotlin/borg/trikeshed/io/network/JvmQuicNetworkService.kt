package borg.trikeshed.io.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket as JavaDatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.UnknownHostException
import kotlin.coroutines.CoroutineContext

actual object JvmQuicNetworkService : QuicNetworkService {

    private var socket: DatagramSocket? = null
    private var _localAddress: NetworkAddress? = null

    override val key: CoroutineContext.Key<*> get() = QuicNetworkServiceKey

    override val isBound: Boolean
        get() = socket?.isBound == true && _localAddress != null

    override val localAddress: NetworkAddress?
        get() = _localAddress

    override suspend fun bind(localAddress: NetworkAddress?): NetworkAddress = withContext(Dispatchers.IO) {
        if (isBound) {
            close() // Close existing socket before rebinding
        }

        try {
            val newSocket = DatagramSocket(localAddress?.let { InetSocketAddress(it.host, it.port) }
                                           ?: InetSocketAddress(0)) // Bind to any local address, ephemeral port
            socket = newSocket

            val actualHost = newSocket.localAddress.hostAddress
            val actualPort = newSocket.localPort
            _localAddress = NetworkAddress(actualHost, actualPort)
            return@withContext _localAddress!!
        } catch (e: SocketException) {
            throw Exception("Failed to bind socket to ${localAddress ?: "any"}: ${e.message}", e)
        }
    }

    override suspend fun send(packet: borg.trikeshed.io.network.DatagramPacket) = withContext(Dispatchers.IO) {
        val currentSocket = socket ?: throw IllegalStateException("Socket not initialized or already closed.")
        if (!currentSocket.isConnected && !isBound) { // DatagramSocket can be connected or just bound for send/receive
             throw IllegalStateException("Socket is not bound or connected.")
        }

        try {
            val targetInetAddress = InetAddress.getByName(packet.address.host)
            val javaPacket = JavaDatagramPacket(
                packet.data,
                packet.length,
                targetInetAddress,
                packet.address.port
            )
            currentSocket.send(javaPacket)
        } catch (e: UnknownHostException) {
            throw Exception("Unknown host: ${packet.address.host}: ${e.message}", e)
        } catch (e: SocketException) {
            throw Exception("Socket error during send to ${packet.address}: ${e.message}", e)
        }  catch (e: Exception) {
            throw Exception("Failed to send packet to ${packet.address}: ${e.message}", e)
        }
    }

    override suspend fun receive(bufferSize: Int): borg.trikeshed.io.network.DatagramPacket = withContext(Dispatchers.IO) {
        val currentSocket = socket ?: throw IllegalStateException("Socket not initialized or already closed.")
        if (!isBound) {
             throw IllegalStateException("Socket is not bound.")
        }

        try {
            val buffer = ByteArray(bufferSize)
            val javaPacket = JavaDatagramPacket(buffer, buffer.size)
            currentSocket.receive(javaPacket) // This is a blocking call

            val receivedFromAddress = NetworkAddress(
                javaPacket.address.hostAddress,
                javaPacket.port
            )

            // Copy only the received part of the buffer
            val actualData = buffer.copyOf(javaPacket.length)

            return@withContext borg.trikeshed.io.network.DatagramPacket(actualData, receivedFromAddress, javaPacket.length)
        } catch (e: SocketException) {
            // Could be due to socket closed, timeout (if set), etc.
            throw Exception("Socket error during receive: ${e.message}", e)
        } catch (e: Exception) {
            throw Exception("Failed to receive packet: ${e.message}", e)
        }
    }

    override suspend fun resolve(hostname: String, port: Int): List<NetworkAddress> = withContext(Dispatchers.IO) {
        try {
            val addresses = InetAddress.getAllByName(hostname)
            return@withContext addresses.map { NetworkAddress(it.hostAddress, port) }
        } catch (e: UnknownHostException) {
            // Host not found or DNS resolution failed
            return@withContext emptyList()
        } catch (e: Exception) {
            throw Exception("Failed to resolve hostname $hostname: ${e.message}", e)
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        socket?.close()
        socket = null
        _localAddress = null
    }
}
