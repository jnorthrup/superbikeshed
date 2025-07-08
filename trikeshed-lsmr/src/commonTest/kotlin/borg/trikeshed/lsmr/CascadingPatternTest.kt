@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.lsmr

import borg.trikeshed.cursor.*
import borg.trikeshed.lib.*
import kotlin.test.*
import kotlinx.datetime.*

/**
 * Test demonstrating the CouchDB cascading MapReduce pattern
 */
class CascadingPatternTest {
    
    @Test
    fun `demonstrate cascading aggregation pattern`() {
        // Given: A simple LSM-R with hierarchical data
        val lsmr = SimpleLSMR()
        val baseTime = Clock.System.now()
        
        // Insert data with clear patterns
        println("\n=== CASCADING MAPREDUCE PATTERN DEMONSTRATION ===")
        println("Inserting hierarchical time-series data...")
        
        // Region1/Facility1/Device1: CPU increases over time
        for (hour in 0..2) {
            for (minute in 0..59) {
                lsmr.write(
                    HierarchicalKey("region1", "facility1", "device1", 2024, 1, 1, hour, minute),
                    MetricReading(
                        baseTime.plus(hour * 60 + minute, DateTimeUnit.MINUTE),
                        "device1", "facility1", "region1",
                        cpu = 10.0 + hour * 10.0,  // 10, 20, 30
                        memory = 1000.0,
                        disk = 100.0
                    )
                )
            }
        }
        
        // Region1/Facility1/Device2: CPU constant
        for (hour in 0..2) {
            for (minute in 0..59) {
                lsmr.write(
                    HierarchicalKey("region1", "facility1", "device2", 2024, 1, 1, hour, minute),
                    MetricReading(
                        baseTime.plus(hour * 60 + minute, DateTimeUnit.MINUTE),
                        "device2", "facility1", "region1",
                        cpu = 50.0,  // Constant
                        memory = 2000.0,
                        disk = 200.0
                    )
                )
            }
        }
        
        // Region1/Facility2/Device3: CPU decreases
        for (hour in 0..2) {
            for (minute in 0..59) {
                lsmr.write(
                    HierarchicalKey("region1", "facility2", "device3", 2024, 1, 1, hour, minute),
                    MetricReading(
                        baseTime.plus(hour * 60 + minute, DateTimeUnit.MINUTE),
                        "device3", "facility2", "region1",
                        cpu = 90.0 - hour * 10.0,  // 90, 80, 70
                        memory = 3000.0,
                        disk = 300.0
                    )
                )
            }
        }
        
        // Demonstrate cascading aggregation
        println("\n1. Device Level (Raw Data):")
        println("   Device1: CPU varies 10→20→30 (avg: 20)")
        println("   Device2: CPU constant at 50")
        println("   Device3: CPU varies 90→80→70 (avg: 80)")
        
        println("\n2. Facility Level Aggregation:")
        val facility1Stats = lsmr.reduceByFacility("region1", "facility1")
        val facility2Stats = lsmr.reduceByFacility("region1", "facility2")
        println("   Facility1 (Device1+2): Avg CPU = ${facility1Stats.avgCpu}")
        println("   Facility2 (Device3): Avg CPU = ${facility2Stats.avgCpu}")
        
        println("\n3. Regional Aggregation (Cascade from Facilities):")
        val regionStats = lsmr.reduceByRegion("region1")
        println("   Region1: Avg CPU = ${regionStats.avgCpu}")
        println("   Total readings: ${regionStats.count}")
        
        println("\n4. Global Aggregation:")
        val globalStats = lsmr.reduceGlobal()
        println("   Global: Avg CPU = ${globalStats.avgCpu}")
        
        // Verify the cascading mathematics
        println("\n=== MATHEMATICAL VERIFICATION ===")
        
        // Device averages
        val device1Avg = 20.0  // (10+20+30)/3
        val device2Avg = 50.0  // constant
        val device3Avg = 80.0  // (90+80+70)/3
        
        // Facility averages (weighted by device count)
        val facility1Avg = (device1Avg + device2Avg) / 2  // 35.0
        val facility2Avg = device3Avg  // 80.0
        
        // Regional average (weighted by reading count)
        val expectedRegionalAvg = (facility1Avg * 2 + facility2Avg) / 3  // 50.0
        
        println("Expected Facility1 avg: $facility1Avg, Actual: ${facility1Stats.avgCpu}")
        println("Expected Facility2 avg: $facility2Avg, Actual: ${facility2Stats.avgCpu}")
        println("Expected Regional avg: $expectedRegionalAvg, Actual: ${regionStats.avgCpu}")
        
        // Verify counts
        assertEquals(360L, facility1Stats.count, "Facility1 should have 180*2 readings")
        assertEquals(180L, facility2Stats.count, "Facility2 should have 180 readings")
        assertEquals(540L, regionStats.count, "Region should have all 540 readings")
        
        // The key insight: averages are correctly weighted by counts
        assertTrue(
            kotlin.math.abs(regionStats.avgCpu - 50.0) < 0.1,
            "Regional average should be 50.0"
        )
    }
    
