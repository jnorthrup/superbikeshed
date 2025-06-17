package com.ta4k.acapulco.model

import borg.trikeshed.acapulco.model.AssetKey
import borg.trikeshed.acapulco.model.AssetModel
import borg.trikeshed.acapulco.model.DataBinanceVision
import borg.trikeshed.cursor.Cursor
import borg.trikeshed.cursor.SimpleCursor
import borg.trikeshed.cursors.TokenizedRow
import borg.trikeshed.isam.IsamDataFile
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.toSeries
import borg.trikeshed.parse.CSVUtil
import borg.trikeshed.parse.HistoryService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.math.BigDecimal
import kotlin.io.path.createTempDirectory

class ContinuumTest {

    @Test
    fun `test continuum with sparse CSV ranges and infinite spans`() = runBlocking {
        // Create test data directory structure
        val testDir = createTempDirectory("continuum_test").toFile()
        testDir.deleteOnExit()
        
        val klinesDir = File(testDir, "klines/1m/DOGE/USDT")
        klinesDir.mkdirs()
        
        // Create test data with the DOGE pump pattern
        val startTime = Instant.parse("2021-01-28T00:00:00Z")
        
        // Create sparse chunks of data with gaps
        val chunks = listOf(
            // Pre-pump chunk (stable price around $0.008)
            createChunk(startTime, 24, 0.008, 0.001, 1000000.0),
            // Pump start chunk (gradual increase)
            createChunk(startTime.plus(48, ChronoUnit.HOURS), 24, 0.008, 0.001, 1000000.0),
            // Peak pump chunk (rapid increase)
            createChunk(startTime.plus(96, ChronoUnit.HOURS), 24, 0.032, 0.005, 2000000.0),
            // Post-pump chunk (stabilization)
            createChunk(startTime.plus(144, ChronoUnit.HOURS), 24, 0.08, -0.001, 3000000.0)
        )
        
        // Create AssetKey for DOGE/USDT
        val assetKey = AssetKey.of("DOGE", "USDT")
        
        // Process chunks through Acapulco ingester with sparse ranges
        chunks.forEachIndexed { index, chunk ->
            // Write chunk to file
            val file = File(klinesDir.absolutePath, "final-DOGE-USDT-1m-${index + 1}.csv")
            file.writeText(chunk)
            
            // Process through Acapulco ingester using TokenizedRow.CsvArraysCursor
            val cursor = TokenizedRow.CsvArraysCursor(file.readLines())
            val fixedCursor = cursor `→` DataBinanceVision.klines.fixup
            
            // Push to AssetModel which will handle the sparse ranges
            // For infinite spans between chunks, it will use the last record's values
            AssetModel.push(assetKey, fixedCursor)
        }
        
        // Verify continuum
        val model = AssetModel[assetKey]
        assertNotNull(model, "AssetModel should be created")
        
        val view = model?.view()
        assertNotNull(view, "View should be available")
        
        // Calculate expected total rows (sum of all chunks)
        val expectedRows = chunks.sumOf { it.lines().size - 1 } // Subtract 1 for header
        assertEquals(expectedRows, view?.a?.rows, "Should have correct number of rows")
        
        // Verify price progression across sparse ranges
        val prices: List<BigDecimal> = view?.a?.rows?.let { rows ->
            (0 until rows).map { i ->
                val row = view.get(i)
                row[1] as BigDecimal // Open price
            }
        } ?: emptyList()
        
        // Verify pre-pump stability
        for (i in 0..23) {
            assertTrue(prices[i] >= BigDecimal("0.007") && prices[i] <= BigDecimal("0.009"),
                "Pre-pump price should be stable around $0.008")
        }
        
        // Verify pump start (note the gap in data - should use last pre-pump value)
        for (i in 24..47) {
            val expectedPrice = BigDecimal("0.008") + BigDecimal(i - 24) * BigDecimal("0.001")
            assertTrue(prices[i] >= expectedPrice,
                "Price should be increasing during pump start")
        }
        
        // Verify peak pump (note the gap in data - should use last pump start value)
        for (i in 48..71) {
            val expectedPrice = BigDecimal("0.032") + BigDecimal(i - 48) * BigDecimal("0.005")
            assertTrue(prices[i] >= expectedPrice,
                "Price should be increasing rapidly during peak pump")
        }
        
        // Verify post-pump (note the gap in data - should use last peak value)
        for (i in 72..95) {
            val expectedPrice = BigDecimal("0.08") - BigDecimal(i - 72) * BigDecimal("0.001")
            assertTrue(prices[i] <= expectedPrice,
                "Price should be decreasing after pump")
        }
    }
    
    private fun createChunk(
        startTime: Instant,
        hours: Int,
        basePrice: Double,
        priceChange: Double,
        baseVolume: Double
    ): String {
        return buildString {
            // Header
            appendLine("Open_time,Open,High,Low,Close,Volume,Close_time,Quote_asset_volume,Number_of_trades,Taker_buy_base_asset_volume,Taker_buy_quote_asset_volume")
            
            // Data rows
            for (i in 0 until hours) {
                val time = startTime.plus(i.toLong(), ChronoUnit.HOURS)
                val price = basePrice + (i * priceChange)
                val volume = baseVolume + (i * 100000.0)
                appendLine("${time.toEpochMilli()},$price,${price * 1.1},${price * 0.9},${price * 1.05},$volume,${time.plus(1, ChronoUnit.HOURS).toEpochMilli()},${price * volume},${100 + i * 10},${volume / 2},${price * volume / 2}")
            }
        }
    }
} 