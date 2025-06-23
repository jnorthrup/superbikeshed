package borg.trikeshed.lib

import kotlin.test.*

class FinancialSeriesTest {
    @Test
    fun testTickSeries() {
        val ticks = 2 j { Tick("SYM", 100.0 + it, 10L + it, kotlinx.datetime.Clock.System.now()) }
        assertEquals(2, ticks.a)
        assertEquals("SYM", ticks.b(0).symbol)
        assertEquals(101.0, ticks.b(1).price)
    }

    @Test
    fun testOHLCVSeries() {
        val candles = 2 j { OHLCV("SYM", 1.0, 2.0, 0.5, 1.5, 100L, kotlinx.datetime.Clock.System.now()) }
        assertEquals(2, candles.a)
        assertEquals("SYM", candles.b(0).symbol)
        assertEquals(1.5, candles.b(1).close)
    }

    @Test
    fun testBookSeries() {
        val books = 2 j { BookEntry(10.0 + it, 100L + it, BookEntry.Side.BID) }
        assertEquals(2, books.a)
        assertEquals(10.0, books.b(0).price)
        assertEquals(BookEntry.Side.BID, books.b(1).side)
    }

    @Test
    fun testEmptySeries() {
        val emptyTicks: TickSeries = 0 j { error("nope") }
        assertEquals(0, emptyTicks.a)
        val emptyCandles: OHLCVSeries = 0 j { error("nope") }
        assertEquals(0, emptyCandles.a)
        val emptyBooks: BookSeries = 0 j { error("nope") }
        assertEquals(0, emptyBooks.a)
    }
} 