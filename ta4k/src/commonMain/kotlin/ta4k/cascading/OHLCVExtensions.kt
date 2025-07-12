package ta4k.cascading

import borg.trikeshed.cursor.*
import borg.trikeshed.lib.*
import kotlinx.datetime.*
import kotlin.math.*

/**
 * Implementation of missing OHLCV functionality identified in gap analysis
 */

// GAP 1: Gap detection
fun Cursor.detectGaps(timeframe: Timeframe): List<Gap> {
    val gaps = mutableListOf<Gap>()
    val timestampIndex = columnNames.toList().indexOf("timestamp")
    
    for (i in 1 until a) {
        val prevTime = at(i - 1).getLong(timestampIndex)!!
        val currTime = at(i).getLong(timestampIndex)!!
        val expectedGap = timeframe.seconds * 1000L
        val actualGap = currTime - prevTime
        
        if (actualGap > expectedGap * 1.5) { // 50% tolerance
            gaps.add(Gap(
                startTime = Instant.fromEpochMilliseconds(prevTime),
                endTime = Instant.fromEpochMilliseconds(currTime),
                durationMs = actualGap
            ))
        }
    }
    
    return gaps
}

// GAP 2: Partial bar detection
fun RowVec.isPartial(): Boolean {
    val tickCount = getInt(8) ?: 0
    val timeframeName = getString(9) ?: ""
    
    return when (timeframeName) {
        "MINUTE" -> tickCount < 60
        "HOUR" -> tickCount < 3600
        else -> false
    }
}

// GAP 3: Symbol grouping
fun Cursor.groupBySymbol(): Map<String, Cursor> {
    val symbolIndex = columnNames.toList().indexOf("symbol")
    val groups = mutableMapOf<String, MutableList<RowVec>>()
    
    for (i in 0 until a) {
        val row = at(i)
        val symbol = row.getString(symbolIndex) ?: continue
        groups.getOrPut(symbol) { mutableListOf() }.add(row)
    }
    
    return groups.mapValues { (_, rows) ->
        val data = rows.map { row ->
            (0 until row.component1()).map { j -> row.component2()(j).component1() }
        }
        
        cursorOf(data, columnNames.toList(), (0 until columnNames.component1()).map { IOMemento.IoDouble })
    }
}

// Parallel OHLCV aggregation
fun Cursor.toOHLCVParallel(timeframe: Timeframe): Map<String, Cursor> {
    return groupBySymbol().mapValues { (symbol, symbolCursor) ->
        with(OHLCVCascade) {
            symbolCursor.toOHLCV(timeframe, symbol)
        }
    }
}

// GAP 4: Volume Profile
fun Cursor.calculateVolumeProfile(): VolumeProfile {
    val priceIndex = columnNames.toList().indexOf("close")
    val volumeIndex = columnNames.toList().indexOf("volume")
    
    // Build price-volume distribution
    val volumeByPrice = mutableMapOf<Double, Long>()
    var totalVolume = 0L
    
    for (i in 0 until a) {
        val price = at(i).getDouble(priceIndex) ?: continue
        val volume = at(i).getLong(volumeIndex) ?: 0L
        
        // Round to price levels (e.g., nearest 10)
        val priceLevel = (price / 10).roundToInt() * 10.0
        volumeByPrice[priceLevel] = volumeByPrice.getOrDefault(priceLevel, 0L) + volume
        totalVolume += volume
    }
    
    // Find Point of Control (highest volume price)
    val poc = volumeByPrice.maxByOrNull { it.value }?.key ?: 0.0
    
    // Calculate Value Area (70% of volume)
    val sortedPrices = volumeByPrice.entries.sortedByDescending { it.value }
    var valueAreaVolume = 0L
    var valueAreaHigh = poc
    var valueAreaLow = poc
    
    for ((price, volume) in sortedPrices) {
        valueAreaVolume += volume
        valueAreaHigh = maxOf(valueAreaHigh, price)
        valueAreaLow = minOf(valueAreaLow, price)
        
        if (valueAreaVolume >= totalVolume * 0.7) break
    }
    
    return VolumeProfile(poc, valueAreaHigh, valueAreaLow, totalVolume, volumeByPrice)
}

// GAP 5: Technical Indicators

