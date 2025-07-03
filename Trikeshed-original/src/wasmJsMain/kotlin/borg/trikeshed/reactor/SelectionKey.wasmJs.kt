package borg.trikeshed.reactor

/**
 * WasmJs implementation of SelectionKey
 * Simplified stub implementation for compilation
 */
actual class SelectionKey {
    actual fun isValid(): Boolean = false
    actual fun cancel() {}
    actual fun interestOps(): Int = 0
    actual fun interestOps(ops: Int): SelectionKey = this
    actual fun readyOps(): Int = 0
}