// =====================================================================
// === TrikeShed/src/jvmMain/kotlin/borg/trikeshed/acapulco/Streamer.kt ===
// =====================================================================
package borg.trikeshed.acapulco

import borg.trikeshed.acapulco.model.AssetKey
import borg.trikeshed.acapulco.model.AssetModel
import borg.trikeshed.acapulco.model.DataBinanceVision
import borg.trikeshed.acapulco.node.config.Help // Assuming ported
import borg.trikeshed.lib.debug
import borg.trikeshed.lib.logDebug
import com.binance.api.client.BinanceApiClientFactory
import com.binance.api.client.domain.account.Account
import com.binance.api.client.domain.event.CandlestickEvent
import com.binance.api.client.domain.market.CandlestickInterval.ONE_MINUTE
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*

/**
 * * [ ]   api credential stuff
 * * [ ]   user/accout state stuff
 * * [ ]   network state stuff
 * * [ ]   source of truth stuff
 * * [x]   historical io / disk chunks stuff
 */
class Streamer(val bFac: BinanceApiClientFactory, coins: CoinsAndPairings) { // Assume CoinsAndPairings is ported

    val tradingWallet = TradingWallet(coins) // Assumes TradingWallet is ported
    private val assetMapper = AssetKeyMapper(sortedMapOf()).also { am -> // Assumes AssetKeyMapper is ported
        AssetModel.assetOracle.keys.forEach { assetKey -> am + (assetKey) } // Assumes AssetModel is ported
    }

    val eventMuxers = AssetModel.assetOracle.map { (k, v) ->
        k to TradePairEventMuxer(v) // Assumes TradePairEventMuxer is ported
    }.toMap()

    fun fsm() {
        System.err.println("Looping in views...") // Keep logging
        val restClient = bFac.newRestClient()
        val (listenKey, currentAccount) = tradingWallet.initWallet(restClient)
        tradingWallet.byValue.take(10).also {
            logDebug { it.toString() } // Use Trikeshed logDebug
        }

        val wsclient = bFac.newWebSocketClient()
        val requestedSymbols = AssetModel.assetOracle.keys.map { k: AssetKey ->
            k.binanceEventRequestSymbol // Use ported AssetKey property
        }.joinToString(",").also {
            logDebug { "Requesting symbols: $it" }
        }

        var runningTally = 0
        val fibonacciReporter = FibonacciReporter(noun = "Candlestick Events") // Use Trikeshed FibonacciReporter

        // User data stream setup (keep as is, relies on Binance client)
        wsclient.onUserDataUpdateEvent(listenKey) {
            val balances = it.accountUpdateEvent.balances
            tradingWallet.updateWallet(balances)
            logDebug { "------USERDATA------ EventType: ${it.eventType}\n$it" }
        }.also { tradingWallet.walletCloseHandle = it }

        // Candlestick event stream setup
        wsclient.onCandlestickEvent(requestedSymbols, ONE_MINUTE) { event: CandlestickEvent ->
            runBlocking { // Consider launching in a dedicated scope?
                launch {
                    try {
                        fibonacciReporter.report(runningTally++)?.also { logDebug { it } } // Use ported reporter

                        val assetKey = assetMapper[event.symbol] // Use ported mapper
                            ?: throw Error("Symbol mismatch: ${event.symbol}. Mapper: $assetMapper")
                        eventMuxers[assetKey]?.push(event) // Use ported muxer and push
                            ?: logDebug { "Warning: No event muxer found for asset key: $assetKey" }
                    } catch (e: Throwable) {
                        System.err.println("Error processing candlestick event for ${event.symbol}: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }.also {
            tradingWallet.tickerCloseHandle = it
        }

        // Book ticker event stream setup
        wsclient.onBookTickerEvent(requestedSymbols) {
            val assetKey = assetMapper[it.symbol]
            if (assetKey != null) {
                val assetModel = AssetModel[assetKey] // Use ported AssetModel companion object access
                assetModel?.book = it // Update book ticker in the model
            } else {
                 logDebug { "Warning: Could not map book ticker symbol ${it.symbol} to AssetKey."}
            }
            // logDebug { "------------BOOKTICKER------------\n$it" } // Optional debug logging
        }.also { tradingWallet.bookHandle = it }

        /* Depth event stream commented out in original
        wsclient.onDepthEvent(requestedSymbols) {
           val assetModel = AssetModel[assetMapper[it.symbol]!!]
            assetModel?.depth=it
//            logDebug { "------------DEPTH\\n$it" }
        }
        */
    }

    // These seem unused in fsm(), might be needed elsewhere or are leftovers
    lateinit var listenKey: String
    lateinit var currentAccount: Account

    companion object {
        val episodeCutoff: Int by lazy { Help.episodeLength.value.toInt() }

        // Define klineSimpleScalar using Trikeshed types
        val klineSimpleScalar: Indexed<ColumnMeta> by lazy {
            DataBinanceVision.klines.let { klinesSpec ->
                klinesSpec.names.zip(klinesSpec.types.asIterable()) { name, type ->
                    ColumnMeta(name, type) // Assuming TypeMemento is compatible/mapped
                }.toSeries() // Convert the list of ColumnMeta to a Indexed
            }
        }
    }
}
