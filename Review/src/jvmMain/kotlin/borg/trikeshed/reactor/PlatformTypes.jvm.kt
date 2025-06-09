package borg.trikeshed.reactor

import java.nio.channels.Selector as JvmSelector

actual class Selector(private val delegate: JvmSelector) {
    actual fun select(): Int = delegate.select()
    actual fun selectedKeys(): MutableSet<SelectionKey> {
        return delegate.selectedKeys().map { jvmKey ->
            SelectionKey() // TODO: Proper implementation
        }.toMutableSet()
    }
    actual fun close() = delegate.close()
    
    companion object {
        fun open(): Selector = Selector(JvmSelector.open())
    }
}