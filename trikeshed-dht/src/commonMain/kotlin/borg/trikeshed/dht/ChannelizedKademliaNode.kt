package borg.trikeshed.dht

import kotlin.coroutines.CoroutineContext

/**
 * ChannelizedKademliaNode stub for trikeshed-ccek compatibility
 */
class ChannelizedKademliaNode(
    val nodeId: NUID,
    val context: CoroutineContext
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<ChannelizedKademliaNode>
    override val key = Key
}

/**
 * NUID stub
 */
data class NUID(val value: String)

/**
 * MetaverseKademliaAgent stub
 */
class MetaverseKademliaAgent(
    val node: ChannelizedKademliaNode,
    val agentId: String
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<MetaverseKademliaAgent>
    override val key = Key
} 