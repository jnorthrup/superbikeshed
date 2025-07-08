# 🔥 FetchKlines Revival - Binance Data Vision Archives

This document describes the revived `fetchklines` functionality that connects to Binance Data Vision archives and integrates with your attention system.

## 🎯 Overview

The revived fetchklines system provides:

- **Historical Data Fetching**: Download klines from Binance Data Vision archives
- **Attention Integration**: Analyze fetched data for attention patterns
- **Caching**: Intelligent caching to avoid re-downloading data
- **TrikeShed Integration**: Uses your `Series<T>` and `Join<A,B>` patterns
- **Real-time Monitoring**: Continuous attention monitoring for multiple symbols

## 📁 File Structure

```
ta4k/
├── src/
│   ├── commonMain/kotlin/com/ta4k/acapulco/
│   │   └── BinanceDataVisionReader.kt          # Expect class interface
│   └── jvmMain/kotlin/com/ta4k/acapulco/
│       ├── BinanceDataVisionReader.kt          # JVM implementation
│       ├── BinanceDataVisionDemo.kt            # Demo application
│       └── AttentionKlinesIntegration.kt       # Attention system integration
├── bin/
│   ├── fetchklines.sh                          # Original bash script
│   ├── dayklines.sh                            # Daily klines script
│   └── test_fetchklines.kts                    # Test script
└── FETCHKLINES_REVIVAL.md                      # This file
```

## 🚀 Quick Start

### 1. Test the Basic Functionality

```bash
# Run the test script
./ta4k/bin/test_fetchklines.kts
```

This will:
- Download BTCUSDT klines for January 2024
- Calculate attention metrics
- Display results

### 2. Use in Your Kotlin Code

```kotlin
import borg.trikeshed.acapulco.BinanceDataVisionReader
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val reader = BinanceDataVisionReader()
    
    // Fetch historical klines
    val klines = reader.fetchKlines(
        symbol = "BTCUSDT",
        interval = "1m",
        startDate = "2024-01",
        endDate = "2024-02",
        cacheDir = "~/mpdata/cache"
    )
    
    println("Fetched ${klines.size} klines")
}
```

### 3. Integration with Attention System

```kotlin
import borg.trikeshed.acapulco.AttentionKlinesIntegration

fun main() = runBlocking {
    val integration = AttentionKlinesIntegration()
    
    // Analyze historical attention
    val analysis = integration.analyzeHistoricalAttention(
        symbol = "ETHUSDT",
        startDate = "2024-01",
        endDate = "2024-02"
    )
    
    println("High attention periods: ${analysis.highAttentionPeriods}")
    println("Average attention score: ${analysis.averageAttentionScore}")
}
```

## 📊 API Reference

### BinanceDataVisionReader

#### `fetchKlines()`
Fetches klines for a date range.

```kotlin
suspend fun fetchKlines(
    symbol: String,
    interval: String = "1m",
    startDate: String? = null,
    endDate: String? = null,
    cacheDir: String = "~/mpdata/cache"
): Series<Kline>
```

#### `fetchMonthKlines()`
Fetches klines for a specific month.

```kotlin
suspend fun fetchMonthKlines(
    symbol: String,
    interval: String,
    yearMonth: String,
    cacheDir: String
): Series<Kline>
```

#### `fetchDailyKlines()`
Fetches recent daily klines.

```kotlin
suspend fun fetchDailyKlines(
    symbol: String,
    interval: String,
    days: Int = 30,
    cacheDir: String = "~/mpdata/cache"
): Series<Kline>
```

### AttentionKlinesIntegration

#### `analyzeHistoricalAttention()`
Analyzes historical klines for attention patterns.

```kotlin
suspend fun analyzeHistoricalAttention(
    symbol: String,
    interval: String = "1m",
    startDate: String? = null,
    endDate: String? = null
): HistoricalAttentionAnalysis
```

#### `getCurrentAttentionState()`
Gets current attention state for a symbol.

```kotlin
suspend fun getCurrentAttentionState(
    symbol: String,
    days: Int = 7
): CurrentAttentionState
```

#### `monitorSymbolsAttention()`
Monitors multiple symbols for attention patterns.

