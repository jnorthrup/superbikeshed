package borg.trikeshed.core

// Core TrikeShed types - canonical definitions
interface Join<A, B> {
    val a: A
    val b: B
    operator fun component1(): A = a
    operator fun component2(): B = b
}

private data class ConcreteJoin<A, B>(override val a: A, override val b: B) : Join<A, B>

infix fun <A, B> A.j(b: B): Join<A, B> = ConcreteJoin(this, b)

typealias Series<T> = Join<Int, (Int) -> T>

inline val <T> Series<T>.size: Int get() = a
inline operator fun <T> Series<T>.get(i: Int): T = b(i)

fun <T> seriesOf(vararg elements: T): Series<T> = 
    elements.size j { i -> elements[i] }

operator fun <T> Series<T>.plus(other: Series<T>): Series<T> =
    (size + other.size) j { i ->
        if (i < size) this[i] else other[i - size]
    }

fun <T> List<T>.toSeries(): Series<T> = seriesOf(*this.toTypedArray())

// Platform-agnostic BigDecimal
expect class BigDecimal {
    constructor(value: String)
    constructor(value: Double)
    constructor(value: Int)
    
    fun add(other: BigDecimal): BigDecimal
    fun subtract(other: BigDecimal): BigDecimal
    fun multiply(other: BigDecimal): BigDecimal
    fun divide(other: BigDecimal): BigDecimal
    fun compareTo(other: BigDecimal): Int
    
    override fun toString(): String
}

expect fun BigDecimal.Companion.ZERO(): BigDecimal
expect fun BigDecimal.Companion.ONE(): BigDecimal
