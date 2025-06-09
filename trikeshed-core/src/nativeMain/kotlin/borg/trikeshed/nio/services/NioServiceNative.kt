package borg.trikeshed.nio.services

import borg.trikeshed.nio.ActualClientSocketChannel // Assuming this is the actual class for ClientSocketChannel
import borg.trikeshed.nio.ActualServerSocketChannel // Assuming this is the actual class for ServerSocketChannel
import borg.trikeshed.nio.ClientSocketChannel
import borg.trikeshed.nio.ServerSocketChannel
import kotlin.coroutines.CoroutineContext

actual class ActualNioService : NioService {
    actual override val key: CoroutineContext.Key<*> = NioService.Key // Referencing companion object Key from expect

    actual override fun createServerSocketChannel(): ServerSocketChannel {
        return ActualServerSocketChannel()
    }

    actual override fun createClientSocketChannel(): ClientSocketChannel {
        return ActualClientSocketChannel()
    }
}
