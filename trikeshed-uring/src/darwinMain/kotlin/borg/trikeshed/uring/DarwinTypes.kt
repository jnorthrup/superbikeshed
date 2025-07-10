@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
package borg.trikeshed.uring

import kotlinx.cinterop.*
import platform.posix.*
import platform.darwin.*
import platform.Foundation.*

/**
 * Darwin implementation of ByteBuffer
 * Uses NSMutableData for backing storage
 */
actual class ByteBuffer private constructor(
    private val data: NSMutableData,
    private var _position: Int = 0,
    private var _limit: Int = -1
) {
    actual val capacity: Int get() = data.length.toInt()
    actual val position: Int get() = _position
    actual val remaining: Int get() = limit - _position
    
    private val limit: Int get() = if (_limit == -1) capacity else _limit
    
    actual fun get(): Byte {
        if (_position >= limit) throw BufferUnderflowException()
        val byte = data.bytes!!.reinterpret<ByteVar>()[_position]
        _position++
        return byte
    }
    
    actual fun get(dst: ByteArray, offset: Int, length: Int): ByteBuffer {
        if (remaining < length) throw BufferUnderflowException()
        memcpy(dst.refTo(offset), data.bytes!!.plus(_position), length.convert())
        _position += length
        return this
    }
    
    actual fun put(b: Byte): ByteBuffer {
        if (_position >= limit) throw BufferOverflowException()
        data.mutableBytes!!.reinterpret<ByteVar>()[_position] = b
        _position++
        return this
    }
    
    actual fun put(src: ByteArray, offset: Int, length: Int): ByteBuffer {
        if (remaining < length) throw BufferOverflowException()
        memcpy(data.mutableBytes!!.plus(_position), src.refTo(offset), length.convert())
        _position += length
        return this
    }
    
    actual fun flip(): ByteBuffer {
        _limit = _position
        _position = 0
        return this
    }
    
    actual fun clear(): ByteBuffer {
        _position = 0
        _limit = -1
        return this
    }
    
    actual fun rewind(): ByteBuffer {
        _position = 0
        return this
    }
    
    // Platform-specific extensions
    fun toNSData(): NSData = data.copy() as NSData
    
    fun getBytes(): CPointer<ByteVar>? = data.mutableBytes?.reinterpret()
    
    actual companion object {
        actual fun allocate(capacity: Int): ByteBuffer {
            val data = NSMutableData.dataWithLength(capacity.convert())
                ?: throw OutOfMemoryError("Failed to allocate buffer")
            return ByteBuffer(data)
        }
        
        actual fun allocateDirect(capacity: Int): ByteBuffer {
            // On Darwin, all buffers are "direct" in the sense they're native memory
            return allocate(capacity)
        }
        
        actual fun wrap(array: ByteArray): ByteBuffer {
            val data = NSMutableData.dataWithBytes(array.refTo(0), array.size.convert())
                ?: throw OutOfMemoryError("Failed to wrap array")
            return ByteBuffer(data)
        }
        
        // Darwin-specific factory
        fun fromNSData(nsData: NSData): ByteBuffer {
            val mutableData = NSMutableData.dataWithData(nsData)
                ?: throw OutOfMemoryError("Failed to copy NSData")
            return ByteBuffer(mutableData)
        }
    }
}

/**
 * Darwin implementation of SocketAddress
 */
actual class SocketAddress(
    actual val host: String,
    actual val port: Int
) {
    // Convert to Darwin sockaddr
    fun toSockaddr(): CValuesRef<sockaddr> = memScoped {
        val addr = alloc<sockaddr_in>()
        addr.sin_family = AF_INET.convert()
        addr.sin_port = htons(port.toUShort())
        
        // Convert host to IP
        inet_aton(host, addr.sin_addr.ptr)
        
        addr.ptr.reinterpret()
    }
    
    fun toSockaddrIn6(): CValuesRef<sockaddr_in6> = memScoped {
        val addr = alloc<sockaddr_in6>()
        addr.sin6_family = AF_INET6.convert()
        addr.sin6_port = htons(port.toUShort())
        
        // Convert host to IPv6
        inet_pton(AF_INET6, host, addr.sin6_addr.ptr)
        
        addr.ptr.reinterpret()
    }
    
    companion object {
        fun fromSockaddr(addr: CPointer<sockaddr>): SocketAddress = memScoped {
            when (addr.pointed.sa_family.toInt()) {
                AF_INET -> {
                    val sin = addr.reinterpret<sockaddr_in>()
                    val host = inet_ntoa(sin.pointed.sin_addr)?.toKString() ?: "0.0.0.0"
                    val port = ntohs(sin.pointed.sin_port).toInt()
                    SocketAddress(host, port)
                }
                AF_INET6 -> {
                    val sin6 = addr.reinterpret<sockaddr_in6>()
                    val buffer = ByteArray(INET6_ADDRSTRLEN)
                    val host = inet_ntop(AF_INET6, sin6.pointed.sin6_addr.ptr, 
                                       buffer.refTo(0), INET6_ADDRSTRLEN.convert())
                                       ?.toKString() ?: "::"
                    val port = ntohs(sin6.pointed.sin6_port).toInt()
                    SocketAddress(host, port)
                }
                else -> SocketAddress("unknown", 0)
            }
        }
    }
}

