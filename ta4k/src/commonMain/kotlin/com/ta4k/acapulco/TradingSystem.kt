package com.ta4k.acapulco

import borg.trikeshed.lib.Indexed
import com.ta4k.core.model.Kline
import com.ta4k.acapulco.model.PortfolioRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.math.BigDecimal

/**
 * Main integration class that coordinates between the trading bot, UI bridge,
 * data loading, and visualization components.
 */
class TradingSystem {
    private val dogeDataLoader = DogeDataLoader()
    
    // State flows for UI updates
    private val _portfolioState = MutableStateFlow<List<PortfolioRow>>(emptyList())
    private val _tradingState = MutableStateFlow(BigDecimal.ZERO)
    private val _visualizationState = MutableStateFlow<SpaceGraphData?>(null)
    
    val portfolioState: StateFlow<List<PortfolioRow>> = _portfolioState.asStateFlow()
    val tradingState: StateFlow<BigDecimal> = _tradingState.asStateFlow()
    val visualizationState: StateFlow<SpaceGraphData?> = _visualizationState.asStateFlow()
    
    /**
     * Load historical DOGE data for backtesting.
     * @param startTime Start time for historical data
     * @param endTime End time for historical data
     * @param timeframe Optional timeframe (defaults to 1h)
     * @return Indexed of Klines for the specified period
     */
    suspend fun loadHistoricalData(
        startTime: Instant,
        endTime: Instant,
        timeframe: String = DogeDataLoader.DEFAULT_TIMEFRAME
    ): Indexed<Kline> {
        return dogeDataLoader.loadHistoricalData(startTime, endTime, timeframe)
    }
    
    /**
     * Update the visualization with new kline data.
     * @param klines Indexed of Klines to visualize
     */
    fun updateVisualization(klines: Indexed<Kline>) {
        _visualizationState.value = SpaceGraphVisualizer.visualizeKlines(klines)
    }
    
    /**
     * Update the portfolio state with new data.
     * @param portfolioRows New portfolio rows
     */
    fun updatePortfolioState(portfolioRows: List<PortfolioRow>) {
        _portfolioState.value = portfolioRows
    }
    
    /**
     * Update the trading state with new data.
     * @param tradingValue New trading value
     */
    fun updateTradingState(tradingValue: BigDecimal) {
        _tradingState.value = tradingValue
    }
    
    /**
     * Load the last N periods of DOGE data.
     * @param n Number of periods to load
     * @param timeframe Optional timeframe (defaults to 1h)
     * @return Indexed of Klines for the last N periods
     */
    suspend fun loadLastNPeriods(
        n: Int,
        timeframe: String = DogeDataLoader.DEFAULT_TIMEFRAME
    ): Indexed<Kline> {
        return dogeDataLoader.loadLastNPeriods(n, timeframe)
    }
} 