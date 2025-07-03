package com.ta4k.backtesting

import com.ta4k.core.model.Kline
import borg.trikeshed.lib.Indexed     // Corrected import
import com.ta4k.trikeshedutils.toSeries // Planned new location
import borg.trikeshed.lib.get         // Corrected import (though might not be used directly)
import com.ta4k.strategy.examples.SMACrossoverStrategy
import com.ta4k.trading.entities.OrderType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal

class BacktesterTest {

    // Adjusted kline helper to ensure unique idx for time if multiple klines are created in a list manually
    private fun kline(close: String, idx: Int, high: String = close, low: String = close, open: String = close) = Kline(
        idx.toLong() * 60000, // Timestamps are unique and sequential for default list data
        BigDecimal(open),
        BigDecimal(high),
        BigDecimal(low),
        BigDecimal(close),
        BigDecimal("100"), // Dummy volume
        idx.toLong() * 60000 + 59999,
        BigDecimal("1000"), // Dummy quote volume
        10, // Dummy trades
        BigDecimal("50"), // Dummy taker buy base
        BigDecimal("500") // Dummy taker buy quote
    )

    @Test
    fun `backtest SMACrossoverStrategy with simple data`() {
        // Prices: (idx, close)
        // 0: 10
        // 1: 8
        // 2: 11  -> BUY signal (SMA1(11) > SMA2(9.5), prev SMA1(8) < SMA2(9)). Executed @ 11. Pos: BUY@11, Amt: 1
        // 3: 9   -> SELL signal (SMA1(9) < SMA2(10), prev SMA1(11) > SMA2(9.5)). Executed @ 9. P&L = (9-11)*1 = -2. Pos: None
        // 4: 12  -> BUY signal (SMA1(12) > SMA2(10.5), prev SMA1(9) < SMA2(10)). Executed @ 12. Pos: BUY@12, Amt: 1
        // 5: 10  -> SELL signal (SMA1(10) < SMA2(11), prev SMA1(12) > SMA2(10.5)). Executed @ 10. P&L = (10-12)*1 = -2. Pos: None

        val klinesData = listOf(
            kline("10", 0), kline("8", 1), kline("11", 2),
            kline("9", 3), kline("12", 4), kline("10", 5)
        ).toSeries()

        val strategy = SMACrossoverStrategy(klinesData, 1, 2, BigDecimal("1.0")) // Short=1, Long=2
        val backtester = Backtester(klinesData, strategy)
        val tradingRecord = backtester.run()

        val trades = tradingRecord.getTrades()
        assertEquals(2, trades.size)
        assertNull(tradingRecord.getOpenPosition())

        // Trade 1: Buy @ 11 (idx 2), Sell @ 9 (idx 3)
        assertEquals(OrderType.BUY, trades[0].entryOrder.type)
        assertEquals(BigDecimal("11"), trades[0].entryOrder.filledPrice)
        assertEquals(2, trades[0].entryOrder.klineIndex) // Signal at index 2
        assertEquals(OrderType.SELL, trades[0].exitOrder.type)
        assertEquals(BigDecimal("9"), trades[0].exitOrder.filledPrice)
        assertEquals(3, trades[0].exitOrder.klineIndex) // Signal at index 3
        assertEquals(BigDecimal("-2.00").setScale(2), trades[0].profitLoss)

        // Trade 2: Buy @ 12 (idx 4), Sell @ 10 (idx 5)
        assertEquals(OrderType.BUY, trades[1].entryOrder.type)
        assertEquals(BigDecimal("12"), trades[1].entryOrder.filledPrice)
        assertEquals(4, trades[1].entryOrder.klineIndex) // Signal at index 4
        assertEquals(OrderType.SELL, trades[1].exitOrder.type)
        assertEquals(BigDecimal("10"), trades[1].exitOrder.filledPrice)
        assertEquals(5, trades[1].exitOrder.klineIndex) // Signal at index 5
        assertEquals(BigDecimal("-2.00").setScale(2), trades[1].profitLoss)

        assertEquals(BigDecimal("-4.00").setScale(2), tradingRecord.getTotalProfitLoss())
    }

    @Test
    fun `backtest with no trades generated due to no crossover`() {
        // Prices always going up, SMA(1) always > SMA(2) after warmup
        // Idx | Close | SMA1 | SMA2
        // 0   | 10    | 10   | -
        // 1   | 11    | 11   | 10.5
        // 2   | 12    | 12   | 11.5 (Warmup complete for strat, S > L)
        // 3   | 13    | 13   | 12.5 (S > L)
        // 4   | 14    | 14   | 13.5 (S > L)
        val klinesData = listOf(
            kline("10",0), kline("11",1), kline("12",2), kline("13",3), kline("14",4)
        ).toSeries()
        val strategy = SMACrossoverStrategy(klinesData, 1, 2) // Warmup period = 2
        val backtester = Backtester(klinesData, strategy)
        val tradingRecord = backtester.run()

        assertEquals(0, tradingRecord.getTrades().size)
        assertNull(tradingRecord.getOpenPosition())
        assertEquals(BigDecimal("0.00").setScale(2), tradingRecord.getTotalProfitLoss())
    }

    @Test
    fun `backtest with position open at the end`(){
        // Prices: 10, 8, 11 (BUY), 13 (Hold), 14 (Hold)
        // Idx | Close | SMA1 | SMA2   | Signal | Position
        // 0   | 10    | 10   | -      | -      | -
        // 1   | 8     | 8    | 9      | -      | -
        // 2   | 11    | 11   | 9.5    | BUY    | BUY@11
        // 3   | 13    | 13   | 12     | -      | BUY@11
        // 4   | 14    | 14   | 13.5   | -      | BUY@11 (ends here)
         val klinesData = listOf(
            kline("10",0), kline("8",1), kline("11",2), kline("13",3), kline("14",4)
        ).toSeries()
        val strategy = SMACrossoverStrategy(klinesData, 1, 2)
        val backtester = Backtester(klinesData, strategy)
        val tradingRecord = backtester.run()

        assertEquals(0, tradingRecord.getTrades().size, "No trades should be closed")
        assertNotNull(tradingRecord.getOpenPosition(), "Position should be open at the end")
        assertEquals(OrderType.BUY, tradingRecord.getOpenPosition()?.type)
        assertEquals(BigDecimal("11"), tradingRecord.getOpenPosition()?.entryPrice)
        assertEquals(BigDecimal("1.0"), tradingRecord.getOpenPosition()?.amount)

        // Check unrealized P&L: (currentPrice - entryPrice) * amount
        // currentPrice is close of last kline (14)
        // (14 - 11) * 1.0 = 3.0
        assertEquals(BigDecimal("3.00").setScale(2), tradingRecord.getOpenPosition()?.unrealizedProfitLoss?.setScale(2))
        assertEquals(BigDecimal("0.00").setScale(2), tradingRecord.getTotalProfitLoss(), "Total realized P&L should be 0")
    }
}