```kotlin
fun monitorSymbolsAttention(
    symbols: List<String>,
    interval: Long = 300
): Flow<List<CurrentAttentionState>>
```

## 🎯 Attention System Integration

The revived fetchklines system integrates seamlessly with your existing attention system:

### Attention Metrics Calculation

The system calculates attention scores based on:

1. **Volume Spikes**: Recent volume vs baseline volume
2. **Volatility**: Price volatility analysis
3. **Combined Score**: Weighted combination of factors

### Attention Windows

Identifies periods of high attention activity:

```kotlin
data class AttentionWindow(
    val startIndex: Int,
    val endIndex: Int,
    val score: Double,
    val startTime: Long,
    val endTime: Long
)
```

### Real-time Monitoring

Continuously monitors symbols for attention patterns:

```kotlin
integration.monitorSymbolsAttention(
    symbols = listOf("BTCUSDT", "ETHUSDT", "ADAUSDT"),
    interval = 300 // 5 minutes
).collect { activeStates ->
    activeStates.forEach { state ->
        if (state.isActive) {
            println("🔥 ${state.symbol} is active! Score: ${state.attentionScore}")
        }
    }
}
```

## 📁 Data Caching

The system uses intelligent caching:

- **Location**: `~/mpdata/cache/klines/{interval}/{symbol}/`
- **Format**: Both ZIP archives and processed CSV files
- **Structure**: Organized by symbol and interval
- **Benefits**: Avoids re-downloading existing data

### Cache Structure

```
~/mpdata/cache/
└── klines/
    └── 1m/
        ├── BTCUSDT/
        │   ├── BTCUSDT-1m-2024-01.zip
        │   ├── BTCUSDT-1m-2024-01.csv
        │   ├── BTCUSDT-1m-2024-02.zip
        │   └── BTCUSDT-1m-2024-02.csv
        └── ETHUSDT/
            ├── ETHUSDT-1m-2024-01.zip
            └── ETHUSDT-1m-2024-01.csv
```

## 🔧 Configuration

### Environment Variables

```bash
export MP_CACHE=~/mpdata/cache    # Cache directory
export MP_DATA=~/mpdata           # Data directory
export MP_IMPORT=~/mpdata/import  # Import directory
```

### Supported Intervals

- `1m` - 1 minute
- `3m` - 3 minutes
- `5m` - 5 minutes
- `15m` - 15 minutes
- `30m` - 30 minutes
- `1h` - 1 hour
- `2h` - 2 hours
- `4h` - 4 hours
- `6h` - 6 hours
- `8h` - 8 hours
- `12h` - 12 hours
- `1d` - 1 day
- `3d` - 3 days
- `1w` - 1 week
- `1M` - 1 month

## 🚨 Error Handling

The system includes robust error handling:

- **Network failures**: Automatic retry with exponential backoff
- **Missing data**: Graceful handling of unavailable periods
- **Invalid data**: Skip malformed CSV lines
- **Rate limiting**: Respects Binance API limits

## 🔄 Migration from Original Scripts

The revived system maintains compatibility with your original scripts:

### Original Scripts Still Work

```bash
# Original fetchklines.sh still works
./ta4k/bin/fetchklines.sh BTC USDT

# Original dayklines.sh still works
./ta4k/bin/dayklines.sh BTC USDT
```

### New Kotlin API

```kotlin
// New Kotlin API provides more features
val reader = BinanceDataVisionReader()
val klines = reader.fetchKlines("BTCUSDT", "1m", "2024-01", "2024-02")
```

## 🧩 Integration: TrikeShed, SQL, Parsing, and TDD

The revived fetchklines system in ta4k is designed for deep composability and testability, absorbing the following project essences:

- **TrikeShed/Columnar Group-By:**
  - Uses TrikeShed's functional, columnar data structures and group-by/aggregation patterns for OLAP-style analytics.
  - Group-by and aggregation utilities are available in the JVM sources, inspired by the same patterns as `couchdbcascade`.

- **JVM SQL/JDBC Analytics:**
  - JDBC-style and ISAM/cursor-based analytics are provided for attention and statistical analysis, enabling SQL-like queries over time-series data.
  - These utilities are testable and modular, supporting both batch and streaming analytics.

