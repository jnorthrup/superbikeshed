package borg.trikeshed.brokeshed

import borg.trikeshed.lib.*
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlinx.datetime.DISTANT_PAST
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual

/**
 * BrokeShed - Financial Brokerage and Market Data Processing
 * 
 * A high-performance financial data processing system built on TrikeShed's
 * Indexed type system for maximum performance and type safety.
 */

// === Core Financial Types ===

typealias Symbol = String
typealias Timestamp = Instant

// === Market Data Structures using Indexed ===

/**
 * Tick data - individual price/volume events
 */
@Serializable
data class Tick(
    val symbol: Symbol,
    val price: Double,
    val volume: Long,
    val timestamp: Timestamp,
    val side: Side = Side.UNKNOWN
) {
    enum class Side { BUY, SELL, UNKNOWN }
}

/**
 * OHLCV candlestick data
 */
@Serializable
data class OHLCV(
    val symbol: Symbol,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val timestamp: Timestamp,
    val interval: String = "1m"
)

/**
 * Order book entry
 */
@Serializable
data class BookEntry(
    val price: Double,
    val volume: Long,
    val side: Side
) {
    enum class Side { BID, ASK }
}

// === TrikeShed-based Market Data Collections ===

typealias TickSeries = Indexed<Tick>
typealias OHLCVSeries = Indexed<OHLCV>
typealias BookSeries = Indexed<BookEntry>

/**
 * Market data container using TrikeShed's Indexed type system
 */
@Serializable
data class MarketData(
    val symbol: Symbol,
    val ticks: TickSeries,
    val candles: OHLCVSeries,
    val orderBook: BookSeries,
    val lastUpdated: Timestamp
)

// === Trading Position and Portfolio ===

@Serializable
data class Position(
    val symbol: Symbol,
    val quantity: Long,
    val averagePrice: Double,
    val unrealizedPnL: Double = 0.0,
    val realizedPnL: Double = 0.0
)

@Serializable
data class Order(
    val id: String,
    val symbol: Symbol,
    val side: OrderSide,
    val type: OrderType,
    val quantity: Long,
    val price: Double? = null,
    val status: OrderStatus = OrderStatus.PENDING,
    val timestamp: Timestamp
) {
    enum class OrderSide { BUY, SELL }
    enum class OrderType { MARKET, LIMIT, STOP, STOP_LIMIT }
    enum class OrderStatus { PENDING, FILLED, CANCELLED, REJECTED }
}

typealias PositionSeries = Indexed<Position>
typealias OrderSeries = Indexed<Order>

/**
 * Portfolio containing positions and orders
 */
@Serializable
data class Portfolio(
    val accountId: String,
    val positions: PositionSeries,
    val orders: OrderSeries,
    val cash: Double,
    val totalValue: Double,
    val lastUpdated: Timestamp
)

// === BrokeShed Core Engine ===

/**
 * Main BrokeShed engine for financial data processing
 */
object BrokeShed {
    
    /**
     * Create market data from tick stream
     */
    fun createMarketData(symbol: Symbol, ticks: List<Tick>): MarketData {
        val tickSeries = ticks.toIdx()
        val candles = ticksToCandles(tickSeries)
        val orderBook = ticksToOrderBook(tickSeries)
        val lastUpdated = ticks.maxOfOrNull { it.timestamp } ?: Timestamp.DISTANT_PAST
        
        return MarketData(
            symbol = symbol,
            ticks = tickSeries,
            candles = candles,
            orderBook = orderBook,
            lastUpdated = lastUpdated
        )
    }
    
