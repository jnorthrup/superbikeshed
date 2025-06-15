// =====================================================================
// === TrikeShed/src/jvmMain/kotlin/borg/trikeshed/acapulco/util/CalculateRSIValues.kt ===
// =====================================================================
package borg.trikeshed.acapulco.util // Adjusted package

import borg.trikeshed.cursor.ColumnMeta
import borg.trikeshed.cursor.Cursor // Type alias for Series<RowVec>
import borg.trikeshed.cursor.at
import borg.trikeshed.cursor.get
import borg.trikeshed.cursor.meta
import borg.trikeshed.isam.meta.IOMemento
import borg.trikeshed.lib.* // Imports Join, Series, j, α, etc.
import borg.trikeshed.common.collections.s_ // Replaces _v for Series creation
import borg.trikeshed.acapulco.util.calcSmmaDown // Assuming ported
import borg.trikeshed.acapulco.util.calcSmmaUp // Assuming ported
import kotlin.math.max

/** Calculating the RS
The RSI indicator is based on the changes in the price action and not on the actual price itself . This is where the term Relative Strength (RS) comes from.
Calculating the RS is quite simple. We need to divide the SMMA of the up changes by the SMMA of the down changes.
Cursor["Open","Close"] -> Series<RowVec> ["Open", "Close"]
 */
fun Cursor.rsi(depth: Int = 14): Cursor = run {
    // Assuming "Open" is at index 0 and "Close" is at index 1 after get
    // This needs robust index lookup based on names from meta
    val openColIndex = this.meta.`▶`.indexOfFirst { it.name == "Open" }.takeIf { it >= 0 } ?: 0 // Fallback, adjust as needed
    val closeColIndex = this.meta.`▶`.indexOfFirst { it.name == "Close" }.takeIf { it >= 0 } ?: 1 // Fallback, adjust as needed

    val open: Series<Double> = this α { todub(it.left[openColIndex]) } // Extract Open column as Series<Double>
    val close: Series<Double> = this α { todub(it.left[closeColIndex]) } // Extract Close column as Series<Double>

    fun calculateRS(up: Double, dn: Double): Double = if (dn == 0.0) Double.POSITIVE_INFINITY else up / dn

    this.size j { y: Int -> // Create a new Cursor (Series<RowVec>)
        var accUp = 0.0
        var accDn = 0.0
        val rsiValue: Double = run {
            // Iterate over the lookback period
            val values = (max(0, y - depth + 1)..y).map { i ->
                // Ensure indices for open and close are valid
                if (i >= 0 && i < open.size && i < close.size) {
                    accUp = calcSmmaUp(open, close, depth, i, accUp)
                    accDn = calcSmmaDown(open, close, depth, i, accDn)
                    val rs = calculateRS(accUp, accDn)
                    if (rs.isInfinite()) 100.0 else 100.0 - 100.0 / (1.0 + rs)
                } else {
                    0.0 // Or handle boundary cases appropriately
                }
            }
            values.lastOrNull() ?: 0.0 // Take the last calculated RSI in the window
        }

        // Create a RowVec for the result (single column: rsi)
        s_[
            rsiValue j { ColumnMeta("rsi:$depth", IOMemento.IoDouble) }
        ]
    }
}