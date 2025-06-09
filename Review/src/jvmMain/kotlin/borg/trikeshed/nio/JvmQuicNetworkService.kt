package borg.trikeshed.nio // Updated package declaration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket as JavaDatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.UnknownHostException
import kotlin.coroutines.CoroutineContext
import borg.trikeshed.lib.Series // Placeholder import
import borg.trikeshed.lib.toSeries // Placeholder import for List<T>.toSeries() extension
import borg.trikeshed.lib.Join // Placeholder import
// Imports for types from the original package (borg.trikeshed.io.network)
// are needed if they are not automatically resolved due to 'actual' keyword.
// However, for 'actual' implementations, the Kotlin compiler usually resolves
// the 'expect' members correctly without explicit import of the 'expect' package.
// Let's assume these are correctly resolved for now.
// If not, we'd need:
import borg.trikeshed.io.network.QuicNetworkService
import borg.trikeshed.io.network.NetworkAddress
import borg.trikeshed.io.network.DatagramPacket // This is now Join, accessed via extensions
import borg.trikeshed.io.network.QuicNetworkServiceKey
// Import extension properties for DatagramPacket if they are in a different package after refactor
import borg.trikeshed.io.network.data // Extension property
import borg.trikeshed.io.network.address // Extension property
import borg.trikeshed.io.network.length // Extension property


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
            val newSocket = DatagramSocket(localAddress?.let { InetSocketAddress(it.first, it.second) }
                                           ?: InetSocketAddress(0)) // Bind to any local address, ephemeral port
            socket = newSocket

            val actualHost = newSocket.localAddress.hostAddress
            val actualPort = newSocket.localPort
            _localAddress = Join(actualHost, actualPort)
            return@withContext _localAddress!!
        } catch (e: SocketException) {
            throw Exception("Failed to bind socket to ${localAddress?.let { it.first + ":" + it.second } ?: "any"}: ${e.message}", e)
        }
    }

    override suspend fun send(packet: DatagramPacket) = withContext(Dispatchers.IO) {
        val currentSocket = socket ?: throw IllegalStateException("Socket not initialized or already closed.")
        if (!currentSocket.isConnected && !isBound) { // DatagramSocket can be connected or just bound for send/receive
             throw IllegalStateException("Socket is not bound or connected.")
        }

        try {
            val targetInetAddress = InetAddress.getByName(packet.address.first) // packet.address is Join<String, Int>
            val javaPacket = JavaDatagramPacket(
                packet.data, // packet.data is ByteArray
                packet.length, // packet.length is Int
                targetInetAddress,
                packet.address.second // port
            )
            currentSocket.send(javaPacket)
        } catch (e: UnknownHostException) {
            throw Exception("Unknown host: ${packet.address.first}: ${e.message}", e)
        } catch (e: SocketException) {
            throw Exception("Socket error during send to ${packet.address.first}:${packet.address.second}: ${e.message}", e)
        }  catch (e: Exception) {
            throw Exception("Failed to send packet to ${packet.address.first}:${packet.address.second}: ${e.message}", e)
        }
    }

    override suspend fun receive(bufferSize: Int): DatagramPacket = withContext(Dispatchers.IO) {
        val currentSocket = socket ?: throw IllegalStateException("Socket not initialized or already closed.")
        if (!isBound) {
             throw IllegalStateException("Socket is not bound.")
        }

        try {
            val buffer = ByteArray(bufferSize)
            val javaPacket = JavaDatagramPacket(buffer, buffer.size)
            currentSocket.receive(javaPacket) // This is a blocking call

            val receivedFromAddress = Join(
                javaPacket.address.hostAddress,
                javaPacket.port
            )

            // Copy only the received part of the buffer
            val actualData = buffer.copyOf(javaPacket.length)

            // Constructing the new DatagramPacket type: Join<ByteArray, Join<NetworkAddress, Int>>
            return@withContext Join(actualData, Join(receivedFromAddress, javaPacket.length))
        } catch (e: SocketException) {
            // Could be due to socket closed, timeout (if set), etc.
            throw Exception("Socket error during receive: ${e.message}", e)
        } catch (e: Exception) {
            throw Exception("Failed to receive packet: ${e.message}", e)
        }
    }

    override suspend fun resolve(hostname: String, port: Int): Series<NetworkAddress> = withContext(Dispatchers.IO) {
        try {
            val addresses = InetAddress.getAllByName(hostname)
            return@withContext addresses.map { Join(it.hostAddress, port) }.toSeries()
        } catch (e: UnknownHostException) {
            // Host not found or DNS resolution failed
            return@withContext emptyList<NetworkAddress>().toSeries()
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
