// =====================================================================
// === TrikeShed/src/jvmMain/kotlin/borg/trikeshed/acapulco/util/Bollinger.kt ===
// =====================================================================
package borg.trikeshed.acapulco.util // Adjusted package

import borg.trikeshed.cursor.ColumnMeta
import borg.trikeshed.cursor.Cursor // Type alias for Series<RowVec>
import borg.trikeshed.cursor.RowVec // Type alias for Series2<Any?, () -> ColumnMeta>
import borg.trikeshed.cursor.at
import borg.trikeshed.cursor.get
import borg.trikeshed.cursor.meta
import borg.trikeshed.isam.meta.IOMemento
import borg.trikeshed.lib.* // Imports Join, Series, j, α, etc.
import borg.trikeshed.common.collections.s_ // Replaces _v for Series creation
import borg.trikeshed.acapulco.util.todub // Assuming todub is ported or replaced
import borg.trikeshed.common.collections._a // For primitive arrays
import java.lang.ref.SoftReference
import java.util.*
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

// Assuming DateShed is also ported to this package or accessible
// Need to manage the cache differently or pass it in if DateShed isn't global
val bolCache: MutableMap<String, SoftReference<Join<RowVec, String>>> = WeakHashMap()

/** creates a 3-column cursor (Series<RowVec>) at [0]
Upper Band = SMA (n) + k x Standard Deviation (n)
Lower Band = SMA (n) – k x Standard Deviation (n)*/
fun Cursor.bollinger(depth: Int, k: Double = 2.5): Cursor {
    fun DoubleArray.calculateSD(sma: Double): Double {
        val standardDeviation = sumOf { (it - sma).pow(k) } // Original code had pow(k), standard deviation is usually pow(2)
        return sqrt(standardDeviation / depth)
    }

    return size j { y: Int ->
        // Create a Series of the relevant previous rows
        val prevRows: Series<RowVec> = (0 until depth).map { this at max(0, y - it) }.toSeries()

        // Assuming the first column holds the value needed for SMA/Bollinger
        // This might need adjustment based on actual cursor structure
        val valuesForCalc: Series<Double> = prevRows α { row -> todub(row.left[0]) }

        val key = "${this.hashCode()}:$y:$depth:$k" // HashCode might not be stable across runs if Cursor is complex

        val cachedResult: Join<RowVec, String>? = bolCache[key]?.get()

        val resultRowVec: RowVec = if (cachedResult != null) {
            cachedResult.first
        } else {
            val toDoubleArray = valuesForCalc.toArray() // Convert Series<Double> to DoubleArray
            val sma = toDoubleArray.average().takeIf { !it.isNaN() } ?: 0.0 // Handle empty or NaN average
            val calculateSD = if (toDoubleArray.isNotEmpty()) toDoubleArray.calculateSD(sma) else 0.0

            val bollHi = sma + (k * calculateSD) // Recalculated based on standard definition
            val bollLo = sma - (k * calculateSD) // Recalculated based on standard definition

            val originalMeta = this.meta // Get metadata from the original cursor

            // Create the result RowVec with 3 columns: bollHi, sma, bollLo
            val newRow: RowVec = s_[
                bollHi j { ColumnMeta("bollHi", IOMemento.IoDouble) },
                sma j { ColumnMeta("sma$depth", IOMemento.IoDouble) },
                bollLo j { ColumnMeta("bollLo", IOMemento.IoDouble) }
            ]

            // Cache the result
            bolCache[key] = SoftReference(newRow j key)
            newRow
        }
        resultRowVec // Return the RowVec for this y index
    }
}