package org.trikeshed.core

/**
 * An immutable sequence of elements.
 * This is a value class that wraps a List to provide type safety and domain-specific operations.
 */
@JvmInline
value class Series<T>(val ▶: List<T>) {
    companion object {
        fun <T> empty(): Series<T> = Series(emptyList())
        fun <T> of(elements: List<T>): Series<T> = Series(elements)
        fun <T> of(vararg elements: T): Series<T> = Series(elements.toList())
    }
    
    val isEmpty: Boolean get() = ▶.isEmpty()
    val isNotEmpty: Boolean get() = ▶.isNotEmpty()
    val size: Int get() = ▶.size
    
    fun <R> map(transform: (T) -> R): Series<R> = Series(▶.map(transform))
    fun <R> flatMap(transform: (T) -> Series<R>): Series<R> = Series(▶.flatMap { transform(it).▶ })
    fun filter(predicate: (T) -> Boolean): Series<T> = Series(▶.filter(predicate))
    fun forEach(action: (T) -> Unit) = ▶.forEach(action)
    
    operator fun plus(other: Series<T>): Series<T> = Series(▶ + other.▶)
    operator fun plus(element: T): Series<T> = Series(▶ + element)
    
    fun α(transform: (Series<T>) -> Series<T>): Series<T> = transform(this)
}

/**
 * Joins two Series together.
 */
infix fun <T> Series<T>.j(other: Series<T>): Series<T> = this + other

/**
 * Joins a Series with a single element.
 */
infix fun <T> Series<T>.j(element: T): Series<T> = this + element

/**
 * Joins two elements into a Series.
 */
infix fun <T> T.j(other: T): Series<T> = Series.of(this, other) 