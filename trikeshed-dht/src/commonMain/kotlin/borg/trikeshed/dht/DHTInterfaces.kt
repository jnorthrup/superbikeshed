@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.dht

import borg.trikeshed.lib.*
import borg.trikeshed.dht.kademlia.id.NUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext

/**
 * Shared interfaces for DHT-CCEK integration without circular dependencies.
 * These interfaces define the contract between DHT and CCEK modules.
 */

/**
 * Interface for Kademlia DHT node operations that CCEK can use.
 */
interface IKademliaNode : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<IKademliaNode>
    override val key: CoroutineContext.Key<IKademliaNode> get() = Key
    
    val nodeId: NUID
    
    /**
     * Join a subnet with specified criteria.
     */
    suspend fun joinSubnet(
        subnetId: String,
        type: String,
        criteria: Map<String, Any>
    ): Result<Any>
    
    /**
     * Leave a subnet.
     */
    suspend fun leaveSubnet(subnetId: String): Result<Unit>
    
    /**
     * Store a key-value pair in the DHT.
     */
    suspend fun store(key: ByteArray, value: ByteArray, replicationFactor: Int = 3): Result<List<NUID>>
    
    /**
     * Retrieve a value from the DHT.
     */
    suspend fun retrieve(key: ByteArray): Result<ByteArray?>
    
    /**
     * Flow of peer events.
     */
    val peerEvents: Flow<Any>
    
    /**
     * Flow of subnet events.
     */
    val subnetEvents: Flow<Any>
}

/**
 * Interface for metaverse agent operations.
 */
interface IMetaverseAgent : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<IMetaverseAgent>
    override val key: CoroutineContext.Key<IMetaverseAgent> get() = Key
    
    val agentId: String
    
    /**
     * Register as fiduciary agent.
     */
    suspend fun registerFiduciaryAgent(
        attentionCapacity: Int,
        trustCredentials: List<String>
    ): Result<Unit>
    
    /**
     * Monitor attention flow.
     */
    fun monitorAttentionFlow(): Flow<Any>
}

/**
 * Factory for creating DHT components.
 */
object DHTFactory {
    /**
     * Create a Kademlia node.
     */
    fun createKademliaNode(nodeId: NUID, context: CoroutineContext): IKademliaNode {
        // This would return the actual implementation
        // For now, return a stub
        return object : IKademliaNode {
            override val nodeId: NUID = nodeId
            override val key = IKademliaNode.Key
            
            override suspend fun joinSubnet(
                subnetId: String,
                type: String,
                criteria: Map<String, Any>
            ): Result<Any> = Result.success(Unit)
            
            override suspend fun leaveSubnet(subnetId: String): Result<Unit> = Result.success(Unit)
            
            override suspend fun store(key: ByteArray, value: ByteArray, replicationFactor: Int): Result<List<NUID>> = 
                Result.success(emptyList())
            
            override suspend fun retrieve(key: ByteArray): Result<ByteArray?> = Result.success(null)
            
            override val peerEvents: Flow<Any> = flow { }
            override val subnetEvents: Flow<Any> = flow { }
        }
    }
    
    /**
     * Create a metaverse agent.
     */
    fun createMetaverseAgent(kademliaNode: IKademliaNode, agentId: String): IMetaverseAgent {
        return object : IMetaverseAgent {
            override val agentId: String = agentId
            override val key = IMetaverseAgent.Key
            
            override suspend fun registerFiduciaryAgent(
                attentionCapacity: Int,
                trustCredentials: List<String>
            ): Result<Unit> = Result.success(Unit)
            
            override fun monitorAttentionFlow(): Flow<Any> = flow { }
        }
    }
} 