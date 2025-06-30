package borg.trikeshed.nio.spi

import borg.trikeshed.lib.*

/**
 * Central registry for managing NIO service providers
 * Provides dynamic service discovery and plugin architecture
 */
object ServiceRegistry {
    private val providers = mutableMapOf<String, NioServiceProvider>()
    private var defaultProvider: NioServiceProvider? = null
    
    /**
     * Register a NIO service provider for a platform
     */
    fun register(platformId: String, provider: NioServiceProvider) {
        providers[platformId] = provider
        if (defaultProvider == null) {
            defaultProvider = provider
        }
    }
    
    /**
     * Get provider for specific platform
     */
    fun getProvider(platformId: String): NioServiceProvider? {
        return providers[platformId]
    }
    
    /**
     * Get the default provider (usually the first registered)
     */
    fun getDefaultProvider(): NioServiceProvider {
        return defaultProvider ?: throw IllegalStateException("No NIO service providers registered")
    }
    
    /**
     * Get all registered providers
     */
    fun getAllProviders(): Map<String, NioServiceProvider> {
        return providers.toMap()
    }
    
    /**
     * Check if a provider is registered for platform
     */
    fun hasProvider(platformId: String): Boolean {
        return providers.containsKey(platformId)
    }
    
    /**
     * Remove a provider
     */
    fun unregister(platformId: String): NioServiceProvider? {
        val removed = providers.remove(platformId)
        if (removed == defaultProvider) {
            defaultProvider = providers.values.firstOrNull()
        }
        return removed
    }
    
    /**
     * Clear all providers
     */
    fun clear() {
        providers.clear()
        defaultProvider = null
    }
    
    /**
     * List all provider IDs
     */
    fun listProviders(): Set<String> = providers.keys.toSet()
    
    /**
     * Auto-discover and register platform-specific providers
     */
    fun autoRegister() {
        // This would be implemented per platform to auto-detect available providers
        // For now, platforms must manually register their providers
    }
}