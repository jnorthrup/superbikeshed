package com.ta4k.acapulco.model

import borg.trikeshed.isam.IsamDataFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.StringReader
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

class KlineTransformerTest {

    @Test
    fun `test DOGE pump integration`() {
        // DOGE pump data from early 2021
        // Starting from January 28, 2021 00:00:00 UTC
        val startTime = Instant.parse("2021-01-28T00:00:00Z")
        
        // Create test data with the pump pattern
        val csvData = buildString {
            // Header
            appendLine("Open_time,Open,High,Low,Close,Volume,Close_time,Quote_asset_volume,Number_of_trades,Taker_buy_base_asset_volume,Taker_buy_quote_asset_volume")
            
            // Pre-pump data (stable price around $0.008)
            for (i in 0..23) {
                val time = startTime.plus(i.toLong(), ChronoUnit.HOURS)
                appendLine("${time.toEpochMilli()},0.008,0.008,0.008,0.008,1000000,${time.plus(1, ChronoUnit.HOURS).toEpochMilli()},8000,100,500000,4000")
            }
            
            // Pump start (price starts rising)
            for (i in 24..47) {
                val time = startTime.plus(i.toLong(), ChronoUnit.HOURS)
                val price = 0.008 + (i - 24) * 0.001 // Gradual increase
                appendLine("${time.toEpochMilli()},$price,${price * 1.1},${price * 0.9},${price * 1.05},${1000000 + (i - 24) * 100000},${time.plus(1, ChronoUnit.HOURS).toEpochMilli()},${price * (1000000 + (i - 24) * 100000)},${100 + (i - 24) * 10},${(500000 + (i - 24) * 50000)},${price * (500000 + (i - 24) * 50000)}")
            }
            
            // Peak pump (rapid price increase)
            for (i in 48..71) {
                val time = startTime.plus(i.toLong(), ChronoUnit.HOURS)
                val price = 0.032 + (i - 48) * 0.005 // Steeper increase
                appendLine("${time.toEpochMilli()},$price,${price * 1.2},${price * 0.8},${price * 1.1},${2000000 + (i - 48) * 200000},${time.plus(1, ChronoUnit.HOURS).toEpochMilli()},${price * (2000000 + (i - 48) * 200000)},${200 + (i - 48) * 20},${(1000000 + (i - 48) * 100000)},${price * (1000000 + (i - 48) * 100000)}")
            }
            
            // Post-pump (price stabilization)
            for (i in 72..95) {
                val time = startTime.plus(i.toLong(), ChronoUnit.HOURS)
                val price = 0.08 - (i - 72) * 0.001 // Gradual decrease
                appendLine("${time.toEpochMilli()},$price,${price * 1.05},${price * 0.95},${price * 1.02},${3000000 - (i - 72) * 100000},${time.plus(1, ChronoUnit.HOURS).toEpochMilli()},${price * (3000000 - (i - 72) * 100000)},${300 - (i - 72) * 10},${(1500000 - (i - 72) * 50000)},${price * (1500000 - (i - 72) * 50000)}")
            }
        }

        // Create a temporary file for testing
        val tempFile = java.io.File.createTempFile("doge_pump_test", ".isam")
        tempFile.deleteOnExit()

        // Process the data
        val reader = StringReader(csvData)
        val dataFile = IsamDataFile(tempFile)
        KlineTransformer.processKlines(reader, dataFile)

        // Verify the results
        val cursor = dataFile.read()
        assertNotNull(cursor)
        assertEquals(96, cursor.a.rows, "Should have 96 hours of data")

        // Verify pre-pump stability
        for (i in 0..23) {
            val row = cursor.get(i)
            assertEquals(BigDecimal("0.008"), row[1] as BigDecimal, "Pre-pump price should be stable")
            assertTrue((row[2] as BigDecimal) <= BigDecimal("0.0088"), "Pre-pump high should be within 10%")
            assertTrue((row[3] as BigDecimal) >= BigDecimal("0.0072"), "Pre-pump low should be within 10%")
        }

        // Verify pump start
        for (i in 24..47) {
            val row = cursor.get(i)
            val expectedPrice = BigDecimal("0.008") + BigDecimal(i - 24) * BigDecimal("0.001")
            assertTrue((row[1] as BigDecimal) >= expectedPrice, "Price should be increasing during pump start")
            assertTrue((row[4] as BigDecimal) > (row[1] as BigDecimal), "Close should be higher than open during pump")
        }

        // Verify peak pump
        for (i in 48..71) {
            val row = cursor.get(i)
            val expectedPrice = BigDecimal("0.032") + BigDecimal(i - 48) * BigDecimal("0.005")
            assertTrue((row[1] as BigDecimal) >= expectedPrice, "Price should be increasing rapidly during peak pump")
            assertTrue((row[2] as BigDecimal) > (row[1] as BigDecimal) * BigDecimal("1.1"), "High should be significantly above open during peak")
        }

        // Verify post-pump
        for (i in 72..95) {
            val row = cursor.get(i)
            val expectedPrice = BigDecimal("0.08") - BigDecimal(i - 72) * BigDecimal("0.001")
            assertTrue((row[1] as BigDecimal) <= expectedPrice, "Price should be decreasing after pump")
            assertTrue((row[4] as BigDecimal) < (row[1] as BigDecimal), "Close should be lower than open during post-pump")
        }

        // Verify volume patterns
        val prePumpVolume = (cursor.get(0)[5] as BigDecimal)
        val peakVolume = (cursor.get(60)[5] as BigDecimal)
        val postPumpVolume = (cursor.get(95)[5] as BigDecimal)
        
        assertTrue(peakVolume > prePumpVolume * BigDecimal("2"), "Peak volume should be significantly higher than pre-pump")
        assertTrue(postPumpVolume > prePumpVolume, "Post-pump volume should remain elevated compared to pre-pump")
    }
} 