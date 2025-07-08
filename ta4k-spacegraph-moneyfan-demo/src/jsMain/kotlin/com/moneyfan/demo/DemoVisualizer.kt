package com.example.demo

import com.example.spacegraphkt.api.AgentAPI
import com.example.spacegraphkt.api.jsObject // Helper for creating JS objects for AgentAPI
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.`play`
// Data classes (VisualGraphPointWithMoneyfanOutcome, etc.) and Enums (TASignalType, MoneyfanActionType)
// are assumed to be in this package (from DemoDataClasses.kt) or correctly imported.
import com.ta4k.core.model.BigDecimal // Using BigDecimal from ta4k

/**
 * Handles the visualization of kline data, TA features, and Moneyfan outcomes
 * using SpaceGraph via the AgentAPI.
 *
 * @property agentApi The AgentAPI instance for interacting with SpaceGraph.
 */
class DemoVisualizer(internal val agentApi: AgentAPI) {

    internal var klineNodeIds = mutableListOf<String?>() // Stores IDs of created kline nodes for linking/updates

    /**
     * Clears the entire graph in SpaceGraph.
     */
    fun clearGraph() {
        agentApi.clearGraph()
        klineNodeIds.clear()
        console.log("DemoVisualizer: SpaceGraph cleared.")
    }

