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
class JvmSelectableChannel(internal val nativeChannel: JvmSelectableChannel) : SelectableChannel {
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

    fun jvmChannel(): JvmSelectableChannel = nativeChannel
}

/**
 * JVM implementation of ServerChannel
 */
actual class JvmServerChannel(private val serverSocketChannel: ServerSocketChannel) : ServerChannel {
    override val isOpen: Boolean get() = serverSocketChannel.isOpen
    override val isBlocking: Boolean get() = serverSocketChannel.isBlocking

    override suspend fun close() {
        serverSocketChannel.close()
    }

    override fun configureBlocking(block: Boolean): SelectableChannel {
        serverSocketChannel.configureBlocking(block)
        return this
    }

    override suspend fun bind(port: Int) {
        serverSocketChannel.bind(InetSocketAddress(port))
    }

    override suspend fun accept(): ClientChannel? {
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
actual class JvmClientChannel(private val socketChannel: SocketChannel) : ClientChannel {
    override val isOpen: Boolean get() = socketChannel.isOpen
    override val isBlocking: Boolean get() = socketChannel.isBlocking

    override suspend fun close() {
        socketChannel.close()
    }

    override fun configureBlocking(block: Boolean): SelectableChannel {
        socketChannel.configureBlocking(block)
        return this
    }

    override suspend fun connect(host: String, port: Int) {
        socketChannel.connect(InetSocketAddress(host, port))
    }

    override suspend fun read(buffer: ByteArray): Int {
        val byteBuffer = ByteBuffer.allocate(buffer.size)
        val bytesRead = socketChannel.read(byteBuffer)
        if (bytesRead > 0) {
            byteBuffer.flip()
            byteBuffer.get(buffer, 0, bytesRead)
        }
        return bytesRead
    }

    override suspend fun write(buffer: ByteArray): Int {
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
        (channel as JvmSelectableChannel).jvmChannel().keyFor(Selector.open()) 
            ?: throw IllegalStateException("Channel not registered with any selector")
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
        val jvmSelectableChannel = channel as? JvmSelectableChannel 
            ?: throw IllegalArgumentException("Expected JvmSelectableChannel but got ${channel::class}")
        val jvmChannel = jvmSelectableChannel.jvmChannel()
        return SelectionKey(jvmChannel.register(jvmSelector, ops, attachment))
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

// Type aliases for the actual implementations
actual typealias ServerChannel = JvmServerChannel
actual typealias ClientChannel = JvmClientChannel