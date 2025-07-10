@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

import java.nio.channels.SelectionKey

actual class IOOperation internal constructor(actual val value: Int) {
    actual companion object {
        actual val Read: IOOperation = IOOperation(SelectionKey.OP_READ)
        actual val Write: IOOperation = IOOperation(SelectionKey.OP_WRITE)
        actual val Accept: IOOperation = IOOperation(SelectionKey.OP_ACCEPT)
        actual val Connect: IOOperation = IOOperation(SelectionKey.OP_CONNECT)
    }
}