/**
 * Darwin implementation of atomic counter
 */
actual class atomic<T>(private var value: T) {
    private val lock = NSLock()
    
    actual fun get(): T {
        lock.lock()
        try {
            return value
        } finally {
            lock.unlock()
        }
    }
    
    actual fun set(value: T) {
        lock.lock()
        try {
            this.value = value
        } finally {
            lock.unlock()
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    actual fun incrementAndGet(): T {
        lock.lock()
        try {
            return when (value) {
                is Long -> {
                    value = (value as Long + 1) as T
                    value
                }
                is Int -> {
                    value = (value as Int + 1) as T
                    value
                }
                else -> throw UnsupportedOperationException("Cannot increment ${value!!::class}")
            }
        } finally {
            lock.unlock()
        }
    }
}

/**
 * Buffer exceptions
 */
class BufferUnderflowException : Exception("Buffer underflow")
class BufferOverflowException : Exception("Buffer overflow")

/**
 * Darwin-specific constants
 */
const val INET6_ADDRSTRLEN = 46
const val ENOTSUP = 45  // Operation not supported

/**
 * GCD (Grand Central Dispatch) integration for async operations
 */
object GCDDispatcher {
    private val mainQueue = dispatch_get_main_queue()
    private val globalQueue = dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.convert(), 0u)
    
    fun submitToMain(block: () -> Unit) {
        dispatch_async(mainQueue) {
            block()
        }
    }
    
    fun submitToBackground(block: () -> Unit) {
        dispatch_async(globalQueue) {
            block()
        }
    }
    
    fun submitAfter(delayMs: Long, block: () -> Unit) {
        val when_ = dispatch_time(DISPATCH_TIME_NOW, delayMs * 1_000_000)
        dispatch_after(when_, globalQueue) {
            block()
        }
    }
}

/**
 * Darwin-specific file operations with DTrace probes
 */
object DarwinFileOps {
    fun openWithFlags(path: String, flags: Int, mode: Int = 0o644): Int {
        // Could add DTrace probe here
        return open(path, flags, mode)
    }
    
    fun pread(fd: Int, buffer: ByteBuffer, offset: Long): Long = memScoped {
        val result = pread(fd, buffer.getBytes(), buffer.remaining.convert(), offset)
        if (result > 0) {
            // Advance buffer position
            repeat(result.toInt()) { buffer.get() }
        }
        result
    }
    
    fun pwrite(fd: Int, buffer: ByteBuffer, offset: Long): Long = memScoped {
        val result = pwrite(fd, buffer.getBytes()?.plus(buffer.position), 
                          buffer.remaining.convert(), offset)
        if (result > 0) {
            // Advance buffer position
            repeat(result.toInt()) { buffer.get() }
        }
        result
    }
}

/**
 * Darwin-specific network operations
 */
object DarwinNetOps {
    fun setNonBlocking(fd: Int) {
        val flags = fcntl(fd, F_GETFL, 0)
        fcntl(fd, F_SETFL, flags or O_NONBLOCK)
    }
    
    fun setReuseAddr(fd: Int) = memScoped {
        val enable = alloc<IntVar>()
        enable.value = 1
        setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, enable.ptr, sizeOf<IntVar>().convert())
    }
    
    fun setTcpNoDelay(fd: Int) = memScoped {
        val enable = alloc<IntVar>()
        enable.value = 1
        setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, enable.ptr, sizeOf<IntVar>().convert())
    }
}