package com.ta4k.acapulco

import borg.trikeshed.lib.Series
import borg.trikeshed.lib.toList
import com.ta4k.core.model.Kline
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Handles visualization of trading data using SpaceGraph.
 */
class SpaceGraphVisualizer {
    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC)
        
        /**
         * Creates a SpaceGraph visualization of kline data.
         * @param klines Series of Klines to visualize
         * @return SpaceGraph visualization data
         */
        fun visualizeKlines(klines: Series<Kline>): SpaceGraphData {
            val nodes = mutableListOf<Node>()
            val edges = mutableListOf<Edge>()
            
            // Create nodes for each kline
            klines.forEachIndexed { index, kline ->
                val nodeId = "kline_$index"
                nodes.add(
                    Node(
                        id = nodeId,
                        label = formatKlineLabel(kline),
                        x = index.toDouble(),
                        y = kline.closePrice.toDouble(),
                        size = kline.volume.toDouble(),
                        color = getKlineColor(kline)
                    )
                )
                
                // Connect to previous kline
                if (index > 0) {
                    val prevKline = klines.toList()[index - 1]
                    edges.add(
                        Edge(
                            source = "kline_${index - 1}",
                            target = nodeId,
                            weight = calculateEdgeWeight(prevKline, kline)
                        )
                    )
                }
            }
            
            return SpaceGraphData(nodes, edges)
        }
        
        private fun formatKlineLabel(kline: Kline): String {
            val time = DATE_FORMATTER.format(Instant.ofEpochMilli(kline.openTimeMillis))
            return "$time\nO: ${kline.openPrice}\nH: ${kline.highPrice}\nL: ${kline.lowPrice}\nC: ${kline.closePrice}"
        }
        
        private fun getKlineColor(kline: Kline): String {
            return if (kline.closePrice >= kline.openPrice) {
                "#00ff00" // Green for bullish
            } else {
                "#ff0000" // Red for bearish
            }
        }
        
        private fun calculateEdgeWeight(prev: Kline, curr: Kline): Double {
            val priceChange = curr.closePrice.subtract(prev.closePrice).abs()
            val volumeChange = curr.volume.subtract(prev.volume).abs()
            return (priceChange.toDouble() + volumeChange.toDouble()) / 2.0
        }
    }
}

data class SpaceGraphData(
    val nodes: List<Node>,
    val edges: List<Edge>
)

data class Node(
    val id: String,
    val label: String,
    val x: Double,
    val y: Double,
    val size: Double,
    val color: String
)

data class Edge(
    val source: String,
    val target: String,
    val weight: Double
) 