package borg.trikeshed.acapulco

import borg.trikeshed.lib.Series
import com.ta4k.core.model.Kline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * Main integration class that coordinates between the trading bot, UI bridge,
 * data loading, and visualization components.
 */
class TradingSystem {
    private val moneyfanBridge = MoneyfanBridge()
    private val dogeDataLoader = DogeDataLoader()
    private val spaceGraphVisualizer = SpaceGraphVisualizer()
    
    // State flows for UI updates
    private val _portfolioState = MutableStateFlow(moneyfanBridge.portfolioState.value)
    private val _tradingState = MutableStateFlow(moneyfanBridge.tradingState.value)
    private val _visualizationState = MutableStateFlow<SpaceGraphData?>(null)
    
    val portfolioState: StateFlow<PortfolioState> = _portfolioState.asStateFlow()
    val tradingState: StateFlow<TradingState> = _tradingState.asStateFlow()
    val visualizationState: StateFlow<SpaceGraphData?> = _visualizationState.asStateFlow()
    
    /**
     * Load historical DOGE data for backtesting.
     * @param startTime Start time for historical data
     * @param endTime End time for historical data
     * @param timeframe Optional timeframe (defaults to 1h)
     * @return Series of Klines for the specified period
     */
    suspend fun loadHistoricalData(
        startTime: Instant,
        endTime: Instant,
        timeframe: String = DogeDataLoader.DEFAULT_TIMEFRAME
    ): Series<Kline> {
        return dogeDataLoader.loadHistoricalData(startTime, endTime, timeframe)
    }
    
    /**
     * Update the visualization with new kline data.
     * @param klines Series of Klines to visualize
     */
    fun updateVisualization(klines: Series<Kline>) {
        _visualizationState.value = SpaceGraphVisualizer.visualizeKlines(klines)
    }
    
    /**
     * Update the portfolio state with new data.
     * @param portfolioState New portfolio state
     */
    fun updatePortfolioState(portfolioState: PortfolioState) {
        moneyfanBridge.updatePortfolioState(portfolioState)
        _portfolioState.value = portfolioState
    }
    
    /**
     * Update the trading state with new data.
     * @param tradingState New trading state
     */
    fun updateTradingState(tradingState: TradingState) {
        moneyfanBridge.updateTradingState(tradingState)
        _tradingState.value = tradingState
    }
    
    /**
     * Load the last N periods of DOGE data.
     * @param n Number of periods to load
     * @param timeframe Optional timeframe (defaults to 1h)
     * @return Series of Klines for the last N periods
     */
    suspend fun loadLastNPeriods(
        n: Int,
        timeframe: String = DogeDataLoader.DEFAULT_TIMEFRAME
    ): Series<Kline> {
        return dogeDataLoader.loadLastNPeriods(n, timeframe)
    }
} 