package borg.trikeshed.uring

import kotlinx.coroutines.CoroutineScope
import java.nio.ByteBuffer as JByteBuffer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * JVM implementation of ByteBuffer
 * Wraps java.nio.ByteBuffer
 */
actual class ByteBuffer private constructor(
    private val buffer: JByteBuffer
) {
    actual val capacity: Int get() = buffer.capacity()
    actual val position: Int get() = buffer.position()
    actual val remaining: Int get() = buffer.remaining()
    
    actual fun get(): Byte = buffer.get()
    
    actual fun get(dst: ByteArray, offset: Int, length: Int): ByteBuffer {
        buffer.get(dst, offset, length)
        return this
    }
    
    actual fun put(b: Byte): ByteBuffer {
        buffer.put(b)
        return this
    }
    
    actual fun put(src: ByteArray, offset: Int, length: Int): ByteBuffer {
        buffer.put(src, offset, length)
        return this
    }
    
    actual fun flip(): ByteBuffer {
        buffer.flip()
        return this
    }
    
    actual fun clear(): ByteBuffer {
        buffer.clear()
        return this
    }
    
    actual fun rewind(): ByteBuffer {
        buffer.rewind()
        return this
    }
    
    // JVM-specific extensions
    fun toJavaBuffer(): JByteBuffer = buffer
    
    actual companion object {
        actual fun allocate(capacity: Int): ByteBuffer = 
            ByteBuffer(JByteBuffer.allocate(capacity))
        
        actual fun allocateDirect(capacity: Int): ByteBuffer = 
            ByteBuffer(JByteBuffer.allocateDirect(capacity))
        
        actual fun wrap(array: ByteArray): ByteBuffer = 
            ByteBuffer(JByteBuffer.wrap(array))
        
        // JVM-specific factory
        fun fromJavaBuffer(buffer: JByteBuffer): ByteBuffer = ByteBuffer(buffer)
    }
}

/**
 * JVM implementation of SocketAddress
 */
actual class SocketAddress(
    actual val host: String,
    actual val port: Int
) {
    fun toInetSocketAddress(): InetSocketAddress = InetSocketAddress(host, port)
    
    companion object {
        fun fromInetSocketAddress(addr: InetSocketAddress): SocketAddress =
            SocketAddress(addr.hostString, addr.port)
    }
}

/**
 * JVM implementation of atomic counter
 */
@Suppress("UNCHECKED_CAST")
actual class atomic<T>(value: T) {
    private val ref = when (value) {
        is Long -> AtomicLong(value)
        is Int -> AtomicInteger(value)
        else -> AtomicReference(value)
    }
    
    actual fun get(): T = when (val r = ref) {
        is AtomicLong -> r.get() as T
        is AtomicInteger -> r.get() as T
        is AtomicReference<*> -> r.get() as T
        else -> throw IllegalStateException()
    }
    
    actual fun set(value: T) {
        when (val r = ref) {
            is AtomicLong -> r.set(value as Long)
            is AtomicInteger -> r.set(value as Int)
            is AtomicReference<*> -> (r as AtomicReference<T>).set(value)
        }
    }
    
    actual fun incrementAndGet(): T = when (val r = ref) {
        is AtomicLong -> r.incrementAndGet() as T
        is AtomicInteger -> r.incrementAndGet() as T
        else -> throw UnsupportedOperationException("Cannot increment ${value!!::class}")
    }
}

/**
 * JVM implementation using NIO - placeholder for now
 */
actual fun createTrikeUring(
    scope: CoroutineScope,
    config: UringConfig
): TrikeUring {
    TODO("JVM implementation using NIO Selector")
}

/**
 * Architecture detection for JVM
 */
actual fun getArchitecture(): String {
    val arch = System.getProperty("os.arch")
    return when {
        arch.contains("aarch64") || arch.contains("arm64") -> "arm64"
        arch.contains("x86_64") || arch.contains("amd64") -> "x64"
        else -> arch
    }
}