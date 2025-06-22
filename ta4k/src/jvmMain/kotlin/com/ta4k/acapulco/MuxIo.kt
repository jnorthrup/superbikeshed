// =====================================================================
// === TrikeShed/src/jvmMain/kotlin/borg/trikeshed/acapulco/MuxIo.kt ===
// =====================================================================
package borg.trikeshed.acapulco

import borg.trikeshed.acapulco.model.AssetKey
import borg.trikeshed.acapulco.model.AssetModel
import borg.trikeshed.acapulco.model.ITradingWallet
import borg.trikeshed.acapulco.node.config.Help
import borg.trikeshed.cursor.*
import borg.trikeshed.lib.*
import borg.trikeshed.common.collections.CirQlar
import borg.trikeshed.lib.logDebug
import kotlinx.coroutines.*
import kotlin.time.Duration

class MuxIo @JvmOverloads constructor(
    streamer: Streamer,
    val coins: CoinsAndPairings = CoinsAndPairings(streamer.bFac),
    val tradingWallet: ITradingWallet = streamer.tradingWallet,
    val muxers: Map<AssetKey, TradePairEventMuxer> = streamer.eventMuxers.filterKeys { it !in AssetModel.hidden },
    historySize: Int = 6.hours.inWholeMinutes.toInt(),
    val historyViewSize: Int = 25,
    var keepalive: () -> Boolean = { true },
    val timeWindow: Int = 14.days.inWholeMinutes.toInt(),
    val hzWidth: Int = Help.horizonDepthMinutes.value.toInt(),
) {
    val cursHorizon: CirQlar<Indexed<RowVec>> = CirQlar(historySize)

    init {
        logDebug { "Horizon window examples (0-19): ${(0 until 20).map { horizon(it, hzWidth, timeWindow) }}" }
    }

    val allTime: Indexed<Indexed<RowVec>>
        get() {
            val currentHistorySize = min(historyViewSize, cursHorizon.size)
            if (currentHistorySize == 0) return emptyIndex()
            val indicesToFetch = (0 until currentHistorySize).map { x -> horizon(x, historyViewSize, cursHorizon.size) }
            return indicesToFetch.size j { i:Int -> cursHorizon[indicesToFetch[i]] }
        }

    suspend fun publish(delayDuration: Duration = 30.seconds) {
        while (keepalive()) {
            val startTime = System.currentTimeMillis()
            try {
                coroutineScope {
                    val rowsToOffer: Indexed<RowVec> = muxers.map { (assetKey, mux) ->
                        async {
                            val (TC, CC) = assetKey
                            val baseCost = coins.pathValue(TC, CC)
                            val (cursorSnapshot, intraCount) = mux.realtime
                            assembleRow(cursorSnapshot, assetKey, intraCount, baseCost, timeWindow, hzWidth, tradingWallet)
                        }
                    }.awaitAll().toSeries()
                    if (rowsToOffer.isNotEmpty()) {
                        cursHorizon.offer(rowsToOffer)
                    }
                }
            } catch (t: Throwable) {
                System.err.println("Error in MuxIo publish loop: ${t.message}")
            } finally {
                val elapsed = System.currentTimeMillis() - startTime
                val waitTime = (delayDuration.inWholeMilliseconds - elapsed).coerceAtLeast(0)
                if (waitTime > 0) delay(waitTime)
            }
        }
    }

    companion object {
        val normTime: Boolean = Help.NormalizeInstants.value.equals("true", ignoreCase = true)
    }
}