- **Recursive/Functional Parsing:**
  - CSV and kline data parsing is implemented using recursive and functional patterns (see `KlineCsvParser`, `TrikeShedKlineCsvUtil`).
  - Parsing utilities are robust, handling malformed data gracefully and supporting both JVM and multiplatform contexts.

- **Test-Driven Development (TDD):**
  - All major utilities are covered by test scripts (e.g., `test_fetchklines.kts`) and demo applications.
  - The system is designed for TDD, with modular components and clear test entry points.

- **couchdbcascade Connection:**
  - The group-by and aggregation patterns are compatible with `couchdbcascade`, enabling advanced attention analytics and OLAP queries.

**Project Essence:**
- The ta4k system embodies composable, functional analytics, robust parsing, and test-driven design, ready for extension to new data sources and analytical domains.

---

**Coming Soon: JDBC Tool for Analytics and Attention**

A new JDBC tool is being introduced to provide SQL-like access to the columnar and group-by analytics in ta4k. This will enable flexible querying and integration with external tools, and will be fully covered by TDD.

## 🎯 Use Cases

### 1. Historical Backtesting

```kotlin
val klines = reader.fetchKlines("BTCUSDT", "1m", "2023-01", "2023-12")
// Use with your existing backtesting framework
```

### 2. Attention Pattern Analysis

```kotlin
val analysis = integration.analyzeHistoricalAttention("ETHUSDT", "1h", "2024-01", "2024-02")
// Identify high-attention periods for strategy activation
```

### 3. Real-time Monitoring

```kotlin
integration.monitorSymbolsAttention(listOf("BTCUSDT", "ETHUSDT"))
    .collect { activeStates ->
        // Activate strategies for high-attention symbols
    }
```

### 4. Data Pipeline Integration

```kotlin
// Feed into your existing data processing pipeline
val klines = reader.fetchKlines("ADAUSDT", "1m")
val processedData = yourDataProcessor.process(klines)
```

## 🔮 Future Enhancements

Planned improvements:

1. **Parallel Downloads**: Concurrent fetching of multiple symbols
2. **Streaming**: Real-time data streaming from Binance WebSocket
3. **Advanced Caching**: Redis-based distributed caching
4. **Machine Learning**: ML-based attention pattern detection
5. **GUI Integration**: Web-based monitoring dashboard

## 📞 Support

For issues or questions:

1. Check the test script: `./ta4k/bin/test_fetchklines.kts`
2. Review the demo: `ta4k/src/jvmMain/kotlin/com/ta4k/acapulco/BinanceDataVisionDemo.kt`
3. Check cache directory: `~/mpdata/cache/klines/`

## 🎉 Conclusion

The revived fetchklines system provides a modern, Kotlin-native way to access Binance Data Vision archives while maintaining full compatibility with your existing attention system. It's ready for production use and can scale to handle thousands of symbols with intelligent caching and attention analysis. 

