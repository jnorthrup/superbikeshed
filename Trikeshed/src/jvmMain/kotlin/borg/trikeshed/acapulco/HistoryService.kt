@file:OptIn(InternalCoroutinesApi::class)

package borg.trikeshed.acapulco

import cursors.*
import cursors.context.Scalar
import cursors.context.TokenizedRow
import cursors.io.IOMemento
import cursors.io.ISAMCursor
import cursors.io.writeCSV
import cursors.io.writeISAM
import cursors.macros.join
import kotlinx.coroutines.*
import kotlinx.datetime.Clock.System.now
import org.bereft.gui.KlinePlotThing
import org.bereft.model.AssetKey
import org.bereft.model.AssetModel
import org.bereft.model.DataBinanceVision
import org.bereft.node.config.Help
//import org.ta4j.core.BaseBarSeriesBuilder
//import org.ta4j.core.BaseStrategy
//import org.ta4j.core.Rule
//import org.ta4j.core.indicators.RSIIndicator
//import org.ta4j.core.indicators.SMAIndicator
//import org.ta4j.core.indicators.bollinger.*
//import org.ta4j.core.indicators.helpers.ClosePriceIndicator
//import org.ta4j.core.indicators.statistics.StandardDeviationIndicator
//import org.ta4j.core.num.DoubleNum
//import org.ta4j.core.num.DoubleNum.*
//import org.ta4j.core.num.Num
//import org.ta4j.core.rules.CrossedDownIndicatorRule
//import org.ta4j.core.rules.CrossedUpIndicatorRule
//import org.ta4j.core.rules.OverIndicatorRule
//import org.ta4j.core.rules.UnderIndicatorRule
import vec.macros.*
import vec.macros.Vect02_.left
import vec.macros.Vect02_.right
import vec.util.*
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors.newFixedThreadPool
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.hours


lateinit var klinePlotThing: KlinePlotThing


/**
 * all the tradepairs share horizon size. they do not share history size.
 */
val horizonDepth: Int by lazy { Help.horizonDepthMinutes.value.toInt() }


fun exchangeAssetSrcFileName(TC: String, CC: String): String =
    "${Help.mpImportDir.value.path}/klines/1m/$TC/$CC/final-$TC-$CC-1m"


/***
 * this is the singleton with all the early setup and book-keeping holding the statics and whatnot for
 * upstream fsm services
 */
