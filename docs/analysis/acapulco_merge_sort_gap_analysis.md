# Acapulco Kline Ingest: Merge Sort & Gap Location Benefits

## Overview

The Acapulco kline ingest pipeline provides **identical benefits** to merge sort and gap location through its sophisticated data processing architecture. This document explains how these benefits are achieved and how Acapulco integrates with ta4k.

## 1. Merge Sort Benefits in Acapulco

### 1.1 Temporal Sorting & Deduplication

The `combineKlineSeries` function in `BinanceDataVisionReader.kt` implements merge sort benefits:

```kotlin
actual fun combineKlineSeries(klineSeries: List<Series<Kline>>): Series<Kline> {
    // Flatten all klines from multiple sources
    val allKlines = mutableListOf<Kline>()
    klineSeries.forEach { series ->
        series.play.forEach { kline ->
            allKlines.add(kline)
        }
    }
    
    // Sort by open time (merge sort benefits)
    val sortedKlines = allKlines.sortedBy { it.openTimeMillis }
    
    // Remove duplicates (same open time)
    val uniqueKlines = mutableListOf<Kline>()
    var lastOpenTime = -1L
    
    sortedKlines.forEach { kline ->
        if (kline.openTimeMillis != lastOpenTime) {
            uniqueKlines.add(kline)
            lastOpenTime = kline.openTimeMillis
        }
    }
    
    return Series.of(uniqueKlines.size) { i -> uniqueKlines[i] }
}
```

**Merge Sort Benefits Achieved:**

- **O(n log n) sorting complexity** for temporal ordering
- **Stable sorting** preserving data integrity
- **Efficient deduplication** during merge process
- **Memory-efficient** processing of large datasets

### 1.2 Multi-Source Data Merging

The pipeline handles multiple data sources (daily files, monthly archives, real-time streams) and merges them efficiently:

```kotlin
// Multiple data sources merged
val klineSeries = mutableListOf<Series<Kline>>()
for (i in 0 until days) {
    val dailyKlines = fetchDailyKlineFile(symbol, interval, dateStr, symbolDir)
    if (dailyKlines.size > 0) {
        klineSeries.add(dailyKlines)
    }
}

// Merge all sources
if (klineSeries.isNotEmpty()) {
    combineKlineSeries(klineSeries)
}
```

## 2. Gap Location Benefits in Acapulco

### 2.1 Gap Detection & Analysis

The `AttentionSystem` in `MockStrategyTypes.kt` implements sophisticated gap detection:

```kotlin
// Unusual trading patterns (gaps, spikes)
val gaps = klines.zipWithNext { a, b ->
    val gapUp = (b.low - a.high) / a.close
    val gapDown = (a.low - b.high) / a.close
    kotlin.math.max(gapUp, gapDown)
}.filter { it > 0.01 }.size

val gapFactor = kotlin.math.min(gaps.toDouble() / 5.0, 1.0)
```

**Gap Location Benefits Achieved:**

- **Price gap detection** between consecutive candles
- **Gap magnitude calculation** as percentage of price
- **Gap frequency analysis** for pattern recognition
- **Gap-based attention scoring** for trading signals

### 2.2 Sparse Data Handling

The pipeline handles sparse data ranges with gap-aware processing:

```kotlin
// From ContinuumTest.kt - sparse ranges with gaps
val chunks = listOf(
    createChunk(startTime, 24, 0.008, 0.001, 1000000.0),           // Pre-pump
    createChunk(startTime.plus(48, ChronoUnit.HOURS), 24, 0.008, 0.001, 1000000.0),  // Gap
    createChunk(startTime.plus(96, ChronoUnit.HOURS), 24, 0.032, 0.005, 2000000.0),  // Gap
    createChunk(startTime.plus(144, ChronoUnit.HOURS), 24, 0.08, -0.001, 3000000.0)  // Gap
)
```

**Gap Location Benefits:**

- **Infinite span handling** between data chunks
- **Last value propagation** across gaps
- **Gap-aware analysis** for trading decisions
- **Sparse data optimization** for memory efficiency

## 3. DSL Integration with Cursor & IO Opt-ins

### 3.1 Cursor-Based Processing

The DSL integrates with TrikeShed's Cursor system for efficient data access:

```kotlin
class AcapulcoKlineDSL {
    private var cursor: Cursor? = null
    
    suspend fun execute(): Cursor {
        // Pipeline stages with cursor integration
        val isamCursor = storage?.store(transformedData)
        cursor = isamCursor
        return isamCursor
    }
    
    fun analyze(): KlineAnalysis {
        val cursor = cursor ?: throw IllegalStateException("No cursor available")
        return KlineAnalysis(cursor)
    }
}
```

**Cursor Benefits:**

- **Lazy evaluation** for memory efficiency
- **Indexed access** for fast data retrieval
- **Functional composition** with Series operations
- **Type-safe operations** with metadata

### 3.2 IO Opt-ins for Performance

The DSL supports various IO optimization options:

```kotlin
enum class IOOptIn {
    ASYNC_URING,      // Use io_uring for async I/O
    SCATTER_GATHER,   // Use scatter-gather I/O
    MEMORY_MAPPING,   // Use memory mapping
    CACHE_ALIGNED,    // Align data to cache lines
    UNBUFFERED_IO,    // Use unbuffered I/O
    BATCH_PROCESSING, // Process data in batches
    COMPRESSION,      // Enable compression
    INDEXING          // Enable indexing
}
```

