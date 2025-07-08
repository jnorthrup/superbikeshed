package borg.trikeshed.lib

/**
 * Fix for ByteBufferPyramid generic arithmetic
 * 
 * Since we can't do arithmetic on generic Comparable<M>, we need:
 * 1. A type class for incrementable values
 * 2. Specific implementations for Int/Long
 */

interface Incrementable<T> {
    fun inc(value: T): T
    fun dec(value: T): T
    fun add(value: T, delta: T): T
    fun zero(): T
}

object IntIncrementable : Incrementable<Int> {
    override fun inc(value: Int): Int = value + 1
    override fun dec(value: Int): Int = value - 1
    override fun add(value: Int, delta: Int): Int = value + delta
    override fun zero(): Int = 0
}

object LongIncrementable : Incrementable<Long> {
    override fun inc(value: Long): Long = value + 1
    override fun dec(value: Long): Long = value - 1
    override fun add(value: Long, delta: Long): Long = value + delta
    override fun zero(): Long = 0L
}

// For now, just use specific types instead of generic M
typealias IntPyramidSlice = PyramidSliceTyped<Int>
typealias LongPyramidSlice = PyramidSliceTyped<Long>

data class PyramidSliceTyped<M>(
    val pyramid: MetaSeries<M, Byte>,
    val position: M,
    val limit: M,
    val mark: M?,
    val incrementable: Incrementable<M>
) where M : Comparable<M> {
    
    val hasRemaining: Boolean get() = position < limit
    
    fun put(byte: Byte): PyramidSliceTyped<M> = 
        copy(position = incrementable.inc(position))
        
    fun flip(): PyramidSliceTyped<M> = 
        copy(position = incrementable.zero(), limit = position, mark = null)
        
    fun clear(): PyramidSliceTyped<M> = 
        copy(position = incrementable.zero(), limit = pyramid.a, mark = null)
        
    fun rewind(): PyramidSliceTyped<M> = 
        copy(position = incrementable.zero(), mark = null)
        
    fun mark(): PyramidSliceTyped<M> = 
        copy(mark = position)
        
    fun reset(): PyramidSliceTyped<M> = 
        copy(position = mark ?: position)
}

// Factory functions
fun intPyramidSlice(pyramid: MetaSeries<Int, Byte>, position: Int = 0, limit: Int = pyramid.a): IntPyramidSlice =
    IntPyramidSlice(pyramid, position, limit, null, IntIncrementable)

fun longPyramidSlice(pyramid: MetaSeries<Long, Byte>, position: Long = 0L, limit: Long = pyramid.a): LongPyramidSlice =
    LongPyramidSlice(pyramid, position, limit, null, LongIncrementable)