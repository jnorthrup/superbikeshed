package borg.trikeshed.lib

/**
 * Core TrikeShed types for rtsgame
 * Simplified implementations following TrikeShed patterns
 */

data class Join<A, B>(val a: A, val b: B)

// j operator imported from canonical borg.trikeshed.lib.Join
// Use: import borg.trikeshed.lib.j

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