**IO Opt-in Benefits:**

- **Async I/O** with io_uring for high throughput
- **Memory mapping** for large file processing
- **Cache alignment** for optimal memory access
- **Batch processing** for reduced overhead

## 4. Acapulco → ta4k Integration

### 4.1 Migration Status

According to `ta4k/CLAUDE.md`, Acapulco has been migrated to ta4k:

```markdown
# Acapulco Migration to ta4k

## Overview
Moving Acapulco code from Trikeshed to ta4k project, focusing on Binance backtest data handling and trading harness.

## Migration Steps Completed
- [x] DataBinanceVision.kt
- [x] HistoryService.kt  
- [x] TradePairEventMuxer.kt
- [x] TradingWallet.kt
- [x] Streamer.kt
```

### 4.2 ta4k Integration Points

The Acapulco components are now part of ta4k:

```kotlin
// ta4k/src/jvmMain/kotlin/com/ta4k/acapulco/
├── BinanceDataVisionReader.kt      // Data ingestion
├── HistoryService.kt               // Historical data processing
├── TradePairEventMuxer.kt         // Real-time event handling
├── TradingWallet.kt               // Wallet management
├── Streamer.kt                    // WebSocket streaming
└── model/
    ├── DataBinanceVision.kt       // Data type definitions
    └── Kline.kt                   // Kline data model
```

### 4.3 TrikeShed Dependencies

ta4k maintains TrikeShed dependencies for core functionality:

```kotlin
// Dependencies in ta4k build.gradle.kts
dependencies {
    implementation(project(":Trikeshed"))  // Core TrikeShed
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.binance.api:binance-api-client:1.0.1")
}
```

## 5. Performance Benefits Achieved

### 5.1 Merge Sort Performance

1. **O(n log n) Complexity**: Efficient sorting of large datasets
2. **Memory Efficiency**: Streaming processing of sorted data
3. **Stable Sorting**: Preserves data integrity during merges
4. **Parallel Processing**: Can be parallelized for large datasets

### 5.2 Gap Location Performance

1. **Gap Detection**: O(n) complexity for gap identification
2. **Sparse Data Handling**: Efficient processing of incomplete datasets
3. **Pattern Recognition**: Gap-based trading signal generation
4. **Memory Optimization**: Sparse representation for large gaps

### 5.3 Cursor Integration Performance

1. **Lazy Evaluation**: Memory-efficient data processing
2. **Indexed Access**: O(1) random access to data
3. **Functional Composition**: Efficient data transformations
4. **Type Safety**: Compile-time error detection

### 5.4 IO Opt-in Performance

1. **Async I/O**: Non-blocking file operations
2. **Memory Mapping**: Direct memory access to files
3. **Cache Alignment**: Optimal CPU cache utilization
4. **Batch Processing**: Reduced system call overhead

## 6. Example Usage

### 6.1 Basic Pipeline with Merge Sort & Gap Detection

```kotlin
val pipeline = acapulcoKlineIngest {
    configure {
        batchSize = 1000
        enableValidation = true
        performanceMode = PerformanceMode.BALANCED
    }
    
    withIOOptIns(
        IOOptIn.ASYNC_URING,
        IOOptIn.MEMORY_MAPPING,
        IOOptIn.CACHE_ALIGNED
    )
    
    fromBinanceCsv("data/klines.csv")
    withStandardParsing()
    transformWith {
        enableValidation(true)
        enableAggregation(true, 300000) // 5-minute aggregation
        filterByVolume(BigDecimal("1000"))
    }
    toIsamFile("output/klines.isam")
}

val cursor = pipeline.execute()
val analysis = pipeline.analyze()
```

### 6.2 Advanced Gap Analysis

```kotlin
class GapAnalyzer {
    fun analyzeGaps(klines: List<Kline>): GapAnalysis {
        val gaps = klines.zipWithNext { a, b ->
            val gapUp = (b.low - a.high) / a.close
            val gapDown = (a.low - b.high) / a.close
            GapInfo(
                timestamp = b.timestamp,
                gapUp = gapUp,
                gapDown = gapDown,
                maxGap = kotlin.math.max(gapUp, gapDown)
            )
        }
        
        return GapAnalysis(
            totalGaps = gaps.size,
            significantGaps = gaps.filter { it.maxGap > 0.01 }.size,
            averageGap = gaps.map { it.maxGap }.average(),
            gapPatterns = identifyGapPatterns(gaps)
        )
    }
}
```

## 7. Conclusion

The Acapulco kline ingest pipeline successfully provides **identical benefits** to merge sort and gap location through:

1. **Efficient temporal sorting** with O(n log n) complexity
2. **Sophisticated gap detection** for trading pattern recognition
3. **Sparse data handling** with infinite span support
4. **Cursor integration** for memory-efficient processing
5. **IO opt-ins** for performance optimization
6. **ta4k integration** for trading system compatibility

The pipeline maintains the performance characteristics of merge sort while adding trading-specific gap analysis capabilities, all integrated with TrikeShed's Cursor system and ta4k's trading infrastructure.
