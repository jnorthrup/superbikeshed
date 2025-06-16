actual class SelectorInterface {
    actual fun select(): Int = throw NotImplementedError("SelectorInterface.select not implemented")
    actual fun wakeup() = throw NotImplementedError("SelectorInterface.wakeup not implemented")
    actual fun register(channel: SelectableChannel, ops: Int, attachment: Any?): SelectionKey = throw NotImplementedError("SelectorInterface.register not implemented") 
    actual fun selectedKeys(): Set<SelectionKey> = throw NotImplementedError("SelectorInterface.selectedKeys not implemented")
    actual fun close() = throw NotImplementedError("SelectorInterface.close not implemented")
}