class HistoryService(
    val coins: CoinsAndPairings,
    vararg val args1: String = Help.tickerAssets.value.split("(,|\\s)+".toRegex()).toTypedArray(),
) {
    init {
        Help.agentDir
        this.start()
    }

    /**
     *
     */
    fun start() {

        //todo using XMR/BNB pulls in XMR/USDT but XMR/USDT fails.

        val availableProcessors = Help.ISAMWriterThreads.value.toIntOrNull()
            ?: Runtime.getRuntime().availableProcessors()

        val hwThreads = newFixedThreadPool(availableProcessors).asCoroutineDispatcher()
        runBlocking(hwThreads) {
            val allAssetPairs1 = coins.allAssetPairs.entries.toList()
            val c: Cursor = combine(
                coins.allAssetPairs.size t2 { z: Int ->
                    val (tt, ccs) = allAssetPairs1[z]
                    val ccl = ccs.toTypedArray()
                    ccl.size t2 { y: Int ->
                        (_v[tt, coins.namedCoins[tt], ccl[y], coins.namedCoins[ccl[y]]] α {
                            it as Any? t2 {
                                Scalar.Scalar(IOMemento.IoString)
                            }
                        })
                    }
                })
            val path = Help.mpCacheDir.value.path

            c.writeCSV(withContext(Dispatchers.IO) {
                Files.createDirectories(path)
            }.resolve("coinIndex.csv").toString())

            val usedSymbols = coins.fiatConnectome(args1).distinct()
            val tooOld = Instant.now().minus(3, ChronoUnit.DAYS)


            //single file seems to avoid heap overflows and thrashing the spinning rust
            /*.reverse()*/

            for (arg in usedSymbols)
                launch {
                    val (TC, CC) = arg
                    val resolve =
                        Paths.get(Help.mpImportDir.value, "klines", "1m", TC, CC).createDirectories()
                            .resolve("final-$TC-$CC-1m.csv")
                    val exists = resolve.exists()
                    if (!exists || Files.getLastModifiedTime(resolve).toInstant().isBefore(tooOld)) {
                        val proc = ProcessBuilder().directory(Help.tmpDir.value.path.toFile()).command(
                            "bash",
                            Help.binDir.value.path.resolve("fetchklines.sh").toString(), TC, CC
                        ).also {
                            logDebug { "running " + it.command() }
                        }.start()

                        FibonacciReporter().let { reportToMe ->
                            var c = 0

                            proc.inputStream.bufferedReader().let { bashStreamReader ->
                                while (proc.isAlive)
                                    bashStreamReader.readLine()
                                        .also {
                                            reportToMe.report(c++)?.debug {
                                                logDebug { it }
                                            }
                                        }
                            }
                            if (proc.waitFor() != 0) {
                                throw Error("fetchklines $proc. returned ${proc.waitFor()}")
                            }
                        }
                        ProcessBuilder("sync").start().waitFor()
                    }
                    //launch is pathological here with ta4j/*.reverse()*/
                    //                                "ema20" t2  emaIndicator ,
                    //                                "stdev20" t2  standardDeviationIndicator,

                    //sc.head()
                    //                             (c2  ).writeISAM(isamPathName)
                    //add bolinger

                    //https://github.com/mdeverdelhan/ta4j-origins/blob/master/ta4j-examples/src/main/java/ta4jexamples/indicators/IndicatorsToChart.java
                    //https://github.com/mdeverdelhan/ta4j-origins/issues/100#issuecomment-237152313
                    //                        DoubleNum.useCache = true
                    //launch is pathological here with ta4j
                    runBlocking {
                        val (tradeAsset, counterAsset) = arg
                        val TC1 = tradeAsset.uppercase()
                        val CC1 = counterAsset.uppercase()
                        val fnameBase = exchangeAssetSrcFileName(TC1, CC1)
                        //                        DoubleNum.useCache = true
                        Streamer.klineSimpleScalar.let { driver ->
                            val arg1 = "$fnameBase.csv"
                            val suspect = "$arg1.suspect.csv"

                            Files.move(arg1.path, suspect.path, StandardCopyOption.REPLACE_EXISTING)
                            val c2 = fixOpaqueCsv(suspect, DataBinanceVision.klines)
                            //add bolinger
                            val tmpName = Paths.get(fnameBase).fileName

                    /*        val series =
                                BaseBarSeriesBuilder().withName("$tmpName").withNumTypeOf(DoubleNum::class.java)
                                    .withMaxBarCount(Help.horizonDepthMinutes.value.toInt() + 201).build()
                            val closePrice = ClosePriceIndicator(series)

                            val shortSma = SMAIndicator(closePrice, 5)
                            val longSma = SMAIndicator(closePrice, 200)

                            // We use a 2-period RSI indicator to identify buying
                            // or selling opportunities within the bigger trend.

                            // We use a 2-period RSI indicator to identify buying
                            // or selling opportunities within the bigger trend.
                            val rsi2 = RSIIndicator(closePrice, 2)


                            // Entry rule
                            // The long-term trend is up when a security is above its 200-period SMA.
                            val rsiEntryRule: Rule = OverIndicatorRule(shortSma, longSma) // Trend
                                .and(CrossedDownIndicatorRule(rsi2, 5)) // Signal 1
                                .and(OverIndicatorRule(shortSma, closePrice)) // Signal 2


                            // Exit rule
                            // The long-term trend is down when a security is below its 200-period SMA.
                            val rsiExitRule: Rule = UnderIndicatorRule(shortSma, longSma) // Trend
                                .and(CrossedUpIndicatorRule(rsi2, 95)) // Signal 1
                                .and(UnderIndicatorRule(shortSma, closePrice)) // Signal 2


                            val barCount = Help.movingAvgSize.value.toInt() * 2
                            //https://github.com/mdeverdelhan/ta4j-origins/blob/master/ta4j-examples/src/main/java/ta4jexamples/indicators/IndicatorsToChart.java
                            val smaIndicator = SMAIndicator(closePrice, barCount)

                            //https://github.com/mdeverdelhan/ta4j-origins/issues/100#issuecomment-237152313
                            val bollingerBandsMiddleIndicator = BollingerBandsMiddleIndicator(smaIndicator)

                            val standardDeviationIndicator = StandardDeviationIndicator(closePrice, barCount)
                            val bollingerBandsUpperIndicator =
                                BollingerBandsUpperIndicator(bollingerBandsMiddleIndicator, standardDeviationIndicator)
                            val bollingerBandsLowerIndicator =
                                BollingerBandsLowerIndicator(bollingerBandsMiddleIndicator, standardDeviationIndicator)
                            val bollingerBandWidthIndicator = BollingerBandWidthIndicator(
                                bollingerBandsUpperIndicator,
                                bollingerBandsMiddleIndicator,
                                bollingerBandsLowerIndicator
                            )
                            val percentBIndicator = PercentBIndicator(closePrice, barCount, 2.0)
                            val strategy = BaseStrategy(rsiEntryRule, rsiExitRule)
                            var e1 = 0
                            var e2 = 0
                            val ta4jIndicator: Vect02<String, (Int) -> Num> = _v[
                                    "bollUpper" t2 { y: Int -> bollingerBandsUpperIndicator.getValue(y) },
                                    "bollMid" t2 { y: Int -> bollingerBandsMiddleIndicator.getValue(y) },
                                    "bollLow" t2 { y: Int -> bollingerBandsLowerIndicator.getValue(y) },
                                    "bollWidth" t2 { y: Int -> bollingerBandWidthIndicator.getValue(y) },
                                    "bollPct" t2 { y: Int -> percentBIndicator.getValue(y) },
                                    "shortSma15" t2 { y: Int -> shortSma.getValue(y) },
                                    "longSma4Hr" t2 { y: Int -> longSma.getValue(y) },
                                    "rsi2" t2 { y: Int -> rsi2.getValue(y) },
                                    "rsi2Entry" t2 { y: Int ->
                                        D1_0.takeIf { strategy.entryRule.isSatisfied(series.endIndex) }?.also { e1++ }
                                            ?: DZERO
                                    },
                                    "rsi2Exit" t2 { y: Int ->
                                        D1_0.takeIf { strategy.exitRule.isSatisfied(series.endIndex) }?.also { e2++ }
                                            ?: DZERO
                                    }
                            ]
                            val theNames = ta4jIndicator.left α { nama: String -> Scalar(IOMemento.IoDouble, nama) }
                            val sc = SimpleCursor(theNames, c2.size t2 { y ->
                                val newY=lastY!=y
                                if(newY)lastY=y

                                val (o, h, l, c, v) = (ohlcDoubles[y].toDoubleArray())
                                if(newY) { series.addBar(times.next(), o, h, l, c, v) }
                                ta4jIndicator.right α { function: (Int) -> Num ->
                                    todub(function(y)) }
                            })
*/


//                            val ohlcvCurs = c2["Open", "High", "Low", "Close", "Volume"]
//                            val ohlcDoubles =
//                                ohlcvCurs / Double::class.java α { dubRow -> dubRow α ::todub }
//                            val tzDateTimeCurs = c2["Open_time"]
//                            val tzdateVec = tzDateTimeCurs % Instant::class.java α { it!!.atZone(UTC) }

//                            val times=tzdateVec.iterator()
//
//                            var lastY=-1

                            //sc.head()

                            val isamPathName = "${fnameBase}.isam"

                            var x: kotlinx.datetime.Instant = now()
                            /*join*/(c2/*, sc*/).writeISAM(isamPathName).debug {
                                logDebug { "writeIsam for $isamPathName: ${now() - x}" }
                                x = now()
                            }

                            //                             (c2  ).writeISAM(isamPathName)
                            Files.move(suspect.path, arg1.path)

                            FileChannel.open(isamPathName.path).let { handleLeak ->
                                val opaque: Cursor = ISAMCursor(isamPathName.path, handleLeak)/*.reverse()*/
                                AssetModel.push(AssetKey.of(TC1, CC1), opaque)
                                AssetModel.leakThese += handleLeak
                            }
                        }
                    }
                }
        }
        hwThreads.close()
    }

    companion object {
        fun fixOpaqueCsv(
            arg: String,
            driver: DataBinanceVision,
            /**
             * if you need to add the headers from driver or other location set it here
             */
            useHeaders: Vect0r<String>? = null,
        ): Cursor {
            logDebug { "entering fixopaquescsv $arg $driver" }
            System.err.println("driver selected for ${arg} is ${driver.name}")
            val path = arg.path
            val csvLines1 = Files.readAllLines(path)
            val srcLines = useHeaders?.let { useHeaders -> _l[useHeaders.`➤`.joinToString { "," }].plus(csvLines1) }
                ?: csvLines1
            val c1 = TokenizedRow.CsvArraysCursor(srcLines, driver.types)
            return driver.fixup(c1)
        }

        fun adjust(earliest: Instant, instant: Instant): Int =
            (((earliest.epochSecond) - instant.epochSecond) / 60).toInt()
    }

}
