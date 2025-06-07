package borg.trikeshed.lib

import borg.trikeshed.reactor.SelectionKey
import borg.trikeshed.reactor.SelectableChannel
import kotlinx.coroutines.delay

actual class CommonSelector {
    actual suspend fun select(): Int {
        println("CommonSelector.select() called (Native placeholder)")
        delay(100) // Simulate some delay
        return 0
    }

    actual suspend fun selectedKeys(): Set<SelectionKey> {
        println("CommonSelector.selectedKeys() called (Native placeholder)")
        return emptySet()
    }

    actual suspend fun close() {
        println("CommonSelector.close() called (Native placeholder)")
    }
}
