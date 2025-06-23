package moneyfan.attention

import moneyfan.core.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Instant

/**
 * Attention-based ticker system for trading pairs
 * Uses TrikeShed Indexed<T> and Join<A,B> to track attention spans on time series data
 */

// Variable time gauge attention spans using TrikeShed patterns
@JvmInline
value class AttentionSpan(val millis: Long) {
    fun scale(factor: Decimal): AttentionSpan = AttentionSpan((millis * factor).toLong())
    fun clamp(min: Long, max: Long): AttentionSpan = AttentionSpan(millis.coerceIn(min, max))
}

@JvmInline
value class TickerInterval(val millis: Long)

@JvmInline
value class VolatilityGauge(val value: Decimal) {
    fun toAttentionSpan(baseSpan: AttentionSpan): AttentionSpan {
        // High volatility = shorter attention span
        val factor = 1.0 / (1.0 + value * 10.0)
        return baseSpan.scale(factor).clamp(500, 30000) // 0.5s to 30s
    }
}

@JvmInline
value class VolumeGauge(val value: Decimal) {
    fun toAttentionSpan(baseSpan: AttentionSpan): AttentionSpan {
        // High volume = shorter attention span
        val factor = 1.0 / (1.0 + value / 10000.0)
        return baseSpan.scale(factor).clamp(1000, 60000) // 1s to 60s
    }
}

@JvmInline
value class PairWeight(val value: Decimal) {
    operator fun times(other: PairWeight): PairWeight = PairWeight(value * other.value)
    operator fun plus(other: PairWeight): PairWeight = PairWeight(value + other.value)
}

// Attention focus using Join composition
typealias AttentionFocus = Join<Symbol, AttentionSpan>
typealias WeightedPair = Join<Symbol, PairWeight>
typealias GaugeReading = Join<VolatilityGauge, VolumeGauge>
typealias AttentionState = Join<AttentionFocus, Indexed<WeightedPair>>

// Time series attention tracker with variable gauges
data class AttentionWindow(
    val symbol: Symbol,
    val startTime: Instant,
    val endTime: Instant,
    val tickCount: Int,
    val volatility: Decimal,
    val volume: Volume,
    val priceChange: Price,
    val attentionScore: PairWeight,
    val gaugeReading: GaugeReading,
    val dynamicSpan: AttentionSpan
)

typealias AttentionSeries = Indexed<AttentionWindow>

/**
 * Real-time ticker that focuses attention on most active/volatile pairs
 */
class AttentionBasedTicker {
    private val tickerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val engine = TradingEngine()
    
    // Base attention span - dynamically adjusted by gauges
    private val baseAttentionSpan = AttentionSpan(5000) // 5 second base
    
    // Current attention state with variable time gauge
    private var currentAttention: AttentionState = 
        (Symbol("BTC") j baseAttentionSpan) j Indexed.of(0) { WeightedPair(Symbol(""), PairWeight(0.0)) }
    
    // Trading pairs with dynamic attention weights and spans
    private val watchedPairs = mutableMapOf<Symbol, PairWeight>()
    private val dynamicSpans = mutableMapOf<Symbol, AttentionSpan>()
    
    // Time series attention tracking with gauge history
    private val attentionWindows = mutableMapOf<Symbol, MutableList<AttentionWindow>>()
    private val gaugeHistory = mutableMapOf<Symbol, MutableList<GaugeReading>>()
    
    fun addPairToWatch(symbol: Symbol, initialWeight: PairWeight = PairWeight(1.0)) {
        watchedPairs[symbol] = initialWeight
        dynamicSpans[symbol] = baseAttentionSpan
        attentionWindows[symbol] = mutableListOf()
        gaugeHistory[symbol] = mutableListOf()
        
        // Update attention state using TrikeShed patterns
        val weightedPairs = watchedPairs.map { (sym, weight) -> sym j weight }
        val pairIndexed = Indexed.of(weightedPairs.size) { weightedPairs[it] }
        currentAttention = currentAttention.a j pairIndexed
        
        println("Added ${symbol.value} to variable time gauge tracker with weight ${initialWeight.value}")
    }
    
