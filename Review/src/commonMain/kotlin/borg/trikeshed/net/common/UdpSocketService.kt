package borg.trikeshed.net.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.CoroutineContext

/**
 * Defines the contract for a platform-agnostic UDP socket service factory.
 * Its primary role is to bind to a local address and provide a [BoundUdpSocket].
 */
interface UdpSocketService {
    /**
     * Represents a network socket address (host/IP and port).
     * @property host For binding, this can be a hostname (e.g., "0.0.0.0", "::", or a specific interface's hostname/IP).
     *                For received packets ([UdpPacket.senderAddress]), this will typically be the sender's IP address string.
     * @property port The port number.
     */
    data class SocketAddress(val host: String, val port: Int)

    /**
     * Represents a UDP packet received or to be sent.
     * @property data The byte payload of the packet.
     * @property address For outgoing packets, this is the target [SocketAddress].
     *                   For incoming packets ([BoundUdpSocket.incomingPackets]), this is the [SocketAddress] of the sender.
     * @property length The actual length of the data in the `data` ByteArray. Defaults to `data.size`.
     *                  Useful when `data` is a pre-allocated buffer larger than the UDP payload.
     * @property localAddress For incoming packets, this is the [SocketAddress] of the local interface on which the packet was received.
     *                        May be null if not available or not applicable (e.g., for outgoing packets before sending).
     */
    data class UdpPacket(
        val data: ByteArray,
        val address: SocketAddress, // Renamed from senderAddress for dual use (target for send, sender for receive)
        val length: Int = data.size,
        val localAddress: SocketAddress? = null // For received packets, identifies receiving interface
    ) {
        // Content-aware equals/hashCode for data
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is UdpPacket) return false
            if (!data.contentEquals(other.data)) return false
            if (address != other.address) return false
            if (length != other.length) return false
            if (localAddress != other.localAddress) return false
            return true
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + address.hashCode()
            result = 31 * result + length.hashCode()
            result = 31 * result + (localAddress?.hashCode() ?: 0)
            return result
        }
    }

    /**
     * Binds to the specified local address to listen for incoming UDP packets.
     *
     * @param localAddress The [SocketAddress] to bind to. Port can be 0 to request an ephemeral port.
     *                     Host can be "0.0.0.0" (IPv4 any) or "::" (IPv6 any) or a specific IP.
     * @return A [Result] containing the [BoundUdpSocket] on success, or an [Exception] on failure.
     */
    suspend fun bind(localAddress: SocketAddress, coroutineContext: CoroutineContext): Result<BoundUdpSocket>

    // Optional: A version that binds to an ephemeral port on a specific interface IP
    // suspend fun bind(localIp: String, localPort: Int = 0): Result<BoundUdpSocket>
}

/**
 * Represents a UDP socket that is bound to a specific local address and can send/receive packets.
 * It operates within a [CoroutineScope] provided by its [coroutineContext].
 * Closing this socket should cancel its [CoroutineScope] and any ongoing operations like the [incomingPackets] flow.
 */
interface BoundUdpSocket : CoroutineScope { // CoroutineScope for managing the lifecycle of the socket's operations
    /**
     * The local [UdpSocketService.SocketAddress] this socket is bound to.
     * If an ephemeral port was requested (port 0 in bind), this will reflect the actual port chosen by the OS.
     */
    val localAddress: UdpSocketService.SocketAddress

    /**
     * Sends UDP packet data to the specified target address.
     * This is a suspending function and will perform network I/O.
     *
     * @param data The [ByteArray] payload to send.
     * @param targetAddress The destination [UdpSocketService.SocketAddress].
     * @throws Exception if sending fails (e.g., network error, socket closed).
     */
    suspend fun send(data: ByteArray, targetAddress: UdpSocketService.SocketAddress)

    /**
     * A [Flow] of incoming [UdpSocketService.UdpPacket]s.
     * The flow will complete when the socket is closed or an irrecoverable error occurs.
     * Each emitted packet contains the data, sender's address, and potentially the local address it was received on.
     * Implementations should ensure this flow is cancellable and tied to the socket's lifecycle.
     */
    fun incomingPackets(): Flow<UdpSocketService.UdpPacket>

    /**
     * Closes the bound socket.
     * This should be idempotent. It will stop any ongoing send/receive operations,
     * complete the [incomingPackets] flow, and release underlying system resources.
     * It should also cancel the [CoroutineScope] associated with this socket.
     */
    fun close()

    /**
     * Checks if the socket is closed.
     * @return True if [close] has been called and resources are released (or being released).
     */
    fun isClosed(): Boolean
}
