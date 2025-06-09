package com.ta4k.trading

import com.ta4k.trading.entities.Order
import com.ta4k.trading.entities.OrderType
import com.ta4k.trading.entities.OrderStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal

class TradingRecordTest {

    @Test
    fun `open and close a BUY position results in one trade with correct P&L`() {
        val record = TradingRecord()
        val entryOrder = Order(0, OrderType.BUY, BigDecimal("100.00"), BigDecimal("1.0"), status = OrderStatus.PENDING)
        entryOrder.filledPrice = BigDecimal("100.50") // Simulate fill
        entryOrder.filledAmount = BigDecimal("1.0")
        entryOrder.status = OrderStatus.FILLED

        val openSuccess = record.openPosition(entryOrder, 0, BigDecimal("100.50"))
        assertTrue(openSuccess)
        assertNotNull(record.getOpenPosition())
        assertEquals(BigDecimal("1.0"), record.getOpenPosition()?.amount)

        val exitOrder = Order(1, OrderType.SELL, BigDecimal("110.00"), BigDecimal("1.0"), status = OrderStatus.PENDING)
        exitOrder.filledPrice = BigDecimal("110.50") // Simulate fill
        exitOrder.filledAmount = BigDecimal("1.0")
        exitOrder.status = OrderStatus.FILLED

        val trade = record.closePosition(exitOrder)
        assertNotNull(trade)
        assertNull(record.getOpenPosition())
        assertEquals(1, record.getTrades().size)
        // P&L = (110.50 - 100.50) * 1.0 = 10.00
        assertEquals(BigDecimal("10.00").setScale(2), trade?.profitLoss)
        assertEquals(BigDecimal("10.00").setScale(2), record.getTotalProfitLoss())
    }

    @Test
    fun `open and close a SELL position (short) results in one trade with correct P&L`() {
        val record = TradingRecord()
        val entryOrder = Order(0, OrderType.SELL, BigDecimal("100.00"), BigDecimal("1.0"))
        entryOrder.filledPrice = BigDecimal("99.50")
        entryOrder.filledAmount = BigDecimal("1.0")
        entryOrder.status = OrderStatus.FILLED

        record.openPosition(entryOrder, 0, BigDecimal("99.50"))
        assertNotNull(record.getOpenPosition())

        val exitOrder = Order(1, OrderType.BUY, BigDecimal("90.00"), BigDecimal("1.0"))
        exitOrder.filledPrice = BigDecimal("90.50")
        exitOrder.filledAmount = BigDecimal("1.0")
        exitOrder.status = OrderStatus.FILLED

        val trade = record.closePosition(exitOrder)
        assertNotNull(trade)
        assertNull(record.getOpenPosition())
        // P&L = (99.50 - 90.50) * 1.0 = 9.00
        assertEquals(BigDecimal("9.00").setScale(2), trade?.profitLoss)
        assertEquals(BigDecimal("9.00").setScale(2), record.getTotalProfitLoss())
    }

    @Test
    fun `cannot open position if one is already open`() {
        val record = TradingRecord()
        val order1 = Order(0, OrderType.BUY, BigDecimal("100"), BigDecimal("1"))
        order1.filledPrice = BigDecimal("100"); order1.filledAmount = BigDecimal("1"); order1.status = OrderStatus.FILLED
        record.openPosition(order1, 0, BigDecimal("100"))

        val order2 = Order(1, OrderType.BUY, BigDecimal("102"), BigDecimal("1"))
        order2.filledPrice = BigDecimal("102"); order2.filledAmount = BigDecimal("1"); order2.status = OrderStatus.FILLED
        val openSuccess = record.openPosition(order2, 1, BigDecimal("102"))
        assertFalse(openSuccess)
        assertEquals(0, record.getOpenPosition()?.entryOrder?.klineIndex)
        assertEquals(order1.id, record.getOpenPosition()?.entryOrder?.id)
    }

    @Test
    fun `cannot close position if none is open`() {
        val record = TradingRecord()
        val exitOrder = Order(1, OrderType.SELL, BigDecimal("100"), BigDecimal("1"))
        exitOrder.filledPrice = BigDecimal("100"); exitOrder.filledAmount = BigDecimal("1"); exitOrder.status = OrderStatus.FILLED
        val trade = record.closePosition(exitOrder)
        assertNull(trade)
    }

    @Test
    fun `updateOpenPositionMarketPrice updates unrealized P&L`() {
        val record = TradingRecord()
        val entryOrder = Order(0, OrderType.BUY, BigDecimal("100"), BigDecimal("1.0"))
        entryOrder.filledPrice = BigDecimal("100.00"); entryOrder.filledAmount = BigDecimal("1.0"); entryOrder.status = OrderStatus.FILLED
        record.openPosition(entryOrder, 0, BigDecimal("100.00"))

        assertEquals(BigDecimal("0.00"), record.getOpenPosition()?.unrealizedProfitLoss?.setScale(2))

        record.updateOpenPositionMarketPrice(1, BigDecimal("105.00"))
        assertEquals(BigDecimal("5.00"), record.getOpenPosition()?.unrealizedProfitLoss?.setScale(2))

        record.updateOpenPositionMarketPrice(2, BigDecimal("98.00"))
        assertEquals(BigDecimal("-2.00"), record.getOpenPosition()?.unrealizedProfitLoss?.setScale(2))
    }
}
