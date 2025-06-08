package borg.trikeshed.reactor

import borg.trikeshed.io.ByteBuffer
import borg.trikeshed.io.ByteBufferFactory
import java.net.InetSocketAddress
import java.nio.channels.SelectionKey as JvmSelectionKey
import java.nio.channels.Selector as JvmSelector
import java.nio.channels.ServerSocketChannel as JvmServerSocketChannel
import java.nio.channels.SocketChannel as JvmSocketChannel

actual class PlatformIO {
    actual suspend fun createSelector(): SelectorInterface {
        return JvmSelectorInterfaceImpl(JvmSelector.open())
    }

    actual suspend fun createServerChannel(): ServerChannel {
        return JvmServerChannelImpl(JvmServerSocketChannel.open())
    }

    actual suspend fun createClientChannel(): ClientChannel {
        return JvmClientChannelImpl(JvmSocketChannel.open())
    }

    actual suspend fun createBufferPool(bufferSize: Int): BufferPool {
        return JvmBufferPool(bufferSize)
    }

    actual companion object {
        actual suspend fun create(): PlatformIO = PlatformIO()
    }
}

private class JvmBufferPool(private val bufferSize: Int) : BufferPool {
    override suspend fun acquire(): ByteBuffer {
        // Here we allocate a ByteBuffer from borg.trikeshed.nio, which is backed by a Java ByteBuffer
        return ByteBufferFactory.allocateDirect(bufferSize)
    }

    override suspend fun release(buffer: ByteBuffer) {
        // In JVM, direct ByteBuffers are managed by the garbage collector, so no explicit release needed here
        // If it was a pooled buffer, logic would go here. For now, it's a no-op as per previous comment.
    }
}

actual class JvmSelectorInterfaceImpl(private val selector: JvmSelector) : SelectorInterface {
    override suspend fun select(): Int = selector.select()
    override suspend fun wakeup() { selector.wakeup() }

    override suspend fun register(channel: SelectableChannel, ops: Int, attachment: Any?): SelectionKey {
        val jvmChannel = when (channel) {
            is JvmServerChannelImpl -> channel.underlying
            is JvmClientChannelImpl -> channel.underlying
            else -> throw IllegalArgumentException("Unknown channel type")
        }
        val jvmKey = jvmChannel.register(selector, ops, attachment)
        return SelectionKey(jvmKey)
    }

    override suspend fun selectedKeys(): Set<SelectionKey> {
        return selector.selectedKeys().map { jvmKey ->
            SelectionKey(jvmKey)
        }.toSet()
    }

    override suspend fun close() = selector.close()

    actual companion object {
        actual suspend fun create(): SelectorInterface {
            return JvmSelectorInterfaceImpl(JvmSelector.open())
        }
    }
}

@Suppress("ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_MEMBERS_AS_NON_FINAL_EXPECT_CLASSIFIER_WARNING")
actual class SelectionKey(private val underlying: JvmSelectionKey) : borg.trikeshed.reactor.SelectionKey {
    actual override val isValid: Boolean get() = underlying.isValid()
    actual override val readyOps: Int get() = underlying.readyOps()
    actual override var interestOps: Int
        get() = underlying.interestOps()
        set(value) {
            underlying.interestOps(value)
        }
    actual override var attachment: Any?
        get() = underlying.attachment()
        set(value) {
            underlying.attach(value)
        }

    actual override fun cancel() = underlying.cancel()
    actual override fun channel(): SelectableChannel {
        val jvmChannel = underlying.channel()
        return when (jvmChannel) {
            is JvmServerSocketChannel -> JvmServerChannelImpl(jvmChannel)
            is JvmSocketChannel -> JvmClientChannelImpl(jvmChannel)
            else -> throw IllegalArgumentException("Unknown channel type")
        }
    }
}

actual class JvmServerChannelImpl(val underlying: JvmServerSocketChannel) : ServerChannel {
    override fun configureBlocking(block: Boolean) {
        underlying.configureBlocking(block)
    }

    override fun register(selector: SelectorInterface, ops: Int, att: Any?): borg.trikeshed.reactor.SelectionKey {
        require(selector is JvmSelectorInterfaceImpl) { "Selector must be JVM implementation" }
        val jvmKey = underlying.register(selector.selector, ops, att)
        return borg.trikeshed.reactor.SelectionKey(jvmKey)
    }

    override suspend fun bind(port: Int) {
        underlying.bind(InetSocketAddress(port))
    }

    override suspend fun accept(): ClientChannel? {
        return underlying.accept()?.let { JvmClientChannelImpl(it) }
    }

    override suspend fun close() = underlying.close()
}

actual class JvmClientChannelImpl(val underlying: JvmSocketChannel) : ClientChannel {
    override fun configureBlocking(block: Boolean) {
        underlying.configureBlocking(block)
    }

    override fun register(selector: SelectorInterface, ops: Int, att: Any?): borg.trikeshed.reactor.SelectionKey {
        require(selector is JvmSelectorInterfaceImpl) { "Selector must be JVM implementation" }
        val jvmKey = underlying.register(selector.selector, ops, att)
        return borg.trikeshed.reactor.SelectionKey(jvmKey)
    }

    override suspend fun connect(host: String, port: Int) {
        underlying.connect(InetSocketAddress(host, port))
    }

    override suspend fun read(buffer: ByteBuffer): Int {
        val jvmBuffer = (buffer as borg.trikeshed.nio.JvmByteBuffer).delegate
        return underlying.read(jvmBuffer)
    }

    override suspend fun write(buffer: ByteBuffer): Int {
        val jvmBuffer = (buffer as borg.trikeshed.nio.JvmByteBuffer).delegate
        return underlying.write(jvmBuffer)
    }

    override suspend fun close() = underlying.close()
