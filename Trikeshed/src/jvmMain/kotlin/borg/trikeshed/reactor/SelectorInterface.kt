actual class SelectorInterface {
    actual fun select(): Int = 0
    actual fun wakeup() {}
    actual fun register(channel: SelectableChannel, ops: Int, attachment: Any?): SelectionKey = throw UnsupportedOperationException("register not implemented")
    actual fun selectedKeys(): Set<SelectionKey> = emptySet()
    actual fun close() {}
}