package moneyfan.core

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TradingCoreTest {
    
    @Test
    fun testPriceOperations() {
        val price1 = Price(100.0)
        val price2 = Price(50.0)
        
        val sum = price1 + price2
        assertEquals(Price(150.0), sum)
        
        val diff = price1 - price2
        assertEquals(Price(50.0), diff)
        
        val product = price1 * 2.0
        assertEquals(Price(200.0), product)
        
        assertTrue(price1 > price2)
    }
    
    @Test
    fun testVolumeOperations() {
        val vol1 = Volume(1000.0)
        val vol2 = Volume(500.0)
        
        val sum = vol1 + vol2
        assertEquals(Volume(1500.0), sum)
        
        val scaled = vol1 * 0.5
        assertEquals(Volume(500.0), scaled)
    }
    
    @Test
    fun testQuantityOperations() {
        val qty = Quantity(10.0)
        val price = Price(100.0)
        
        val totalValue = qty * price
        assertEquals(Price(1000.0), totalValue)
    }
    
    @Test
    fun testOHLCVCreation() {
        val open = Price(95.0)
        val high = Price(105.0)
        val low = Price(90.0)
        val close = Price(100.0)
        val volume = Volume(1000.0)
        
        val ohlcv = OHLCV(open, high, low, close, volume)
        
        assertEquals(open, ohlcv.open)
        assertEquals(high, ohlcv.high)
        assertEquals(low, ohlcv.low)
        assertEquals(close, ohlcv.close)
        assertEquals(volume, ohlcv.volume)
    }
    
    @Test
    fun testPortfolioManager() {
        val manager = PortfolioManager()
        val symbol = Symbol("AAPL")
        val quantity = Quantity(10.0)
        val price = Price(150.0)
        
        val success = manager.buyPosition(symbol, quantity, price)
        assertTrue(success)
        
        val currentPrices = mapOf(symbol to Price(160.0))
        val portfolioState = manager.getPortfolioState(currentPrices)
        
        assertTrue(portfolioState.positions.size > 0)
        assertTrue(portfolioState.totalValue.value > 0.0)
    }
}