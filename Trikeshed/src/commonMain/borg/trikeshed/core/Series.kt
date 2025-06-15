package borg.trikeshed.core

@JvmInline
value class Series<T>(val size: Int, val accessor: (Int) -> T) {
    companion object {
        fun <T> of(vararg items: T): Series<T> = Series(items.size) { items[it] }
    }
    
    fun <R> α(transform: (T) -> R): Series<R> = Series(size) { transform(accessor(it)) }
    
    operator fun plus(other: Series<T>): Series<T> = Series(size + other.size) { 
        if (it < size) accessor(it) else other.accessor(it - size)
    }
    
    val ▶: Iterable<T> get() = object : Iterable<T> {
        override fun iterator() = object : Iterator<T> {
            private var index = 0
            override fun hasNext() = index < size
            override fun next() = accessor(index++)
        }
    }
}

data class Join<A, B>(val first: A, val second: B)

infix fun <A, B> A.j(b: B): Join<A, B> = Join(this, b) 