package borg.trikeshed.nexus.reactor

import java.nio.channels.Selector
import java.nio.channels.SelectableChannel as JvmSelectableChannel
import java.nio.channels.SelectionKey as JvmSelectionKey
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.ByteBuffer
import java.net.InetSocketAddress

/**
 * JVM implementation of SelectableChannel interface
 */
class JvmSelectableChannel(val nativeChannel: java.nio.channels.SelectableChannel) : SelectableChannel {
    override val isOpen: Boolean
        get() = nativeChannel.isOpen

    override suspend fun close() {
        nativeChannel.close()
    }

    override fun configureBlocking(block: Boolean): SelectableChannel {
        nativeChannel.configureBlocking(block)
        return this
    }

    override val isBlocking: Boolean
        get() = nativeChannel.isBlocking

    fun jvmChannel(): java.nio.channels.SelectableChannel = nativeChannel
}

/**
 * JVM implementation of ServerChannel
 */
class JvmServerChannel(private val serverSocketChannel: ServerSocketChannel) {
    val isOpen: Boolean get() = serverSocketChannel.isOpen
    val isBlocking: Boolean get() = serverSocketChannel.isBlocking

    suspend fun close() {
        serverSocketChannel.close()
    }

    fun configureBlocking(block: Boolean): JvmServerChannel {
        serverSocketChannel.configureBlocking(block)
        return this
    }

    suspend fun bind(port: Int) {
        serverSocketChannel.bind(InetSocketAddress(port))
    }

    suspend fun accept(): JvmClientChannel? {
        val socketChannel = serverSocketChannel.accept()
        return socketChannel?.let { JvmClientChannel(it) }
    }

    companion object {
        fun open(): JvmServerChannel {
            return JvmServerChannel(ServerSocketChannel.open())
        }
    }
}

/**
 * JVM implementation of ClientChannel
 */
class JvmClientChannel(private val socketChannel: SocketChannel) {
    val isOpen: Boolean get() = socketChannel.isOpen
    val isBlocking: Boolean get() = socketChannel.isBlocking

    suspend fun close() {
        socketChannel.close()
    }

    fun configureBlocking(block: Boolean): JvmClientChannel {
        socketChannel.configureBlocking(block)
        return this
    }

    suspend fun connect(host: String, port: Int) {
        socketChannel.connect(InetSocketAddress(host, port))
    }

    suspend fun read(buffer: ByteArray): Int {
        val byteBuffer = ByteBuffer.allocate(buffer.size)
        val bytesRead = socketChannel.read(byteBuffer)
        if (bytesRead > 0) {
            byteBuffer.flip()
            byteBuffer.get(buffer, 0, bytesRead)
        }
        return bytesRead
    }

    suspend fun write(buffer: ByteArray): Int {
        val byteBuffer = ByteBuffer.wrap(buffer)
        return socketChannel.write(byteBuffer)
    }

    companion object {
        fun open(): JvmClientChannel {
            return JvmClientChannel(SocketChannel.open())
        }
    }
}

/**
 * JVM implementation of SelectionKey
 */
actual class SelectionKey(private val jvmKey: JvmSelectionKey) {
    actual constructor(channel: SelectableChannel, ops: Int) : this(
        SocketChannel.open().keyFor(Selector.open()) ?: throw IllegalStateException("Channel not registered")
    )

    actual fun channel(): SelectableChannel = JvmSelectableChannel(jvmKey.channel())
    actual fun selector(): SelectorInterface = SelectorInterface(jvmKey.selector())
    actual fun isValid(): Boolean = jvmKey.isValid
    actual fun cancel() = jvmKey.cancel()
    actual fun interestOps(): Int = jvmKey.interestOps()
    actual fun interestOps(ops: Int): SelectionKey {
        jvmKey.interestOps(ops)
        return this
    }
    actual fun readyOps(): Int = jvmKey.readyOps()
    actual fun isReadable(): Boolean = jvmKey.isReadable
    actual fun isWritable(): Boolean = jvmKey.isWritable
    actual fun isConnectable(): Boolean = jvmKey.isConnectable
    actual fun isAcceptable(): Boolean = jvmKey.isAcceptable
    actual fun attachment(): Any? = jvmKey.attachment()
    actual fun attach(ob: Any?): Any? = jvmKey.attach(ob)
}

/**
 * JVM implementation of SelectorInterface
 */
actual class SelectorInterface(private val jvmSelector: Selector) {
    actual constructor() : this(Selector.open())

    actual fun select(): Int = jvmSelector.select()
    actual fun wakeup() {
        jvmSelector.wakeup()
    }
    actual fun register(channel: SelectableChannel, ops: Int, attachment: Any?): SelectionKey {
        // Simplified registration - in real implementation this would be more complex
        val tempChannel = SocketChannel.open()
        return SelectionKey(tempChannel.register(jvmSelector, ops, attachment))
    }
    actual fun selectedKeys(): Set<SelectionKey> =
        jvmSelector.selectedKeys().map { SelectionKey(it) }.toSet()

    actual suspend fun close() {
        jvmSelector.close()
    }
}

/**
 * JVM implementation of IOOperation
 */
actual class IOOperation(actual val value: Int) {
    actual companion object {
        actual val Read = IOOperation(JvmSelectionKey.OP_READ)
        actual val Write = IOOperation(JvmSelectionKey.OP_WRITE)
        actual val Accept = IOOperation(JvmSelectionKey.OP_ACCEPT)
        actual val Connect = IOOperation(JvmSelectionKey.OP_CONNECT)
    }
}

/**
 * JVM actual implementation of SelectableChannel interface
 */
actual interface SelectableChannel {
    actual val isOpen: Boolean
    actual suspend fun close()
    actual fun configureBlocking(block: Boolean): SelectableChannel
    actual val isBlocking: Boolean
}

// Type aliases for the actual implementations
actual typealias ServerChannel = JvmServerChannel
actual typealias ClientChannel = JvmClientChannel