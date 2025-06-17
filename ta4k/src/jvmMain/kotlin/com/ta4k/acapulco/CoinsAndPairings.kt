package com.ta4k.acapulco

import com.binance.api.client.BinanceApiClientFactory
import com.binance.api.client.domain.market.TickerPrice
import borg.trikeshed.cursors.at
import borg.trikeshed.cursors.context.TokenizedRow
import borg.trikeshed.vec.macros.*
import borg.trikeshed.vec.macros.Vect02_.left
import borg.trikeshed.vec.macros.Vect02_.right
import borg.trikeshed.vec.util.*
import com.ta4k.acapulco.model.AssetKey
import com.ta4k.acapulco.model.AssetKey.Companion.of
import com.ta4k.acapulco.model.AssetModel
import com.ta4k.acapulco.config.Help
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.FileWriter
import java.math.BigDecimal
import java.math.MathContext
import java.nio.file.Files
import java.util.*

class CoinsAndPairings(
    val bFac: BinanceApiClientFactory,
) {
    val allPrices: Map<String, BigDecimal> by lazy {
        runBlocking {
            bFac.newRestClient().allPrices.asFlow()
                .filter { it.symbol.endsWith("USDT") }
                .map { it.symbol to BigDecimal(it.price) }
                .toList()
                .toMap()
        }
    }

    val allAssetKeys: List<AssetKey> by lazy {
        allPrices.keys.map { symbol ->
            val base = symbol.dropLast(4)
            AssetKey.of(base, "USDT")
        }
    }

    val rawCoins: List<String> by lazy {
        allAssetKeys.map { it.tradeAsset }
    }

    val allCoinSymbols: List<String> by lazy {
        rawCoins.distinct()
    }

    val fiatCurrencies: List<Map<String, Any>> by lazy {
        allCoinSymbols.map { coin ->
            mapOf(
                "coin" to coin,
                "trading" to (coin in setOf("BTC", "ETH", "BNB"))
            )
        }
    }

    val allAssetPairs: List<Pair<AssetKey, AssetKey>> by lazy {
        allAssetKeys.flatMap { key1 ->
            allAssetKeys.map { key2 ->
                key1 to key2
            }
        }
    }

    fun pairPath(TC: String, CC: String): List<String> {
        val path = mutableListOf<String>()
        var current = TC
        while (current != CC) {
            path.add(current)
            current = allPrices.keys.find { it.startsWith(current) && it.endsWith(CC) }?.let {
                CC
            } ?: break
        }
        if (current == CC) {
            path.add(CC)
        }
        return path
    }

    fun pathValue(
        TC: String,
        CC: String,
        vararg contexts: MathContext
    ): BigDecimal {
        val path = pairPath(TC, CC)
        if (path.isEmpty()) return BigDecimal.ZERO

        var value = BigDecimal.ONE
        for (i in 0 until path.size - 1) {
            val from = path[i]
            val to = path[i + 1]
            val price = allPrices["${from}${to}"] ?: allPrices["${to}${from}"]?.let { BigDecimal.ONE.divide(it, contexts.getOrNull(i) ?: MathContext.DECIMAL128) }
                ?: return BigDecimal.ZERO
            value = value.multiply(price, contexts.getOrNull(i) ?: MathContext.DECIMAL128)
        }
        return value
    }
} 