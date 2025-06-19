package borg.trikeshed.acapulco

import com.ta4k.core.model.Kline
import borg.trikeshed.lib.Series
import kotlinx.coroutines.*
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Integration layer connecting revived fetchklines functionality with the attention system
 * This bridges the gap between historical data fetching and real-time attention analysis
 */
class AttentionKlinesIntegration(
    private val cacheDir: String = "~/mpdata/cache"
) {
    private val reader = BinanceDataVisionReader()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Fetches historical klines and analyzes them for attention patterns
     * @param symbol Trading symbol
     * @param interval Time interval
     * @param startDate Start date in YYYY-MM format
     * @param endDate End date in YYYY-MM format
     * @return Attention analysis results
     */
    suspend fun analyzeHistoricalAttention(
        symbol: String,
        interval: String = "1m",
        startDate: String? = null,
        endDate: String? = null
    ): HistoricalAttentionAnalysis = withContext(Dispatchers.Default) {
        
        // Fetch historical klines
        val klines = reader.fetchKlines(symbol, interval, startDate, endDate, cacheDir)
        
        if (klines.size == 0) {
            return@withContext HistoricalAttentionAnalysis(
                symbol = symbol,
                totalKlines = 0,
                attentionWindows = emptyList(),
                averageAttentionScore = 0.0,
                highAttentionPeriods = 0,
                volumeSpikes = 0,
                volatilitySpikes = 0
            )
        }
        
        // Analyze for attention patterns
        val attentionWindows = findAttentionWindows(klines)
        val averageAttentionScore = calculateAverageAttentionScore(klines)
        val highAttentionPeriods = attentionWindows.size
        val volumeSpikes = countVolumeSpikes(klines)
        val volatilitySpikes = countVolatilitySpikes(klines)
        
        HistoricalAttentionAnalysis(
            symbol = symbol,
            totalKlines = klines.size,
            attentionWindows = attentionWindows,
            averageAttentionScore = averageAttentionScore,
            highAttentionPeriods = highAttentionPeriods,
            volumeSpikes = volumeSpikes,
            volatilitySpikes = volatilitySpikes
        )
    }
    
    /**
     * Fetches recent klines and identifies current attention patterns
     * @param symbol Trading symbol
     * @param days Number of recent days to analyze
     * @return Current attention state
     */
    suspend fun getCurrentAttentionState(
        symbol: String,
        days: Int = 7
    ): CurrentAttentionState = withContext(Dispatchers.Default) {
        
        val klines = reader.fetchDailyKlines(symbol, "1m", days, cacheDir)
        
        if (klines.size == 0) {
            return@withContext CurrentAttentionState(
                symbol = symbol,
                isActive = false,
                attentionScore = 0.0,
                volumeSpike = 1.0,
                volatility = 0.0,
                lastUpdate = Instant.now()
            )
        }
        
        val attentionMetrics = calculateAttentionMetrics(klines)
        val isActive = attentionMetrics.attentionScore > 0.6
        
        CurrentAttentionState(
            symbol = symbol,
            isActive = isActive,
            attentionScore = attentionMetrics.attentionScore,
            volumeSpike = attentionMetrics.volumeSpike,
            volatility = attentionMetrics.volatility,
            lastUpdate = Instant.now()
        )
    }
    
    /**
     * Monitors multiple symbols for attention patterns
     * @param symbols List of symbols to monitor
     * @param interval Monitoring interval in seconds
     * @return Flow of attention updates
     */
    fun monitorSymbolsAttention(
        symbols: List<String>,
        interval: Long = 300 // 5 minutes
    ) = flow {
        while (true) {
            val attentionStates = mutableListOf<CurrentAttentionState>()
            
            symbols.forEach { symbol ->
                try {
                    val state = getCurrentAttentionState(symbol, 1) // Last day
                    attentionStates.add(state)
                } catch (e: Exception) {
                    println("Error monitoring $symbol: ${e.message}")
                }
            }
            
            // Filter to only active symbols
            val activeStates = attentionStates.filter { it.isActive }
            
            if (activeStates.isNotEmpty()) {
                emit(activeStates)
            }
            
            delay(interval * 1000)
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Finds attention windows in kline data
     */
    private fun findAttentionWindows(klines: Series<Kline>): List<AttentionWindow> {
        val windows = mutableListOf<AttentionWindow>()
        val klineList = klines.play
        
        if (klineList.size < 20) return windows
        
        var windowStart = -1
        var windowScore = 0.0
        
        for (i in 10 until klineList.size) {
            val window = klineList.subList(i - 10, i + 1)
            val metrics = calculateAttentionMetrics(Series.of(window.size) { j -> window[j] })
            
            if (metrics.attentionScore > 0.7) {
                if (windowStart == -1) {
                    windowStart = i - 10
                    windowScore = metrics.attentionScore
                } else {
                    windowScore = kotlin.math.max(windowScore, metrics.attentionScore)
                }
            } else {
                if (windowStart != -1 && i - windowStart >= 5) {
                    windows.add(AttentionWindow(
                        startIndex = windowStart,
                        endIndex = i - 1,
                        score = windowScore,
                        startTime = klineList[windowStart].openTimeMillis,
                        endTime = klineList[i - 1].openTimeMillis
                    ))
                }
                windowStart = -1
                windowScore = 0.0
            }
        }
        
        // Handle window at end
        if (windowStart != -1 && klineList.size - windowStart >= 5) {
            windows.add(AttentionWindow(
                startIndex = windowStart,
                endIndex = klineList.size - 1,
                score = windowScore,
                startTime = klineList[windowStart].openTimeMillis,
                endTime = klineList[klineList.size - 1].openTimeMillis
            ))
        }
        
        return windows
    }
    
    /**
     * Calculates attention metrics for a kline series
     */
    private fun calculateAttentionMetrics(klines: Series<Kline>): AttentionMetrics {
        if (klines.size < 20) {
            return AttentionMetrics(1.0, 0.0, 0.0)
        }
        
        val klineList = klines.play
        
        // Volume spike analysis
        val recentVolumes = klineList.takeLast(10).map { it.volume.toDouble() }
        val baselineVolumes = klineList.take(klineList.size - 10).map { it.volume.toDouble() }
        
        val recentAvgVolume = recentVolumes.average()
        val baselineAvgVolume = baselineVolumes.average()
        val volumeSpike = if (baselineAvgVolume > 0) recentAvgVolume / baselineAvgVolume else 1.0
        
        // Volatility analysis
        val prices = klineList.map { it.closePrice.toDouble() }
        val recentPrices = prices.takeLast(10)
        val baselinePrices = prices.take(prices.size - 10)
        
        val recentVolatility = calculateVolatility(recentPrices)
        val baselineVolatility = calculateVolatility(baselinePrices)
        val volatilitySpike = if (baselineVolatility > 0) recentVolatility / baselineVolatility else 1.0
        
        // Combined attention score
        val attentionScore = (
            volumeSpike * 0.35 +
            volatilitySpike * 0.25 +
            (recentVolatility * 100) * 0.25 +
            0.15
        ).coerceIn(0.0, 1.0)
        
        return AttentionMetrics(volumeSpike, recentVolatility, attentionScore)
    }
    
    private fun calculateVolatility(prices: List<Double>): Double {
        if (prices.size < 2) return 0.0
        
        val returns = prices.zipWithNext { a, b -> kotlin.math.ln(b / a) }
        val mean = returns.average()
        val variance = returns.map { (it - mean) * (it - mean) }.average()
        
        return kotlin.math.sqrt(variance)
    }
    
    private fun calculateAverageAttentionScore(klines: Series<Kline>): Double {
        if (klines.size < 20) return 0.0
        
        val klineList = klines.play
        var totalScore = 0.0
        var count = 0
        
        for (i in 10 until klineList.size) {
            val window = klineList.subList(i - 10, i + 1)
            val metrics = calculateAttentionMetrics(Series.of(window.size) { j -> window[j] })
            totalScore += metrics.attentionScore
            count++
        }
        
        return if (count > 0) totalScore / count else 0.0
    }
    
    private fun countVolumeSpikes(klines: Series<Kline>): Int {
        if (klines.size < 20) return 0
        
        val klineList = klines.play
        var spikeCount = 0
        
        for (i in 10 until klineList.size) {
            val window = klineList.subList(i - 10, i + 1)
            val metrics = calculateAttentionMetrics(Series.of(window.size) { j -> window[j] })
            if (metrics.volumeSpike > 2.0) spikeCount++
        }
        
        return spikeCount
    }
    
    private fun countVolatilitySpikes(klines: Series<Kline>): Int {
        if (klines.size < 20) return 0
        
        val klineList = klines.play
        var spikeCount = 0
        
        for (i in 10 until klineList.size) {
            val window = klineList.subList(i - 10, i + 1)
            val metrics = calculateAttentionMetrics(Series.of(window.size) { j -> window[j] })
            if (metrics.volatility > 0.05) spikeCount++ // 5% volatility threshold
        }
        
        return spikeCount
    }
    
    fun stop() {
        scope.cancel()
    }
}

// Data classes for attention analysis results
data class HistoricalAttentionAnalysis(
    val symbol: String,
    val totalKlines: Int,
    val attentionWindows: List<AttentionWindow>,
    val averageAttentionScore: Double,
    val highAttentionPeriods: Int,
    val volumeSpikes: Int,
    val volatilitySpikes: Int
)

data class CurrentAttentionState(
    val symbol: String,
    val isActive: Boolean,
    val attentionScore: Double,
    val volumeSpike: Double,
    val volatility: Double,
    val lastUpdate: Instant
)

data class AttentionWindow(
    val startIndex: Int,
    val endIndex: Int,
    val score: Double,
    val startTime: Long,
    val endTime: Long
)

private data class AttentionMetrics(
    val volumeSpike: Double,
    val volatility: Double,
    val attentionScore: Double
) 