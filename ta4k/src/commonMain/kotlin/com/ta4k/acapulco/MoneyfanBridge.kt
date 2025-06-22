package com.ta4k.acapulco

import com.ta4k.acapulco.model.PortfolioRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import java.time.Instant
import moneyfan.core.*

/**
 * Bridge class to connect the Coinbase trading bot with the Moneyfan trading core.
 * Handles data transformation and state synchronization between systems using TrikeShed patterns.
 */
class MoneyfanBridge {
    // Core trading components
    private val tradingEngine = TradingEngine()
    private val portfolioManager = PortfolioManager()
    private val technicalAnalysis = TechnicalAnalysis()
    
    // State flows for UI updates
    private val _portfolioState = MutableStateFlow<AcapulcoPortfolioState?>(null)
    val portfolioState: StateFlow<AcapulcoPortfolioState?> = _portfolioState.asStateFlow()

    private val _tradingState = MutableStateFlow<AcapulcoTradingState?>(null)
    val tradingState: StateFlow<AcapulcoTradingState?> = _tradingState.asStateFlow()
    
    private val _marketState = MutableStateFlow<MarketState?>(null)
    val marketState: StateFlow<MarketState?> = _marketState.asStateFlow()

    // Data transformation methods integrating with Moneyfan core
    fun updatePortfolioState(
        portfolioRows: List<PortfolioRow>,
        totalValue: BigDecimal,
        cashBalance: BigDecimal
    ) {
        // Convert to Moneyfan core types using TrikeShed patterns
        val currentPrices = portfolioRows.associate { row ->
            Symbol(row.symbol) to Price(row.price?.toDouble() ?: 0.0)
        }
        
        val corePortfolioState = portfolioManager.getPortfolioState(currentPrices)
        
        _portfolioState.value = AcapulcoPortfolioState(
            assets = portfolioRows.map { row: PortfolioRow ->
                AssetState(
                    symbol = row.symbol,
                    quantity = row.quantity,
                    price = row.price,
                    value = row.value,
                    baseline = row.baseline,
                    deviation = row.deviation,
                    priceChange = row.priceChange
                )
            },
            totalValue = totalValue,
            cashBalance = cashBalance,
            timestamp = Instant.now(),
            coreState = corePortfolioState
        )
    }

    fun updateTradingState(
        harvestedAmount: BigDecimal,
        anyTrades: Boolean,
        portfolioDeviation: Double,
        crashProtectionActive: Boolean
    ) {
        _tradingState.value = AcapulcoTradingState(
            harvestedAmount = harvestedAmount,
            anyTrades = anyTrades,
            portfolioDeviation = portfolioDeviation,
            crashProtectionActive = crashProtectionActive,
            timestamp = Instant.now()
        )
    }
    
    fun processTicks(symbol: String, ticks: List<MarketTick>): CandleSeries {
        val tickIndexed = borg.trikeshed.lib.Indexed.of(ticks.size) { i -> ticks[i] }
        return tradingEngine.processTickSeries(tickIndexed)
    }
    
    fun updateMarketState(
        candleSeries: CandleSeries,
        rsiPeriod: Int = 14,
        smaPeriod: Int = 20
    ) {
        val prices = candleSeries.α { candle -> candle.ohlcv.close }
        val rsi = technicalAnalysis.rsi(prices, rsiPeriod)
        val sma = technicalAnalysis.simpleMovingAverage(prices, smaPeriod)
        
        _marketState.value = MarketState(
            candleSeries = candleSeries,
            rsi = rsi,
            sma = sma,
            timestamp = Instant.now()
        )
    }
    
    fun executeOrder(symbol: String, quantity: Double, price: Double, isBuy: Boolean): Boolean {
        val symbolObj = Symbol(symbol)
        val quantityObj = Quantity(quantity)
        val priceObj = Price(price)
        
        return if (isBuy) {
            portfolioManager.buyPosition(symbolObj, quantityObj, priceObj)
        } else {
            portfolioManager.sellPosition(symbolObj, quantityObj, priceObj)
        }
    }
}

// Enhanced state classes integrating Moneyfan core with Acapulco bridge
data class AcapulcoPortfolioState(
    val assets: List<AssetState>,
    val totalValue: BigDecimal,
    val cashBalance: BigDecimal,
    val timestamp: Instant,
    val coreState: moneyfan.core.PortfolioState
)

data class AssetState(
    val symbol: String,
    val quantity: BigDecimal,
    val price: BigDecimal?,
    val value: BigDecimal?,
    val baseline: Double?,
    val deviation: Double?,
    val priceChange: BigDecimal?
)

data class AcapulcoTradingState(
    val harvestedAmount: BigDecimal,
    val anyTrades: Boolean,
    val portfolioDeviation: Double,
    val crashProtectionActive: Boolean,
    val timestamp: Instant
)

data class MarketState(
    val candleSeries: CandleSeries,
    val rsi: PriceSeries,
    val sma: PriceSeries,
    val timestamp: Instant
) 