    /**
     * Displays the augmented kline data (including TA and Moneyfan outcomes) in SpaceGraph.
     * Creates nodes for each data point and edges to connect them sequentially.
     * Node content and styling reflect the data.
     *
     * @param augmentedVisualData The DSEL Indexed of data points to visualize.
     */
    fun displayDataWithMoneyfanOutcomes(
        augmentedVisualData: Indexed<VisualGraphPointWithMoneyfanOutcome>
    ) {
        clearGraph() // Start with a fresh graph

        val points = augmentedVisualData.`play`.toList()
        if (points.isEmpty()) {
            console.log("DemoVisualizer: No augmented visual data to display.")
            return
        }

        klineNodeIds = MutableList<String?>(points.size) { null }

        var minClose = points.firstOrNull()?.kline?.closePrice?.toDouble() ?: 0.0
        var maxClose = minClose
        points.forEach { vp ->
            val close = vp.kline.closePrice.toDouble()
            if (close < minClose) minClose = close
            if (close > maxClose) maxClose = close
        }
        val priceRange = if ((maxClose - minClose) > 0.00001) maxClose - minClose else 1.0

        val firstTimestamp = points.firstOrNull()?.kline?.openTimeMillis ?: 0L
        val lastTimestamp = points.lastOrNull()?.kline?.openTimeMillis ?: (firstTimestamp + points.size * 60000L) // Estimate if last is missing
        val timeRange = if (lastTimestamp > firstTimestamp) (lastTimestamp - firstTimestamp).toDouble() else (points.size * 60000.0) // Avoid 0 timeRange

        val defaultNodeWidth = 250.0
        val defaultNodeHeight = 185.0

        points.forEachIndexed { index, vp ->
            val nodeId = "kline_mf_${vp.index}"
            klineNodeIds[index] = nodeId

            // --- Node Content (Corrected) ---
            val deviationStr = vp.deviationPercent?.let { "${(it * 100).toFixed(2)}%" } ?: "N/A"
            val taSignalStr = if (vp.taSignal == TASignalType.NEUTRAL) "" else "TA: <strong>${vp.taSignal.name.replace('_', ' ')}</strong><br>"
            val moneyfanActionStr = "MF Action: <strong>${vp.moneyfanAction.name.replace('_', ' ')}</strong>"
            val adzStr = if (vp.adzActive) "<span style='color: #FFA500;'>ADZ Active</span><br>" else ""
            val cpStr = if (vp.cpActiveGlobally) "<span style='color: #FF6347; font-weight:bold;'>CP GLOBAL!</span><br>" else ""

            val line1 = "$cpStr<strong>${vp.assetSymbol} @ ${vp.kline.closePrice.toFixed(2)}</strong> (Idx ${vp.index})<br>"
            val line2 = "Time: ${formatTimestamp(vp.kline.openTimeMillis)}<br>"
            val line3 = "$adzStr Baseline: ${vp.baseline?.toFixed(2) ?: "N/A"}, Dev: $deviationStr<br>"
            val line4 = "SMA(${DselInterfaceAugmented.shortSmaPeriod}): ${vp.smaShort?.toFixed(2) ?: "N/A"}<br>"
            val line5 = "SMA(${DselInterfaceAugmented.longSmaPeriod}): ${vp.smaLong?.toFixed(2) ?: "N/A"}<br>"
            val line6 = "RSI(${DselInterfaceAugmented.rsiPeriod}): ${vp.rsi?.toFixed(2) ?: "N/A"}<br>"
            val line7 = "$taSignalStr$moneyfanActionStr"
            val nodeContent = listOf(line1, line2, line3, line4, line5, line6, line7).joinToString("")


            // --- Node Styling based on Moneyfan Action & TA ---
            var nodeColor = "rgba(80, 80, 100, 0.85)"
            var currentWidth = defaultNodeWidth
            var currentHeight = defaultNodeHeight

            when (vp.moneyfanAction) {
                MoneyfanActionType.SUGGEST_HARVEST -> nodeColor = "rgba(70, 180, 70, 0.9)"
                MoneyfanActionType.OPPORTUNITY_ADZ_SELL -> nodeColor = "rgba(120, 200, 120, 0.9)"
                MoneyfanActionType.SUGGEST_REBALANCE_BUY -> nodeColor = "rgba(70, 100, 200, 0.9)"
                MoneyfanActionType.OPPORTUNITY_ADZ_BUY -> nodeColor = "rgba(120, 150, 220, 0.9)"
                MoneyfanActionType.HOLD_DUE_TO_CP -> { nodeColor = "rgba(200, 50, 50, 0.95)"; currentWidth += 10; currentHeight += 5; }
                MoneyfanActionType.NO_ACTION_NO_BASELINE -> nodeColor = "rgba(120, 120, 120, 0.8)"
                MoneyfanActionType.HOLD -> {
                    if (vp.adzActive) nodeColor = "rgba(200, 150, 50, 0.85)"
                    else when (vp.taSignal) {
                        TASignalType.STRONG_BUY, TASignalType.BUY -> nodeColor = "rgba(80, 100, 150, 0.85)"
                        TASignalType.STRONG_SELL, TASignalType.SELL -> nodeColor = "rgba(150, 100, 80, 0.85)"
                        else -> {}
                    }
                }
            }

            val xPos = if (timeRange > 0.0 && points.size > 1) {
                ((vp.kline.openTimeMillis - firstTimestamp) / timeRange) * (points.size * defaultNodeWidth * 0.55)
            } else {
                vp.index * (defaultNodeWidth * 0.65)
            }
            val yPos = if (priceRange > 0.00001) {
                 ((vp.kline.closePrice.toDouble() - minClose) / priceRange - 0.5) * -700.0
            } else { 0.0 }


            val nodeConfig = jsObject {
                this.id = nodeId; this.type = "note"
                this.position = jsObject {
                    this.x = xPos; this.y = yPos
                    this.z = when {
                        vp.moneyfanAction == MoneyfanActionType.HOLD_DUE_TO_CP -> -50.0
                        vp.taSignal == TASignalType.STRONG_BUY || vp.taSignal == TASignalType.STRONG_SELL -> -30.0
                        vp.moneyfanAction != MoneyfanActionType.HOLD && vp.moneyfanAction != MoneyfanActionType.NO_ACTION_NO_BASELINE -> -15.0
                        else -> 0.0
                    }
                }
                this.data = jsObject {
                    this.label = "${vp.assetSymbol}-${vp.index}"; this.content = nodeContent
                    this.width = currentWidth; this.height = currentHeight
                    this.backgroundColor = nodeColor; this.editable = false
                    this.custom = jsObject {
                        this.taSignal = vp.taSignal.name; this.moneyfanAction = vp.moneyfanAction.name
                        this.deviation = vp.deviationPercent; this.adz = vp.adzActive; this.cp = vp.cpActiveGlobally
                    }
                }
            }
            agentApi.addNode(id = nodeId, nodeConfigJs = nodeConfig)
        }

        for (i in 0 until klineNodeIds.size - 1) {
            val sourceNodeId = klineNodeIds[i]; val targetNodeId = klineNodeIds[i + 1]
            if (sourceNodeId != null && targetNodeId != null) {
                agentApi.addEdge(id = null, sourceNodeId = sourceNodeId, targetNodeId = targetNodeId,
                    edgeConfigJs = jsObject { this.data = jsObject { this.color = 0x506070; this.thickness = 1.2 } })
            }
        }
        agentApi.kickLayout(1.0)
        console.log("DemoVisualizer: Displayed ${points.size} kline data points.")
    }

