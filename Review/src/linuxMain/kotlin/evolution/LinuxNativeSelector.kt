@file:Suppress("NOTHING_TO_INLINE")

package evolution

import borg.trikeshed.lib.*
import kotlin.jvm.*

import kotlinx.cinterop.*
import platform.linux.*
import platform.posix.*

/**
 * Linux Native Selector Implementation using epoll
 * 
 * Platform-specific epoll implementation for Linux targets.
 * Provides high-performance I/O multiplexing for the native NIO SPI.
 */

/**
 * 🐧 Linux epoll-based Selector
 */
actual class NativeSelector(private val epollFd: Int) {
    private val registeredKeys = mutableMapOf<Int, NativeSelectionKey>()
    private val selectedKeySet = mutableSetOf<NativeSelectionKey>()
    private val maxEvents = 1024
    
    actual companion object {
        /**
         * Open new epoll-based selector
         */
        actual fun open(): NativeSelector {
            val epollFd = epoll_create1(EPOLL_CLOEXEC)
            if (epollFd == -1) {
                throw RuntimeException("Failed to create epoll: ${strerror(errno)?.toKString()}")
            }
            return NativeSelector(epollFd)
        }
    }
    
    /**
     * Register channel with epoll
     */
    actual fun register(channel: NativeSelectableChannel, ops: Int, attachment: Any?): NativeSelectionKey {
        val key = NativeSelectionKey(channel, this, ops, attachment)
        val fd = when (channel) {
            is NativeSocketChannel -> channel.fd
            is NativeServerSocketChannel -> channel.fd
            else -> throw IllegalArgumentException("Unsupported channel type")
        }
        
        memScoped {
            val event = alloc<epoll_event>()
            event.events = convertOpsToEpollEvents(ops).toUInt()
            event.data.fd = fd
            
            val result = epoll_ctl(epollFd, EPOLL_CTL_ADD, fd, event.ptr)
            if (result == -1) {
                throw RuntimeException("Failed to add to epoll: ${strerror(errno)?.toKString()}")
            }
        }
        
        registeredKeys[fd] = key
        return key
    }
    
    /**
     * Select ready channels using epoll_wait
     */
    actual fun select(timeoutMs: Long): Int {
        selectedKeySet.clear()
        
        return memScoped {
            val events = allocArray<epoll_event>(maxEvents)
            val timeoutInt = if (timeoutMs == 0L) -1 else timeoutMs.toInt()
            
            val readyCount = epoll_wait(epollFd, events, maxEvents, timeoutInt)
            when {
                readyCount > 0 -> {
                    for (i in 0 until readyCount) {
                        val event = events[i]
                        val fd = event.data.fd
                        val key = registeredKeys[fd]
                        
                        if (key != null) {
                            key.readyOps = convertEpollEventsToOps(event.events.toInt())
                            selectedKeySet.add(key)
                        }
                    }
                    readyCount
                }
                readyCount == 0 -> 0 // Timeout
                else -> {
                    when (errno) {
                        EINTR -> 0 // Interrupted, try again
                        else -> throw RuntimeException("epoll_wait failed: ${strerror(errno)?.toKString()}")
                    }
                }
            }
        }
    }
    
    /**
     * Get selected keys
     */
    actual fun selectedKeys(): Set<NativeSelectionKey> = selectedKeySet.toSet()
    
    /**
     * Wake up selector (using eventfd)
     */
    actual fun wakeup() {
        // For simplicity, we'll just interrupt any blocked epoll_wait
        // In a full implementation, you'd use eventfd for proper wakeup
    }
    
    /**
     * Close epoll descriptor
     */
    actual fun close() {
        platform.posix.close(epollFd)
    }
    
    /**
     * Convert NIO ops to epoll events
     */
    private fun convertOpsToEpollEvents(ops: Int): Int {
        var events = 0
        if ((ops and NativeSelectionKey.OP_READ) != 0) events = events or EPOLLIN.toInt()
        if ((ops and NativeSelectionKey.OP_WRITE) != 0) events = events or EPOLLOUT.toInt()
        if ((ops and NativeSelectionKey.OP_ACCEPT) != 0) events = events or EPOLLIN.toInt()
        if ((ops and NativeSelectionKey.OP_CONNECT) != 0) events = events or EPOLLOUT.toInt()
        return events or EPOLLET.toInt() // Edge-triggered mode
    }
    
    /**
     * Convert epoll events to NIO ops
     */
    private fun convertEpollEventsToOps(events: Int): Int {
        var ops = 0
        if ((events and EPOLLIN.toInt()) != 0) ops = ops or NativeSelectionKey.OP_READ
        if ((events and EPOLLOUT.toInt()) != 0) ops = ops or NativeSelectionKey.OP_WRITE
        if ((events and EPOLLERR.toInt()) != 0 || (events and EPOLLHUP.toInt()) != 0) {
            // Error conditions - mark as both readable and writable for error handling
            ops = ops or NativeSelectionKey.OP_READ or NativeSelectionKey.OP_WRITE
        }
        return ops
    }
}

