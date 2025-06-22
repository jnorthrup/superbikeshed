package borg.trikeshed.reactor

import kotlinx.coroutines.CoroutineDispatcher

expect val IO: CoroutineDispatcher

expect object System {
    fun currentTimeMillis(): Long
    fun nanoTime(): Long
}

expect class PlatformIO {
    fun createSelector(): SelectorInterface
    fun createServerChannel(): ServerChannel
    fun createClientChannel(): ClientChannel
    fun createBufferPool(bufferSize: Int): BufferPool
    
    companion object {
        fun create(): PlatformIO
    }
} 