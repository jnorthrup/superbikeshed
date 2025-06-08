package borg.trikeshed.io.network

import kotlin.coroutines.CoroutineContext
import borg.trikeshed.lib.Series // Placeholder import
import borg.trikeshed.lib.Join // Placeholder import

// Key for the QUIC Network Service in the CoroutineContext
object QuicNetworkServiceKey : CoroutineContext.Key<QuicNetworkService>

// Represents a network address (IP and port)
typealias NetworkAddress = Join<String, Int>
// Consider adding extension properties for clarity if direct .first/.second is too verbose:
// val NetworkAddress.host: String get() = first
// val NetworkAddress.port: Int get() = second

// Represents a UDP datagram
// Structure: Join(data: ByteArray, Join(address: NetworkAddress, length: Int))
typealias DatagramPacket = Join<ByteArray, Join<NetworkAddress, Int>>

// Extension properties for easier access to DatagramPacket components
val DatagramPacket.data: ByteArray get() = this.first
val DatagramPacket.address: NetworkAddress get() = this.second.first
val DatagramPacket.length: Int get() = this.second.second


// Interface for QUIC network operations
interface QuicNetworkService : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = QuicNetworkServiceKey

    // Binds the UDP socket to a local address.
    // If address is null or port is 0, an ephemeral port is typically chosen.
    suspend fun bind(localAddress: NetworkAddress? = null): NetworkAddress

    // Sends a datagram packet to the specified remote address.
    suspend fun send(packet: DatagramPacket)

    // Receives a datagram packet. This function will suspend until a packet is received.
    // The bufferSize parameter suggests the expected maximum size of the incoming packet.
    suspend fun receive(bufferSize: Int): DatagramPacket

    // Resolves a hostname to a list of NetworkAddresses.
    // Could return multiple if DNS resolves to multiple IPs.
    suspend fun resolve(hostname: String, port: Int): Series<NetworkAddress>

    // Closes the UDP socket and releases any underlying resources.
    suspend fun close()

    // Checks if the socket is currently bound.
    val isBound: Boolean

    // Gets the local address the socket is bound to, or null if not bound.
    val localAddress: NetworkAddress?
}
