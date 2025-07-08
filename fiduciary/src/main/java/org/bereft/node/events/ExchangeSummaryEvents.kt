package org.bereft.node.events

import com.fasterxml.jackson.databind.JsonMappingException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bereft.model.CmcCurrency
import org.bereft.model.ExchangeAsset
import org.bereft.model.ExchangeSummary
import org.bereft.node.config.MeshNode
import org.bereft.node.config.MeshNode.iAtomicCmcReference
import org.bereft.node.config.NodeConfig
import org.bereft.node.config.configDecrypt
import org.bereft.node.keys.ApiKeyNode
import org.knowm.xchange.Exchange
import org.knowm.xchange.ExchangeFactory
import java.math.BigDecimal
import java.math.BigDecimal.ZERO
import java.util.concurrent.TimeUnit
import kotlin.random.Random


object ExchangeSummaryEvents {

    fun daemon(vararg args: String) {
        runBlocking {
            val publishers: Channel<ExchangeSummary> = Channel(Channel.UNLIMITED)
            launch {
                do {
                    val exSummary = publishers.receive()
                    /*exSummary.assets*/
                    System.err.println(exSummary.xname to exSummary.apiKey to exSummary.btcPriceInUsd)
                    System.err.println(NodeConfig.gson.toJson(
                        exSummary.assets/*.filter { it.usdValue.toDouble()>0.01 } done upstream */
                            .map { it.toString() }))
                    System.err.println("\tworth $${exSummary.btcValue to exSummary.usdValue}")
                    MeshNode.meshOp().getQueue<ExchangeSummary>(
                        "ExchangeSummaries") += exSummary
                } while (true)
            }

            do runBlocking {
                val distributedExchanges = async {
                    val runtimeExchanges = linkedMapOf<String, Exchange>()

                    val distributedExchanges = ApiKeyNode.distributedExchanges
                    val toTypedArray = distributedExchanges.toTypedArray()
                    toTypedArray.forEach { className ->
                        val theExchange = ExchangeFactory.INSTANCE.createExchangeWithoutSpecification(className)
                        launch {
                            try {
                                val defaultExchangeSpecification = theExchange.defaultExchangeSpecification
                                val exchangeName = defaultExchangeSpecification.exchangeName

                                val configMap = ApiKeyNode.configMap
                                configMap[className]?.apply {
                                    val iter = this.iterator()
                                    defaultExchangeSpecification.userName = iter.next().configDecrypt()
                                    defaultExchangeSpecification.apiKey = iter.next().configDecrypt()
                                    defaultExchangeSpecification.secretKey = iter.next().configDecrypt()

                                    runtimeExchanges[exchangeName] = ExchangeFactory.INSTANCE.createExchange(
                                        defaultExchangeSpecification)

                                }
                            } catch (e: JsonMappingException) {
                                System.err.println("!!! - ignoring " + className + " -- " + e.localizedMessage);
                            } catch (e: Exception) {
                                System.err.println("!!! - config fail " + className + " -- " + e.localizedMessage);
                                e.printStackTrace()
                            }
                        }

                    }
                    runtimeExchanges
                }

                iAtomicCmcReference().get()?.also { cmcRecent ->

                    /*these are short term charting candles, not really long lived entities, so this is a defensive choice */
                    val cmcKeyCache = async {
                        linkedMapOf<String, CmcCurrency>()
                            .also {
                                cmcRecent.forEach { cmc ->
                                    setOf(cmc.id, cmc.name, cmc.symbol).forEach { t: String -> it[t] = cmc }
                                }
                            }
                    }
                    val btcPriceInUsd = cmcRecent.first().price_usd
                    launch {

                        distributedExchanges.await().forEach {

                            launch {
                                val exchange = it.value
                                val apiKey = exchange.exchangeSpecification.apiKey
                                val exchangeName = it.key
                                val lockName = "lock/$exchangeName/$apiKey"
                                System.err.println("?=== locking $lockName ")
                                if (!MeshNode.meshOp().cpSubsystem.getLock(lockName).tryLock(Random.nextLong(15, 60),
                                        TimeUnit.SECONDS)
                                )
                                    System.err.println("-=== nolock $lockName ")
                                else {
                                    val dust = ExchangeAsset(
                                        displayName = "dust",
                                        hiloStanding = -1,
                                        holdingInBtc = ZERO,
                                        last_updated = cmcRecent.first().last_updated,
                                        symbol = "dust",
                                        total = ZERO,
                                        usdValue = ZERO
                                    )
                                    System.err.println("+=== locked $lockName ")
                                    val accountService = exchange.accountService
                                    val wallets = accountService.accountInfo?.wallets
                                    val values = wallets!!.values
                                    System.err.println(
                                        exchangeName + " has " + values.size + " wallets: " + wallets.keys)

                                    val keyCache = cmcKeyCache.await()

                                    publishers.send(
                                        ExchangeSummary(
                                            xname = exchangeName,
                                            btcPriceInUsd = btcPriceInUsd,
                                            apiKey = apiKey,
                                            assets = values.map { it.balances.values }.flatten()
                                                .filter { it.total > ZERO }.map { balanceCandle ->


                                                val key = setOf(
                                                    balanceCandle.currency.iso4217Currency.currencyCode,
                                                    balanceCandle.currency.currencyCode,
                                                    balanceCandle.currency.symbol
                                                ).intersect(keyCache.keys).firstOrNull()
                                                when (key) {
                                                    null -> {
                                                        System.err.println(
                                                            "${exchangeName}/${balanceCandle.currency} not in cmc! ${balanceCandle.currency.symbol} / ${balanceCandle.currency.displayName}")
                                                        ExchangeAsset(
                                                            symbol = balanceCandle.currency.symbol,
                                                            displayName = balanceCandle.currency.displayName,
                                                            total = balanceCandle.total,
                                                            hiloStanding = -1,
                                                            usdValue = ZERO,
                                                            holdingInBtc = ZERO,
                                                            last_updated = keyCache["BTC"]?.last_updated!!)
                                                    }
                                                    else -> {
                                                        val cmcTicker = keyCache[key]!!
                                                        ExchangeAsset(
                                                            symbol = balanceCandle.currency.symbol,
                                                            displayName = listOf(
                                                                balanceCandle.currency.displayName,
                                                                cmcTicker.name).distinct().joinToString("|"),
                                                            total = balanceCandle.total,
                                                            hiloStanding = listOf(
                                                                cmcTicker.percent_change_1h,
                                                                cmcTicker.percent_change_24h,
                                                                cmcTicker.percent_change_7d).map { it.toInt() },
                                                            usdValue = cmcTicker.price_usd * balanceCandle.total,
                                                            holdingInBtc = cmcTicker.price_btc * balanceCandle.total,
                                                            last_updated = cmcTicker.last_updated

                                                        )
                                                    }
                                                }
                                            }.filter {
                                                val holdingInBtc1 = it.holdingInBtc
                                                val usdValue1 = it.usdValue
                                                (usdValue1.toDouble() > 0.01).also {
                                                    when (it) {
                                                        false -> dust.apply {
                                                            holdingInBtc += holdingInBtc1
                                                            total++;
                                                            usdValue += usdValue1
                                                        }
                                                    }
                                                }
                                            }.sortedByDescending(
                                                ExchangeAsset::usdValue).let {

                                                when {
                                                    dust.total > ZERO -> {
                                                        val list = it.toMutableList() + dust
                                                        list
                                                    }
                                                    else -> it
                                                };
                                            }
                                        )

                                    )
                                }
                            }
                        }
                    }.join()
                }
                delay(TimeUnit.MINUTES.toMillis(1))
            }
            while (true)
        }
    }

    fun main(vararg args: String) {
        daemon(*args)
    }
}
