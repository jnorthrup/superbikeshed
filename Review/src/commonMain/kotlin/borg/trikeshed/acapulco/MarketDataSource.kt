package borg.trikeshed.acapulco

import borg.trikeshed.lib.Series

interface MarketDataSource {
    suspend fun nextTick(): MarketTick?
    val assets: Set<String>  // AssetKey as String for simplicity, as per proposal
}

data class MarketTick(
    val timestamp: Long,  // Using Long for timestamp as in TrikeShed primitives
    val data: Map<String, TickData>  // Map for assets, leveraging KMP common code
    // Add potential extensions if needed, but keeping it minimal as per proposal
)

data class TickData(
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
    // Add other fields as necessary, e.g., bid, ask
)