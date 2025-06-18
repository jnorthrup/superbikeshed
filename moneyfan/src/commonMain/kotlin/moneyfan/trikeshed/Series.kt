package moneyfan.trikeshed

import kotlin.jvm.JvmInline

/**
 * A functional, immutable, indexed series of elements.
 *
 * @param T The type of elements in the series.
 * @property a The size of the series. (Corresponds to 'size' in TrikeShed)
 * @property b The getter function for elements by index. (Corresponds to 'getter' in TrikeShed)
 */
interface Series<out T> {
    val a: Int // Size of the series
    val b: (index: Int) -> T // Getter function: index -> element

    operator fun get(index: Int): T {
        if (index < 0 || index >= a) throw IndexOutOfBoundsException("Index $index out of bounds for size $a")
        return b(index)
    }
}

/**
 * Concrete factory for creating Series instances.
 * Allows syntax like `10 j { it * it }` to create a series of 10 squares.
 */
infix fun <T> Int.j(getter: (index: Int) -> T): Series<T> {
    if (this < 0) throw IllegalArgumentException("Series size cannot be negative: $this")
    val size = this
    return object : Series<T> {
        override val a: Int = size
        override val b: (index: Int) -> T = getter
    }
}

/**
 * The "alpha" operator (map / transform).
 * Creates a new series by applying a transformation function to each element of the original series.
 *
 * @param X The type of elements in the original series.
 * @param C The type of elements in the resulting series.
 * @param V The specific Series subtype.
 * @param xform The transformation function.
 * @return A new Series containing the transformed elements.
 */
inline infix fun <X, C, V : Series<X>> V.α(crossinline xform: (X) -> C): Series<C> {
    return this.a j { index -> xform(this.b(index)) }
}

/**
 * Wraps a Series to make it easily iterable in for-loops.
 */
@JvmInline
value class IterableSeries<A>(val s: Series<A>) : Iterable<A>, Series<A> by s {
    override operator fun iterator(): Iterator<A> = s.iterator()
}

/**
 * Extension property to easily obtain an IterableSeries from any Series.
 * Allows syntax like `mySeries.`play`.forEach { ... }`
 */
val <T> Series<T>.`play`: IterableSeries<T> get() = IterableSeries(this)


// --- Basic .toSeries() extensions ---
/** Converts a List to a Series. */
fun <T> List<T>.toSeries(): Series<T> = size j { index -> this[index] }

/** Converts an Array to a Series. */
fun <T> Array<T>.toSeries(): Series<T> = size j { index -> this[index] }

// --- Empty Series ---
/** Represents an empty series. */
object EmptySeries : Series<Nothing> {
    override val a: Int = 0
    override val b: (index: Int) -> Nothing = { throw IndexOutOfBoundsException("Empty series") }
}

/** Returns an empty series of the specified type. */
@Suppress("UNCHECKED_CAST")
fun <T> emptySeries(): Series<T> = EmptySeries as Series<T>

// --- Iterator Support ---
/** Provides an iterator for Series elements. */
operator fun <A> Series<A>.iterator(): Iterator<A> = object : Iterator<A> {
    private var currentIndex = 0
    override fun hasNext(): Boolean = currentIndex < a
    override fun next(): A {
        if (!hasNext()) throw NoSuchElementException()
        return b(currentIndex++)
    }
}

// --- Other Essential Series Extensions ---

/** Returns `true` if the series contains no elements. */
fun Series<*>.isEmpty(): Boolean = a == 0

/** Returns `true` if the series contains elements. */
fun Series<*>.isNotEmpty(): Boolean = a != 0

/** Performs the given [action] on each element. */
inline fun <T> Series<T>.forEach(action: (T) -> Unit) {
    for (index in 0 until a) {
        action(b(index))
    }
}

/**
 * Returns a new series containing the results of applying the given [transform] function
 * to each element in the original series. This is a simplified map, equivalent to `α`.
 */
inline fun <T, R> Series<T>.map(crossinline transform: (T) -> R): Series<R> {
    return this.a j { index -> transform(this.b(index)) }
}

/** Returns the first element. @throws NoSuchElementException if the series is empty. */
fun <T> Series<T>.first(): T {
    if (isEmpty()) throw NoSuchElementException("Series is empty.")
    return b(0)
}

/** Returns the first element, or `null` if the series is empty. */
fun <T> Series<T>.firstOrNull(): T? = if (isEmpty()) null else b(0)

/** Returns the last element. @throws NoSuchElementException if the series is empty. */
fun <T> Series<T>.last(): T {
    if (isEmpty()) throw NoSuchElementException("Series is empty.")
    return b(a - 1)
}

/** Returns the last element, or `null` if the series is empty. */
fun <T> Series<T>.lastOrNull(): T? = if (isEmpty()) null else b(a - 1)

/** Returns a series containing the first [n] elements. */
fun <T> Series<T>.take(n: Int): Series<T> {
    require(n >= 0) { "Requested element count $n is less than zero." }
    if (n == 0) return emptySeries()
    val newSize = kotlin.math.min(n, this.a)
    return newSize j { index -> this.b(index) }
}

/** Returns a series containing all elements except first [n] elements. */
fun <T> Series<T>.drop(n: Int): Series<T> {
    require(n >= 0) { "Requested element count $n is less than zero." }
    if (n == 0) return this
    if (n >= this.a) return emptySeries()
    val newSize = this.a - n
    return newSize j { index -> this.b(index + n) }
}

/** Returns a series with elements in reversed order. */
fun <T> Series<T>.reversed(): Series<T> {
    if (isEmpty()) return this
    return this.a j { index -> this.b(this.a - 1 - index) }
}

/**
 * Returns a list containing all elements.
 */
fun <T> Series<T>.toList(): List<T> {
    if (isEmpty()) return emptyList()
    val list = ArrayList<T>(a)
    for (i in 0 until a) {
        list.add(b(i))
    }
    return list
}
