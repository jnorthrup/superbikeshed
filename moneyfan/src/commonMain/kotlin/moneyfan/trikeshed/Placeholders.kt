package moneyfan.trikeshed

/**
 * Placeholder for TrikeShed's Indexed<T> interface.
 * Represents an indexed collection, typically a Series.
 * 'a' is usually the size, 'b(index)' is the element access.
 */
interface Indexed<out T> {
    val a: Int // Represents size
    fun b(index: Int): T // Represents element access at index

    // Common operations that might be available on Indexed<T>
    // These are simplified placeholders for what TrikeShed might offer.

    // Example of how .toList() might be used (seen in chronicle examples)
    fun toList(): List<T> {
        val list = mutableListOf<T>()
        for (i in 0 until a) {
            list.add(b(i))
        }
        return list
    }
}

/**
 * Placeholder for TrikeShed's emptySeries factory function.
 */
fun <T> emptySeries(): Indexed<T> {
    return object : Indexed<T> {
        override val a: Int = 0
        override fun b(index: Int): T {
            throw IndexOutOfBoundsException("Accessing empty series at index $index")
        }
    }
}

/**
 * Placeholder for TrikeShed's α (alpha) transformation operator.
 * Typically a map-like operation.
 * fun <T, R> Indexed<T>.α(transform: (T) -> R): Indexed<R>
 */
inline fun <T, R> Indexed<T>.α(crossinline transform: (T) -> R): Indexed<R> {
    val source = this
    return object : Indexed<R> {
        override val a: Int = source.a
        override fun b(index: Int): R = transform(source.b(index))
    }
}

/**
 * Placeholder for TrikeShed's j (join) operator or series construction.
 * This is a simplified version for constructing an Indexed series based on size and an element provider.
 * fun <R> Int.j(provider: (Int) -> R): Indexed<R>
 * This allows syntax like `size j { index -> element }`
 */
inline fun <R> Int.j(crossinline provider: (Int) -> R): Indexed<R> {
    val size = this
    return object : Indexed<R> {
        override val a: Int = size
        override fun b(index: Int): R = provider(index)
    }
}

// Example of a concrete Indexed implementation for tests if needed elsewhere,
// though each test file currently defines its own for simplicity.
class ListIndexed<T>(private val items: List<T>) : Indexed<T> {
    override val a: Int = items.size
    override fun b(index: Int): T = items[index]
}