    // Real-time ticker with variable time gauge intervals
    fun startTicker(baseInterval: TickerInterval = TickerInterval(1000)): Flow<AttentionSeries> = flow {
        while (true) {
            val currentTime = kotlinx.datetime.Clock.System.now()
            val attentionWindows = updateAttentionWindowsWithGauges(currentTime)
            
            // Recompute attention weights and dynamic spans based on gauge readings
            recomputeAttentionWithGauges(attentionWindows)
            
            emit(attentionWindows)
            
            // Variable delay based on fastest attention span
            val minSpan = dynamicSpans.values.minByOrNull { it.millis }?.millis ?: baseInterval.millis
            val adaptiveDelay = (minSpan * 0.5).toLong().coerceAtLeast(100) // At least 100ms
            delay(adaptiveDelay)
        }
    }.flowOn(Dispatchers.Default)
    
    private fun updateAttentionWindowsWithGauges(currentTime: Instant): AttentionSeries {
        val windows = mutableListOf<AttentionWindow>()
        
        watchedPairs.keys.forEach { symbol ->
            // Generate sample tick data for this symbol  
            val basePrice = when(symbol.value) {
                "BTC" -> 45000.0
                "ETH" -> 3000.0
                "AAPL" -> 150.0
                "GOOGL" -> 2800.0
                else -> 100.0
            }
            
            // Create sample ticks with volatility simulation
            val ticks = engine.generateSampleTicks(symbol.value, 10, basePrice)
            val tickList = ticks.play
            
            if (tickList.isNotEmpty()) {
                val prices = tickList.map { it.price }
                val volumes = tickList.map { it.volume }
                
                // Calculate gauge readings
                val priceChange = prices.last() - prices.first()
                val totalVolume = volumes.fold(Volume(0.0)) { acc, vol -> acc + vol }
                val volatility = calculateVolatility(prices)
                
                // Create gauge readings using TrikeShed Join
                val volatilityGauge = VolatilityGauge(volatility)
                val volumeGauge = VolumeGauge(totalVolume.value)
                val gaugeReading = volatilityGauge j volumeGauge
                
                // Calculate dynamic attention span using gauges
                val currentSpan = dynamicSpans[symbol] ?: baseAttentionSpan
                val volBasedSpan = volatilityGauge.toAttentionSpan(currentSpan)
                val volVolumeSpan = volumeGauge.toAttentionSpan(volBasedSpan)
                val dynamicSpan = volVolumeSpan
                
                // Update dynamic spans
                dynamicSpans[symbol] = dynamicSpan
                
                // Attention score based on volatility and volume with time factor
                val volumeScore = totalVolume.value / 10000.0
                val volatilityScore = volatility * 100.0
                val timeSpanFactor = baseAttentionSpan.millis.toDouble() / dynamicSpan.millis.toDouble()
                val attentionScore = PairWeight((volatilityScore + volumeScore) * timeSpanFactor)
                
                val window = AttentionWindow(
                    symbol = symbol,
                    startTime = tickList.first().timestamp,
                    endTime = tickList.last().timestamp,
                    tickCount = tickList.size,
                    volatility = volatility,
                    volume = totalVolume,
                    priceChange = priceChange,
                    attentionScore = attentionScore,
                    gaugeReading = gaugeReading,
                    dynamicSpan = dynamicSpan
                )
                
                windows.add(window)
                
                // Store in attention and gauge history
                attentionWindows[symbol]?.add(window)
                gaugeHistory[symbol]?.add(gaugeReading)
                
                // Keep only recent windows and gauge readings
                attentionWindows[symbol]?.let { list ->
                    if (list.size > 20) list.removeAt(0)
                }
                gaugeHistory[symbol]?.let { list ->
                    if (list.size > 50) list.removeAt(0) // Keep more gauge history
                }
            }
        }
        
        return Indexed.of(windows.size) { windows[it] }
    }
    
