@file:Suppress("NOTHING_TO_INLINE")

package borg.trikeshed.cursor

// import the IoMemento enum
import borg.trikeshed.isam.meta.IOMemento.*
import borg.trikeshed.lib.*
import borg.trikeshed.lib.bridge.toSeries
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmOverloads
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.reflect.KClass

// Use DatabaseCursor for operations
// typealias RowVec = Indexed2<Any?, () -> ColumnMeta>
// typealias Cursor = Indexed<RowVec>

/** Operator DatabaseCursor '/' Class<A>
 *
 * returns Indexed<Indexed<A?>>> where the meta is stripped out and the values are cast using
 *
 * it "as?" A return only A values and null for non-A values */
inline operator fun <A : Any, IR : Any?, SrInnr : Indexed<Join<A, *>>, SrOutr : Indexed<SrInnr>, RC : KClass<A?>> SrOutr.div(
    c: KClass<out A>,
): Indexed<Indexed<A?>> = this α { it α Join<A, *>::a } α { it α { it } } α { it α { it } }


/** cursor get by IntRange -- return a DatabaseCursor with the columns specified by the IntRange */
operator fun DatabaseCursor.get(i: IntRange): DatabaseCursor {
    require(i.first >= 0) { "index ${i.first} out of bounds for cursor of size ${this.a}" }
    require(i.last < this.a) { "index ${i.last} out of bounds for cursor of size ${this.a}" }
    return this.a j { y ->
        // get the size of range
        val rangeSize = i.last - i.first + 1
        rangeSize j { x ->
            row(y)[i.first + x]
        }
    }
}

/** get meta for a cursor from row 0 */
val DatabaseCursor.meta: Indexed<ColumnMeta>
    get() = row(0) α { (_, b): Join<*, () -> ColumnMeta> ->
        b()
    }

/** create an Intarray of cursor meta by Strings of column names */
fun DatabaseCursor.meta(vararg s: String): Indexed<Int> {
    val meta: Indexed<ColumnMeta> = meta
    return s.size j { i ->
        meta.play.indexOfFirst { columnMeta: ColumnMeta -> columnMeta.a == s[i] }
    }
}

/** cursor get by String vararg -- return a DatabaseCursor with the columns specified by the vararg */
fun DatabaseCursor.get(vararg s: String): DatabaseCursor = this[meta(*s).play.toList().toIntArray()]

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
operator fun DatabaseCursor.minus(killbag: Indexed<Int>) {
    val toSet = (0 until meta.size).toSet()
    val ints = (toSet - killbag.play.toSet()).toIntArray()
    this[ints]
}

/** cursor get by ColumnExclusion vararg -- return a DatabaseCursor with the columns excluded by the vararg */
fun DatabaseCursor.get(s: Indexed<ColumnExclusion>): DatabaseCursor {

    val exclusionBag = mutableSetOf<Int>()
    s.play.forEachIndexed { i: Int, it: ColumnExclusion ->
        exclusionBag.add(meta.play.indexOfFirst { columnMeta -> columnMeta.a == it.name })
    }
    val retained = ((0 until meta.size).toSet() - exclusionBag).toIntArray()
    return this[retained]
}

//in columnar project this is meta.right
val Indexed<ColumnMeta>.names get() = this α { it.a }

/** head default 5 rows
 * just like unix head - print default 5 lines from cursor contents to stdout */
@JvmOverloads
fun DatabaseCursor.head(last: Int = 5): Unit = show(0 until (max(0, min(last, this.a))))

/** run head starting at random index */
fun DatabaseCursor.showRandom(n: Int = 5) {
    head(0);repeat(n) {
        if (this.a > 0) showValues(Random.nextInt(0, this.a).let { it..it })
    }
}

/** simple printout macro*/
fun DatabaseCursor.show(range: IntRange = 0 until this.a) {
    val meta: Indexed<ColumnMeta> = meta
    println("rows:${this.a}" to meta.names.play.toList())
    showValues(range)
}

fun DatabaseCursor.showValues(range: IntRange) {
    try {
        range.forEach { x: Int ->
            val row: RowVec = row(x)

            val show = row α { cell ->
                val value = cell.a
                val meta = cell.b()
                when ((meta.b as? KClass<*>)?.simpleName) {
                    "IoCharSeries" -> meta.a to (value as? Indexed<Char>)?.play?.joinToString("") ?: value
                    else -> meta.a to value
                }
            }

            println(show.play.toList())
        }
    } catch (e: NoSuchElementException) {
        println("cannot fully access range $range")
    }
}


/** gets the RowVec at y or if y is negative then -y from last */
infix fun DatabaseCursor.at(y: Int): RowVec = b(if (y < 0) this.a - y else y)
infix fun DatabaseCursor.row(y: Int): RowVec = at(y)

/** Cursor get by Int vararg -- return a DatabaseCursor with the columns specified by the vararg */
operator fun DatabaseCursor.get(vararg i: Int): DatabaseCursor = this.a j { y: Int ->
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
val DatabaseCursor.isNumerical: Boolean
    get() = meta.play.all {
        when ((it.b as? KClass<*>)?.simpleName) {
            "IoByte", "IoShort", "IoInt", "IoFloat", "IoDouble", "IoLong" -> true
            else -> false
        }
    }

val DatabaseCursor.isHomoMorphic: Boolean get() = !meta.play.any { it.b != meta[0].b } 