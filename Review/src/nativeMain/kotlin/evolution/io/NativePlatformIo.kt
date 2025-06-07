package evolution.io

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*

// These epoll and eventfd functions MUST be provided by a C interop library
@OptIn(ExperimentalForeignApi::class)
expect fun epoll_create1(flags: Int): Int
@OptIn(ExperimentalForeignApi::class)
expect fun epoll_ctl(epfd: Int, op: Int, fd: Int, event: CValuesRef<epoll_event>?): Int
@OptIn(ExperimentalForeignApi::class)
expect fun epoll_wait(epfd: Int, events: CValuesRef<epoll_event>?, maxevents: Int, timeout: Int): Int
@OptIn(ExperimentalForeignApi::class)
expect fun eventfd(initval: UInt, flags: Int): Int

// Epoll constants
const val EPOLL_CTL_ADD = 1
const val EPOLL_CTL_DEL = 2
const val EPOLL_CTL_MOD = 3
const val EPOLLIN = 0x001
const val EPOLLOUT = 0x004
const val EPOLLERR = 0x008
const val EPOLLHUP = 0x010

// eventfd constants
const val EFD_NONBLOCK = platform.posix.O_NONBLOCK

// Renamed constant to avoid conflict if another INET_ADDRSTRLEN is globally available
internal const val INET_ADDRSTRLEN_NATIVE = 16 // For IPv4 string representation


@OptIn(ExperimentalForeignApi::class)
actual class SocketAddress actual constructor(actual val host: String, actual val port: Int) {
    actual fun getHostName(): String = host
    actual fun getPort(): Int = port

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SocketAddress) return false
        return host == other.host && port == other.port
    }

    override fun hashCode(): Int = 31 * host.hashCode() + port
    override fun toString(): String = "$host:$port"
}

