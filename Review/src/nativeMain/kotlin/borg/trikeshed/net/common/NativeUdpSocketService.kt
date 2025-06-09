package borg.trikeshed.net.common

import kotlinx.cinterop.*
import platform.posix.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.CoroutineContext

const val UDP_PACKET_MAX_SIZE_NATIVE = 65507

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.Experimental কথাটাનો वापर வேண்டாம்।)
class NativeUdpSocketService : UdpSocketService {

    override suspend fun bind(
        localAddress: UdpSocketService.SocketAddress,
        coroutineContext: CoroutineContext
    ): Result<BoundUdpSocket> {
        val socketFd = socket(AF_INET, SOCK_DGRAM, 0)
        if (socketFd < 0) {
            return Result.failure(RuntimeException("Failed to create socket: ${strerror(errno)?.toKString()}"))
        }

        // Enable SO_REUSEADDR
        memScoped {
            val optVal = alloc<IntVar>()
            optVal.value = 1
            if (setsockopt(socketFd, SOL_SOCKET, SO_REUSEADDR, optVal.ptr, sizeOf<IntVar>().toUInt()) < 0) {
                 // Non-fatal if it fails, but log it
                println("NativeUdpSocketService: Failed to set SO_REUSEADDR: ${strerror(errno)?.toKString()}")
            }
        }


        val nativeAddr = UdpSocketServiceUtilsNative.socketAddressToNative(localAddress)
            ?: return Result.failure(IllegalArgumentException("Invalid local address format or failed getaddrinfo for host: ${localAddress.host}"))

        var boundSocketAddress: UdpSocketService.SocketAddress? = null

        nativeAddr.usePinned { pinnedNativeAddr ->
            if (bind(socketFd, pinnedNativeAddr.get().ptr.reinterpret(), sizeOf<sockaddr_in>().toUInt()) < 0) {
                close(socketFd) // Close socket if bind fails
                return Result.failure(RuntimeException("Failed to bind socket to ${localAddress.host}:${localAddress.port}: ${strerror(errno)?.toKString()}"))
            }
            // If port was 0, get the ephemeral port assigned by the OS
            if (localAddress.port == 0 || localAddress.host == "0.0.0.0" || localAddress.host == "::") {
                 memScoped {
                    val actualSockAddr = alloc<sockaddr_in>()
                    val len = alloc<socklen_tVar>()
                    len.value = sizeOf<sockaddr_in>().toUInt()
                    if (getsockname(socketFd, actualSockAddr.ptr.reinterpret(), len.ptr) == 0) {
                        boundSocketAddress = UdpSocketServiceUtilsNative.nativeToSocketAddress(actualSockAddr.ptr)
                    } else {
                        println("NativeUdpSocketService: Warning - failed to get socket name after bind: ${strerror(errno)?.toKString()}")
                        boundSocketAddress = localAddress // Fallback, port might be 0
                    }
                }
            } else {
                boundSocketAddress = localAddress
            }
        }

        val finalBoundAddress = boundSocketAddress ?: localAddress // Should always be set if bind succeeded

        val socketJob = Job(coroutineContext[Job])
        val socketScope = CoroutineScope(coroutineContext + socketJob + Dispatchers.Default) // Using Default for native, can be custom IO

        return Result.success(NativeBoundUdpSocket(socketFd, finalBoundAddress, socketScope, socketJob))
    }
}

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.Experimental কথাটাનો वापर வேண்டாம்।)
internal class NativeBoundUdpSocket(
    private val socketFd: Int,
    override val localAddress: UdpSocketService.SocketAddress,
    private val scope: CoroutineScope,
    private val job: Job
) : BoundUdpSocket, CoroutineScope by scope {

    private val closed = kotlinx.atomicfu.atomic(false)

    override suspend fun send(data: ByteArray, targetAddress: UdpSocketService.SocketAddress) {
        if (isClosed()) throw SocketException("Socket is closed $localAddress")

        withContext(Dispatchers.Default) { // Use Default or a dedicated IO dispatcher for Native
            val nativeTargetAddr = UdpSocketServiceUtilsNative.socketAddressToNative(targetAddress)
                ?: throw IllegalArgumentException("Invalid target address: ${targetAddress.host}")

            nativeTargetAddr.usePinned { pinnedTargetAddr ->
                data.usePinned { pinnedData ->
                    val bytesSent = sendto(
                        socketFd,
                        pinnedData.addressOf(0),
                        data.size.convert(),
                        0,
                        pinnedTargetAddr.get().ptr.reinterpret(),
                        sizeOf<sockaddr_in>().toUInt()
                    )
                    if (bytesSent < 0) {
                        if (!isClosed()) { // Don't throw if error is due to closing
                             throw SocketException("Failed to send UDP packet from $localAddress to $targetAddress: ${strerror(errno)?.toKString()}")
                        } else {
                            println("NativeBoundUdpSocket: Send failed on closed socket $localAddress to $targetAddress (ignored)")
                        }
                    } else if (bytesSent != data.size.toLong()) {
                        // Partial send, not typical for UDP unless buffer too large and truncated by OS, but check anyway
                        println("Warning: Partial UDP send. Expected ${data.size}, sent $bytesSent from $localAddress to $targetAddress")
                    }
                }
            }
        }
    }

    override fun incomingPackets(): Flow<UdpSocketService.UdpPacket> = channelFlow<UdpSocketService.UdpPacket> {
        val buffer = ByteArray(UDP_PACKET_MAX_SIZE_NATIVE)

        memScoped { // Scope for native memory allocation
            val senderSockAddr = alloc<sockaddr_in>()
            val senderSockAddrLen = alloc<socklen_tVar>()

            while (isActive && !isClosed()) {
                senderSockAddrLen.value = sizeOf<sockaddr_in>().toUInt()
                val bytesRead = buffer.usePinned { pinnedBuffer ->
                    recvfrom(
                        socketFd,
                        pinnedBuffer.addressOf(0),
                        buffer.size.convert(),
                        0, // flags
                        senderSockAddr.ptr.reinterpret(),
                        senderSockAddrLen.ptr
                    )
                }

                if (bytesRead < 0) {
                    if (errno == EAGAIN || errno == EWOULDBLOCK) {
                        // This shouldn't happen with blocking sockets by default.
                        // If socket becomes non-blocking, this is normal. For now, treat as error if unexpected.
                        println("NativeBoundUdpSocket: recvfrom returned EAGAIN/EWOULDBLOCK on blocking socket for $localAddress. Errno: $errno")
                        delay(10) // Small delay before retrying to avoid busy loop on unexpected non-blocking behavior
                        continue
                    }
                    if (!isClosed()) { // Don't propagate error if socket was intentionally closed
                        val error = SocketException("UDP receive failed for $localAddress: ${strerror(errno)?.toKString()}")
                        println("NativeBoundUdpSocket: Closing channel due to recvfrom error: ${error.message}")
                        close(error) // Close the channel with error
                    }
                    break // Exit loop on error
                }
                if (bytesRead == 0L) {
                    // EOF, not typical for UDP connected sockets, but for raw UDP it means an empty datagram (which is possible).
                    // Or could indicate a closed connection if it were connection-oriented.
                    // For datagrams, an empty packet is valid.
                     println("NativeBoundUdpSocket: Received empty datagram on $localAddress.")
                    // Continue to process it as an empty packet if desired, or filter.
                    // For now, let's assume we want to emit it.
                }

                val receivedData = buffer.copyOfRange(0, bytesRead.toInt())
                val senderAddress = UdpSocketServiceUtilsNative.nativeToSocketAddress(senderSockAddr.ptr)

                // Get local address this packet was received on (useful for wildcard binds)
                val currentLocalAddr = memScoped {
                    val actualLocalSockAddr = alloc<sockaddr_in>()
                    val len = alloc<socklen_tVar>()
                    len.value = sizeOf<sockaddr_in>().toUInt()
                    if (getsockname(socketFd, actualLocalSockAddr.ptr.reinterpret(), len.ptr) == 0) {
                        UdpSocketServiceUtilsNative.nativeToSocketAddress(actualLocalSockAddr.ptr)
                    } else {
                        localAddress // Fallback to the initially known localAddress
                    }
                }

                try {
                    send(UdpSocketService.UdpPacket(receivedData, senderAddress, bytesRead.toInt(), currentLocalAddr))
                } catch (e: Exception) { // Catch if channel is closed while trying to send
                    println("NativeBoundUdpSocket: Failed to send packet to channel for $localAddress: ${e.message}")
                    break // Exit loop
                }
            }
        }
        println("NativeBoundUdpSocket: Incoming packets flow ended for $localAddress.")
    }.flowOn(Dispatchers.Default) // Use Default or a dedicated IO dispatcher for Native blocking calls


    override fun close() {
        if (closed.compareAndSet(expect = false, update = true)) {
            println("NativeBoundUdpSocket: Closing socket fd $socketFd for $localAddress")
            job.cancel() // Cancel the scope's job
            // Actual socket close should happen on a non-application thread if it blocks.
            // For POSIX close(), it's generally quick but can theoretically block.
            // Dispatching to an IO context for the actual close call.
            GlobalScope.launch(Dispatchers.Default) { // Or a specific IO dispatcher
                shutdown(socketFd, SHUT_RDWR) // Gracefully shutdown read/write to unblock recvfrom
                val closeResult = close(socketFd)
                if (closeResult < 0) {
                    println("NativeBoundUdpSocket: Error closing socket fd $socketFd for $localAddress: ${strerror(errno)?.toKString()}")
                } else {
                     println("NativeBoundUdpSocket: Successfully closed socket fd $socketFd for $localAddress")
                }
            }
        }
    }

    override fun isClosed(): Boolean {
        return closed.value || job.isCompleted
    }
}

