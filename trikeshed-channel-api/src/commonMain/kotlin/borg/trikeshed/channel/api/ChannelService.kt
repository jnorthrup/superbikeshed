@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.channel.api

import kotlin.coroutines.CoroutineContext

/**
 * ChannelService for managing channel operations within coroutine contexts
 */
class ChannelService(
    val provider: ChannelProvider
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<ChannelService>
    override val key = Key
    
    suspend fun createServer(config: ChannelConfig, bindAddress: ChannelAddress): ServerChannel {
        return provider.createServerChannel(config, bindAddress)
    }
    
    suspend fun connect(config: ChannelConfig, address: ChannelAddress): ConnectedChannel {
        return provider.createConnectedChannel(config, address)
    }
    
    suspend fun create(config: ChannelConfig): Channel {
        return provider.createChannel(config)
    }
}

/**
 * MemoryChannelProvider implementation
 */
class MemoryChannelProvider : ChannelProvider {
    override val name: String = "memory"
    override val supportedTypes: Set<ChannelType> = setOf(ChannelType.MEMORY)
    override val priority: Int = 100
    
    override fun canHandle(config: ChannelConfig): Boolean {
        return config.type == ChannelType.MEMORY
    }
    
    override suspend fun createChannel(config: ChannelConfig): Channel {
        TODO("Memory channel implementation")
    }
    
    override suspend fun createConnectedChannel(
        config: ChannelConfig,
        address: ChannelAddress
    ): ConnectedChannel {
        TODO("Memory connected channel implementation")
    }
    
    override suspend fun createServerChannel(
        config: ChannelConfig,
        bindAddress: ChannelAddress
    ): ServerChannel {
        TODO("Memory server channel implementation")
    }
}