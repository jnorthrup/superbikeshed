package borg.trikeshed.io.network

import kotlin.coroutines.CoroutineContext

// Key for the QUIC Network Service in the CoroutineContext
object QuicNetworkServiceKey : CoroutineContext.Key<QuicNetworkService>

// Represents a network address (IP and port)
data class NetworkAddress(val host: String, val port: Int) {
    override fun toString(): String = "$host:$port"
}

// Represents a UDP datagram
data class DatagramPacket(
    val data: ByteArray,
    val address: NetworkAddress,
    val length: Int = data.size // Actual length of data in the buffer, can be less than data.size
) {
    // Ensure data is copied to avoid external modification issues if needed,
    // or document that the ByteArray is not to be modified.
    // For now, assume direct use.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as DatagramPacket

        if (!data.contentEquals(other.data)) return false
        if (address != other.address) return false
        if (length != other.length) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + address.hashCode()
        result = 31 * result + length
        return result
    }
}

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
    suspend fun resolve(hostname: String, port: Int): List<NetworkAddress>

    // Closes the UDP socket and releases any underlying resources.
    suspend fun close()

    // Checks if the socket is currently bound.
    val isBound: Boolean

    // Gets the local address the socket is bound to, or null if not bound.
    val localAddress: NetworkAddress?
}
