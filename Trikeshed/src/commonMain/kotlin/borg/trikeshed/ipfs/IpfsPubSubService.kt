@file:OptIn(ExperimentalUnsignedTypes::class)
package borg.trikeshed.ipfs


import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.*

/**
 * Minimal placeholder IPFS PubSub service for compilation
 */
class IpfsPubSubService {
    fun publish(topic: String, message: String) {
        // Placeholder implementation
    }
    
    fun subscribe(topic: String): kotlinx.coroutines.flow.Flow<String> {
        // Placeholder implementation
        return kotlinx.coroutines.flow.flowOf()
    }
} 