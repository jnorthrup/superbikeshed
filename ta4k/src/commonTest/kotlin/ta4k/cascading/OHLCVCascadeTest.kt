package ta4k.cascading

import borg.trikeshed.cursor.*
import borg.trikeshed.lib.*
import kotlin.test.*
import kotlinx.datetime.*
import kotlin.random.Random

/**
 * Test the cascading OHLCV magic
 */
class OHLCVCascadeTest {
    
    @Test
    fun `test tick to minute OHLCV aggregation`() {
        // Generate some test ticks
        val baseTime = Clock.System.now()
        val ticks = (0..119).map { i -> // 2 minutes of second ticks
            Tick(
                symbol = "BTC/USD",
                timestamp = baseTime.plus(i, DateTimeUnit.SECOND),
                price = 30000.0 + i * 10 + Random.nextDouble(-5.0, 5.0),
                volume = Random.nextLong(100, 1000),
                bid = 29999.0 + i * 10,
                ask = 30001.0 + i * 10
            )
        }
        
        // Convert to cursor
        val tickCursor = OHLCVCascade.ticksToCursor(ticks)
        
        // Aggregate to minute bars
        val minuteBars = tickCursor.toOHLCV(Timeframe.MINUTE)
        
        // Should have 2 minute bars
        assertEquals(2, minuteBars.a)
        
        // Check first minute bar
        val firstBar = minuteBars.at(0)
        val open = firstBar.getDouble(2)!!
        val high = firstBar.getDouble(3)!!
        val low = firstBar.getDouble(4)!!
        val close = firstBar.getDouble(5)!!
        
        println("First minute bar: O=$open H=$high L=$low C=$close")
        
        // Verify OHLC relationships
        assertTrue(high >= open)
        assertTrue(high >= close)
        assertTrue(low <= open)
        assertTrue(low <= close)
        assertTrue(high >= low)
    }
    
    @Test
    fun `test cascading minute to hour aggregation`() {
        val baseTime = Clock.System.now()
        
        // Create minute bars
        val minuteBars = (0..119).map { i -> // 2 hours of minute bars
            listOf(
                "BTC/USD",
                baseTime.plus(i * 60, DateTimeUnit.SECOND).toEpochMilliseconds(),
                30000.0 + i * 5.0,  // open
                30000.0 + i * 5.0 + 10.0,  // high
                30000.0 + i * 5.0 - 5.0,   // low
                30000.0 + i * 5.0 + 5.0,   // close
                1000L * i,  // volume
                30000.0 + i * 5.0 + 2.5,  // vwap
                60,  // tick count
                "MINUTE"
            )
        }
        
        val minuteCursor = cursorOf(
            minuteBars,
            listOf("symbol", "timestamp", "open", "high", "low", "close", "volume", "vwap", "tick_count", "timeframe"),
            listOf(
                IOMemento.IoString,
                IOMemento.IoLong,
                IOMemento.IoDouble,
                IOMemento.IoDouble,
                IOMemento.IoDouble,
                IOMemento.IoDouble,
                IOMemento.IoLong,
                IOMemento.IoDouble,
                IOMemento.IoInt,
                IOMemento.IoString
            )
        )
        
        // Cascade to hour bars
        val hourBars = minuteCursor.cascadeOHLCV(Timeframe.MINUTE, Timeframe.HOUR)
        
        // Should have 2 hour bars
        assertEquals(2, hourBars.a)
        
        // Verify cascading preserved OHLCV integrity
        val firstHour = hourBars.at(0)
        val firstMinute = minuteCursor.at(0)
        val lastMinuteInFirstHour = minuteCursor.at(59)
        
        // Open should be first minute's open
        assertEquals(firstMinute.getDouble(2), firstHour.getDouble(2))
        
        // Close should be last minute's close
        assertEquals(lastMinuteInFirstHour.getDouble(5), firstHour.getDouble(5))
        
        // Volume should be sum
        val expectedVolume = (0..59).sumOf { minuteCursor.at(it).getLong(6)!! }
        assertEquals(expectedVolume, firstHour.getLong(6))
        
        println("Cascading preserved OHLCV integrity!")
    }
    
    @Test
    fun `test technical indicators on OHLCV`() {
        // Create OHLCV data with trend
        val baseTime = Clock.System.now()
        val ohlcvData = (0..99).map { i ->
            val trend = i * 10.0  // Upward trend
            val noise = Random.nextDouble(-5.0, 5.0)
            val price = 30000.0 + trend + noise
            
            listOf(
                "BTC/USD",
                baseTime.plus(i * 60, DateTimeUnit.SECOND).toEpochMilliseconds(),
                price - 2.0,  // open
                price + 5.0,  // high
                price - 5.0,  // low
                price,        // close
                1000L,        // volume
                price,        // vwap
                60,           // tick count
                "MINUTE"
            )
        }
        
        val ohlcvCursor = cursorOf(
            ohlcvData,
            listOf("symbol", "timestamp", "open", "high", "low", "close", "volume", "vwap", "tick_count", "timeframe"),
            listOf(
                IOMemento.IoString,
                IOMemento.IoLong,
                IOMemento.IoDouble,
                IOMemento.IoDouble,
                IOMemento.IoDouble,
                IOMemento.IoDouble,
                IOMemento.IoLong,
                IOMemento.IoDouble,
                IOMemento.IoInt,
                IOMemento.IoString
            )
        )
        
        // Add indicators
        val withIndicators = ohlcvCursor.withIndicators()
        
        // Check SMA calculation
        val sma20Col = withIndicators.columnNames.toList().indexOf("sma20")
        val lastRow = withIndicators.at(99)
        val sma20 = lastRow.getDouble(sma20Col)
        
        assertNotNull(sma20)
        println("Last SMA(20): $sma20")
        
        // SMA should be less than current price in uptrend
        assertTrue(sma20 < lastRow.getDouble(5)!!)  // close price
    }
    
    @Test
    fun `demonstrate CouchDB view equivalence`() {
        println("\n=== CouchDB View vs Cursor Transformation ===")
        
        val ticks = generateTestTicks(1000)
        val cursor = OHLCVCascade.ticksToCursor(ticks)
        
        // CouchDB would emit keys like:
        // ["BTC/USD", 2024, 1, 15, 14, 30, 45]
        
        // Our cursor equivalent:
        val minuteBars = cursor.toOHLCV(Timeframe.MINUTE, "BTC/USD")
        
        println("Generated ${minuteBars.a} minute bars from ${ticks.size} ticks")
        
        // The beauty: This is exactly what CouchDB's reduce does,
        // but with type safety and cursor composability!
        
        // Cascade up timeframes just like CouchDB re-reduce
        val hourBars = minuteBars.cascadeOHLCV(Timeframe.MINUTE, Timeframe.HOUR)
        val dayBars = hourBars.cascadeOHLCV(Timeframe.HOUR, Timeframe.DAY)
        
        println("Cascaded to ${hourBars.a} hour bars and ${dayBars.a} day bars")
        
        // And we can query any timeframe efficiently!
        assertTrue(dayBars.a <= hourBars.a)
        assertTrue(hourBars.a <= minuteBars.a)
    }
    
    internal fun generateTestTicks(count: Int): List<Tick> {
        val baseTime = Clock.System.now()
        val random = Random(42)
        
        return (0 until count).map { i ->
            Tick(
                symbol = if (i % 2 == 0) "BTC/USD" else "ETH/USD",
                timestamp = baseTime.plus(i * 100, DateTimeUnit.MILLISECOND),
                price = 30000.0 + random.nextDouble(-100.0, 100.0),
                volume = random.nextLong(100, 1000)
            )
        }
    }
}