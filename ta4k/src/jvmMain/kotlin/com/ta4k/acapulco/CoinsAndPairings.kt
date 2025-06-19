package borg.trikeshed.acapulco

import com.binance.api.client.BinanceApiClientFactory
import com.binance.api.client.domain.market.TickerPrice
import cursors.at
import cursors.context.TokenizedRow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.bereft.model.AssetKey
import org.bereft.model.AssetKey.Companion.of
import org.bereft.model.AssetModel
import org.bereft.node.config.Help
import vec.macros.*
import vec.macros.Vect02_.left
import vec.macros.Vect02_.right
import vec.util.*
import java.io.FileWriter
import java.math.BigDecimal
import java.math.MathContext
import java.nio.file.Files
import java.util.*


/**
 * maybe this is two classes? or maybe it's just a rarely used loop filter thing that needs no more attention.
 */
class CoinsAndPairings(
        /** this requires the real api key for real userdata */
        val bFac: BinanceApiClientFactory,
) {
    suspend fun fiatConnectome(strings: Array<out String>): MutableList<AssetKey> {
        val usedSymbols = strings.map { AssetKey(it) }.toMutableList()
        val keys1 = AssetModel.run { assetOracle.keys - hidden }
        val combine = combine(usedSymbols.α { (s1, s2): AssetKey -> Tw1n(s1, s2) }.left,
                usedSymbols.α { (s1, s2): AssetKey -> Tw1n(s1, s2) }.right)
        val distinct = combine.toList().distinct() - Help.valueAsset.value
        val hashSet = distinct.map {
            pairPath(it, Help.valueAsset.value).zipWithNext()
        }.map {
            it - usedSymbols.map(AssetKey::pair).toSet() - keys1.map(AssetKey::pair).toSet()
        }.flatten().map { (s1: String, s2: String) -> of(s1, s2) }.toHashSet()
        AssetModel.hidden += hashSet
        usedSymbols += AssetModel.hidden
        return usedSymbols
    }

    val rawCoins: SortedSet<Map<String, String>> by lazy {
        @Suppress("UNCHECKED_CAST")
        (bFac.newRestClient().coinsAvail() as List<Map<String, String>> ).toSortedSet(compareBy {
            it["coin"].toString()
        })
    }
    val namedCoins: SortedMap<String, String> by lazy {
        rawCoins.associate {
            it["coin"].toString() to it["name"].toString()
        }.toSortedMap()
    }
    val allPrices: SortedMap<String, TickerPrice> by lazy { sortedMapOf(*bFac.newRestClient().allPrices.map { it.symbol to it }.toTypedArray()) }

    val fiatCurrencies: SortedSet<Map<String, String>> by lazy {
        CoinsAndPairings(bFac).rawCoins.filter {
            it["isLegalMoney"] as? Boolean ?: false
        }.toSortedSet(compareBy { it["coin"].toString() })
    }

    /**
     *
     * only appears usefull to perform
     *
     *
    }*/
    @Suppress("UNCHECKED_CAST")
    private fun coinNetworkGraphViz(binanceApiClientFactory: BinanceApiClientFactory) {
        rawCoins.map { map: Map<String, String> ->
            "\"${map["name"].toString()}/${map["coin"]}\"" to (map["networkList"] as? List<Map<String, String>>)?.map {
                "\"${it["network"].toString()}:${it["name"].toString()}\""
            }
        }.map { (a: String, b: List<String>?) ->
            (a + b?.joinToString(",", "->"))

        }.let { graph: List<String> ->
            FileWriter("/tmp/coingraphs.dot").use {
                it.write(
                        """
                        |digraph networks{
                        |   ${graph.joinToString("\n")}
                        |}
                        """.trimMargin("|")
                )
            }
        }
    }

    val allCoinSymbols by lazy {
        try {
            rawCoins.map { it["coin"].toString() }.toSortedSet()
        } catch (e: Exception) {
//            "BNB/BUSD,BTC/BUSD,ETH/BUSD,LTC/BUSD,TRX/BUSD,XRP/BUSD,BNB/USDT,BTC/USDT,ETH/USDT,LTC/USDT,TRX/USDT,XRP/USDT,BNB/BTC,ETH/BTC,LTC/BTC,TRX/BTC,XRP/BTC,LTC/BNB,TRX/BNB,XRP/BNB"

            _s["BNB", "BTC", "BUSD", "ETH", "LTC", "TRX", "USDT", "XRP"
            ].toSortedSet()

        }
    }
    val allSymbols by lazy {
        allPrices.values.filter { it.price.toDoubleOrNull()?.let { d -> d > 0.0 } ?: false }.map { tickerPrice ->
            tickerPrice.symbol
        }.toSortedSet()
    }
    val allAssetPairs by lazy {
        var totalSym = 0
        runBlocking {
            allCoinSymbols.asFlow().map { TC ->
                TC to allSymbols.filter { it.startsWith(TC) }.map { it.takeLast(it.length - TC.length) }
                        .filter { it in allCoinSymbols }.toSet()
            }.filter { (_, b) -> b.also { totalSym += it.size }.isNotEmpty() }.toList().debug {
                logDebug { "total symbols:$totalSym" }
            }
        }.toMap(sortedMapOf())
    }

    val allAssetKeys = allAssetPairs.map { (k, v) ->
        v.map { of(k, it) }
    }.flatten()

    val humanReadableIndex by lazy {
        allAssetPairs.map { (tc, ccl) ->
            ("$tc(${namedCoins[tc]}) ${ccl.joinToString(",", ": ")}")
        }
    }

    //presumed permanent during the object instance
    var pairCache: MutableMap<AssetKey, List<String>> = linkedMapOf()
    suspend fun pairPath(tc: String, cc: String, dupes: LinkedHashSet<String>? = null): List<String> =
            pairCache.getOrPut(of(tc, cc)) {
                var r: List<String>? = null
                try {
                    val routes = allAssetPairs[tc]
                    if (routes != null) {
                        r = if (routes.contains(cc))
                            (dupes ?: linkedSetOf(tc) + cc).toList()
                        else {
                            routes.asFlow().map { route ->
                                pairPath(route, cc, (dupes ?: linkedSetOf(tc) + route) as LinkedHashSet<String>)
                            }.filter {
                                it.last() == cc
                            }.toList().sortedBy {
                                it.size
                            }.first()
                        }
                    }
                    return (r ?: Collections.EMPTY_LIST) as List<String>
                } catch (e: Exception) {
                    System.err.println(" ${e.message} found with $tc $cc $dupes")
                    throw e
                }
            }

    suspend fun pathValue(tc: String, cc: String, vararg mcva: MathContext = _a[MathContext.UNLIMITED]) =
            try {
                pairPath(tc, cc).let { pairPath ->
                    val priceStrings = (0 until (pairPath.size - 1)).map {
                        allPrices[AssetKey(pairPath[it] + "/" + pairPath[it + 1]).binanceEventResponseSymbol]!!.price/*.toBigDecimal(mc )*/
                    }/*.also {
//                    logDebug { "origPrices=" + it.toString() }
                }*/

                    mcva.map { mc ->
                        priceStrings.fold(1.toBigDecimal(mc)) { acc: BigDecimal, price ->
                            acc * price.toBigDecimal(mc)
                        }
                    } + if (mcva.size > 1)
                        _l[priceStrings.fold(1.0) { acc, price ->
                            acc * price.toDouble()
                        }, priceStrings.fold(1.0) { acc, price ->
                            acc * price.toDouble()
                        }
                        ] else emptyList()
                }/*.also {
//                logDebug { "fakePrices=" + it.toString() }

            }*/.first().toDouble()
            } catch (e: Throwable) {
                /*e.printStackTrace()*/
                Double.NEGATIVE_INFINITY
            }
}

