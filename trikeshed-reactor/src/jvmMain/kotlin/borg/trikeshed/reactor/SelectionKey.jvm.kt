@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

import java.nio.channels.SelectionKey as JvmSelectionKey
import java.nio.channels.Selector as JvmSelector
import java.nio.channels.SelectableChannel as JvmSelectableChannel

actual class SelectionKey(internal val key: JvmSelectionKey) {
    actual fun channel(): SelectableChannel = JvmSelectableChannelWrapper(key.channel())
    
    actual fun selector(): SelectorInterface = SelectorInterface(key.selector())
    
    actual fun isValid(): Boolean = key.isValid
    
    actual fun cancel() = key.cancel()
    
    actual fun interestOps(): Int = key.interestOps()
    
    actual fun interestOps(ops: Int): SelectionKey {
        key.interestOps(ops)
        return this
    }
    
    actual fun readyOps(): Int = key.readyOps()
    
    actual fun isReadable(): Boolean = key.isReadable
    
    actual fun isWritable(): Boolean = key.isWritable
    
    actual fun isConnectable(): Boolean = key.isConnectable
    
    actual fun isAcceptable(): Boolean = key.isAcceptable
    
    actual fun attachment(): Any? = key.attachment()
    
    actual fun attach(ob: Any?): Any? = key.attach(ob)
}

actual class SelectorInterface(internal val selector: JvmSelector) {
    
    actual constructor() : this(JvmSelector.open())
    
    actual fun select(): Int = selector.select()
    
    actual fun wakeup() {
        selector.wakeup()
    }
    
    actual fun register(channel: SelectableChannel, ops: Int, attachment: Any?): SelectionKey {
        val jvmChannel = when (channel) {
            is JvmSelectableChannelWrapper -> channel.channel
            else -> throw IllegalArgumentException("Not a JVM channel")
        }
        return SelectionKey(jvmChannel.register(selector, ops, attachment))
    }
    
    actual fun selectedKeys(): Set<SelectionKey> = 
        selector.selectedKeys().map { SelectionKey(it) }.toSet()
    
    actual suspend fun close() {
        selector.close()
    }
}