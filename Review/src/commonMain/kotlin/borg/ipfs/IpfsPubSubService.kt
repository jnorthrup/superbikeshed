package borg.ipfs

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.Flow

/**
 * Key for accessing the IpfsPubSubService in a CoroutineContext.
 */
object IpfsPubSubServiceKey : CoroutineContext.Key<IpfsPubSubService>

/**
 * Interface for interacting with IPFS PubSub functionality.
 * Allows publishing messages to topics and subscribing to receive messages from topics.
 * This can be used, for example, to notify peers about updates to datasets or IPNS records.
 */
interface IpfsPubSubService : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = IpfsPubSubServiceKey

    /**
     * Publishes a message to a given IPFS PubSub topic.
     *
     * @param topic The topic to publish the message to.
     * @param message The message content to publish.
     */
    suspend fun publish(topic: IpfsTopic, message: IpfsMessage)

    /**
     * Subscribes to a given IPFS PubSub topic to receive messages.
     *
     * @param topic The topic to subscribe to.
     * @return A [Flow] of messages received on the topic.
     *         The flow will emit messages as they arrive.
     */
    fun subscribe(topic: IpfsTopic): Flow<IpfsMessage>
}
