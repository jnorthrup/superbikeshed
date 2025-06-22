package com.ta4k.acapulco.data

import com.ta4k.acapulco.model.*
import com.ta4k.acapulco.BinanceDataVisionReader
import borg.trikeshed.lib.*
import moneyfan.core.*
import kotlinx.datetime.Instant
import java.math.BigDecimal

/**
 * Unified Binance data processing module consolidating various data sources
 * Integrates with Moneyfan core trading system using TrikeShed patterns
 */
class BinanceDataProcessor {
    private val tradingEngine = TradingEngine()
    
    /**
     * Processes Binance Data Vision kline archive into trading-ready candlestick data
     */
    fun processKlineArchive(filePath: String, symbol: String): CandleSeries {
        val klineData = BinanceDataVisionReader.readArchive(filePath)
        return convertKlinesToCandles(klineData, symbol)
    }
    
    /**
     * Converts Binance kline data to Moneyfan candlestick format
     */
    fun convertKlinesToCandles(klines: Indexed<Kline>, symbol: String): CandleSeries {
        val candleData = mutableListOf<Candlestick>()
        
        klines.play.forEach { kline ->
            val symbolObj = Symbol(symbol)
            val open = Price(kline.open)
            val high = Price(kline.high)
            val low = Price(kline.low)
            val close = Price(kline.close)
            val volume = Volume(kline.volume)
            
            val ohlcv = OHLCV(open, high, low, close, volume)
            
            val candle = Candlestick(
                symbol = symbolObj,
                ohlcv = ohlcv,
                startTime = Instant.fromEpochMilliseconds(kline.openTime),
                endTime = Instant.fromEpochMilliseconds(kline.closeTime),
                tickCount = kline.tradeCount,
                volatility = calculateSimpleVolatility(open, high, low, close),
                vwap = Price(kline.vwap ?: close.value)
            )
            
            candleData.add(candle)
        }
        
        return Indexed.of(candleData.size) { i -> candleData[i] }
    }
    
    /**
     * Processes Binance trade data into market ticks
     */
    fun processTradeData(trades: Indexed<BinanceTrade>, symbol: String): TickSeries {
        val tickData = mutableListOf<MarketTick>()
        
        trades.play.forEach { trade ->
            val tick = MarketTick(
                symbol = Symbol(symbol),
                data = (Price(trade.price) j Volume(trade.quantity)) j Instant.fromEpochMilliseconds(trade.timestamp),
                tradeId = TradeId(trade.tradeId.toString())
            )
            tickData.add(tick)
        }
        
        return Indexed.of(tickData.size) { i -> tickData[i] }
    }
    
    /**
     * Aggregates tick data into candlesticks using TrikeShed transformations
     */
    fun aggregateTicksToCandles(ticks: TickSeries, timeWindowMs: Long): CandleSeries {
        return tradingEngine.processTickSeries(ticks)
    }
    
    /**
     * Creates portfolio rows from Binance account data
     */
    fun createPortfolioRows(
        balances: Map<String, BigDecimal>,
        prices: Map<String, BigDecimal>,
        baselines: Map<String, Double>
    ): List<PortfolioRow> {
        return balances.map { (symbol, quantity) ->
            val price = prices[symbol]
            val value = price?.multiply(quantity)
            val baseline = baselines[symbol]
            val deviation = if (value != null && baseline != null && baseline > 0.0) {
                (value.toDouble() - baseline) / baseline
            } else null
            
            PortfolioRow(
                symbol = symbol,
                quantity = quantity,
                price = price,
                value = value,
                baseline = baseline,
                deviation = deviation,
                priceChange = null // Would need historical data
            )
        }
    }
    
    /**
     * Calculates simple volatility from OHLC data
     */
    private fun calculateSimpleVolatility(open: Price, high: Price, low: Price, close: Price): Decimal {
        val trueRange = kotlin.math.max(
            high.value - low.value,
            kotlin.math.max(
                kotlin.math.abs(high.value - open.value),
                kotlin.math.abs(low.value - open.value)
            )
        )
        return trueRange / open.value
    }
}

/**
 * Binance trade data structure
 */
data class BinanceTrade(
    val tradeId: Long,
    val price: Double,
    val quantity: Double,
    val timestamp: Long,
    val isBuyerMaker: Boolean
)

/**
 * Enhanced Kline data with calculated fields
 */
data class Kline(
    val openTime: Long,
    val closeTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val tradeCount: Int,
    val vwap: Double? = null
)