fun Cursor.withMACD(fastPeriod: Int, slowPeriod: Int, signalPeriod: Int): Cursor {
    val closeIndex = columnNames.toList().indexOf("close")
    
    // Calculate EMAs
    val fastEMA = ema(fastPeriod, closeIndex)
    val slowEMA = ema(slowPeriod, closeIndex)
    
    // MACD line = fast EMA - slow EMA
    val macdLine = (0 until \1 j { \2: Int ->
        val fast = fastEMA.component2()(i)
        val slow = slowEMA.component2()(i)
        if (fast != null && slow != null) fast - slow else null
    }
    
    // Signal line = EMA of MACD line
    val signalLine = macdLine.ema(signalPeriod)
    
    // Histogram = MACD - Signal
    val histogram = (0 until \1 j { \2: Int ->
        val macd = macdLine.component2()(i)
        val signal = signalLine.component2()(i)
        if (macd != null && signal != null) macd - signal else null
    }
    
    return addColumns(
        "macd_line" to macdLine,
        "macd_signal" to signalLine,
        "macd_histogram" to histogram
    )
}

fun Cursor.withBollingerBands(period: Int, stdDevMultiplier: Double): Cursor {
    val closeIndex = columnNames.toList().indexOf("close")
    
    val sma = sma(period, "close")
    val stdDev = (0 until \1 j { \2: Int ->
        if (i < period - 1) {
            null
        } else {
            val values = (0 until period).map { j ->
                at(i - j).getDouble(closeIndex) ?: 0.0
            }
            val mean = values.average()
            sqrt(values.map { (it - mean).pow(2) }.average())
        }
    }
    
    val upperBand = (0 until \1 j { \2: Int ->
        val middle = sma.component2()(i)
        val std = stdDev.component2()(i)
        if (middle != null && std != null) middle + stdDevMultiplier * std else null
    }
    
    val lowerBand = (0 until \1 j { \2: Int ->
        val middle = sma.component2()(i)
        val std = stdDev.component2()(i)
        if (middle != null && std != null) middle - stdDevMultiplier * std else null
    }
    
    return addColumns(
        "bb_upper" to upperBand,
        "bb_middle" to sma,
        "bb_lower" to lowerBand
    )
}

fun Cursor.withATR(period: Int): Cursor {
    val highIndex = columnNames.toList().indexOf("high")
    val lowIndex = columnNames.toList().indexOf("low")
    val closeIndex = columnNames.toList().indexOf("close")
    
    // True Range = max(high - low, abs(high - prevClose), abs(low - prevClose))
    val trueRange = (0 until \1 j { \2: Int ->
        if (i == 0) {
            at(i).getDouble(highIndex)!! - at(i).getDouble(lowIndex)!!
        } else {
            val high = at(i).getDouble(highIndex)!!
            val low = at(i).getDouble(lowIndex)!!
            val prevClose = at(i - 1).getDouble(closeIndex)!!
            
            maxOf(
                high - low,
                abs(high - prevClose),
                abs(low - prevClose)
            )
        }
    }
    
    // ATR = EMA of True Range
    val atr = trueRange.ema(period)
    
    return addColumns("atr" to atr)
}

// GAP 6: Incremental updates
fun Cursor.updateWithTick(tick: Tick): Cursor {
    val timestampIndex = columnNames.toList().indexOf("timestamp")
    val lastTimestamp = at(a - 1).getLong(timestampIndex)!!
    
    // Check if tick belongs to current bar or new bar
    val tickBucket = (tick.timestamp.toEpochMilliseconds() / 60000) * 60000 // minute bucket
    val lastBucket = (lastTimestamp / 60000) * 60000
    
    return if (tickBucket == lastBucket) {
        // Update last bar
        updateLastBar(tick)
    } else {
        // Create new bar
        appendNewBar(tick)
    }
}

// GAP 7: Anomaly detection
fun Cursor.detectAnomalies(): List<Anomaly> {
    val anomalies = mutableListOf<Anomaly>()
    val closeIndex = columnNames.toList().indexOf("close")
    val timestampIndex = columnNames.toList().indexOf("timestamp")
    
    // Calculate rolling statistics
    val window = 20
    
    for (i in window until a) {
        val currentClose = at(i).getDouble(closeIndex)!!
        val previousClose = at(i - 1).getDouble(closeIndex)!!
        
        // Calculate recent average and std dev
        val recentCloses = (1..window).map { j ->
            at(i - j).getDouble(closeIndex)!!
        }
        val avgClose = recentCloses.average()
        val stdDev = sqrt(recentCloses.map { (it - avgClose).pow(2) }.average())
        
        // Detect flash crash (> 3 std dev move)
        val priceChange = abs(currentClose - previousClose)
        if (priceChange > 3 * stdDev) {
            anomalies.add(Anomaly(
                type = if (currentClose < previousClose) AnomalyType.FLASH_CRASH else AnomalyType.FAT_FINGER,
                timestamp = Instant.fromEpochMilliseconds(at(i).getLong(timestampIndex)!!),
                priceDeviation = currentClose - previousClose
            ))
        }
    }
    
    return anomalies
}

// GAP 8: Correlation matrix
fun Cursor.calculateCorrelationMatrix(timeframe: Timeframe?): CorrelationMatrix {
    val groups = groupBySymbol()
    val matrix = mutableMapOf<Pair<String, String>, Double>()
    
    val symbols = groups.keys.toList()
    for (i in symbols.indices) {
        for (j in i + 1 until symbols.size) {
            val symbol1 = symbols[i]
            val symbol2 = symbols[j]
            
            val correlation = calculateCorrelation(
                groups[symbol1]!!,
                groups[symbol2]!!
            )
            
            matrix[symbol1 to symbol2] = correlation
        }
    }
    
    return CorrelationMatrix(matrix)
}

// GAP 9: Historical volatility
fun Cursor.historicalVolatility(period: Int): Indexed<Double?> {
    val closeIndex = columnNames.toList().indexOf("close")
    
    return (0 until \1 j { \2: Int ->
        if (i < period) {
            null
        } else {
            val returns = (1..period).map { j ->
                val curr = at(i - j + 1).getDouble(closeIndex)!!
                val prev = at(i - j).getDouble(closeIndex)!!
                ln(curr / prev)
            }
            
            // Annualized volatility (assuming 252 trading days)
            sqrt(returns.map { it.pow(2) }.average() * 252)
        }
    }
}

// Helper functions

internal fun Indexed<Double?>.ema(period: Int): Indexed<Double?> {
    val multiplier = 2.0 / (period + 1)
    var ema: Double? = null
    
    return \1 j { \2: Int ->
        val value = b(i)
        if (value != null) {
            ema = if (ema == null) {
                value // First value is the starting point
            } else {
                value * multiplier + ema!! * (1 - multiplier)
            }
            ema
        } else {
            null
        }
    }
}

internal fun Cursor.ema(period: Int, columnIndex: Int): Indexed<Double?> {
    val values = (0 until \1 j { \2: Int ->
        at(i).getDouble(columnIndex)
    }
    return values.ema(period)
}

internal fun calculateCorrelation(cursor1: Cursor, cursor2: Cursor): Double {
    // Simple Pearson correlation
    val closeIndex = 5 // Assuming close is at index 5
    
    val values1 = (0 until minOf(cursor1.component1(), cursor2.component1())).map {
        cursor1.at(it).getDouble(closeIndex) ?: 0.0
    }
    val values2 = (0 until minOf(cursor1.component1(), cursor2.component1())).map {
        cursor2.at(it).getDouble(closeIndex) ?: 0.0
    }
    
    val mean1 = values1.average()
    val mean2 = values2.average()
    
    val covariance = values1.zip(values2).map { (x, y) ->
        (x - mean1) * (y - mean2)
    }.average()
    
    val std1 = sqrt(values1.map { (it - mean1).pow(2) }.average())
    val std2 = sqrt(values2.map { (it - mean2).pow(2) }.average())
    
    return covariance / (std1 * std2)
}

internal fun Cursor.updateLastBar(tick: Tick): Cursor {
    // Implementation for updating last bar with new tick
    // This would modify the high/low/close/volume of the last bar
    TODO("Implement last bar update")
}

internal fun Cursor.appendNewBar(tick: Tick): Cursor {
    // Implementation for creating new bar from tick
    TODO("Implement new bar creation")
}

// Extension to VolumeProfile
fun VolumeProfile.getVolumeInRange(low: Double, high: Double): Long {
    return volumeByPrice.entries
        .filter { it.key in low..high }
        .sumOf { it.value }
}