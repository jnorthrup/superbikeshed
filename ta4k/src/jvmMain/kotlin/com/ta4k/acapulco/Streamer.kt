package com.ta4k.acapulco

import com.ta4k.acapulco.model.AssetKey
import com.ta4k.acapulco.model.AssetModel
import com.ta4k.acapulco.model.DataBinanceVision
import com.ta4k.acapulco.config.Help
import borg.trikeshed.lib.debug
import borg.trikeshed.lib.logDebug
import com.binance.api.client.BinanceApiClientFactory
import com.binance.api.client.domain.account.Account
import com.binance.api.client.domain.event.CandlestickEvent
import com.binance.api.client.domain.market.CandlestickInterval.ONE_MINUTE
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*

class Streamer(val bFac: BinanceApiClientFactory, coins: CoinsAndPairings) {
    val tradingWallet = TradingWallet(coins)
    private val assetMapper = AssetKeyMapper(sortedMapOf()).also { am ->
        AssetModel.assetOracle.keys.forEach { assetKey -> am + (assetKey) }
    }

    val eventMuxers = AssetModel.assetOracle.map { (k, v) ->
        k to TradePairEventMuxer(v)
    }.toMap()

    fun fsm() {
        System.err.println("Looping in views...")
        val restClient = bFac.newRestClient()
        val (listenKey, currentAccount) = tradingWallet.initWallet(restClient)
        tradingWallet.byValue.take(10).also {
            logDebug { it.toString() }
        }

        val wsclient = bFac.newWebSocketClient()
        val requestedSymbols = AssetModel.assetOracle.keys.map { k: AssetKey ->
            k.binanceEventRequestSymbol
        }.joinToString(",").also {
            logDebug { "Requesting symbols: $it" }
        }

        var runningTally = 0
        val fibonacciReporter = FibonacciReporter(noun = "Candlestick Events")

        wsclient.onUserDataUpdateEvent(listenKey) {
            val balances = it.accountUpdateEvent.balances
            tradingWallet.updateWallet(balances)
            logDebug { "------USERDATA------ EventType: ${it.eventType}\n$it" }
        }.also { tradingWallet.walletCloseHandle = it }

        wsclient.onCandlestickEvent(requestedSymbols, ONE_MINUTE) { event: CandlestickEvent ->
            runBlocking {
                launch {
                    try {
                        fibonacciReporter.report(runningTally++)?.also { logDebug { it } }

                        val assetKey = assetMapper[event.symbol]
                            ?: throw Error("Symbol mismatch: ${event.symbol}. Mapper: $assetMapper")
                        eventMuxers[assetKey]?.push(event)
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

        wsclient.onBookTickerEvent(requestedSymbols) {
            val assetKey = assetMapper[it.symbol]
            if (assetKey != null) {
                val assetModel = AssetModel[assetKey]
                assetModel?.book = it
            } else {
                logDebug { "Warning: Could not map book ticker symbol ${it.symbol} to AssetKey." }
            }
        }.also { tradingWallet.bookHandle = it }
    }

    companion object {
        val klineSimpleScalar = DataBinanceVision.klines.names.toSeries()
        val episodeCutoff = 1000
    }
} 