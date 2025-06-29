@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package borg.trikeshed.reactor

/**
 * Native implementation of SelectionKey
 */
actual class SelectionKey {
    actual fun isValid(): Boolean = false
    actual fun cancel() {}
    actual fun interestOps(): Int = 0
    actual fun interestOps(ops: Int): SelectionKey = this
    actual fun readyOps(): Int = 0
}