package borg.trikeshed.ipfs
@file:OptIn(ExperimentalUnsignedTypes::class)


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