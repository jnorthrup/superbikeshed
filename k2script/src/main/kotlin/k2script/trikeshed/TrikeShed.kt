@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.trikeshed

@kotlin.jvm.JvmInline
value class Indexed<T>(val size: Int, val accessor: (Int) -> T) {
    companion object {
        fun <T> of(vararg items: T): Indexed<T> = Indexed(items.size) { items[it] }
    }
    
    fun <R> α(transform: (T) -> R): Indexed<R> = Indexed(size) { transform(accessor(it)) }
    
    operator fun plus(other: Indexed<T>): Indexed<T> = Indexed(size + other.size) { 
        if (it < size) accessor(it) else other.accessor(it - size)
    }
    
    val play: Iterable<T> get() = object : Iterable<T> {
        override fun iterator() = object : Iterator<T> {
            internal var index = 0
            override fun hasNext() = index < size
            override fun next() = accessor(index++)
        }
    }
}

data class Join<A, B>(val first: A, val second: B)

infix fun <A, B> A.j(b: B): Join<A, B> = Join(this, b) 