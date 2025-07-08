@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.channel.api

/**
 * SPI (Service Provider Interface) for channel implementations.
 * Allows pluggable channel backends without tight coupling.
 */
interface ChannelProvider {
    /**
     * Unique name for this channel provider.
     */
    val name: String
    
    /**
     * Channel types supported by this provider.
     */
    val supportedTypes: Set<ChannelType>
    
    /**
     * Priority for provider selection (higher = preferred).
     */
    val priority: Int get() = 0
    
    /**
     * Check if this provider can handle the given configuration.
     */
    fun canHandle(config: ChannelConfig): Boolean
    
    /**
     * Create a new channel instance with the given configuration.
     */
    suspend fun createChannel(config: ChannelConfig): Channel
    
    /**
     * Create a channel connected to the specified address.
     */
    suspend fun createConnectedChannel(
        config: ChannelConfig,
        address: ChannelAddress
    ): ConnectedChannel
    
    /**
     * Create a server channel that can accept connections.
     */
    suspend fun createServerChannel(
        config: ChannelConfig,
        bindAddress: ChannelAddress
    ): ServerChannel
}

/**
 * Server channel that accepts incoming connections.
 */
interface ServerChannel : Channel {
    val bindAddress: ChannelAddress
    
    /**
     * Accept an incoming connection.
     * Returns null if no connection is available.
     */
    suspend fun accept(): ConnectedChannel?
    
    /**
     * Start listening for connections.
     */
    suspend fun bind()
    
    /**
     * Stop accepting new connections.
     */
    suspend fun unbind()
}

/**
 * Factory for creating channels through registered providers.
 */
object ChannelFactory {
    internal val providers = mutableListOf<ChannelProvider>()
    
    /**
     * Register a channel provider.
     */
    fun register(provider: ChannelProvider) {
        providers.add(provider)
        providers.sortByDescending { it.priority }
    }
    
    /**
     * Unregister a channel provider.
     */
    fun unregister(provider: ChannelProvider) {
        providers.remove(provider)
    }
    
    /**
     * Get all registered providers.
     */
    fun getProviders(): List<ChannelProvider> = providers.toList()
    
    /**
     * Find a provider that can handle the given configuration.
     */
    fun findProvider(config: ChannelConfig): ChannelProvider? {
        return providers.firstOrNull { it.canHandle(config) }
    }
    
    /**
     * Create a channel using the best available provider.
     */
    suspend fun createChannel(config: ChannelConfig): Channel {
        val provider = findProvider(config)
            ?: throw ChannelException.ConfigurationError("No provider found for config: $config")
        return provider.createChannel(config)
    }
    
    /**
     * Create a connected channel using the best available provider.
     */
    suspend fun createConnectedChannel(
        config: ChannelConfig,
        address: ChannelAddress
    ): ConnectedChannel {
        val provider = findProvider(config)
            ?: throw ChannelException.ConfigurationError("No provider found for config: $config")
        return provider.createConnectedChannel(config, address)
    }
    
    /**
     * Create a server channel using the best available provider.
     */
    suspend fun createServerChannel(
        config: ChannelConfig,
        bindAddress: ChannelAddress
    ): ServerChannel {
        val provider = findProvider(config)
            ?: throw ChannelException.ConfigurationError("No provider found for config: $config")
        return provider.createServerChannel(config, bindAddress)
    }
}