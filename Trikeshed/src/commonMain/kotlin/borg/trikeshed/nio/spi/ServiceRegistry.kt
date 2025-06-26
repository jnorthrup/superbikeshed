package borg.trikeshed.nio.spi

/**
 * Service registry for NIO service providers
 */
object ServiceRegistry {
    private val providers = mutableMapOf<String, NioServiceProvider>()
    
    fun register(name: String, provider: NioServiceProvider) {
        providers[name] = provider
    }
    
    fun getProvider(name: String): NioServiceProvider? = providers[name]
    
    fun getDefaultProvider(): NioServiceProvider = providers.values.firstOrNull() 
        ?: throw IllegalStateException("No NIO provider registered")
        
    fun listProviders(): Set<String> = providers.keys.toSet()
    
    fun clear() {
        providers.clear()
    }
} 