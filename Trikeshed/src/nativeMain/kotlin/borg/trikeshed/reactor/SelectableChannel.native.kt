package borg.trikeshed.reactor

// Native stub implementations for SelectableChannel and related types
actual interface SelectableChannel {
    actual fun isOpen(): Boolean
    actual fun close()
    actual fun configureBlocking(block: Boolean): SelectableChannel
    actual fun isBlocking(): Boolean
}

class NativeSelectableChannel : SelectableChannel {
    private var open = true
    private var blocking = true
    
    override fun isOpen(): Boolean = open
    override fun close() { open = false }
    override fun configureBlocking(block: Boolean): SelectableChannel {
        blocking = block
        return this
    }
    override fun isBlocking(): Boolean = blocking
}

actual class SelectionKey {
    actual fun channel(): SelectableChannel = NativeSelectableChannel()
    actual fun selector(): SelectorInterface = SelectorInterface()
    actual fun isValid(): Boolean = true
    actual fun cancel() {}
    actual fun interestOps(): Int = 0
    actual fun interestOps(ops: Int): SelectionKey = this
    actual fun readyOps(): Int = 0
    actual fun isReadable(): Boolean = false
    actual fun isWritable(): Boolean = false
    actual fun isConnectable(): Boolean = false
    actual fun isAcceptable(): Boolean = false
    actual fun attachment(): Any? = null
    actual fun attach(ob: Any?): Any? = null
}

actual class SelectorInterface {
    actual fun select(): Int = 0
    actual fun wakeup() {}
    actual fun register(channel: SelectableChannel, ops: Int, attachment: Any?): SelectionKey = SelectionKey()
    actual fun selectedKeys(): Set<SelectionKey> = emptySet()
    actual fun close() {}
}