package borg.trikeshed.acapulco.rl

import borg.trikeshed.cursor.*
import borg.trikeshed.lib.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BinanceRlAdapterTest {
    @Test
    fun `should fail with meaningful message when dependencies missing`() {
        assertFailsWith<IllegalStateException> {
            BinanceRlAdapter()
        }
    }

    @Test
    fun `should support basic cursor operations`() {
        // TODO: Mock Binance API client
        // val adapter = BinanceRlAdapter(mockClient)
        
        // Verify cursor contract
        // assertEquals(expected, adapter.reset())
        // assertEquals(expected, adapter.current())
        // assertEquals(expected, adapter.next())
    }

    @Test
    fun `should properly initialize cursor operations`() {
        // Create mock Binance client
        val mockClient = TestUtils.relaxedMock<BinanceApiClient>()
        
        // Setup test data
        val testData = listOf(
            mapOf("price" to "100.0", "time" to 123456L),
            mapOf("price" to "101.0", "time" to 123457L)
        )
        
        // Configure mock
        wheneverBlocking { mockClient.getKlines(any()) }.thenReturn(testData)
        
        // Create adapter with mock
        val adapter = BinanceRlAdapter(mockClient)
        
        // Test cursor operations
        adapter.reset()
        assertEquals(100.0, adapter.current()["price"])
        adapter.next()
        assertEquals(101.0, adapter.current()["price"])
    }
}