    /**
     * Convert tick data to OHLCV candles using TrikeShed transforms
     */
    fun ticksToCandles(ticks: TickSeries, interval: String = "1m"): OHLCVSeries {
        // Group ticks by time interval and create OHLCV candles
        val candlesList = mutableListOf<OHLCV>()
        
        // Simple implementation - can be optimized with TrikeShed α transforms
        if (ticks.a > 0) {
            val firstTick = ticks.b(0)
            val prices = (0 until ticks.a).map { ticks.b(it).price }
            val volumes = (0 until ticks.a).map { ticks.b(it).volume }
            
            val ohlcv = OHLCV(
                symbol = firstTick.symbol,
                open = firstTick.price,
                high = prices.maxOrNull() ?: 0.0,
                low = prices.minOrNull() ?: 0.0,
                close = ticks.b(ticks.a - 1).price,
                volume = volumes.sum(),
                timestamp = firstTick.timestamp,
                interval = interval
            )
            candlesList.add(ohlcv)
        }
        
        return candlesList.toIdx()
    }
    
    /**
     * Build order book from tick data
     */
    fun ticksToOrderBook(ticks: TickSeries): BookSeries {
        val bookEntries = mutableListOf<BookEntry>()
        
        // Simplified order book construction using proper Indexed access
        for (i in 0 until ticks.a) {
            val tick = ticks.b(i)
            val side = when (tick.side) {
                Tick.Side.BUY -> BookEntry.Side.BID
                Tick.Side.SELL -> BookEntry.Side.ASK
                else -> BookEntry.Side.BID // Default
            }
            bookEntries.add(BookEntry(tick.price, tick.volume, side))
        }
        
        return bookEntries.toIdx()
    }
    
    /**
     * Calculate portfolio metrics
     */
    fun calculatePortfolioMetrics(portfolio: Portfolio, marketData: Map<Symbol, MarketData>): PortfolioMetrics {
        var totalValue = portfolio.cash
        var totalPnL = 0.0
        
        for (i in 0 until portfolio.positions.a) {
            val position = portfolio.positions.b(i)
            marketData[position.symbol]?.let { data ->
                if (data.ticks.a > 0) {
                    val currentPrice = data.ticks.b(data.ticks.a - 1).price
                    val positionValue = position.quantity * currentPrice
                    totalValue += positionValue
                    totalPnL += (currentPrice - position.averagePrice) * position.quantity
                }
            }
        }
        
        return PortfolioMetrics(
            totalValue = totalValue,
            totalPnL = totalPnL,
            positionCount = portfolio.positions.a,
            orderCount = portfolio.orders.a
        )
    }
}

/**
 * Portfolio performance metrics
 */
@Serializable
data class PortfolioMetrics(
    val totalValue: Double,
    val totalPnL: Double,
    val positionCount: Int,
    val orderCount: Int,
    val sharpeRatio: Double = 0.0,
    val maxDrawdown: Double = 0.0
)

@Serializable
data class BrokeShedEvent(
    val timestamp: @Contextual Instant,
    val eventType: String,
    val details: String
)

@Serializable
data class BrokeShedMetrics(
    val timestamp: @Contextual Instant,
    val eventCount: Int,
    val errorRate: Double
)

fun recordEvent(eventType: String, details: String) {
    val event = BrokeShedEvent(
        timestamp = Clock.System.now(),
        eventType = eventType,
        details = details
    )
    events = (events.a + 1) j { i -> if (i == events.a) event else events.b(i) }
}

fun getMetrics(): BrokeShedMetrics {
    val now = Clock.System.now()
    val errorCount = events.α { it.eventType == "ERROR" }.a
    val errorRate = if (events.a > 0) errorCount.toDouble() / events.a else 0.0
    
    return BrokeShedMetrics(
        timestamp = now,
        eventCount = events.a,
        errorRate = errorRate
    )
}

fun getLatestEvent(): BrokeShedEvent? {
    return if (events.a > 0) events.b(events.a - 1) else null
}

fun getEventsSince(timestamp: @Contextual Instant): Indexed<BrokeShedEvent> {
    val filtered = events.α { it.timestamp >= timestamp }
    return filtered
}

fun getMaxTimestamp(): @Contextual Instant {
    return events.α { it.timestamp }.maxOfOrNull { it } ?: DISTANT_PAST
}