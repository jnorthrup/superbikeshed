package borg.trikeshed.reactor

import java.nio.channels.Selector
import java.nio.channels.SelectableChannel as JvmSelectableChannel
import java.nio.channels.SelectionKey as JvmSelectionKey
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

class JvmSelectableChannel(private val nativeChannel: JvmSelectableChannel) : SelectableChannel {
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

actual class SelectionKey(private val jvmKey: JvmSelectionKey) {
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

actual class SelectorInterface(private val jvmSelector: Selector) {
    actual fun select(): Int = jvmSelector.select()
    actual fun wakeup() {
        jvmSelector.wakeup()
    }
    actual fun register(channel: SelectableChannel, ops: Int, attachment: Any?): SelectionKey {
        val jvmChannel = (channel as JvmSelectableChannel).jvmChannel()
        return SelectionKey(jvmChannel.register(jvmSelector, ops, attachment))
    }
    actual fun selectedKeys(): Set<SelectionKey> =
        jvmSelector.selectedKeys().map { SelectionKey(it) }.toSet()

    actual suspend fun close() {
        jvmSelector.close()
    }

    actual constructor() : this(Selector.open())
}