@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.lib

/**
 * Minimal TrikeShed compatibility layer for k2script standalone operation
 * This provides the essential patterns without external dependencies
 */

// Indexed<T> - Core series pattern
interface Indexed<T> {
    val size: Int
    operator fun get(index: Int): T
}

// Indexed implementation
class IndexedImpl<T>(internal val generator: (Int) -> T, override val size: Int) : Indexed<T> {
    override fun get(index: Int): T {
        if (index < 0 || index >= size) throw IndexOutOfBoundsException("Index $index out of bounds for size $size")
        return generator(index)
    }
}

// Join<A,B> - Core composition pattern
data class Join<A, B>(val a: A, val b: B)

// DSL operators
infix fun <A, B> A.j(other: B): Join<A, B> = Join(this, other)
infix fun <T> Int.j(generator: (Int) -> T): Indexed<T> = IndexedImpl(generator, this)

// Utility functions
fun <T> emptySeries(): Indexed<T> = IndexedImpl({ throw IndexOutOfBoundsException("Empty series") }, 0)

// Extension for compatibility
val <T> Indexed<T>.play: List<T>
    get() = (0 until size).map { this[it] }