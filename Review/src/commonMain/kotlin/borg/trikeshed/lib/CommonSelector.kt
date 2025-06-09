package borg.trikeshed.lib

import borg.trikeshed.reactor.SelectionKey

expect class CommonSelector {
    suspend fun select(): Int
    suspend fun selectedKeys(): Set<SelectionKey>
    suspend fun close()
}
