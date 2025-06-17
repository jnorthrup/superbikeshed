package com.ta4k.acapulco

import borg.trikeshed.cursors.*
import borg.trikeshed.cursors.context.Scalar
import borg.trikeshed.cursors.context.TokenizedRow
import borg.trikeshed.cursors.io.IOMemento
import borg.trikeshed.cursors.io.ISAMCursor
import borg.trikeshed.cursors.io.writeCSV
import borg.trikeshed.cursors.io.writeISAM
import borg.trikeshed.cursors.macros.join
import borg.trikeshed.vec.macros.*
import borg.trikeshed.vec.macros.Vect02_.left
import borg.trikeshed.vec.macros.Vect02_.right
import borg.trikeshed.vec.util.*
import com.ta4k.acapulco.model.AssetKey
import com.ta4k.acapulco.model.AssetModel
import com.ta4k.acapulco.model.DataBinanceVision
import kotlinx.coroutines.*
import kotlinx.datetime.Clock.System.now
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

val horizonDepth: Int by lazy { Help.horizonDepthMinutes.value.toInt() }

fun exchangeAssetSrcFileName(TC: String, CC: String): String =
    "${Help.mpImportDir.value.path}/klines/1m/$TC/$CC/final-$TC-$CC-1m"

class HistoryService(
    val coins: CoinsAndPairings,
    vararg val args1: String = Help.tickerAssets.value.split("(,|\\s)+".toRegex()).toTypedArray(),
) {
    init {
        Help.agentDir
        this.start()
    }

    fun start() {
        val availableProcessors = Help.ISAMWriterThreads.value.toIntOrNull()
            ?: Runtime.getRuntime().availableProcessors()

        val hwThreads = newFixedThreadPool(availableProcessors).asCoroutineDispatcher()

        val tooOld = now().minus(6.hours).toInstant(UTC)
        val usedSymbols = args1.map { arg ->
            val (TC, CC) = arg.split("/")
            AssetKey.of(TC, CC)
        }

        runBlocking {
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

                    runBlocking {
                        val (tradeAsset, counterAsset) = arg
                        val TC1 = tradeAsset.uppercase()
                        val CC1 = counterAsset.uppercase()
                        val fnameBase = exchangeAssetSrcFileName(TC1, CC1)
                        Streamer.klineSimpleScalar.let { driver ->
                            val arg1 = "$fnameBase.csv"
                            val suspect = "$arg1.suspect.csv"

                            Files.move(arg1.path, suspect.path, StandardCopyOption.REPLACE_EXISTING)
                            val c2 = fixOpaqueCsv(suspect, DataBinanceVision.klines)
                            val tmpName = Paths.get(fnameBase).fileName
                            c2.writeISAM("$fnameBase.isam")
                            AssetModel.push(arg, ISAMCursor("$fnameBase.isam".path, FileChannel.open("$fnameBase.isam".path)))
                        }
                    }
                }
        }
    }

    companion object {
        fun fixOpaqueCsv(fname: String, dataBinanceVision: DataBinanceVision): Cursor {
            val cursor = TokenizedRow.CsvArraysCursor(Files.readAllLines(fname.path))
            return cursor `→` dataBinanceVision.fixup
        }
    }
} 