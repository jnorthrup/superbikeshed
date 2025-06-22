// =====================================================================
// === TrikeShed/src/jvmMain/kotlin/borg/trikeshed/acapulco/util/CalcSmmaUp.kt ===
// =====================================================================
package borg.trikeshed.acapulco.util // Adjusted package

import borg.trikeshed.lib.Indexed // Replaces Vect0r
import borg.trikeshed.lib.get // Replaces get extension or operator

//todo:fold
fun calcSmmaUp(open: Indexed<Double>, close: Indexed<Double>, n: Int, i: Int, avgUt1: Double): Double {
    return if (avgUt1 == 0.0) {
        var sumUpChanges = 0.0
        var j = 0
        while (j < n) {
            // Add boundary checks
            val index = i - j
            if (index >= 0 && index < close.size && index < open.size) {
                val change = (close[index] - open[index])
                if (change > 0.0) sumUpChanges += change
            }
            j++
        }
        if (n > 0) sumUpChanges / n else 0.0 // Avoid division by zero
    } else {
        // Add boundary checks
        if (i >= 0 && i < close.size && i < open.size) {
            var change = close[i] - open[i]
            if (change < .0) change = 0.0
            if (n > 0) (avgUt1 * (n - 1.0) + change) / n else 0.0 // Avoid division by zero
        } else {
             avgUt1 // Or handle boundary appropriately
        }
    }
}