    @Test
    fun `demonstrate cursor transformation equivalence`() {
        // Given: Data in LSM-R
        val lsmr = SimpleLSMR()
        val baseTime = Clock.System.now()
        
        // Insert sample data
        val readings = (0..99).map { i ->
            val reading = MetricReading(
                timestamp = baseTime.plus(i, DateTimeUnit.MINUTE),
                deviceId = "device${i % 3}",
                facilityId = "facility${i % 2}",
                regionId = "region1",
                cpu = 10.0 + (i % 10) * 5.0,
                memory = 1000.0 + i * 10,
                disk = 100.0
            )
            val key = HierarchicalKey(
                "region1", 
                "facility${i % 2}", 
                "device${i % 3}",
                2024, 1, 1, i / 60, i % 60
            )
            lsmr.write(key, reading)
            reading
        }
        
        // When: Using cursor transformations
        val cursor = lsmr.asCursor()
        
        // Group by facility using cursor operations
        val facilityGroups = mutableMapOf<String, MutableList<Double>>()
        for (i in 0 until cursor.a) {
            val row = cursor.at(i)
            val facility = row.getString(1) ?: ""
            val cpu = row.getDouble(4) ?: 0.0
            facilityGroups.getOrPut(facility) { mutableListOf() }.add(cpu)
        }
        
        val cursorAggregates = facilityGroups.mapValues { (_, cpus) ->
            cpus.average()
        }
        
        // Then: Compare with direct aggregation
        val directAggregates = mapOf(
            "facility0" to lsmr.reduceByFacility("region1", "facility0").avgCpu,
            "facility1" to lsmr.reduceByFacility("region1", "facility1").avgCpu
        )
        
        println("\n=== CURSOR vs DIRECT AGGREGATION ===")
        cursorAggregates.forEach { (facility, avg) ->
            println("Cursor - $facility: $avg")
            println("Direct - $facility: ${directAggregates[facility]}")
            assertEquals(
                directAggregates[facility] ?: 0.0,
                avg,
                0.001,
                "Aggregations should match"
            )
        }
    }
    
    @Test
    fun `demonstrate statistical preservation through re-reduce`() {
        // This is the key innovation of the CouchDB pattern
        
        println("\n=== STATISTICAL PRESERVATION IN RE-REDUCE ===")
        
        // Phase 1: Initial aggregation
        val hourlyStats = listOf(
            SimpleLSMR.AggregateStats(
                avgCpu = 20.0, minCpu = 10.0, maxCpu = 30.0, sumCpu = 2000.0,
                avgMemory = 1000.0, minMemory = 500.0, maxMemory = 1500.0, sumMemory = 100000.0,
                count = 100
            ),
            SimpleLSMR.AggregateStats(
                avgCpu = 40.0, minCpu = 20.0, maxCpu = 60.0, sumCpu = 4000.0,
                avgMemory = 2000.0, minMemory = 1000.0, maxMemory = 3000.0, sumMemory = 200000.0,
                count = 100
            ),
            SimpleLSMR.AggregateStats(
                avgCpu = 60.0, minCpu = 40.0, maxCpu = 80.0, sumCpu = 3000.0,
                avgMemory = 3000.0, minMemory = 2000.0, maxMemory = 4000.0, sumMemory = 150000.0,
                count = 50
            )
        )
        
        println("Hourly aggregates:")
        hourlyStats.forEachIndexed { i, stats ->
            println("  Hour $i: Avg CPU = ${stats.avgCpu}, Count = ${stats.count}")
        }
        
        // Phase 2: Re-reduce (the magic happens here)
        val totalCount = hourlyStats.sumOf { it.count }
        val dailyStats = SimpleLSMR.AggregateStats(
            avgCpu = hourlyStats.sumOf { it.sumCpu } / totalCount,  // Correct weighted average!
            minCpu = hourlyStats.minOf { it.minCpu },  // True minimum
            maxCpu = hourlyStats.maxOf { it.maxCpu },  // True maximum
            sumCpu = hourlyStats.sumOf { it.sumCpu },
            avgMemory = hourlyStats.sumOf { it.sumMemory } / totalCount,
            minMemory = hourlyStats.minOf { it.minMemory },
            maxMemory = hourlyStats.maxOf { it.maxMemory },
            sumMemory = hourlyStats.sumOf { it.sumMemory },
            count = totalCount
        )
        
        println("\nDaily aggregate (re-reduced):")
        println("  Avg CPU = ${dailyStats.avgCpu}")
        println("  Min CPU = ${dailyStats.minCpu}")
        println("  Max CPU = ${dailyStats.maxCpu}")
        println("  Total Count = ${dailyStats.count}")
        
        // Verify correctness
        val expectedAvg = (2000.0 + 4000.0 + 3000.0) / 250  // 36.0
        assertEquals(expectedAvg, dailyStats.avgCpu, 0.001)
        
        // Show what would happen with naive averaging (WRONG!)
        val naiveAvg = (20.0 + 40.0 + 60.0) / 3  // 40.0
        println("\nNaive average of averages (WRONG): $naiveAvg")
        println("Correct weighted average: ${dailyStats.avgCpu}")
        println("Difference: ${naiveAvg - dailyStats.avgCpu}")
        
        assertTrue(
            kotlin.math.abs(naiveAvg - dailyStats.avgCpu) > 1.0,
            "Naive averaging gives different (wrong) result"
        )
    }
}