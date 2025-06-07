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
     * @param topic The topic string to publish the message to.
     * @param message The message content to publish.
     */
    suspend fun publish(topic: String, message: String)

    /**
     * Subscribes to a given IPFS PubSub topic to receive messages.
     *
     * @param topic The topic string to subscribe to.
     * @return A [Flow] of strings, where each string is a message received on the topic.
     *         The flow will emit messages as they arrive.
     *         Consideration for future: A data class like IpfsPubSubMessage(from: String?, data: String, topic: String)
     *         might be more robust than raw String for the Flow, providing more context about the message origin.
     */
    fun subscribe(topic: String): Flow<String>
}
