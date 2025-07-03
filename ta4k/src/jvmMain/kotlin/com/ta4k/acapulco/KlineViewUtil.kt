// =====================================================================
// === TrikeShed/src/jvmMain/kotlin/borg/trikeshed/acapulco/KlineViewUtil.kt ===
// =====================================================================
package borg.trikeshed.acapulco // Adjusted package

import borg.trikeshed.cursor.*
import borg.trikeshed.isam.meta.IOMemento
import borg.trikeshed.lib.*
import borg.trikeshed.common.collections.s_

import borg.trikeshed.acapulco.model.TradingWallet
import borg.trikeshed.acapulco.util.DateShed
import borg.trikeshed.acapulco.util.horizon
import borg.trikeshed.acapulco.TradePairEventMuxer

object KlineViewUtil {
    val depthScalr: () -> ColumnMeta = { ColumnMeta("depth", IOMemento.IoInt) }
    val ixScalr: () -> ColumnMeta = { ColumnMeta("index", IOMemento.IoInt) }

    fun decorateView(
        muxer: TradePairEventMuxer,
        event0: Join<Cursor, Int>,
        tradingWallet: TradingWallet,
    ): Cursor {
        val assetKey = muxer.model.tradeSymbol
        val (c0, intra) = event0
        if (c0.isEmpty()) return emptySeries()

        val curs: Cursor = c0.reversed()
        val openTimeColIndex = curs.meta.`play`.indexOfFirst { it.name == "Open_time" }.takeIf { it >= 0 } ?: 0
        val copentime: Indexed<Any?> = curs α { it.left.getOrNull(openTimeColIndex) }
        val walletFreeCursor = tradingWallet.walletFree(assetKey, "USDT")
        val depthIntraCursor: Cursor = s_[
            s_[
                curs.size j { ColumnMeta("Depth", IOMemento.IoInt) },
                intra j { ColumnMeta("IntraEvents", IOMemento.IoInt) }
            ]
        ]

        val timeColumns: Cursor = if (DateShed.normTime) {
            copentime.size j { y:Int ->
                val instant = copentime.getOrNull(y) as? Instant
                if (instant != null) {
                    DateShed.normalizeInstant(instant)
                } else {
                    emptySeries()
                }
            }
        } else {
            curs.first().let { row -> s_[DateShed.treatInstant(row)] }
        }

        val openColIndex = 1
        val volumeColIndex = 5
        val selectedDataCols: Cursor = curs α { row -> s_[row.getOrNull(openColIndex), row.getOrNull(volumeColIndex)] }
        val pancakeInput: Cursor = horizonDepth j { y:Int ->
            val horizonIndex = horizon(y, horizonDepth, selectedDataCols.size)
            selectedDataCols at horizonIndex
        }
        val candles: Cursor = pancake(pancakeInput)

        val fiatValue = coins.pathValue(assetKey.a, assetKey.b)
        val holdingAmount = todub(tradingWallet.holdings[assetKey.a]?.free, 0.0)
        val bankXferValue = holdingAmount * fiatValue
        val valueCursor: Cursor = s_[s_[bankXferValue j { ColumnMeta("BankXferValue", IoDouble) }]]

        return joinCursors(walletFreeCursor, depthIntraCursor, timeColumns, walletFreeCursor, candles, valueCursor)
    }

    var brandedPancake: Int? = null
    fun pancake(c: Cursor): Cursor {
        if (c.isEmpty()) return emptySeries()
        val numInputCols = c.row(0).size
        val numInputRows = c.size

        if (brandedPancake == null) {
            brandedPancake = numInputCols
        }

        val totalOutputCols = numInputRows * numInputCols
        if (totalOutputCols == 0) return emptySeries()

        return totalOutputCols j { flatIndex:Int ->
            val rowIndex = flatIndex / numInputCols
            val colIndex = flatIndex % numInputCols
            val sourceRow = c.row(rowIndex)
            val sourceCell = sourceRow[colIndex]
            sourceCell
        }
    }
}