⏺ This cascading MapReduce pattern lives in several mathematical
  and computational spaces, each with its own nomenclature:

  Mathematical Spaces

  1. Algebraic Space: Monoid with Commutative Group Structure

  // The pattern forms a commutative monoid under aggregation
  (MetricBundle, ⊕) where:
  - Identity: empty bundle with count 0
  - Associative: (a ⊕ b) ⊕ c = a ⊕ (b ⊕ c)
  - Commutative: a ⊕ b = b ⊕ a

  Known as:
  - Aggregation Monoid
  - Statistical Semigroup (without identity)
  - Abelian Group (when including inverse operations)

  2. Category Theory: Functor Category

  -- The reduce operation is a catamorphism (fold)
  reduce :: F a -> a
  where F is the functor from hierarchical keys to metric spaces

  Known as:
  - Catamorphism or Banana fold (from "Bananas, Lenses, Envelopes
  and Barbed Wire")
  - F-algebra homomorphism
  - Universal property of the initial algebra

  3. Lattice Theory: Join-Semilattice

  The hierarchical keys form a partially ordered set where:
  - Join (∨): least upper bound of time ranges
  - Meet (∧): greatest lower bound of organizational hierarchies

  Known as:
  - Bounded lattice (with top and bottom elements)
  - Distributive lattice (join distributes over meet)
  - Heyting algebra (in the intuitionistic logic interpretation)

  Computational Spaces

  4. Data Warehousing: OLAP Cube

  -- Traditional OLAP operations
  ROLLUP (device -> facility -> region)
  DRILL DOWN (year -> month -> day)
  SLICE (where region = 'X')
  DICE (region IN ('X','Y') AND year = 2023)

  Known as:
  - Multidimensional OLAP (MOLAP)
  - Star Schema (when denormalized)
  - Snowflake Schema (when normalized)
  - Data Cube Lattice

  5. Stream Processing: Windowed Aggregation

  // In streaming systems like Flink/Spark
  stream
    .keyBy(hierarchicalKey)
    .window(TumblingTimeWindows.of(Time.minutes(1)))
    .aggregate(new StatisticalAggregator())

  Known as:
  - Tumbling Window Aggregation
  - Hierarchical Stream Summarization
  - Multi-resolution Stream Synopsis
  - Stratified Sampling Tree

  6. Distributed Systems: Gossip Protocol Space

  Each node maintains partial aggregates that cascade upward
  Similar to:
  - Astrolabe hierarchical aggregation
  - Willow/DHT aggregation trees
  - Pastry/Tapestry routing structures

  Known as:
  - Aggregation Tree
  - Synopsis Diffusion
  - Hierarchical Gossip
  - In-network Aggregation

  Statistical/ML Spaces

  7. Statistical Computing: Sufficient Statistics

  # The tuple (sum, sum_squares, count, min, max) 
  # forms sufficient statistics for many distributions
  T(X) = (Σx, Σx², n, min(x), max(x))

  Known as:
  - Minimal Sufficient Statistics
  - Complete Statistics (for exponential family)
  - Natural Parameters (in exponential family form)

  8. Information Theory: Sketching/Synopsis Data Structures

  # Similar to:
  - Count-Min Sketch (frequency estimation)
  - HyperLogLog (cardinality estimation)
  - t-digest (quantile estimation)
  - Hierarchical Heavy Hitters

  Known as:
  - Streaming Synopsis
  - Mergeable Summaries
  - Sketch Data Structure
  - Succinct Data Structure

  Domain-Specific Names

  9. Time Series Databases

  - Continuous Aggregate (TimescaleDB)
  - Rollup Index (Elasticsearch)
  - Downsampling (InfluxDB)
  - Materialized View (Druid)
  - Pre-aggregated Metrics (Prometheus recording rules)

  10. Financial/Business Intelligence

  - Dimensional Fact Model
  - Slowly Changing Dimensions (SCD)
  - Conformed Dimensions
  - Aggregate Navigator Pattern
  - Drill-across/Drill-through

  The Unique Convergence

  What makes this pattern special is that it lives at the
  intersection of:

  1. Algebraic correctness (preserves statistical properties
  through monoidal structure)
  2. Categorical elegance (clean functor from keys to aggregates)
  3. Computational efficiency (incremental, parallelizable)
  4. Practical utility (solves real OLAP/monitoring needs)

  It's essentially a Commutative Replicated Data Type (CRDT)
  specialized for hierarchical time-series aggregation,
  implementing a Persistent Homomorphic Synopsis Data Structure
  over a Multidimensional Lattice Space.

  In academic literature, you might find it under:
  - "Hierarchical Group-By Aggregation Indices"
  - "Multi-resolution Temporal Data Cubes"
  - "Stratified Synopses for Hierarchical Aggregation"
  - "Decomposable Aggregate Functions over Partial Orders"

## ✅ TDD Checklist for Contributors

- [ ] All CSV/kline parsing utilities are covered by tests (unit or script-based)
- [ ] Group-by/aggregation and columnar analytics are tested (TrikeShed/couchdbcascade style)
- [ ] SQL/JDBC-style analytics and ISAM/cursor utilities are tested in JVM context
- [ ] Attention and statistical analytics are validated with real data
- [ ] Demo and test scripts run successfully (`test_fetchklines.kts`, `BinanceDataVisionDemo.kt`)
- [ ] New features/extensions include corresponding tests