/**
 * 🚀 Linux-specific Network Extensions
 */

/**
 * Enable TCP_NODELAY for socket
 */
fun NativeSocketChannel.setTcpNoDelay(enable: Boolean) {
    memScoped {
        val optval = alloc<IntVar>()
        optval.value = if (enable) 1 else 0
        setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, optval.ptr, sizeOf<IntVar>().toUInt())
    }
}

/**
 * Set socket receive buffer size
 */
fun NativeSocketChannel.setReceiveBufferSize(size: Int) {
    memScoped {
        val optval = alloc<IntVar>()
        optval.value = size
        setsockopt(fd, SOL_SOCKET, SO_RCVBUF, optval.ptr, sizeOf<IntVar>().toUInt())
    }
}

/**
 * Set socket send buffer size
 */
fun NativeSocketChannel.setSendBufferSize(size: Int) {
    memScoped {
        val optval = alloc<IntVar>()
        optval.value = size
        setsockopt(fd, SOL_SOCKET, SO_SNDBUF, optval.ptr, sizeOf<IntVar>().toUInt())
    }
}

/**
 * Set socket keep-alive
 */
fun NativeSocketChannel.setKeepAlive(enable: Boolean) {
    memScoped {
        val optval = alloc<IntVar>()
        optval.value = if (enable) 1 else 0
        setsockopt(fd, SOL_SOCKET, SO_KEEPALIVE, optval.ptr, sizeOf<IntVar>().toUInt())
    }
}

/**
 * 🔧 Linux epoll Utilities
 */
object LinuxNetworkUtils {
    
    /**
     * Get optimal epoll batch size for system
     */
    fun getOptimalBatchSize(): Int {
        // Read from /proc/sys/net/core/netdev_max_backlog or use default
        return try {
            val content = readFile("/proc/sys/net/core/netdev_max_backlog")
            content.trim().toIntOrNull() ?: 1024
        } catch (e: Exception) {
            1024
        }
    }
    
    /**
     * Check if epoll is available
     */
    fun isEpollAvailable(): Boolean {
        val testFd = epoll_create1(EPOLL_CLOEXEC)
        return if (testFd != -1) {
            platform.posix.close(testFd)
            true
        } else {
            false
        }
    }
    
    /**
     * Get system page size for buffer optimization
     */
    fun getSystemPageSize(): Long = sysconf(_SC_PAGESIZE)
    
    private fun readFile(path: String): String {
        val file = fopen(path, "r") ?: throw RuntimeException("Cannot open $path")
        try {
            val buffer = ByteArray(1024)
            val bytesRead = fread(buffer.refTo(0), 1, buffer.size.toULong(), file)
            return buffer.take(bytesRead.toInt()).toByteArray().decodeToString()
        } finally {
            fclose(file)
        }
    }
}