    private fun calculateVolatility(prices: List<Price>): Decimal {
        if (prices.size < 2) return 0.0
        
        val returns = prices.zipWithNext { prev, curr ->
            kotlin.math.ln(curr.value / prev.value)
        }
        
        val mean = returns.average()
        val variance = returns.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance)
    }
    
    private fun recomputeAttentionWithGauges(windows: AttentionSeries) {
        // Use TrikeShed α transform to update weights and spans based on gauge readings
        val updatedWeights = windows.α { window ->
            val currentWeight = watchedPairs[window.symbol] ?: PairWeight(1.0)
            val gaugeMultiplier = window.attentionScore.value * 0.1
            val newWeight = currentWeight + PairWeight(gaugeMultiplier)
            
            // Decay factor based on time span - faster spans get less decay
            val spanFactor = window.dynamicSpan.millis.toDouble() / baseAttentionSpan.millis.toDouble()
            val decayFactor = 0.95 + (0.04 * (1.0 - spanFactor)) // Less decay for fast spans
            val decayedWeight = PairWeight(newWeight.value * decayFactor)
            
            window.symbol j decayedWeight
        }
        
        // Update watched pairs with new weights
        updatedWeights.play.forEach { (symbol, weight) ->
            watchedPairs[symbol] = weight
        }
        
        // Update current attention state with dynamic span from most active pair
        val topPair = watchedPairs.maxByOrNull { it.value.value }
        if (topPair != null) {
            val topSpan = dynamicSpans[topPair.key] ?: baseAttentionSpan
            val focusPair = topPair.key j topSpan
            val weightedPairs = watchedPairs.map { (sym, weight) -> sym j weight }
            val pairIndexed = Indexed.of(weightedPairs.size) { weightedPairs[it] }
            currentAttention = focusPair j pairIndexed
        }
    }
    
    // Get current attention focus
    fun getCurrentFocus(): AttentionFocus = currentAttention.a
    
    // Get attention history for a pair using Series
    fun getAttentionHistory(symbol: Symbol): AttentionSeries {
        val history = attentionWindows[symbol] ?: emptyList()
        return Indexed.of(history.size) { history[it] }
    }
    
    // Get most attended pairs using TrikeShed patterns
    fun getMostAttentionPairs(count: Int = 5): Indexed<WeightedPair> {
        val sorted = watchedPairs.toList().sortedByDescending { it.second.value }
        val topPairs = sorted.take(count).map { (symbol, weight) -> symbol j weight }
        return Indexed.of(topPairs.size) { topPairs[it] }
    }
    
    fun getDynamicSpan(symbol: Symbol): AttentionSpan? = dynamicSpans[symbol]
    
    fun stop() {
        tickerScope.cancel()
    }
}

/**
 * Attention-based GUI state manager
 */
class AttentionGUI {
    private val ticker = AttentionBasedTicker()
    private var isRunning = false
    
    suspend fun startAttentionTicker(pairs: List<String>) {
        // Add pairs to attention tracker
        pairs.forEach { pairName ->
            ticker.addPairToWatch(Symbol(pairName))
        }
        
        isRunning = true
        println("Starting attention-based ticker for pairs: ${pairs.joinToString(", ")}")
        
        // Collect ticker updates
        ticker.startTicker(TickerInterval(2000)).collect { attentionSeries ->
            displayAttentionUpdate(attentionSeries)
        }
    }
    
    private fun displayAttentionUpdate(windows: AttentionSeries) {
        println("\n=== Variable Time Gauge Ticker Update ===")
        
        // Current focus with dynamic span
        val currentFocus = ticker.getCurrentFocus()
        val (focusSymbol, focusSpan) = currentFocus
        println("🎯 Current Focus: ${focusSymbol.value} (${focusSpan.millis}ms dynamic span)")
        
        // Top attended pairs with their dynamic spans
        val topPairs = ticker.getMostAttentionPairs(3)
        println("\n📊 Most Attended Pairs:")
        topPairs.play.forEach { (symbol, weight) ->
            val span = ticker.getDynamicSpan(symbol)?.millis ?: 5000
            println("  ${symbol.value}: weight ${weight.value.format(2)} span ${span}ms")
        }
        
        // Recent windows with gauge readings and dynamic spans
        println("\n⚡ Variable Time Activity:")
        windows.play.sortedByDescending { it.attentionScore.value }.take(3).forEach { window ->
            val (volGauge, volumeGauge) = window.gaugeReading
            println("  ${window.symbol.value}: " +
                   "Δ${window.priceChange.value.format(2)} " +
                   "Vol:${volGauge.value.format(3)} " +
                   "VolumeG:${volumeGauge.value.format(0)} " +
                   "Span:${window.dynamicSpan.millis}ms " +
                   "Attention:${window.attentionScore.value.format(2)}")
        }
    }
    
    fun stop() {
        isRunning = false
        ticker.stop()
    }
}

// Extension for formatting
private fun Decimal.format(decimals: Int): String = "%.${decimals}f".format(this)