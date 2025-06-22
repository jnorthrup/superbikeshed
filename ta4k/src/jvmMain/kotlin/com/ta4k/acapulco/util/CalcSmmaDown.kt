// =====================================================================
// === TrikeShed/src/jvmMain/kotlin/borg/trikeshed/acapulco/util/CalcSmmaDown.kt ===
// =====================================================================
package borg.trikeshed.acapulco.util // Adjusted package

import borg.trikeshed.lib.Indexed // Replaces Vect0r
import borg.trikeshed.lib.get // Replaces get extension or operator

fun calcSmmaDown(open: Indexed<Double>, close: Indexed<Double>, n: Int, i: Int, avgDt1: Double): Double =
    if (avgDt1 == 0.0) {
        var sumDownChanges = 0.0
        var j = 0
        while (j < n) {
            // Add boundary checks
            val index = i - j
            if (index >= 0 && index < close.size && index < open.size) {
                val change = (close[index] - open[index])
                if (change < .0) sumDownChanges -= change // Accumulate positive value for down changes
            }
            j++
        }
         if (n > 0) sumDownChanges / n else 0.0 // Avoid division by zero
    } else {
        // Add boundary checks
         if (i >= 0 && i < close.size && i < open.size) {
             var change = (close[i] - open[i])
             if (change > .0) change = 0.0
             if (n > 0) (avgDt1 * (n - 1.0) - change) / n else 0.0 // Avoid division by zero, use -change
         } else {
              avgDt1 // Or handle boundary appropriately
         }
    }