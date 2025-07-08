package org.bereft.node.keys

import com.hazelcast.collection.IList
import com.hazelcast.map.IMap
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.bereft.node.config.MeshNode
import org.bereft.node.config.NodeConfig
import org.bereft.node.config.configCrypt
import org.knowm.xchange.Exchange
import org.knowm.xchange.ExchangeFactory
import org.knowm.xchange.binance.BinanceExchange

//import org.knowm.xchange.bitcoincoid.BitcoincoidExchange
//import org.knowm.xchange.bitmex.BitmexExchange
//import org.knowm.xchange.bittrex.BittrexExchange
//import org.knowm.xchange.cryptopia.CryptopiaExchange
//import org.knowm.xchange.hitbtc.v2.HitbtcExchange
//import org.knowm.xchange.huobipro.HuobiproExchange
//import org.knowm.xchange.idex.IdexExchange
//import org.knowm.xchange.kucoin.KucoinExchange
//import org.knowm.xchange.poloniex.PoloniexExchange
//import org.knowm.xchange.yobit.YoBitExchange

/**
 * configNode should be a seperate process with private configs sent in from cmdline or ENV
 *
 * todo: config files to remove snooping from ps?
 */
object ApiKeyNode {
    /**
     * this method is a canary in the coalmine for imports of Xchange, for now
     *
     */
    private val exchanges: List<String>
        get() = NodeConfig.getK("xchangeclasses",
            listOf<Class<out Exchange>>(
//                        IdexExchange::class.java,
//                        BitcoincoidExchange::class.java,
////                 ignored requests for support       org.knowm.xchange.stocksexchange.StocksexchangeExchange::class.java,
//                        CryptopiaExchange::class.java,
//                        BittrexExchange::class.java,
//                        HuobiproExchange::class.java,
//                        BitmexExchange::class.java,
// bitrot                       org.knowm.xchange.livecoin.LivecoinExchange::class.java,
                BinanceExchange::class.java,
//                        PoloniexExchange::class.java,
//                        YoBitExchange::class.java,
//                        HitbtcExchange::class.java,
//                        KucoinExchange::class.java
            ).map { it.canonicalName }.sorted().joinToString(
                " ")
        ).split(Regex("\\s+"))

    suspend fun configMap(): IMap<String, List<String>> {
        return Configs.await()
    }

    val configMap: IMap<String, List<String>> get() = runBlocking { Configs.await() }


    private val Configs
        get() = runBlocking {
            async {
                val meshOp = MeshNode.meshOp()
                val map = meshOp.getMap<String, List<String>>("ApiAccess")
                map
            }
        }

    private suspend fun loftConfigs(className: String) {
        try {
            val exchangeName = ExchangeFactory.INSTANCE.createExchangeWithoutSpecification(className)
                .defaultExchangeSpecification.exchangeName

            Configs.await()[className] = listOf(
                NodeConfig.qget2(exchangeName + ".username", "")!!/* apiKey =   */,
                NodeConfig.qget2(exchangeName + ".apikey", "")!!/* apiSecret =*/,
                NodeConfig.qget2(exchangeName + ".apisecret", "")!!
            ).map { it.configCrypt() }
        } catch (e: NotImplementedError) {
            e.printStackTrace()
        }

    }

    val distributedExchanges: IList<String>
        get() = runBlocking {
            MeshNode.meshOp().getList<String>(NodeConfig.sessionKey + "/exchanges")
        }


    suspend fun bootStrap() {
        exchanges.forEach {
            loftConfigs(it)
        }
        distributedExchanges.clear()
        distributedExchanges.addAll(exchanges)
    }


    /**
     * if the pkey exists, encrypt this string
     */

}