fun main() {
    val bFac = runBlocking {
        Help.mpKeyFile.value.let { fname ->
            val keys = TokenizedRow.CsvArraysCursor(Files.readAllLines(fname.path))
            val row = (keys at 0).left
            val test = row[0].toString().contains("test")
            BinanceApiClientFactory.newInstance(row[1].toString(), row[2].toString(), test, test)
        }
    }

    CoinsAndPairings(bFac).apply {

        val ap = allPrices

        val allkeys = allAssetKeys
        val raw = rawCoins
        val coins = allCoinSymbols
        val layer0 = fiatCurrencies.filter { it["trading"] as? Boolean ?: false }
        val layer1 = fiatCurrencies.map { it["coin"] }
        val paths = allAssetPairs.toMap().also { logDebug { it.toString() } }
        val testd = _l[
                _l["BTC", "USDT"],
                _l["VIB", "BTC"],
                _l["VIB", "USDT"],
                _l["VIB", "ZAR"],
                _l["WAVES", "ZAR"],
                _l["TRX", "XRP"],
                _l["XRP", "BTC"],
        ]
        runBlocking {
            testd.map { (a, b) ->


                System.err.println("$a,$b ${pairPath(a, b)}")
                System.err.println("$a,$b ${
                    pathValue(a,
                            b,
                            MathContext.UNLIMITED,
                            MathContext.DECIMAL128,
                            MathContext.DECIMAL64,
                            MathContext.DECIMAL32)
                }")
            }
        }

        val x = ""
    }
}
