package borg.trikeshed.lsmr

import borg.trikeshed.cursor.*
import borg.trikeshed.lib.*

/**
 * Simplified LSM-R implementation focusing on the cascading MapReduce pattern
 */
class SimpleLSMR {
    
    // In-memory storage for demonstration
    private val deviceData = mutableListOf<Pair<HierarchicalKey, MetricReading>>()
    private val facilityAggregates = mutableMapOf<String, AggregateStats>()
    private val regionAggregates = mutableMapOf<String, AggregateStats>()
    
    data class AggregateStats(
        val avgCpu: Double,
        val minCpu: Double,
        val maxCpu: Double,
        val sumCpu: Double,
        val avgMemory: Double,
        val minMemory: Double,
        val maxMemory: Double,
        val sumMemory: Double,
        val count: Long
    )
    
    fun write(key: HierarchicalKey, value: MetricReading) {
        deviceData.add(key to value)
        
        // Trigger cascading aggregation
        updateFacilityAggregate(key.facility)
        updateRegionAggregate(key.region)
    }
    
    private fun updateFacilityAggregate(facility: String) {
        val facilityReadings = deviceData
            .filter { it.first.facility == facility }
            .map { it.second }
        
        if (facilityReadings.isNotEmpty()) {
            facilityAggregates[facility] = computeStats(facilityReadings)
        }
    }
    
    private fun updateRegionAggregate(region: String) {
        val regionReadings = deviceData
            .filter { it.first.region == region }
            .map { it.second }
        
        if (regionReadings.isNotEmpty()) {
            regionAggregates[region] = computeStats(regionReadings)
        }
    }
    
    fun reduceByFacility(region: String, facility: String): AggregateStats {
        return facilityAggregates["$region/$facility"] 
            ?: computeStats(
                deviceData
                    .filter { it.first.region == region && it.first.facility == facility }
                    .map { it.second }
            )
    }
    
    fun reduceByRegion(region: String): AggregateStats {
        return regionAggregates[region]
            ?: computeStats(
                deviceData
                    .filter { it.first.region == region }
                    .map { it.second }
            )
    }
    
    fun reduceGlobal(): AggregateStats {
        return computeStats(deviceData.map { it.second })
    }
    
    fun asCursor(): borg.trikeshed.cursor.Cursor {
        val rows = deviceData.map { (key, reading) ->
            listOf(
                reading.deviceId,
                reading.facilityId,
                reading.regionId,
                reading.timestamp.toString(),
                reading.cpu,
                reading.memory,
                reading.disk
            )
        }
        
        return cursorOf(
            rows,
            listOf("device", "facility", "region", "timestamp", "cpu", "memory", "disk"),
            listOf(
                IOMemento.IoString,
                IOMemento.IoString,
                IOMemento.IoString,
                IOMemento.IoString,
                IOMemento.IoDouble,
                IOMemento.IoDouble,
                IOMemento.IoDouble
            )
        )
    }
    
    private fun computeStats(readings: List<MetricReading>): AggregateStats {
        if (readings.isEmpty()) {
            return AggregateStats(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0)
        }
        
        val cpuValues = readings.map { it.cpu }
        val memoryValues = readings.map { it.memory }
        
        return AggregateStats(
            avgCpu = cpuValues.average(),
            minCpu = cpuValues.minOrNull() ?: 0.0,
            maxCpu = cpuValues.maxOrNull() ?: 0.0,
            sumCpu = cpuValues.sum(),
            avgMemory = memoryValues.average(),
            minMemory = memoryValues.minOrNull() ?: 0.0,
            maxMemory = memoryValues.maxOrNull() ?: 0.0,
            sumMemory = memoryValues.sum(),
            count = readings.size.toLong()
        )
    }
}