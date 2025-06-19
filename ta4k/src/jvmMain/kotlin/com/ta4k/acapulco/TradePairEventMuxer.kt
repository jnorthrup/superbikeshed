package borg.trikeshed.acapulco

import com.binance.api.client.domain.event.CandlestickEvent
import cursors.Cursor
import cursors.SimpleCursor
import cursors.io.ISAMCursor
import cursors.io.writeISAM
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import org.bereft.Streamer.Companion.klineSimpleScalar
import org.bereft.model.AssetModel
import org.bereft.model.DataBinanceVision
import org.bereft.node.config.Help
import vec.macros.*
import vec.util._a
import vec.util._v
import vec.util.logDebug
import vec.util.path
import java.nio.channels.FileChannel
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.absolutePathString

/**
 * when RL etc. system wants to track a tradepair with realtime updates this provides an async callback.
 *
 * intra_events provides notifications which include nonFinal bars and an extra tuple member indicating intra-size
 *
 * events fires only on completed candles.
 */
class TradePairEventMuxer(
    val model: AssetModel, val catchup: Boolean = true,
    override val intra_events: MutableSharedFlow<Pai2<Cursor, Int>> = MutableSharedFlow<Pai2<Cursor, Int>>(),
    var latestKlines: ArrayList<CandlestickEvent> = ArrayList<CandlestickEvent>(Help.episodeLength.value.toInt()),
    var tempKlines: ArrayList<CandlestickEvent> = ArrayList<CandlestickEvent>(30),
    var _current: Pai2<Int, Cursor> = -1 t2 (_v[_v["never" t2 {}]] as Cursor)
) : ITradePairEventMuxer {
    suspend fun updateIntraClients() = intra_events.emit(realtime) /* suspends until all subscribers receive it*/

    val current: Pai2<Int, Cursor> get() = model.viewLatest(_current.first)?.also { _current = it } ?: _current
    val modelCursor get() = current.second
    val curEpisode get() = SimpleCursor(klineSimpleScalar, latestKlines α CandlestickEvent::row)
    val curTemp get() = SimpleCursor(klineSimpleScalar, tempKlines α CandlestickEvent::row)

    /**cache the realtime construction by its component lengths */
    val realtime: Pai2<Cursor, Int>
        get() = assembleCursor(_a[curEpisode, curTemp].filter { it.size > 0 }) t2 curTemp.size

    fun assembleCursor(chunks: List<Cursor>) = when {
        chunks.size > 1 -> combine(modelCursor, combine(*(chunks.toTypedArray())) `→` DataBinanceVision.klines.fixup)
        chunks.size == 1 -> combine(modelCursor, chunks.first() `→` DataBinanceVision.klines.fixup)
        else -> modelCursor
    }

    /**cache the realtime construction by its component lengths */
    val latest: Cursor
        get() = assembleCursor(_a[curEpisode].filter { it.size > 0 })

    suspend fun push(candlestickEvent: CandlestickEvent) = runBlocking {

        synchronized(this) {
            try {
                when (candlestickEvent.barFinal) {
                    false -> tempKlines.add(candlestickEvent)
                    else -> latestKlines.add(candlestickEvent).also {
                        tempKlines = ArrayList(tempKlines.size)
                    }
                }
                if (latestKlines.size >= Streamer.episodeCutoff) {
                    val pathname =
                        Files.createTempFile(
                            "${System.currentTimeMillis()}-${model.tradeSymbol.binanceEventResponseSymbol}-",
                            ".isam"
                        ).absolutePathString().also { f ->
                            logDebug { "creating tempfile $f - deleted:" + Files.deleteIfExists(f.path) }
                        }

                    DataBinanceVision.klines.fixup(curEpisode).writeISAM(pathname)
                    AssetModel.push(model.tradeSymbol, ISAMCursor(pathname.path, FileChannel.open(pathname.path)))
                    latestKlines = ArrayList(latestKlines.size)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        try {
            updateIntraClients()

        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
        }
    }
}

@Suppress("USELESS_CAST")
val CandlestickEvent.row
    get() = _v[
            openTime as Any?,
            open,
            high,
            low,
            close,
            volume,
            closeTime,
            quoteAssetVolume,
            numberOfTrades,
            takerBuyBaseAssetVolume,
            takerBuyQuoteAssetVolume,
            0
    ]
