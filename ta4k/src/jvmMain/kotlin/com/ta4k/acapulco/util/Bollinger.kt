package borg.trikeshed.acapulco.util

import borg.trikeshed.cursor.ColumnMeta
import borg.trikeshed.cursor.Cursor
import borg.trikeshed.cursor.RowVec
import borg.trikeshed.cursor.at
import borg.trikeshed.cursor.get
import borg.trikeshed.cursor.meta
import borg.trikeshed.isam.meta.IOMemento
import borg.trikeshed.lib.*
import borg.trikeshed.common.collections.s_
import java.lang.ref.SoftReference
import java.util.*
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

fun todub(a: Any?): Double {
    return when(a) {
        is Number -> a.toDouble()
        is String -> a.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }
}

val bolCache: MutableMap<String, SoftReference<Join<RowVec, String>>> = WeakHashMap()

fun Cursor.bollinger(depth: Int, k: Double = 2.5): Cursor {
 fun DoubleArray.calculateSD(sma: Double): Double {
     if (this.isEmpty()) return 0.0
     val variance = this.sumOf { (it - sma).pow(2.0) } / this.size
     return sqrt(variance)
 }

 return size j { y: Int ->
 val prevRows: Series<RowVec> = (0 until depth).map { this at max(0, y - it) }.toSeries()
 val valuesForCalc: Series<Double> = prevRows α { row -> todub(row.left[0]) }
 val key = "${this.hashCode()}:$y:$depth:$k" 
 val cachedResult: Join<RowVec, String>? = bolCache[key]?.get()

 val resultRowVec: RowVec = if (cachedResult \!= null) {
 cachedResult.first
 } else {
 val toDoubleArray = valuesForCalc.toArray()
 val sma = toDoubleArray.average().takeIf { \!it.isNaN() } ?: 0.0
 val calculateSD = if (toDoubleArray.isNotEmpty()) toDoubleArray.calculateSD(sma) else 0.0

 val bollHi = sma + (k * calculateSD)
 val bollLo = sma - (k * calculateSD)

 val newRow: RowVec = s_[
 bollHi j { ColumnMeta("bollHi", IOMemento.IoDouble) },
 sma j { ColumnMeta("sma$depth", IOMemento.IoDouble) },
 bollLo j { ColumnMeta("bollLo", IOMemento.IoDouble) }
 ]
 bolCache[key] = SoftReference(newRow j key)
 newRow
 }
 resultRowVec
 }
}
