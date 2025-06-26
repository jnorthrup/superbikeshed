package borg.trikeshed.dht.gossip

import borg.trikeshed.lib.*
import borg.trikeshed.dht.kademlia.id.NUID
import borg.trikeshed.dht.kademlia.events.NodeInfo
import borg.trikeshed.dht.kademlia.subnet.SubnetManager
import borg.trikeshed.dht.agent.GossipMessage
import borg.trikeshed.dht.agent.SubscriptionHandle
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/**
 * Gossip service implementation with TTL, storm prevention, and subscription management
 * Provides efficient message dissemination across the DHT network
 */
class GossipService(
    private val localNodeId: NUID,
    private val subnetManager: SubnetManager,
    private val sendToNode: suspend (nodeId: NUID, message: GossipMessage) -> Boolean
) {
    // Message cache for duplicate detection
    private val messageCache = MessageCache(maxSize = 10000, ttlMillis = 300000) // 5 minutes
    
    // Active subscriptions
    private val subscriptions = mutableListOf<GossipSubscription>()
    
    // Gossip flow for reactive processing
    private val gossipFlow = MutableSharedFlow<GossipMessage>(
        replay = 0,
        extraBufferCapacity = 1000
    )
    
    // Configuration
    private val defaultFanout = 8
    private val maxHops = 6
    
    /**
     * Publish a message to the gossip network
     */
    suspend fun publish(
        payload: Indexed<Byte>,
        targetSubnets: Indexed<String> = 0 j { "" },
        ttl: Int = maxHops
    ): Boolean {
        val message = GossipMessage(
            messageId = NUID.random(),
            publisherId = localNodeId,
            targetSubnets = targetSubnets,
            payload = payload,
            timestamp = borg.trikeshed.reactor.getCurrentTimeMillis(),
            signature = 0 j { 0.toByte() } // TODO: Implement signing
        )
        
        return propagate(message, ttl)
    }
    
    /**
     * Subscribe to gossip messages
     */
    fun subscribe(
        filter: GossipFilter = GossipFilter.AcceptAll,
        handler: (GossipMessage) -> Unit
    ): SubscriptionHandle {
        val subscription = GossipSubscription(filter, handler)
        subscriptions.add(subscription)
        
        return object : SubscriptionHandle {
            override fun unsubscribe() {
                subscriptions.remove(subscription)
            }
        }
    }
    
    /**
     * Get gossip message flow for reactive processing
     */
    fun getGossipFlow(): Flow<GossipMessage> = gossipFlow.asSharedFlow()
    
    /**
     * Handle incoming gossip message
     */
    suspend fun handleIncomingGossip(message: GossipMessage, ttl: Int = maxHops): Boolean {
        // Check if we've seen this message before
        if (messageCache.hasSeenMessage(message.messageId)) {
            return false
        }
        
        // Mark as seen
        messageCache.markAsSeen(message.messageId)
        
        // Deliver to local subscribers
        deliverToSubscribers(message)
        
        // Emit to flow
        gossipFlow.tryEmit(message)
        
        // Propagate if TTL allows
        if (ttl > 0) {
            propagate(message, ttl - 1)
        }
        
        return true
    }
    
    /**
     * Propagate message to peers
     */
    private suspend fun propagate(message: GossipMessage, ttl: Int): Boolean {
        if (ttl <= 0) return false
        
        val peers = selectPeersForGossip(message)
        if (peers.isEmpty()) return false
        
        // Send to selected peers
        val results = peers.map { peerId ->
            GlobalScope.async {
                sendToNode(peerId, message)
            }
        }
        
        // Wait for at least one success
        val successes = results.awaitAll().count { it }
        return successes > 0
    }
    
    /**
     * Select peers for gossip propagation
     */
    private fun selectPeersForGossip(message: GossipMessage): List<NUID> {
        val peers = mutableSetOf<NUID>()
        
        // If message targets specific subnets
        if (message.targetSubnets.a > 0) {
            for (i in 0 until message.targetSubnets.a) {
                val subnet = message.targetSubnets[i]
                val subnetPeers = subnetManager.findNodesInSubnets(1 j { subnet })
                
                // Select random subset for fanout
                val selected = subnetPeers.play.shuffled().take(defaultFanout / message.targetSubnets.a)
                peers.addAll(selected)
            }
        } else {
            // Broadcast to all known subnets
            val mySubnets = subnetManager.getNodeSubnets(localNodeId)
            for (i in 0 until mySubnets.a) {
                val subnet = mySubnets[i]
                val subnetPeers = subnetManager.findNodesInSubnets(1 j { subnet })
                
                // Select random subset
                val selected = subnetPeers.play.shuffled().take(defaultFanout / maxOf(1, mySubnets.a))
                peers.addAll(selected)
            }
        }
        
        // Ensure we don't exceed fanout
        return peers.take(defaultFanout).filter { it != localNodeId }
    }
    
    /**
     * Deliver message to local subscribers
     */
    private fun deliverToSubscribers(message: GossipMessage) {
        for (subscription in subscriptions) {
            if (subscription.filter.accepts(message)) {
                try {
                    subscription.handler(message)
                } catch (e: Exception) {
                    // Log error but continue delivery to other subscribers
                    println("Error delivering gossip to subscriber: ${e.message}")
                }
            }
        }
    }
}

/**
 * Gossip subscription with filter and handler
 */
private data class GossipSubscription(
    val filter: GossipFilter,
    val handler: (GossipMessage) -> Unit
)

/**
 * Filter for gossip messages
 */
sealed class GossipFilter {
    abstract fun accepts(message: GossipMessage): Boolean
    
    object AcceptAll : GossipFilter() {
        override fun accepts(message: GossipMessage) = true
    }
    
    data class ByPublisher(val publisherId: NUID) : GossipFilter() {
        override fun accepts(message: GossipMessage) = message.publisherId == publisherId
    }
    
    data class BySubnet(val subnetId: String) : GossipFilter() {
        override fun accepts(message: GossipMessage): Boolean {
            return message.targetSubnets.play.contains(subnetId)
        }
    }
    
    data class ByPayloadSize(val maxSize: Int) : GossipFilter() {
        override fun accepts(message: GossipMessage) = message.payload.a <= maxSize
    }
    
    data class Composite(val filters: List<GossipFilter>, val all: Boolean = true) : GossipFilter() {
        override fun accepts(message: GossipMessage): Boolean {
            return if (all) {
                filters.all { it.accepts(message) }
            } else {
                filters.any { it.accepts(message) }
            }
        }
    }
}

/**
 * Message cache for duplicate detection and storm prevention
 */
private class MessageCache(
    private val maxSize: Int,
    private val ttlMillis: Long
) {
    private val cache = mutableMapOf<NUID, Long>()
    
    fun hasSeenMessage(messageId: NUID): Boolean {
        cleanExpired()
        return cache.containsKey(messageId)
    }
    
    fun markAsSeen(messageId: NUID) {
        cleanExpired()
        cache[messageId] = borg.trikeshed.reactor.getCurrentTimeMillis()
        
        // Evict oldest if over capacity
        if (cache.size > maxSize) {
            val oldest = cache.minByOrNull { it.value }
            oldest?.let { cache.remove(it.key) }
        }
    }
    
    private fun cleanExpired() {
        val now = borg.trikeshed.reactor.getCurrentTimeMillis()
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > ttlMillis) {
                iterator.remove()
            }
        }
    }
}