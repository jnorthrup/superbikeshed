actual abstract class SelectionKey {
    actual abstract val isValid: Boolean
    actual abstract val readyOps: Int
    actual abstract var interestOps: Int
    actual abstract var attachment: Any?
    actual abstract fun cancel()
    actual abstract fun channel(): SelectableChannel
}