package borg.trikeshed.lib

/**
 * Mutable Indexed with operators
 * 
 * Adapted from MutableSeries to work with Indexed<T> instead of Indexed<T>
 */
interface MutableIndexed<T> : Indexed<T> {
    operator fun set(index: Int, item: T)
    fun add(item: T)
    fun add(index: Int, item: T)
    fun removeAt(index: Int): T
    fun remove(item: T): Boolean
    fun clear()

    //+,-,+=,-=, etc
    operator fun plus(item: T): MutableIndexed<T>
    operator fun minus(item: T): MutableIndexed<T>
    operator fun plusAssign(item: T)
    operator fun minusAssign(item: T)
}