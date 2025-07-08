@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.lsmr

import kotlinx.datetime.Instant

/**
 * Shared types for LSM-R implementation and tests
 */

data class MetricReading(
    val timestamp: Instant,
    val deviceId: String,
    val facilityId: String,
    val regionId: String,
    val cpu: Double,
    val memory: Double,
    val disk: Double
)

data class HierarchicalKey(
    val region: String,
    val facility: String,
    val device: String,
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int
) : Comparable<HierarchicalKey> {
    override fun compareTo(other: HierarchicalKey): Int {
        return compareValuesBy(this, other,
            { it.region }, { it.facility }, { it.device },
            { it.year }, { it.month }, { it.day },
            { it.hour }, { it.minute }
        )
    }
}

data class StatisticalAggregate(
    val sum: Double,
    val avg: Double,
    val min: Double,
    val max: Double,
    val count: Long
)

data class HourlyAggregate(
    val region: String,
    val facility: String,
    val device: String,
    val hour: Int,
    val stats: StatisticalAggregate
)