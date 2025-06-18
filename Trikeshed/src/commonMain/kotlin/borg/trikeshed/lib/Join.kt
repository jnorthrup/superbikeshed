@file:Suppress("NonAsciiCharacters", "FunctionName", "ObjectPropertyName", "OVERRIDE_BY_INLINE", "UNCHECKED_CAST")

package borg.trikeshed.lib

/**
 * Core composition type for TrikeShed.
 * Used to combine two values into a single immutable structure.
 */
interface Join<A, B> {
    val a: A
    val b: B
    operator fun component1(): A = a
    operator fun component2(): B = b
    val pair: Pair<A, B> get() = Pair(a, b)

    /** debugger hack only, violates all common sense */
    val list: List<Any?> get() = (this as? Series<out Any?>)?.toList() ?: emptyList()

    companion object {
        //the Join factory method
        operator fun <A, B> invoke(a: A, b: B): Join<A, B> = JoinImpl(a, b)

        //the Pair factory method
        operator fun <A, B> invoke(pair: Pair<A, B>): Join<A, B> = JoinImpl(pair.first, pair.second)

        //the Map factory method
        operator fun <A, B> invoke(map: Map<A, B>): Series<Join<A, B>> = object : Series<Join<A, B>> {
            override val a: Int get() = map.size
            override val b: (Int) -> Join<A, B> get() = { map.entries.elementAt(it).let { Join(it.key, it.value) } }
        }

        fun <B> emptySeriesOf(): Series<B> = EmptySeries as Series<B>
    }
}

/**
 * Implementation of Join that stores two values.
 */
internal data class JoinImpl<A, B>(
    override val a: A,
    override val b: B
) : Join<A, B>

/**
 * Creates a Join from two values.
 */
inline infix fun <A, B> A.j(b: B): Join<A, B> = JoinImpl(this, b)


/**
 * Type alias for a Join of the same type.
 */
typealias Twin<T> = Join<T, T>

//Twin factory method
inline fun <T> Twin(a: T, b: T): Twin<T> = a j b

inline val <A> Join<A, *>.first: A get() = this.a
inline val <B> Join<*, B>.second: B get() = this.b

/**
 * Utility functions for working with Join instances.
 */
object JoinUtil {
    fun <A, B, R> Join<A, B>.map(transform: (A, B) -> R): R = transform(a, b)
    fun <A, B, R> Join<A, B>.mapFirst(transform: (A) -> R): Join<R, B> = Join(transform(a), b)
    fun <A, B, R> Join<A, B>.mapSecond(transform: (B) -> R): Join<A, R> = Join(a, transform(b))
}

