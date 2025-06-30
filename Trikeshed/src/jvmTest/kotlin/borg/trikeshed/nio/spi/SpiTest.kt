package borg.trikeshed.nio.spi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SpiTest {
    
    @Test
    fun testServiceRegistry() {
        val provider = JvmNioProvider()
        ServiceRegistry.register("jvm", provider)
        
        assertEquals(setOf("jvm"), ServiceRegistry.listProviders())
        assertNotNull(ServiceRegistry.getProvider("jvm"))
        assertEquals(provider, ServiceRegistry.getDefaultProvider())
        
        ServiceRegistry.clear()
        assertTrue(ServiceRegistry.listProviders().isEmpty())
    }
    
    @Test
    fun testBufferCreation() {
        val provider = JvmNioProvider()
        val buffer = provider.createBuffer(1024)
        
        assertEquals(1024, buffer.limit())
        assertEquals(0, buffer.position())
        assertEquals(1024, buffer.remaining())
    }
    
    @Test
    fun testBufferWrapping() {
        val provider = JvmNioProvider()
        val data = ByteArray(100) { it.toByte() }
        val buffer = provider.wrapBuffer(data, 10, 50)
        
        assertEquals(50, buffer.remaining())
        assertEquals(10, buffer.position())
        assertEquals(60, buffer.limit())
    }
    
    @Test
    fun testChannelCreation() {
        val provider = JvmNioProvider()
        val channel = provider.createChannel()
        
        assertTrue(channel.isOpen)
        channel.close()
    }
} 