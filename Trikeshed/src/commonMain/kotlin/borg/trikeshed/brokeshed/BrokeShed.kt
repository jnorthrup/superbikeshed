package borg.trikeshed.brokeshed

import borg.trikeshed.lib.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * BrokeShed - Financial Brokerage and Market Data Processing
 * 
 * A high-performance financial data processing system built on TrikeShed's
 * Indexed type system for maximum performance and type safety.
 */

// === Core Financial Types ===

typealias Symbol = String
typealias Price = Double
typealias Volume = Long
typealias Timestamp = Instant

// === Market Data Structures using Indexed ===

/**
 * Tick data - individual price/volume events
 */
@Serializable
data class Tick(
    val symbol: Symbol,
    val price: Price,
    val volume: Volume,
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
    val open: Price,
    val high: Price,
    val low: Price,
    val close: Price,
    val volume: Volume,
    val timestamp: Timestamp,
    val interval: String = "1m"
)

/**
 * Order book entry
 */
@Serializable
data class BookEntry(
    val price: Price,
    val volume: Volume,
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
    val quantity: Volume,
    val averagePrice: Price,
    val unrealizedPnL: Price = 0.0,
    val realizedPnL: Price = 0.0
)

@Serializable
data class Order(
    val id: String,
    val symbol: Symbol,
    val side: OrderSide,
    val type: OrderType,
    val quantity: Volume,
    val price: Price? = null,
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
    val cash: Price,
    val totalValue: Price,
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
        val tickSeries = ticks.toSeries()
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
        val ticksList = ticks.play.toList()
        if (ticksList.isNotEmpty()) {
            val firstTick = ticksList.first()
            val ohlcv = OHLCV(
                symbol = firstTick.symbol,
                open = firstTick.price,
                high = ticksList.maxOf { it.price },
                low = ticksList.minOf { it.price },
                close = ticksList.last().price,
                volume = ticksList.sumOf { it.volume },
                timestamp = firstTick.timestamp,
                interval = interval
            )
            candlesList.add(ohlcv)
        }
        
        return candlesList.toSeries()
    }
    
    /**
     * Build order book from tick data
     */
    fun ticksToOrderBook(ticks: TickSeries): BookSeries {
        val bookEntries = mutableListOf<BookEntry>()
        
        // Simplified order book construction
        ticks.play.forEach { tick ->
            val side = when (tick.side) {
                Tick.Side.BUY -> BookEntry.Side.BID
                Tick.Side.SELL -> BookEntry.Side.ASK
                else -> BookEntry.Side.BID // Default
            }
            bookEntries.add(BookEntry(tick.price, tick.volume, side))
        }
        
        return bookEntries.toSeries()
    }
    
    /**
     * Calculate portfolio metrics
     */
    fun calculatePortfolioMetrics(portfolio: Portfolio, marketData: Map<Symbol, MarketData>): PortfolioMetrics {
        var totalValue = portfolio.cash
        var totalPnL = 0.0
        
        portfolio.positions.play.forEach { position ->
            marketData[position.symbol]?.let { data ->
                if (data.ticks.size > 0) {
                    val currentPrice = data.ticks.play.last().price
                    val positionValue = position.quantity * currentPrice
                    totalValue += positionValue
                    totalPnL += (currentPrice - position.averagePrice) * position.quantity
                }
            }
        }
        
        return PortfolioMetrics(
            totalValue = totalValue,
            totalPnL = totalPnL,
            positionCount = portfolio.positions.size,
            orderCount = portfolio.orders.size
        )
    }
}

/**
 * Portfolio performance metrics
 */
@Serializable
data class PortfolioMetrics(
    val totalValue: Price,
    val totalPnL: Price,
    val positionCount: Int,
    val orderCount: Int,
    val sharpeRatio: Double = 0.0,
    val maxDrawdown: Double = 0.0
)