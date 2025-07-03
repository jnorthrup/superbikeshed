package borg.trikeshed.reactor

actual class SelectionKey(private val key: java.nio.channels.SelectionKey) {
    actual fun isValid(): Boolean = key.isValid
    actual fun cancel() = key.cancel()
    actual fun interestOps(): Int = key.interestOps()
    actual fun interestOps(ops: Int): SelectionKey = SelectionKey(key.interestOps(ops))
    actual fun readyOps(): Int = key.readyOps()
}