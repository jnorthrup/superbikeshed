package borg.trikeshed.reactor

// WASM implementation using stubs (no direct socket access without host bindings)
actual interface SelectableChannel {
    actual fun isOpen(): Boolean
    actual fun close()
    actual fun configureBlocking(block: Boolean): SelectableChannel
    actual fun isBlocking(): Boolean
}

actual class SelectionKey {
    actual fun channel(): SelectableChannel = WasmSelectableChannel()
    actual fun selector(): SelectorInterface = WasmSelectorInterface()
    actual fun isValid(): Boolean = false
    actual fun cancel() { /* No-op */ }
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

class WasmSelectableChannel : SelectableChannel {
    private var open = true
    
    actual override fun isOpen(): Boolean = open
    actual override fun close() { open = false }
    actual override fun configureBlocking(block: Boolean): SelectableChannel = this
    actual override fun isBlocking(): Boolean = false
}

class WasmSelectorInterface : SelectorInterface {
    actual override fun select(): Int = 0
    actual override fun wakeup() { /* No-op */ }
    actual override fun register(channel: SelectableChannel, ops: Int, attachment: Any?): SelectionKey = SelectionKey()
    actual override fun selectedKeys(): Set<SelectionKey> = emptySet()
    actual override fun close() { /* No-op */ }
}

actual class SelectorInterface {
    actual fun select(): Int = 0
    actual fun wakeup() { /* No-op */ }
    actual fun register(channel: SelectableChannel, ops: Int, attachment: Any?): SelectionKey = SelectionKey()
    actual fun selectedKeys(): Set<SelectionKey> = emptySet()
    actual fun close() { /* No-op */ }
}