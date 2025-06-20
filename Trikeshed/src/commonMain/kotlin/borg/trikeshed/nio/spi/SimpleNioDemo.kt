package borg.trikeshed.nio.spi

/**
 * Simple demonstration of the SPI system for NIO operations
 */
object SimpleNioDemo {
    
    fun demonstrateSpiSystem() {
        println("=== SPI NIO System Demonstration ===")
        
        // Register the JVM provider
        val provider = JvmNioProvider()
        ServiceRegistry.register("jvm", provider)
        
        println("Registered providers: ${ServiceRegistry.listProviders()}")
        
        // Demonstrate buffer creation with attention monitoring
        println("\n--- Buffer Creation Demo ---")
        val buffer = provider.createBuffer(1024)
        println("Created buffer with capacity: ${buffer.limit()}")
        
        // Demonstrate buffer wrapping with attention monitoring
        println("\n--- Buffer Wrapping Demo ---")
        val data = ByteArray(100) { it.toByte() }
        val wrappedBuffer = provider.wrapBuffer(data, 10, 50)
        println("Wrapped buffer with remaining: ${wrappedBuffer.remaining()}")
        
        // Demonstrate channel creation with attention monitoring
        println("\n--- Channel Creation Demo ---")
        val channel = provider.createChannel()
        println("Created channel, isOpen: ${channel.isOpen}")
        channel.close()
        
        // Demonstrate attention delegate
        println("\n--- Attention Delegate Demo ---")
        val attentionDelegate = provider.getAttentionDelegate()
        attentionDelegate.onIoOperation("demo_read", 1024, 500_000) // 0.5ms
        attentionDelegate.onIoOperation("demo_write", 2048, 2_000_000) // 2ms (slow)
        
        println("\n=== Demo Complete ===")
    }
} 