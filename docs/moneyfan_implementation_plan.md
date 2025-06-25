# Moneyfan Trading Architecture Implementation Plan

**Agent**: moneyfan-agent  
**Timestamp**: 2025-06-21  
**Status**: Implementation Plan

## 2-Factor Reach Analysis

### Direct Impact Assessment
- **Files to Modify**: moneyfan/src/commonMain/kotlin/ directory only
- **Core Components**: TradingCore.kt, Series implementations, Join implementations, indicators, strategies
- **Dependencies**: Internal to moneyfan package, no external sibling projects affected

### Transitive Impact Assessment  
- **Upstream Dependencies**: None - moneyfan is leaf module
- **Downstream Effects**: Limited to moneyfan trading functionality
- **Build Impact**: Only moneyfan build affected, no impact on Trikeshed, k2script, nexus
- **API Compatibility**: Maintain existing public APIs while enhancing internals

## Current State Analysis

### Existing Strengths
1. **TrikeShed Integration**: Already using Series<T> and Join<A,B> patterns
2. **Type Safety**: Value classes for Price, Volume, Symbol, TradeId, Quantity
3. **Functional Composition**: OHLCV using nested Join structures  
4. **Local Implementations**: Own Series<T> and Join<A,B> in trikeshed package
5. **Technical Indicators**: Basic SMA, EMA, RSI, Bollinger Bands using Series transformations

### Current Gaps
1. **MetaSeries Pattern**: Not leveraging MetaSeries<A,T> universal foundation
2. **Primitive Arrays**: No high-frequency optimizations with primitive arrays  
3. **Portfolio Time-Series**: Basic Series usage, missing advanced time-series patterns
4. **Performance Bottlenecks**: Using List<T> and Pair<A,B> in hot paths
5. **Memory Efficiency**: Missing cache-friendly data structures for HFT

## Proposed Improvements

### 1. Enhanced Portfolio Management with Series<T>

#### Current Implementation
```kotlin
typealias PriceSeries = Series<Price>
typealias CandleSeries = Series<Candlestick>
data class PortfolioState(val positions: Series<Position>, ...)
```

#### Enhanced Implementation
```kotlin
// Time-series specializations for portfolio management
typealias TimestampSeries = Series<Instant>
typealias PortfolioTimeSeries<T> = Join<TimestampSeries, Series<T>>
typealias PositionHistory = PortfolioTimeSeries<Position>
typealias ValueHistory = PortfolioTimeSeries<Price>
typealias RiskMetricsHistory = PortfolioTimeSeries<Join<Decimal, Decimal>> // VaR j Beta

// Enhanced portfolio state with time-series tracking
data class EnhancedPortfolioState(
    val positions: Series<Position>,
    val positionHistory: PositionHistory,
    val valueHistory: ValueHistory,
    val riskHistory: RiskMetricsHistory,
    val performanceMetrics: PerformanceMetrics
)
```

### 2. Advanced Join<A,B> for Asset Mappings

#### Current Implementation  
```kotlin
typealias PriceVolume = Join<Price, Volume>
typealias TickData = Join<PriceVolume, Instant>
```

#### Enhanced Implementation
```kotlin
// Asset relationship mappings
typealias AssetCorrelationMap = Join<Symbol, Series<Join<Symbol, Decimal>>>
typealias PortfolioWeights = Join<Symbol, Decimal>
typealias AssetAllocation = Join<PortfolioWeights, Join<Price, Volume>>

// Risk factor mappings
typealias RiskFactors = Join<Symbol, Join<Decimal, Join<Decimal, Decimal>>> // beta j (var j covar)
typealias PerformanceAttribution = Join<Symbol, Join<Decimal, Decimal>> // contribution j alpha

// Strategy signal mappings
typealias StrategySignals = Join<Symbol, Join<TradingSignal, Decimal>> // signal j confidence
```

### 3. High-Frequency Trading Optimization with Primitive Arrays

