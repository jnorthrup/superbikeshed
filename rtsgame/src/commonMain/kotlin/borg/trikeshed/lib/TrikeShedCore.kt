package borg.trikeshed.lib

/**
 * Core TrikeShed types for rtsgame
 * Simplified implementations following TrikeShed patterns
 */

data class Join<A, B>(val a: A, val b: B)

infix fun <A, B> A.j(other: B): Join<A, B> = Join(this, other)

// Import alias for canonical j operator  
typealias RtsJoin<A, B> = Join<A, B>
infix fun <A, B> A.rj(other: B): RtsJoin<A, B> = Join(this, other)

class Series<T>(internal val data: List<T>) {
    val size: Int get() = data.size
    
    companion object {
        fun <T> of(size: Int, accessor: (Int) -> T): Series<T> =
            Series(List(size, accessor))
    }
}

fun <T, R> Series<T>.α(transform: (T) -> R): Series<R> =
    Series.of(size) { i -> transform(data[i]) }

val <T> Series<T>.play: List<T> get() = data