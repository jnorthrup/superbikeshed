package borg.trikeshed.lib

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDate
import kotlin.Comparator

interface TypeMemento {
    val networkSize: Int?
}

enum class IOMemento(override val networkSize: Int? = null) : TypeMemento {
    IoBoolean(1),
    IoByte(1),
    IoInt(4),
    IoLong(8),
    IoFloat(4),
    IoDouble(8),
    IoString,
    IoLocalDate(8),
    IoInstant(12),
    IoNothing;

    companion object {
        val cmpMap: MutableMap<TypeMemento, Comparator<Any?>> = linkedMapOf(
            IoLocalDate to Comparator { o1, o2 -> (o1 as LocalDate).compareTo(o2 as LocalDate) },
            IoInstant to Comparator { o1, o2 -> (o1 as Instant).compareTo(o2 as Instant) },
            IoBoolean to Comparator { o1, o2 -> (o1 as Boolean).compareTo(o2 as Boolean) },
            IoByte to Comparator { o1, o2 -> (o1 as Byte).compareTo(o2 as Byte) },
            IoInt to Comparator { o1, o2 -> (o1 as Int).compareTo(o2 as Int) },
            IoLong to Comparator { o1, o2 -> (o1 as Long).compareTo(o2 as Long) },
            IoFloat to Comparator { o1, o2 -> (o1 as Float).compareTo(o2 as Float) },
            IoDouble to Comparator { o1, o2 -> (o1 as Double).compareTo(o2 as Double) },
            IoString to Comparator { o1, o2 -> o1.toString().compareTo(o2.toString()) }
        )

        fun cmp(t: TypeMemento): Comparator<Any?> = cmpMap[t] ?: cmpMap[IoString]!!

        fun listComparator(progression: List<out TypeMemento>): Comparator<List<*>> = Comparator { o1, o2 ->
            val comp = progression.map(::cmp)
            var res = 0
            var idx = 0
            val size = minOf(o1?.size ?: 0, o2?.size ?: 0)
            while (res == 0 && idx < size) {
                val compare = comp[idx]
                res = compare.compare(o1?.get(idx), o2?.get(idx))
                idx++
            }
            if (res == 0 && (o1?.size ?: 0) != (o2?.size ?: 0)) {
                res = (o1?.size ?: 0).compareTo(o2?.size ?: 0)
            }
            res
        }
    }
}

val floatSum: (Any?, Any?) -> Any? = { acc: Any?, any2: Any? ->
    val fl = (acc as? Float) ?: 0.0f
    val fl1 = (any2 as? Float) ?: 0.0f
    fl + fl1
}

val sumReducer: Map<IOMemento, (Any?, Any?) -> Any?> = mapOf(
    IOMemento.IoInt to { acc, any2 -> ((acc as? Int) ?: 0) + ((any2 as? Int) ?: 0) },
    IOMemento.IoLong to { acc, any2 -> ((acc as? Long) ?: 0L) + ((any2 as? Long) ?: 0L) },
    IOMemento.IoFloat to floatSum,
    IOMemento.IoDouble to { acc, any2 -> ((acc as? Double) ?: 0.0) + ((any2 as? Double) ?: 0.0) },
    IOMemento.IoString to { acc, any2 -> ((acc as? String) ?: "") + ((any2 as? String) ?: "") }
)