#### New HFT Data Structures
```kotlin
// Primitive array wrappers for tick data
@JvmInline
value class TickPriceArray(val data: DoubleArray) {
    val size: Int get() = data.size
    operator fun get(index: Int): Price = Price(data[index])
    fun update(index: Int, price: Price) { data[index] = price.value }
}

@JvmInline  
value class TickVolumeArray(val data: DoubleArray) {
    val size: Int get() = data.size
    operator fun get(index: Int): Volume = Volume(data[index])
    fun update(index: Int, volume: Volume) { data[index] = volume.value }
}

@JvmInline
value class TickTimestampArray(val data: LongArray) {
    val size: Int get() = data.size
    operator fun get(index: Int): Instant = Instant.fromEpochMilliseconds(data[index])
    fun update(index: Int, timestamp: Instant) { data[index] = timestamp.toEpochMilliseconds() }
}

// High-frequency tick buffer using primitive arrays
data class HFTTickBuffer(
    val symbol: Symbol,
    val prices: TickPriceArray,
    val volumes: TickVolumeArray, 
    val timestamps: TickTimestampArray,
    var head: Int = 0,
    var size: Int = 0
) {
    val capacity: Int get() = prices.size
    
    fun addTick(price: Price, volume: Volume, timestamp: Instant) {
        prices.update(head, price)
        volumes.update(head, volume)
        timestamps.update(head, timestamp)
        head = (head + 1) % capacity
        if (size < capacity) size++
    }
    
    fun toSeries(): Series<MarketTick> = size j { i ->
        val index = if (size < capacity) i else (head + i) % capacity
        MarketTick(
            symbol = symbol,
            data = (prices[index] j volumes[index]) j timestamps[index],
            tradeId = TradeId("tick_$index")
        )
    }
}
```

### 4. Performance-Critical Trading Logic Optimizations

#### Current Issues
```kotlin
// Avoid these patterns in hot paths:
val priceList = prices.play.toList() // Materializes entire series
val averages = prices.zipWithNext { prev, curr -> /* */ } // Uses Pair<A,B>
```

#### Optimized Patterns
```kotlin
// Use for loops for performance-critical sections
fun fastMovingAverage(prices: PriceSeries, period: Int): PriceSeries {
    return prices.size j { index ->
        if (index < period - 1) {
            Price.UNDEFINED
        } else {
            var sum = 0.0
            // Performance-critical: use for loop, not forEach
            for (i in (index - period + 1)..index) {
                sum += prices[i].value
            }
            Price(sum / period)
        }
    }
}

// Cache-friendly data access patterns
fun calculateRSI(prices: PriceSeries, period: Int): PriceSeries {
    val changes = prices.size - 1 j { i -> prices[i + 1].value - prices[i].value }
    
    return changes.size j { index ->
        if (index < period - 1) {
            Price(50.0) // Neutral RSI
        } else {
            var gainSum = 0.0
            var lossSum = 0.0
            
            // Optimized for loop pattern
            for (i in (index - period + 1)..index) {
                val change = changes[i]
                if (change > 0) gainSum += change else lossSum -= change
            }
            
            val rs = if (lossSum > 0) gainSum / lossSum else Double.MAX_VALUE
            Price(100.0 - (100.0 / (1.0 + rs)))
        }
    }
}
```

## Implementation Steps

### Phase 1: Core Type System Enhancement
1. ✅ Read existing CoreTypes.kt from Trikeshed for context
2. Enhance moneyfan/trikeshed/ types with MetaSeries patterns
3. Add primitive array wrappers for HFT data
4. Update TradingCore.kt with enhanced type definitions

### Phase 2: Portfolio Management Enhancement  
1. Implement time-series portfolio tracking
2. Add advanced asset correlation mappings
3. Enhance risk metrics with historical tracking
4. Update portfolio manager with Series<T> optimizations

### Phase 3: High-Frequency Trading Optimization
1. Implement primitive array tick buffers
2. Add cache-friendly indicator calculations
3. Optimize hot path trading logic with for loops
4. Performance test and benchmark improvements

### Phase 4: Integration and Testing
1. Update existing strategies to use enhanced types
2. Add comprehensive test coverage
3. Performance validation against current implementation
4. Documentation updates

## Success Metrics

### Performance Targets
- **Tick Processing**: 10x improvement in high-frequency data handling
- **Memory Usage**: 50% reduction in GC pressure for hot paths  
- **Indicator Calculation**: 3x faster moving averages and technical indicators
- **Portfolio Updates**: 5x faster position and risk metric calculations

### Functional Enhancements
- **Time-Series Analytics**: Rich historical portfolio tracking
- **Asset Relationships**: Comprehensive correlation and allocation mapping
- **Risk Management**: Real-time risk factor attribution
- **Strategy Framework**: Enhanced signal processing and confidence tracking

## Risk Mitigation
- **Backward Compatibility**: Maintain existing APIs during transition
- **Incremental Rollout**: Phase-based implementation with validation
- **Performance Monitoring**: Benchmark each enhancement
- **Fallback Strategy**: Preserve current implementations during transition

## Next Steps
1. Begin Phase 1 implementation
2. Create performance benchmarks
3. Implement primitive array optimizations
4. Validate enhanced Series<T> patterns

This plan provides a comprehensive roadmap for enhancing moneyfan's trading architecture while adhering to TrikeShed principles and maintaining scope boundaries.