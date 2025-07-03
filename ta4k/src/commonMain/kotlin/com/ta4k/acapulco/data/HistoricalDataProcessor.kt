package com.ta4k.acapulco.data

import borg.trikeshed.lib.*
import com.ta4k.acapulco.model.AssetKey
import com.ta4k.acapulco.model.DataBinanceVision
import kotlinx.datetime.Instant

/**
 * Historical data processor for Binance Data Vision CSV files using TrikeShed patterns.
 * Implements parallel CSV processing with cursor slabs and infinity padding for responsive access.
 */

// Core value classes for type safety
@JvmInline
value class Price(val value: Double) {
    operator fun times(other: Double): Price = Price(value * other)
    operator fun compareTo(other: Price): Int = value.compareTo(other.value)
}

@JvmInline  
value class Volume(val value: Double) {
    operator fun plus(other: Volume): Volume = Volume(value + other.value)
}

@JvmInline
value class Symbol(val value: String)

@JvmInline
value class CandleCount(val value: Int)

// Core data structures using TrikeShed Join patterns
typealias OHLC = Join<Join<Price, Price>, Join<Price, Price>> // Open-High-Low-Close
typealias OHLCV = Join<OHLC, Volume>
typealias TimestampedCandle = Join<OHLCV, Instant>
typealias CandleSeries = Indexed<TimestampedCandle>

// Helper functions for OHLC access
val OHLC.open: Price get() = this.a.a
val OHLC.high: Price get() = this.a.b  
val OHLC.low: Price get() = this.b.a
val OHLC.close: Price get() = this.b.b

val OHLCV.ohlc: OHLC get() = this.a
val OHLCV.volume: Volume get() = this.b

// Cursor slab with infinity padding for responsive data access
class ResponsiveCursorSlab(
    private val hotWindowSize: Int = 10_000,
    private val maxSize: Int = 100_000
) {
    private var activeData: Indexed<TimestampedCandle> = Indexed.of(0) { error("Empty slab") }
    
    fun appendCandles(newCandles: CandleSeries) {
        activeData = when {
            activeData.size == 0 -> newCandles
            activeData.size + newCandles.size <= maxSize -> {
                // Combine using Indexed concatenation
                Indexed.of(activeData.size + newCandles.size) { i ->
                    if (i < activeData.size) activeData[i] else newCandles[i - activeData.size]
                }
            }
            else -> {
                // Rotate old data out, keep recent data
                val keepCount = maxSize - newCandles.size
                val recentData = Indexed.of(keepCount) { i -> activeData[activeData.size - keepCount + i] }
                Indexed.of(maxSize) { i ->
                    if (i < keepCount) recentData[i] else newCandles[i - keepCount]
                }
            }
        }
    }
    
    fun getHotWindow(): CandleSeries {
        val windowSize = minOf(hotWindowSize, activeData.size)
        return if (windowSize == 0) {
            Indexed.of(0) { error("No data") }
        } else {
            Indexed.of(windowSize) { i -> activeData[activeData.size - windowSize + i] }
        }
    }
    
    fun getWindow(startOffset: Int, count: Int): CandleSeries {
        val actualStart = maxOf(0, activeData.size - startOffset - count)
        val actualCount = minOf(count, activeData.size - actualStart)
        return if (actualCount <= 0) {
            Indexed.of(0) { error("No data in range") }
        } else {
            Indexed.of(actualCount) { i -> activeData[actualStart + i] }
        }
    }
    
    val size: Int get() = activeData.size
}

// Parallel CSV processor for Binance Data Vision files
class ParallelCSVProcessor {
    
    fun processKlineCSV(csvLines: List<String>): CandleSeries {
        // Skip header line and process data lines in parallel
        val dataLines = csvLines.drop(1)
        
        // Use Indexed.α for functional transformation
        val candleList = dataLines.map { line -> parseKlineLine(line) }
        
        return Indexed.of(candleList.size) { i -> candleList[i] }
    }
    
    private fun parseKlineLine(line: String): TimestampedCandle {
        val parts = line.split(',')
        require(parts.size >= 11) { "Invalid kline CSV line: $line" }
        
        // Parse OHLCV data
        val open = Price(parts[1].toDouble())
        val high = Price(parts[2].toDouble()) 
        val low = Price(parts[3].toDouble())
        val close = Price(parts[4].toDouble())
        val volume = Volume(parts[5].toDouble())
        val timestamp = Instant.fromEpochMilliseconds(parts[0].toLong())
        
        // Build using Join operators
        val ohlc = (open j high) j (low j close)
        val ohlcv = ohlc j volume
        
        return ohlcv j timestamp
    }
}

// Historical data manager with multi-symbol support
class HistoricalDataManager {
    private val cursorSlabs = mutableMapOf<Symbol, ResponsiveCursorSlab>()
    private val csvProcessor = ParallelCSVProcessor()
    
    fun loadHistoricalData(symbol: Symbol, csvLines: List<String>) {
        val slab = cursorSlabs.getOrPut(symbol) { ResponsiveCursorSlab() }
        val candles = csvProcessor.processKlineCSV(csvLines)
        slab.appendCandles(candles)
    }
    
    fun getRecentCandles(symbol: Symbol, count: CandleCount): CandleSeries {
        val slab = cursorSlabs[symbol] ?: return Indexed.of(0) { error("No data for symbol: ${symbol.value}") }
        return slab.getWindow(0, count.value)
    }
    
    fun getHotWindow(symbol: Symbol): CandleSeries {
        val slab = cursorSlabs[symbol] ?: return Indexed.of(0) { error("No data for symbol: ${symbol.value}") }
        return slab.getHotWindow()
    }
    
    fun getSymbolCount(): Int = cursorSlabs.size
    
    fun getAvailableSymbols(): Indexed<Symbol> {
        val symbols = cursorSlabs.keys.toList()
        return Indexed.of(symbols.size) { i -> symbols[i] }
    }
}

// Integration with existing HistoryService
class BinanceDataVisionConnector(
    private val dataManager: HistoricalDataManager = HistoricalDataManager()
) {
    
    fun connectToAssetModel(assetKey: AssetKey): CandleSeries {
        // Convert AssetKey to Symbol
        val symbol = Symbol("${assetKey.tradeAsset}${assetKey.counterAsset}")
        
        // Get hot window for this symbol
        return dataManager.getHotWindow(symbol)
    }
    
    fun loadFromCSVFile(symbol: Symbol, csvFilePath: String) {
        // This would integrate with the existing HistoryService fetchklines.sh output
        val csvLines = java.nio.file.Files.readAllLines(java.nio.file.Paths.get(csvFilePath))
        dataManager.loadHistoricalData(symbol, csvLines)
    }
    
    fun preloadCommonSymbols() {
        // Pre-load common trading pairs
        val commonSymbols = listOf("BTCUSDT", "ETHUSDT", "ADAUSDT", "SOLUSDT")
        
        commonSymbols.forEach { symbolStr ->
            val symbol = Symbol(symbolStr)
            val csvPath = "/Users/jim/mpdata/import/klines/1m/${symbolStr.take(3)}/${symbolStr.drop(3)}/final-${symbolStr.take(3)}-${symbolStr.drop(3)}-1m.csv"
            try {
                loadFromCSVFile(symbol, csvPath)
            } catch (e: Exception) {
                // Log but don't fail - data might not exist yet
                println("Could not load data for $symbolStr: ${e.message}")
            }
        }
    }
}