@file:Suppress("NOTHING_TO_INLINE")

package borg.trikeshed.cursor

// Imports from TrikeShedCore.kt (the new core library)
import borg.trikeshed.core.IOMemento
import borg.trikeshed.core.Join
import borg.trikeshed.core.Series
import borg.trikeshed.core.IterableSeries
import borg.trikeshed.core.ColumnMeta
import borg.trikeshed.core.alpha // The 'α' infix function
import borg.trikeshed.core.asString
import borg.trikeshed.core.j
import borg.trikeshed.core.size
import borg.trikeshed.core.`▶`


import kotlin.jvm.JvmInline
import kotlin.jvm.JvmOverloads
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.reflect.KClass

typealias RowVec = Series<Join<Any?, () -> ColumnMeta>>

//val RowVec.left get() =  this α Join<*, () -> RecordMeta>::a

/** Cursors are a columnar abstraction composed of Series of Joined value+meta pairs (RecordMeta) */
typealias Cursor = Series<RowVec>

///**
// * overload unary minus operator for Cursor to strip out the meta and return a series of values-only
// *
// * apparently this duplicates the unaryMinus() function above it, but it's not clear how to get the compiler to use that one
// */
//operator fun Cursor.unaryMinus(): Series<Series<*>> = this α { it α Join<*, () -> RecordMeta>::a }

/** Operator Cursor '/' Class<A>
 *
 * returns Series<Series<A?>>> where the meta is stripped out and the values are cast using
 *
 * it "as?" A return only A values and null for non-A values */
inline operator fun <A : Any, SrInnr : Series<Join<A, *>>, SrOutr : Series<SrInnr>> SrOutr.div( // Removed unused IR, RC
    c: KClass<out A>,
): Series<Series<A?>> = this α { rowVec -> rowVec α { join -> join.a as A? } } // Simplified alpha chain


/** cursor get by IntRange -- return a Cursor with the columns specified by the IntRange */
operator fun Cursor.get(i: IntRange): Cursor {
    require(i.first >= 0) { "index ${i.first} out of bounds for cursor of size $size" }
    require(i.last < size) { "index ${i.last} out of bounds for cursor of size $size" }
    return size j { y ->
        // get the size of range
        val rangeSize = i.last - i.first + 1
        rangeSize j { x ->
            row(y)[i.first + x]
        }
    }
}

/** get meta for a cursor from row 0 */
val Cursor.meta: Series<ColumnMeta>
    get() = row(0) α { item: Join<Any?, () -> ColumnMeta> -> item.b() }

/** create an Intarray of cursor meta by Strings of column names */
fun Cursor.meta(vararg s: String): Series<Int> {
    val meta: Series<ColumnMeta> = meta
    return s.size j { i ->
        meta.`▶`.indexOfFirst { columnMeta: ColumnMeta -> columnMeta.name == s[i] }
    }
}

/** cursor get by String vararg -- return a Cursor with the columns specified by the vararg */
fun Cursor.get(vararg s: String): Cursor = this[meta(*s)]

/** ColumnExclusion value class
 *
 * used to exclude columns from a cursor by name
 *
 * @param name the name of the column to exclude */
@JvmInline
value class ColumnExclusion(val name: String) {
    override fun toString(): String = "ColumnExclusion($name)"
}

/** create operator unary minus for ColumnExclusion on string */
operator fun String.unaryMinus(): ColumnExclusion = ColumnExclusion(this)

/** Return cursor with columns excluded by indexes */
operator fun Cursor.minus(killbag: Series<Int>): Cursor { // Added return type Cursor
    val toSet = (0 until meta.size).toSet()
    val ints = (toSet - killbag.`▶`.toSet()).toIntArray() // Use `▶` for Series to Set conversion
    return this[ints] // Ensure this returns the modified cursor
}

/** cursor get by ColumnExclusion vararg -- return a Cursor with the columns excluded by the vararg */
fun Cursor.get(s: Series<ColumnExclusion>): Cursor {

    val exclusionBag = mutableSetOf<Int>()
    s.`▶`.forEach { excludedCol -> // Removed unused index 'i'
        // Corrected the logic to use excludedCol.name to find the index
        val index = meta.`▶`.indexOfFirst { it.name == excludedCol.name }
        if (index != -1) {
            exclusionBag.add(index)
        }
    }
    val retained = ((0 until meta.size).toSet() - exclusionBag).toIntArray()
    return this[retained]
}

//in columnar project this is meta.right
val Series< ColumnMeta>.names get() = this α ColumnMeta::name

/** head default 5 rows
 * just like unix head - print default 5 lines from cursor contents to stdout */
@JvmOverloads
fun Cursor.head(last: Int = 5): Unit = show(0 until (max(0, min(last, size))))

/** run head starting at random index */
fun Cursor.showRandom(n: Int = 5) {
    head(0);repeat(n) {
        if (size > 0) showValues(Random.nextInt(0, size).let { it..it })
    }
}

/** simple printout macro*/
fun Cursor.show(range: IntRange = 0 until size) {
    val meta: Series<ColumnMeta> = meta
    println("rows:$size" to meta.names.toList())
    showValues(range)
}

fun Cursor.showValues(range: IntRange) {
    try {
        range.forEach { x: Int ->
            val row: RowVec = row(x)

            val show=row α { (c,d) -> // Destructure Join into c (value) and d (meta supplier)
                val meta = d()
                when(meta.type){
                    IOMemento.IoCharSeries -> meta.name to (c as Series<Char>).asString()
                    else -> meta.name to c.toString() // Ensure it's a string for Pair
                }
            }

            println(show.toList())
        }
    } catch (e: IndexOutOfBoundsException) { // Changed NoSuchElementException to IndexOutOfBoundsException for consistency
        println("cannot fully access range $range")
    } catch (e: Exception) {
        println("An error occurred displaying range $range: ${e.message}")
    }
}


/** gets the RowVec at y or if y is negative then -y from last */
infix fun Cursor.at(y: Int): RowVec = b(if (y < 0) size - y else y)
infix fun Cursor.row(y: Int): RowVec = at(y)

/** Cursor get by Int vararg -- return a Cursor with the columns specified by the vararg */
operator fun Cursor.get(vararg i: Int): Cursor = size j { y: Int ->
    i.size j { x: Int ->
        row(y)[i[x]]
    }
}


/** IsNumerical
 * iterate the meta enum types and check if all are numerical
 *
 * IoByte,IoShort,IoInt,IoDouble,IoLong   qualify as numerical
 *
 * kotlin enumset is not available in JS
 *
 */
val Cursor.isNumerical: Boolean
    get() = meta.`▶`.all {
        when (it.type) {
            IOMemento.IoByte, IOMemento.IoShort, IOMemento.IoInt, IOMemento.IoFloat, IOMemento.IoDouble, IOMemento.IoLong -> true
            else -> false
        }
    }

val Cursor.isHomoMorphic: Boolean get() = !meta.`▶`.any { it.type != meta[0].type }
