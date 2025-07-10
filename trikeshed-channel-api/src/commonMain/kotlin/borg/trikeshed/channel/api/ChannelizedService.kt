package borg.trikeshed.channel.api

import kotlinx.coroutines.CoroutineScope

/**
 * Generic interface for channelized services.
 * Services implementing this interface can be started and stopped,
 * and provide a common entry point for channel-based communication.
 */
interface ChannelizedService {
    /**
     * Starts the service, typically launching coroutines to process requests.
     * @param scope The CoroutineScope in which the service's operations will run.
     */
    fun start(scope: CoroutineScope)

    /**
     * Stops the service, cleaning up any resources and stopping coroutines.
     */
    fun stop()
}
