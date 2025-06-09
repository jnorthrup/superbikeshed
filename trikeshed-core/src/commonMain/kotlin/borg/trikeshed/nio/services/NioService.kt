package borg.trikeshed.nio.services

import borg.trikeshed.nio.ClientSocketChannel
import borg.trikeshed.nio.ServerSocketChannel
import kotlin.coroutines.CoroutineContext

/**
 * A CoroutineContext Element (CCEK) service for creating platform-specific NIO channels.
 * This service provides an abstraction layer for obtaining server and client socket channels,
 * allowing other components to be platform-agnostic in their network I/O setup.
 */
expect interface NioService : CoroutineContext.Element {
    /**
     * The key for [NioService] in a [CoroutineContext].
     * Used to retrieve the service instance, for example: `coroutineContext[NioService.Key]`.
     */
    companion object Key : CoroutineContext.Key<NioService>

    /**
     * The key for this service instance.
     */
    override val key: CoroutineContext.Key<*> // This will be NioService.Key

    /**
     * Creates a new platform-specific server socket channel.
     * The returned channel is typically unbound and needs to be configured and bound
     * by the caller.
     *
     * @return A [ServerSocketChannel] instance ready for configuration and use.
     * @throws borg.trikeshed.nio.NioException if channel creation fails due to underlying platform issues.
     */
    fun createServerSocketChannel(): ServerSocketChannel

    /**
     * Creates a new platform-specific client socket channel.
     * The returned channel is typically unconnected and needs to be configured and connected
     * by the caller.
     *
     * @return A [ClientSocketChannel] instance ready for configuration and use.
     * @throws borg.trikeshed.nio.NioException if channel creation fails due to underlying platform issues.
     */
    fun createClientSocketChannel(): ClientSocketChannel
}
