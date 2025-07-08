@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

import kotlinx.cinterop.*
import platform.posix.*

actual class ClientChannel(internal var fd: Int = -1) : ReadableChannel, WritableChannel {
    override val isOpen: Boolean get() = fd >= 0
    
    override suspend fun close() {
        if (fd >= 0) {
            close(fd)
            fd = -1
        }
    }
    
    override fun configureBlocking(block: Boolean): SelectableChannel {
        if (fd >= 0) {
            val flags = fcntl(fd, F_GETFL, 0)
            if (block) {
                fcntl(fd, F_SETFL, flags and O_NONBLOCK.inv())
            } else {
                fcntl(fd, F_SETFL, flags or O_NONBLOCK)
            }
        }
        return this
    }
    
    override val isBlocking: Boolean get() {
        if (fd < 0) return true
        val flags = fcntl(fd, F_GETFL, 0)
        return (flags and O_NONBLOCK) == 0
    }
    
    actual suspend fun connect(host: String, port: Int) {
        fd = socket(AF_INET, SOCK_STREAM, 0)
        if (fd < 0) return
        
        // This is a simplified implementation
        // In a real implementation, you'd need proper address resolution
        // and async connect handling
    }
    
    override suspend fun write(buffer: ByteBuffer): Int {
        val (array, pos, limit) = buffer.arrayWithBounds()
        return if (fd >= 0) {
            array.usePinned { pinned ->
                write(fd, pinned.addressOf(pos), (limit - pos).toULong()).toInt()
            }
        } else -1
    }
    
    override suspend fun read(buffer: ByteBuffer): Int {
        val (array, pos, limit) = buffer.arrayWithBounds()
        return if (fd >= 0) {
            array.usePinned { pinned ->
                read(fd, pinned.addressOf(pos), (limit - pos).toULong()).toInt()
            }
        } else -1
    }
}

actual class SelectionKey(internal var valid: Boolean = true) {
    actual fun isValid(): Boolean = valid
    actual fun cancel() { valid = false }
    actual fun interestOps(): Int = 0
    actual fun interestOps(ops: Int): SelectionKey = this
    actual fun readyOps(): Int = 0
    actual fun channel(): Any = Unit
    actual fun selector(): Any = Unit
    actual fun isReadable(): Boolean = false
    actual fun isWritable(): Boolean = false
    actual fun isConnectable(): Boolean = false
    actual fun isAcceptable(): Boolean = false
    actual fun attachment(): Any? = null
    actual fun attach(ob: Any?): Any? = null
}

actual class SelectorInterface actual constructor() {
    actual fun select(): Int = 0
    actual fun wakeup() {}
    actual fun register(channel: SelectableChannel, ops: Int, attachment: Any?): SelectionKey =
        SelectionKey()
    actual fun selectedKeys(): Set<SelectionKey> = emptySet()
    actual suspend fun close() {}
}

actual class IOOperation actual constructor(actual val value: Int) {
    actual companion object {
        actual val Read = IOOperation(1)
        actual val Write = IOOperation(4)
        actual val Accept = IOOperation(16)
        actual val Connect = IOOperation(8)
    }
}

actual fun currentTimeMillis(): Long = kotlin.system.getTimeMillis()