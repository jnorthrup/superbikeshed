// =====================================================================
// === TrikeShed/src/jvmMain/kotlin/borg/trikeshed/acapulco/RealtimeStreamer.kt ===
// =====================================================================
package borg.trikeshed.acapulco // Adjusted package

import borg.trikeshed.acapulco.model.AssetKey
import borg.trikeshed.acapulco.model.AssetModel
import borg.trikeshed.acapulco.model.DataBinanceVision
import borg.trikeshed.acapulco.node.config.Help // Assuming ported
import borg.trikeshed.lib.debug
import borg.trikeshed.lib.logDebug
import com.binance.api.client.BinanceApiClientFactory
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.io.path.Path
import kotlin.io.path.pathString // Use pathString for ProcessBuilder
import kotlin.io.path.exists // Use exists extension
import kotlin.time.Duration.Companion.seconds

class RealtimeStreamer(
    val bFac: BinanceApiClientFactory, // Keep as is
    val coins: CoinsAndPairings, // Assumes ported
    val fourThreds: ExecutorCoroutineDispatcher, // Keep as is
    val usedSymbols: MutableList<AssetKey>, // Assumes AssetKey is ported
) {
    fun init() = runBlocking { // Keep coroutine structure
        logDebug { "Up-to-date assets: ${AssetModel.assetOracle.keys}" } // Use AssetModel from ported code
        val streamer = Streamer(bFac, coins) // Assumes Streamer is ported
        launch { streamer.fsm() }
        delay(10.seconds) // Keep delay
        val purpose = MuxIo(streamer) // Assumes MuxIo is ported
        launch {
            purpose.publish(30.seconds) // Use ported MuxIo
        }
        launch(fourThreds) { // Launch on provided dispatcher
            usedSymbols.forEach { assetKey -> // Use AssetKey
                launch { // Launch coroutine per symbol
                    try {
                        val scriptPath = Path(Help.binDir.value).resolve("dayklines.sh")
                        if (!scriptPath.exists()) {
                            System.err.println("dayklines.sh not found at $scriptPath")
                             return@launch // Skip if script doesn't exist
                        }
                        val proc = ProcessBuilder().directory(Path(Help.tmpDir.value).toFile()).command(
                            "bash",
                            scriptPath.pathString, // Use pathString
                            assetKey.tradeAsset,
                            assetKey.counterAsset
                        ).redirectErrorStream(true) // Redirect error stream for easier debugging
                         .also { logDebug { "Running command: ${it.command().joinToString(" ")}" } }
                         .start()

                        val outputLines = proc.inputStream.bufferedReader().readLines()
                        val exitCode = proc.waitFor()

                         if (exitCode != 0 || outputLines.isEmpty()) {
                             System.err.println("dayklines.sh for $assetKey failed (code $exitCode) or produced no output.")
                             outputLines.forEach { System.err.println("Output: $it") } // Log output on error
                             return@launch
                         }

                        // Assuming the last line contains space-separated paths to new CSV chunks
                        val lastLine = outputLines.last()
                        val tempCsvPaths = lastLine.split("\\s+".toRegex()).filter { it.isNotBlank() }

                        if (tempCsvPaths.isEmpty()) {
                            logDebug { "No new CSV chunks reported by dayklines.sh for $assetKey" }
                             return@launch
                        }

                        logDebug { "Processing new chunks for $assetKey: $tempCsvPaths" }
                        val newEpisodeCursors = tempCsvPaths.mapNotNull { tempCsvPath ->
                            try {
                                // Use ported fixOpaqueCsv
                                val cursor = HistoryService.fixOpaqueCsv(
                                    tempCsvPath,
                                    DataBinanceVision.klines,
                                    DataBinanceVision.klines.names.toSeries() // Pass headers
                                )
                                // TODO: Optionally delete the temp file: Files.deleteIfExists(Paths.get(tempCsvPath))
                                cursor.takeIf { it.isNotEmpty() }
                            } catch (e: Exception) {
                                System.err.println("Error processing chunk $tempCsvPath for $assetKey: ${e.message}")
                                null
                            }
                        }

                        if (newEpisodeCursors.isNotEmpty()) {
                            // Push the new cursors to the AssetModel
                            AssetModel.push(assetKey, *newEpisodeCursors.toTypedArray())
                            logDebug { "Pushed ${newEpisodeCursors.size} new cursor(s) for $assetKey" }
                        } else {
                             logDebug { "No valid cursors generated from new chunks for $assetKey" }
                        }

                    } catch (e: Exception) {
                        System.err.println("Error during realtime update for $assetKey: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}
