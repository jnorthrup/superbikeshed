package borg.trikeshed.lib

/**
 * Core TrikeShed Type System - Essential types for entity scanner
 * Self-contained implementation to avoid dependency issues during development
 */

// ==== CORE FOUNDATION TYPES ====

/**
 * Join<A,B> - The ONLY composition operator in TrikeShed
 */
interface Join<A, B> {
    val a: A
    val b: B
    operator fun component1(): A = a
    operator fun component2(): B = b
    val pair: Pair<A, B> get() = Pair(a, b)
    
    companion object {
        operator fun <A, B> invoke(a: A, b: B): Join<A, B> = object : Join<A, B> {
            override val a: A = a
            override val b: B = b
        }
    }
}

/**
 * Twin<T> - Join of same type
 */
typealias Twin<T> = Join<T, T>

/**
 * j operator - The ONLY composition mechanism in TrikeShed
 */
inline infix fun <A, B> A.j(b: B): Join<A, B> = Join.invoke(this, b)

/**
 * Series<T> - The canonical collection type in TrikeShed  
 */
typealias Series<T> = Join<Int, (Int) -> T>

/**
 * Series2<A,B> - Series of joins
 */
typealias Series2<A, B> = Series<Join<A, B>>

// ==== SERIES OPERATIONS ====

/**
 * Essential operators for Series<T>
 */
val <T> Series<T>.size: Int get() = a
operator fun <T> Series<T>.get(i: Int): T = b(i)

/**
 * α transform - The ONLY transformation operator in TrikeShed
 */
inline infix fun <X, C, V : Series<X>> V.α(crossinline xform: (X) -> C): Series<C> = 
    size j { i -> xform(this[i]) }

/**
 * Play materialization - Gateway to AbstractList/Iterable<T>
 * Use ONLY for final materialization to legacy collections
 */
val <T> Series<T>.play: IterableSeries<T> get() = IterableSeries(this)

@JvmInline
value class IterableSeries<A>(val s: Series<A>) : Iterable<A> {
    override fun iterator(): Iterator<A> = s.iterator()
    val size: Int get() = s.size
    operator fun get(i: Int): A = s[i]
}

/**
 * Iterator implementation for Series<T>
 */
fun <T> Series<T>.iterator(): Iterator<T> = object : Iterator<T> {
    private var index = 0
    override fun hasNext(): Boolean = index < size
    override fun next(): T = this@iterator[index++]
}

// ==== UTILITY FUNCTIONS ====

/**
 * Create Series from List (for interop)
 */
fun <T> List<T>.toSeries(): Series<T> = size j { index -> this[index] }

/**
 * Create Series from Array
 */
fun <T> Array<T>.toSeries(): Series<T> = size j { index -> this[index] }

/**
 * Create empty Series
 */
fun <T> emptySeries(): Series<T> = 0 j { throw IndexOutOfBoundsException("Empty series") }

/**
 * Create Series from varargs
 */
fun <T> seriesOf(vararg elements: T): Series<T> = elements.toList().toSeries()

/**
 * Filter Series (using play for stdlib compatibility)
 */
fun <T> Series<T>.filter(predicate: (T) -> Boolean): List<T> = play.filter(predicate)

/**
 * Map Series (using α transform preferred)
 */
fun <T, R> Series<T>.map(transform: (T) -> R): List<R> = play.map(transform)

// ==== SERIES BUILDERS ====

/**
 * Series builder object for common operations
 */
object SeriesBuilder {
    fun <T> empty(): Series<T> = emptySeries()
    fun <T> of(vararg elements: T): Series<T> = seriesOf(*elements)
    fun <T> fromList(list: List<T>): Series<T> = list.toSeries()
}

// ==== CONVENIENCE ALIASES ====

/**
 * Common type aliases for entity scanning
 */
typealias StringSeries = Series<String>
typealias IntSeries = Series<Int>
typealias BooleanSeries = Series<Boolean>

// For backward compatibility during development
typealias Tensor<T> = Join<IntArray, (IntArray) -> T>