    fun highlightMoneyfanAction(
        actionToHighlight: MoneyfanActionType?,
        fullVisualData: Indexed<VisualGraphPointWithMoneyfanOutcome>
    ) {
        if (klineNodeIds.isEmpty() || fullVisualData.size != klineNodeIds.size) {
            console.warn("DemoVisualizer: Cannot highlight, data mismatch or empty.")
            return
        }

        fullVisualData.`play`.forEachIndexed { index, vp ->
            val nodeId = klineNodeIds.getOrNull(index) ?: return@forEachIndexed

            var baseBgColor = "rgba(80, 80, 100, 0.85)"; var baseScale = 0.95; var baseZ = 0.0
            var baseWidth = 250.0 * 0.9; var baseHeight = 185.0*0.9;
            when (vp.moneyfanAction) {
                MoneyfanActionType.SUGGEST_HARVEST -> baseBgColor = "rgba(70, 180, 70, 0.8)"
                MoneyfanActionType.OPPORTUNITY_ADZ_SELL -> baseBgColor = "rgba(120, 200, 120, 0.8)"
                MoneyfanActionType.SUGGEST_REBALANCE_BUY -> baseBgColor = "rgba(70, 100, 200, 0.8)"
                MoneyfanActionType.OPPORTUNITY_ADZ_BUY -> baseBgColor = "rgba(120, 150, 220, 0.8)"
                MoneyfanActionType.HOLD_DUE_TO_CP -> { baseBgColor = "rgba(200, 50, 50, 0.9)"; baseWidth *=1.05; baseHeight *=1.05; baseScale=1.0; baseZ = -50.0 }
                MoneyfanActionType.NO_ACTION_NO_BASELINE -> baseBgColor = "rgba(120, 120, 120, 0.7)"
                MoneyfanActionType.HOLD -> {
                     if (vp.adzActive) baseBgColor = "rgba(200, 150, 50, 0.8)"
                     else when (vp.taSignal) {
                        TASignalType.STRONG_BUY || TASignalType.BUY -> baseBgColor = "rgba(80, 100, 150, 0.8)"
                        TASignalType.STRONG_SELL || TASignalType.SELL -> baseBgColor = "rgba(150, 100, 80, 0.8)"
                        else -> {}
                    }
                }
            }
            if (baseZ == 0.0 && (vp.taSignal == TASignalType.STRONG_BUY || vp.taSignal == TASignalType.STRONG_SELL)) baseZ = -30.0


            var finalBgColor = baseBgColor; var finalScale = baseScale; var finalZ = baseZ
            var finalWidth = baseWidth; var finalHeight = baseHeight;


            if (actionToHighlight != null && vp.moneyfanAction == actionToHighlight &&
                actionToHighlight != MoneyfanActionType.HOLD && actionToHighlight != MoneyfanActionType.NO_ACTION_NO_BASELINE) {
                finalBgColor = "rgba(255, 255, 0, 0.95)" // Bright Yellow
                finalScale = 1.05; finalWidth *= 1.1; finalHeight *= 1.1;
                finalZ = -60.0
            }

            val updateNodeData = jsObject { this.data = jsObject {
                this.backgroundColor = finalBgColor;
                this.contentScale = finalScale;
                this.width = finalWidth; // AgentAPI needs to support width/height update in updateNodeData's data field
                this.height = finalHeight;
            } }
            agentApi.updateNodeData(nodeId, updateNodeData)

            val currentNodeJs = agentApi.getNode(nodeId)?.asDynamic()
            val currentPosition = currentNodeJs?.position?.unsafeCast<dynamic>()
            if (currentPosition != null && currentPosition.z != finalZ) {
                 agentApi.updateNodePosition(nodeId, jsObject{
                    this.x = currentPosition.x; this.y = currentPosition.y; this.z = finalZ;
                 }, false)
            }
        }
        agentApi.kickLayout(0.3)
        console.log("DemoVisualizer: Highlighted attention for Moneyfan Action: $actionToHighlight")
    }
}

// --- Helper Formatting Functions ---
fun Double.toFixed(digits: Int): String = this.asDynamic().toFixed(digits) as String
fun BigDecimal.toFixed(digits: Int): String = this.toDouble().toFixed(digits)

fun formatTimestamp(timestampMillis: Long): String {
    if (timestampMillis <= 0) return "Invalid Date"
    val date = kotlin.js.Date(timestampMillis.toDouble()) // kotlin.js.Date needs Double
    val hours = date.getHours().pad(2)
    val minutes = date.getMinutes().pad(2)
    val seconds = date.getSeconds().pad(2)
    return "$hours:$minutes:$seconds"
}

fun Int.pad(length: Int): String = this.toString().padStart(length, '0')
