package nexus.providers

import nexus.implementations.*
import kotlin.test.*

class ProviderTest {
    
    @Test
    fun `mock provider works`() {
        val provider = MockProvider()
        val context = buildInitialCCEKContext()
        
        runBlocking {
            val response = provider.generate("create a function", context)
            assertTrue(response.contains("Generated response"))
            assertTrue(response.contains("create a function"))
        }
    }
    
    @Test
    fun `provider factory creates from environment`() {
        val provider = ProviderFactory.fromEnvironment()
        
        // Should default to MockProvider when no API keys set
        assertEquals("mock", provider.name)
    }
    
    @Test
    fun `provider factory creates specific types`() {
        val mockConfig = ProviderConfig(ProviderType.MOCK)
        val provider = ProviderFactory.createProvider(mockConfig)
        
        assertEquals("mock", provider.name)
    }
    
    @Test
    fun `enhanced nexus works with provider`() {
        val nexus = buildWorkingNexus()
        val provider = MockProvider()
        val enhanced = nexus.withProvider(provider)
        
        val response = enhanced.generate("test prompt")
        
        assertTrue(response.contains("Generated response"))
    }
}

fun buildWorkingNexus(): CCEKNexus {
    // Create minimal working CCEKNexus for testing
    val context = buildInitialCCEKContext()
    return context.initializeNexus()
}

// Add missing runBlocking for common tests
import kotlinx.coroutines.runBlocking