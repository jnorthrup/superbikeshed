package borg.trikeshed.reactor

import java.nio.channels.Selector
import java.nio.channels.SelectableChannel as JvmSelectableChannel
import java.nio.channels.SelectionKey as JvmSelectionKey
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

actual interface SelectableChannel {
    actual fun isOpen(): Boolean
    actual fun close()
    actual fun configureBlocking(block: Boolean): SelectableChannel
    actual fun isBlocking(): Boolean
}

class JvmSelectableChannel(private val nativeChannel: JvmSelectableChannel) : SelectableChannel {
    actual override fun isOpen(): Boolean = nativeChannel.isOpen
    actual override fun close() = nativeChannel.close()
    actual override fun configureBlocking(block: Boolean): SelectableChannel {
        nativeChannel.configureBlocking(block)
        return this
    }
    actual override fun isBlocking(): Boolean = nativeChannel.isBlocking
    
    fun jvmChannel(): JvmSelectableChannel = nativeChannel
}

actual class SelectionKey(private val jvmKey: JvmSelectionKey) {
    actual fun channel(): SelectableChannel = JvmSelectableChannel(jvmKey.channel())
    actual fun selector(): SelectorInterface = JvmSelectorInterface(jvmKey.selector())
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

class JvmSelectorInterface(private val jvmSelector: Selector) {
    fun select(): Int = jvmSelector.select()
    fun wakeup(): Unit = jvmSelector.wakeup().let { }
    fun register(channel: SelectableChannel, ops: Int, attachment: Any?): SelectionKey {
        val jvmChannel = (channel as JvmSelectableChannel).jvmChannel()
        return SelectionKey(jvmChannel.register(jvmSelector, ops, attachment))
    }
    fun selectedKeys(): Set<SelectionKey> = 
        jvmSelector.selectedKeys().map { SelectionKey(it) }.toSet()
    fun close(): Unit = jvmSelector.close()
}

actual class SelectorInterface {
    private val jvmSelector = Selector.open()
    
    actual fun select(): Int = jvmSelector.select()
    actual fun wakeup(): Unit = jvmSelector.wakeup().let { }
    actual fun register(channel: SelectableChannel, ops: Int, attachment: Any?): SelectionKey {
        val jvmChannel = (channel as JvmSelectableChannel).jvmChannel()
        return SelectionKey(jvmChannel.register(jvmSelector, ops, attachment))
    }
    actual fun selectedKeys(): Set<SelectionKey> = 
        jvmSelector.selectedKeys().map { SelectionKey(it) }.toSet()
    actual fun close(): Unit = jvmSelector.close()
}