@OptIn(ExperimentalForeignApi::class)
actual class NativeUdpChannel internal constructor(
    internal val fd: Int,
    private val ioService: NativePlatformIoService
) : PlatformUdpChannel {

    actual override val key: CoroutineContext.Key<*> get() = PlatformUdpChannelKey
    private var nativeKeyRepresentation: Any? = fd
    private var isBlocking: Boolean = true
    private var boundAddress: SocketAddress? = null

    init {
        try {
            configureBlocking(false)
        } catch (e: Exception) {
            platform.posix.close(fd)
            throw RuntimeException("Failed to set channel to non-blocking on init for fd $fd", e)
        }
    }

    actual override suspend fun bind(localAddress: SocketAddress): Boolean {
        return withContext(Dispatchers.Default) {
            memScoped {
                val addr = alloc<platform.posix.sockaddr_in>()
                platform.posix.memset(addr.ptr, 0, sizeOf<platform.posix.sockaddr_in>().convert())
                addr.sin_family = platform.posix.AF_INET.convert()
                addr.sin_port = evolution.io.posix_htons(localAddress.getPort().toShort())
                if (localAddress.getHostName() == "0.0.0.0" || localAddress.getHostName() == "::") { // Wildcard address
                    addr.sin_addr.s_addr = platform.posix.htonl(INADDR_ANY) // Use INADDR_ANY for wildcard
                } else if (platform.posix.inet_pton(platform.posix.AF_INET, localAddress.getHostName(), addr.sin_addr.ptr) != 1) {
                    throw Exception("inet_pton failed for bind host ${localAddress.getHostName()}: ${platform.posix.strerror(platform.posix.errno)?.toKString()}")
                }
                if (platform.posix.bind(fd, addr.ptr.reinterpret(), sizeOf<platform.posix.sockaddr_in>().convert()) != 0) {
                    // Optionally log: platform.posix.strerror(platform.posix.errno)?.toKString()
                    false
                } else {
                    val actualPort = if (localAddress.getPort() == 0) { getActualBoundPort() } else { localAddress.getPort() }
                    // For host, if bound to INADDR_ANY, getLocalAddress will resolve it later if needed.
                    this.boundAddress = SocketAddress(localAddress.getHostName(), actualPort) // Store originally requested host for wildcard case
                    true
                }
            }
        }
    }

    private fun getActualBoundPort(): Int = memScoped {
        val currentAddr = alloc<platform.posix.sockaddr_in>()
        val len = alloc<platform.posix.socklen_tVar>()
        len.value = sizeOf<platform.posix.sockaddr_in>().convert()
        if (platform.posix.getsockname(fd, currentAddr.ptr.reinterpret(), len.ptr) == 0) { evolution.io.posix_ntohs(currentAddr.sin_port).toInt() } else { -1 }
    }

    actual override fun getLocalAddress(): SocketAddress? {
         if (boundAddress != null && boundAddress!!.getPort() != 0) return boundAddress // Return cached if port is known
         // If port was ephemeral (0), or not bound explicitly, try getsockname
         memScoped {
             val currentAddr = alloc<platform.posix.sockaddr_in>()
             val len = alloc<platform.posix.socklen_tVar>()
             len.value = sizeOf<platform.posix.sockaddr_in>().convert()
             if (platform.posix.getsockname(fd, currentAddr.ptr.reinterpret(), len.ptr) == 0) {
                 val hostChars = ByteArray(INET_ADDRSTRLEN_NATIVE)
                 if (platform.posix.inet_ntop(platform.posix.AF_INET, currentAddr.sin_addr.ptr, hostChars.refTo(0), INET_ADDRSTRLEN_NATIVE.convert()) == null) {
                     null // inet_ntop failed
                 } else {
                     val host = hostChars.toKString().substringBefore('\u0000')
                     val port = evolution.io.posix_ntohs(currentAddr.sin_port).toInt()
                     SocketAddress(host, port).also { this.boundAddress = it }
                 }
             } else { null }
         }
    }

    actual override suspend fun register(interest: InterestOp, attachment: Any?): Any? {
        return ioService.registerChannel(this, interest, attachment)
    }

    actual override suspend fun send(data: ByteArray, targetAddress: SocketAddress): Int {
        return withContext(Dispatchers.Default) {
            memScoped {
                val socketAddrIn = alloc<sockaddr_in>()
                platform.posix.memset(socketAddrIn.ptr, 0, sizeOf<sockaddr_in>().convert())
                socketAddrIn.sin_family = AF_INET.convert()
                socketAddrIn.sin_port = posix_htons(targetAddress.getPort().toShort())
                if (platform.posix.inet_pton(AF_INET, targetAddress.getHostName(), socketAddrIn.sin_addr.ptr) != 1) {
                    throw Exception("inet_pton failed for ${targetAddress.getHostName()}: ${strerror(errno)?.toKString()}")
                }

                val bytesSent = platform.posix.sendto(
                    fd,
                    data.refTo(0),
                    data.size.convert(),
                    0,
                    socketAddrIn.ptr.reinterpret(),
                    sizeOf<sockaddr_in>().convert()
                )
                if (bytesSent < 0) {
                    val error = errno
                    if (error == EAGAIN || error == EWOULDBLOCK) return@withContext 0
                    throw Exception("sendto failed for fd $fd: ${strerror(error)?.toKString()} (errno $error)")
                }
                bytesSent.toInt()
            }
        }
    }

    actual override suspend fun receive(buffer: ByteArray): Pair<Int, SocketAddress?> {
        return withContext(Dispatchers.Default) {
            memScoped {
                val fromAddr = alloc<sockaddr_in>()
                val fromAddrLen = alloc<socklen_tVar>()
                fromAddrLen.value = sizeOf<sockaddr_in>().convert()

                val bytesRead = platform.posix.recvfrom(
                    fd,
                    buffer.refTo(0),
                    buffer.size.convert(),
                    0,
                    fromAddr.ptr.reinterpret(),
                    fromAddrLen.ptr
                )

                if (bytesRead < 0) {
                    val error = errno
                    if (error == EAGAIN || error == EWOULDBLOCK) return@withContext Pair(0, null)
                    throw Exception("recvfrom failed for fd $fd: ${strerror(error)?.toKString()} (errno $error)")
                }
                if (bytesRead == 0L) return@withContext Pair(0, null)

                val sourceHostChars = allocArray<ByteVar>(INET_ADDRSTRLEN_NATIVE)
                if (platform.posix.inet_ntop(AF_INET, fromAddr.sin_addr.ptr, sourceHostChars, INET_ADDRSTRLEN_NATIVE.convert()) == null) {
                    throw Exception("inet_ntop failed for fd $fd: ${strerror(errno)?.toKString()}")
                }

                val sourceHost = sourceHostChars.toKString().substringBefore('\u0000')
                val sourcePort = posix_ntohs(fromAddr.sin_port).toInt()

                Pair(bytesRead.toInt(), SocketAddress(sourceHost, sourcePort))
            }
        }
    }

    actual override fun configureBlocking(block: Boolean) {
        val flags = platform.posix.fcntl(fd, F_GETFL, 0)
        if (flags == -1) throw Exception("fcntl(F_GETFL) failed for fd $fd: ${strerror(errno)?.toKString()}")
        val newFlags = if (block) flags and O_NONBLOCK.inv() else flags or O_NONBLOCK
        if (platform.posix.fcntl(fd, F_SETFL, newFlags) == -1) {
            throw Exception("fcntl(F_SETFL) failed for fd $fd: ${strerror(errno)?.toKString()}")
        }
        isBlocking = block
    }

    actual override fun close() {
        ioService.unregisterChannel(this)
        platform.posix.close(fd)
    }

    actual override fun getNativeKey(): Any? = nativeKeyRepresentation
    internal fun setNativeKey(key: Any?) { nativeKeyRepresentation = key }
}