/** Helper object for Native address conversions. */
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.Experimental কথাটাનો वापर வேண்டாம்।)
internal object UdpSocketServiceUtilsNative {
    fun socketAddressToNative(socketAddress: UdpSocketService.SocketAddress): CValue<sockaddr_in>? {
        return memScoped {
            val addrInfo = allocPointerTo<addrinfo>()
            val hints = alloc<addrinfo>()
            hints.ai_family = AF_INET
            hints.ai_socktype = SOCK_DGRAM
            hints.ai_flags = AI_PASSIVE or AI_NUMERICSERV // AI_PASSIVE for bind, AI_NUMERICSERV for port as number

            // getaddrinfo can resolve hostnames or use numeric IPs
            val result = getaddrinfo(socketAddress.host, socketAddress.port.toString(), hints.ptr, addrInfo)
            if (result != 0) {
                println("getaddrinfo failed for ${socketAddress.host}: ${gai_strerror(result)?.toKString()}")
                return@memScoped null
            }

            val resolvedAddrInfo = addrInfo.pointed ?: return@memScoped null
            val sockaddrPtr = resolvedAddrInfo.ai_addr?.reinterpret<sockaddr_in>() ?: return@memScoped null

            val nativeAddr = alloc<sockaddr_in>()
            nativeAddr.sin_family = sockaddrPtr.pointed.sin_family
            nativeAddr.sin_port = sockaddrPtr.pointed.sin_port
            nativeAddr.sin_addr.s_addr = sockaddrPtr.pointed.sin_addr.s_addr

            freeaddrinfo(addrInfo.value)
            return@memScoped nativeAddr.readValue()
        }
    }

    fun nativeToSocketAddress(sockaddrPtr: CPointer<sockaddr_in>?): UdpSocketService.SocketAddress {
        if (sockaddrPtr == null) return UdpSocketService.SocketAddress("0.0.0.0", 0) // Or throw

        val address = sockaddrPtr.pointed
        val hostBytes = ByteArray(INET_ADDRSTRLEN) { 0 }
        val host = inet_ntop(AF_INET, address.sin_addr.ptr, hostBytes.refTo(0), INET_ADDRSTRLEN.convert())
            ?.toKString() ?: "unknown_host"

        val port = memGlobal // ntohs is for network to host short, sin_port is already network byte order u_short
            .getUShortAt(address.sin_port.rawValue.toInt()).toShort()
            .toUShort() // Read as UShort directly from memory
            .let { java.lang.Short.reverseBytes(it.toShort()).toUShort().toInt() } // Correctly reverse bytes for port

        return UdpSocketService.SocketAddress(host, port)
    }
}

// Basic SocketException for Native, similar to JVM's SocketException
class SocketException(message: String) : Exception(message)
