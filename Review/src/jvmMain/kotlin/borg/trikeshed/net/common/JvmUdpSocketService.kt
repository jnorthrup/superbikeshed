package borg.trikeshed.net.common

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException
import kotlin.coroutines.CoroutineContext

const val UDP_PACKET_MAX_SIZE_JVM = 65507 // Max UDP payload size

class JvmUdpSocketService : UdpSocketService {
    override suspend fun bind(
        localAddress: UdpSocketService.SocketAddress,
        coroutineContext: CoroutineContext
    ): Result<BoundUdpSocket> {
        return try {
            val socket = DatagramSocket(null) // Create unbound socket
            // Set SO_REUSEADDR before binding, useful for quick restarts
            // Not strictly necessary for ephemeral ports but good practice.
            try { socket.reuseAddress = true } catch (e: SocketException) { /* Some OS might not support this well */ }

            socket.bind(InetSocketAddress(localAddress.host, localAddress.port))

            // Create a new scope for the BoundUdpSocket, making the passed context its parent
            // This allows cancelling all socket operations by cancelling this new scope's job.
            val socketJob = Job(coroutineContext[Job]) // Inherit parent job for structured concurrency
            val socketScope = CoroutineScope(coroutineContext + socketJob + Dispatchers.IO) // Default to IO dispatcher

            Result.success(JvmBoundUdpSocket(socket, socketScope, socketJob))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

internal class JvmBoundUdpSocket(
    private val socket: DatagramSocket,
    private val scope: CoroutineScope, // CoroutineScope for this bound socket's operations
    private val job: Job // The job associated with this socket's scope
) : BoundUdpSocket, CoroutineScope by scope { // Delegate CoroutineScope implementation

    override val localAddress: UdpSocketService.SocketAddress by lazy {
        val addr = socket.localSocketAddress as InetSocketAddress
        UdpSocketService.SocketAddress(addr.address.hostAddress, addr.port)
    }

    override suspend fun send(data: ByteArray, targetAddress: UdpSocketService.SocketAddress) {
        if (isClosed()) throw SocketException("Socket is closed")
        withContext(Dispatchers.IO) { // Ensure network operation is on IO dispatcher
            try {
                val inetTargetAddress = InetSocketAddress(targetAddress.host, targetAddress.port)
                val packet = DatagramPacket(data, data.size, inetTargetAddress)
                socket.send(packet)
            } catch (e: Exception) {
                // Handle specific exceptions: SocketException, IOException, etc.
                // Re-throw or wrap in a custom exception if needed.
                if (!isClosed()) { // Don't throw if error is due to closing
                    throw e
                } else {
                    println("JvmBoundUdpSocket: Send failed on closed socket ${localAddress} to $targetAddress (ignored)")
                }
            }
        }
    }

    override fun incomingPackets(): Flow<UdpSocketService.UdpPacket> = channelFlow {
        val buffer = ByteArray(UDP_PACKET_MAX_SIZE_JVM)
        while (isActive && !socket.isClosed) { // isActive checks the scope of channelFlow
            try {
                val datagramPacket = DatagramPacket(buffer, buffer.size)
                socket.receive(datagramPacket) // Blocking call

                val receivedData = datagramPacket.data.copyOfRange(0, datagramPacket.length)
                val senderInetAddr = datagramPacket.socketAddress as InetSocketAddress
                val senderAddr = UdpSocketService.SocketAddress(
                    host = senderInetAddr.address.hostAddress,
                    port = senderInetAddr.port
                )
                // Determine local address on which packet was received (can be useful for multi-homed hosts)
                val localInetAddr = socket.localSocketAddress as? InetSocketAddress // Already bound, so should be available
                val currentLocalAddr = localInetAddr?.let { UdpSocketService.SocketAddress(it.address.hostAddress, it.port) }


                send(UdpSocketService.UdpPacket(receivedData, senderAddr, datagramPacket.length, currentLocalAddr))
            } catch (e: SocketException) {
                // SocketException is expected when socket.close() is called from another coroutine/thread
                if (!socket.isClosed) {
                    println("JvmBoundUdpSocket: SocketException in receive loop for ${localAddress}: ${e.message}")
                    close(e) // Close the channel with error if socket not intentionally closed
                } else {
                    // Socket was closed, flow should terminate gracefully
                    println("JvmBoundUdpSocket: Receive loop terminating for ${localAddress} due to socket closure.")
                }
                break // Exit loop
            } catch (e: Exception) {
                if (isActive) { // Only close if the channelFlow scope is still active
                    println("JvmBoundUdpSocket: Error in receive loop for ${localAddress}: ${e.message}")
                    close(e) // Close channel with error
                }
                break // Exit loop
            }
        }
    }.flowOn(Dispatchers.IO) // Ensure the blocking receive runs on IO dispatcher

    override fun close() {
        if (!job.isCompleted) { // Idempotency: only act if not already closing/closed
            println("JvmBoundUdpSocket: Closing socket ${localAddress}")
            job.cancel() // Cancel the scope's job, which cancels ongoing coroutines (like the flow's collector)
            try {
                if (!socket.isClosed) {
                    socket.close()
                }
            } catch (e: Exception) {
                // Log or handle socket close exception, though usually not critical if already cancelling
                println("JvmBoundUdpSocket: Exception while closing socket ${localAddress}: ${e.message}")
            }
        }
    }

    override fun isClosed(): Boolean {
        return socket.isClosed || job.isCompleted
    }
}