@OptIn(ExperimentalForeignApi::class)
actual class NativePlatformIoService actual constructor() : PlatformIoService {
    actual override val key: CoroutineContext.Key<*> get() = PlatformIoServiceKey

    private val epollFd: Int
    private val wakeupEventFd: Int
    private val activeChannels = mutableMapOf<Int, NativeUdpChannel>()

    init {
        epollFd = epoll_create1(0)
        if (epollFd == -1) throw Exception("epoll_create1 failed: ${strerror(errno)?.toKString()}")

        wakeupEventFd = eventfd(0u, EFD_NONBLOCK)
        if (wakeupEventFd == -1) {
            try { platform.posix.close(epollFd) } catch (e: Exception) { /* ignore */ }
            throw Exception("eventfd failed: ${strerror(errno)?.toKString()}")
        }
        memScoped {
            val event = alloc<epoll_event>()
            event.events = EPOLLIN.convert()
            event.data.fd = wakeupEventFd
            if (epoll_ctl(epollFd, EPOLL_CTL_ADD, wakeupEventFd, event.ptr) == -1) {
                try { platform.posix.close(wakeupEventFd) } catch (e: Exception) { /* ignore */ }
                try { platform.posix.close(epollFd) } catch (e: Exception) { /* ignore */ }
                throw Exception("epoll_ctl failed to add wakeupEventFd: ${strerror(errno)?.toKString()}")
            }
        }
    }

    internal suspend fun registerChannel(channel: NativeUdpChannel, interest: InterestOp, attachment: Any?): Any? {
       return withContext(Dispatchers.Default) {
            memScoped {
                val event = alloc<epoll_event>()
                event.events = when (interest) {
                    InterestOp.READ -> EPOLLIN
                    InterestOp.WRITE -> EPOLLOUT
                }.convert()
                event.data.fd = channel.fd

                val op = if (activeChannels.containsKey(channel.fd)) EPOLL_CTL_MOD else EPOLL_CTL_ADD

                if (epoll_ctl(epollFd, op, channel.fd, event.ptr) == -1) {
                    throw Exception("epoll_ctl(${if(op == EPOLL_CTL_ADD) "ADD" else "MOD"}) failed for fd ${channel.fd}: ${strerror(errno)?.toKString()}")
                }
                if (op == EPOLL_CTL_ADD) {
                    activeChannels[channel.fd] = channel
                }
                channel.setNativeKey(channel.fd)
                channel.fd
            }
        }
    }

    internal fun unregisterChannel(channel: NativeUdpChannel) {
        if (activeChannels.containsKey(channel.fd)) {
            epoll_ctl(epollFd, EPOLL_CTL_DEL, channel.fd, null)
            activeChannels.remove(channel.fd)
        }
    }

    actual override suspend fun createUdpChannel(): PlatformUdpChannel? {
        return withContext(Dispatchers.Default) {
            val socketFd = platform.posix.socket(AF_INET, SOCK_DGRAM, 0)
            if (socketFd == -1) {
                return@withContext null
            }

            memScoped {
               val localAddr = alloc<sockaddr_in>()
               platform.posix.memset(localAddr.ptr, 0, sizeOf<sockaddr_in>().convert())
               localAddr.sin_family = AF_INET.convert()
               localAddr.sin_port = posix_htons(0)
               localAddr.sin_addr.s_addr = platform.posix.htonl(INADDR_ANY)

               if (platform.posix.bind(socketFd, localAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == -1) {
                   platform.posix.close(socketFd)
                   throw Exception("bind failed for UDP channel (fd $socketFd): ${strerror(errno)?.toKString()}")
               }
            }
            NativeUdpChannel(socketFd, this@NativePlatformIoService)
        }
    }

    actual override suspend fun runSelectorLoop(handler: suspend (SelectionEvent) -> Unit) {
        withContext(Dispatchers.Default) {
            val eventsArray = nativeHeap.allocArray<epoll_event>(MAX_EVENTS)
            try {
                while (isActive && platform.posix.fcntl(epollFd, F_GETFD) != -1 ) {
                    val numEvents = epoll_wait(epollFd, eventsArray, MAX_EVENTS, -1)

                    if (!isActive) break

                    if (numEvents == -1) {
                        if (errno == EINTR) continue
                        break
                    }

                    for (i in 0 until numEvents) {
                        val currentEvent = eventsArray[i]
                        val eventFd = currentEvent.data.fd

                        if (eventFd == wakeupEventFd) {
                            memScoped {
                                val buf = allocArray<ByteVar>(8)
                                platform.posix.read(wakeupEventFd, buf, 8L)
                            }
                            if (!isActive) break
                            continue
                        }

                        val channel = activeChannels[eventFd] ?: continue
                        val attachment = null

                        if ((currentEvent.events.toInt() and EPOLLIN) != 0) {
                            handler(SelectionEvent(eventFd, InterestOp.READ, attachment))
                        }
                        if (activeChannels.containsKey(eventFd) && (currentEvent.events.toInt() and EPOLLOUT) != 0) {
                            handler(SelectionEvent(eventFd, InterestOp.WRITE, attachment))
                        }
                    }
                }
            } finally {
                nativeHeap.free(eventsArray)
            }
        }
    }

    actual override fun wakeupSelector() {
        if (wakeupEventFd != -1 && platform.posix.fcntl(wakeupEventFd, F_GETFD) != -1) {
            memScoped {
                val valPtr = alloc<ULongVar>()
                valPtr.value = 1uL
                if (platform.posix.write(wakeupEventFd, valPtr.ptr, sizeOf<ULongVar>().convert()) < 0) {
                    // log error
                }
            }
        }
    }

    // Renamed from close() to closeServiceInternal() and added actual override close()
    actual override fun close() { closeServiceInternal() }
    internal fun closeServiceInternal() {
        val tempWakeupFd = wakeupEventFd
        if (tempWakeupFd != -1) {
             memScoped {
                val valPtr = alloc<ULongVar>()
                valPtr.value = 1uL
                platform.posix.write(tempWakeupFd, valPtr.ptr, sizeOf<ULongVar>().convert())
            }
        }

        activeChannels.values.toList().forEach {
            try { it.close() } catch (e: Exception) { /* Log error */ }
        }
        activeChannels.clear()

        if (tempWakeupFd != -1) try { platform.posix.close(tempWakeupFd) } catch (e: Exception) { /* Log error */ }
        if (epollFd != -1) try { platform.posix.close(epollFd) } catch (e: Exception) { /* Log error */ }
    }

    companion object {
        private const val MAX_EVENTS = 10
        // private const val INET_ADDRSTRLEN = 16 // Now INET_ADDRSTRLEN_NATIVE
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun posix_htons(value: Short): UShort = when (BYTE_ORDER) {
    BIG_ENDIAN -> value.toUShort()
    LITTLE_ENDIAN -> value.toUShort().reverseBytes()
    else -> error("Unsupported byte order for htons: $BYTE_ORDER")
}
@OptIn(ExperimentalForeignApi::class)
internal fun posix_ntohs(value: UShort): UShort = when (BYTE_ORDER) {
    BIG_ENDIAN -> value
    LITTLE_ENDIAN -> value.reverseBytes()
    else -> error("Unsupported byte order for ntohs: $BYTE_ORDER")
}

val INADDR_ANY : UInt = 0u
// Removed Experimental जाईल::class opt-in as it was likely a typo
// Ensured INET_ADDRSTRLEN_NATIVE is used in getLocalAddress
// Added memset to NativeUdpChannel.bind
// Added bind to 0.0.0.0 (INADDR_ANY) in createUdpChannel for server-like behavior / receiving replies.
// Corrected NativeUdpChannel.init error handling slightly.
// Corrected NativePlatformIoService.init error handling for epoll/eventfd creation.
// Corrected registerChannel to only add to activeChannels on EPOLL_CTL_ADD.
// Corrected NativePlatformIoService.closeServiceInternal to be more robust.
// Corrected wakeupSelector to check fcntl before writing.
// Corrected runSelectorLoop to check fcntl before epoll_wait.
// Corrected send/receive error messages to include fd for better debugging.
// Corrected inet_pton call in NativeUdpChannel.bind.
// Ensured INET_ADDRSTRLEN_NATIVE is defined.
// Made INET_ADDRSTRLEN_NATIVE internal.
// Ensured `configureBlocking(false)` is called in NativeUdpChannel constructor.
// Corrected `posix_htons` call in `NativeUdpChannel.bind` to use `evolution.io.posix_htons`.
// Corrected `posix_ntohs` calls in `NativeUdpChannel` similarly.
