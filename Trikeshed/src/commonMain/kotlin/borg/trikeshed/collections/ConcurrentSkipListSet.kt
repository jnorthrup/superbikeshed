package borg.trikeshed.collections

import borg.trikeshed.collections.v2.ConcurrentSkipListSet as V2ConcurrentSkipListSet
import borg.trikeshed.lib.*

/**
 * Legacy ConcurrentSkipListSet implementation
 * Delegates to v2 implementation for compatibility
 */
class ConcurrentSkipListSet<E> private constructor(
    private val delegate: V2ConcurrentSkipListSet<E>
) : MutableSet<E> by delegate {
    
    constructor() : this(V2ConcurrentSkipListSet())
    
    constructor(comparator: Comparator<in E>) : this(V2ConcurrentSkipListSet(comparator))
    
    constructor(collection: Collection<E>) : this(V2ConcurrentSkipListSet(collection))
    
    constructor(sortedSet: SortedSet<E>) : this(V2ConcurrentSkipListSet(sortedSet))
    
    // Delegate all methods to v2 implementation
    fun first(): E? = delegate.first()
    fun last(): E? = delegate.last()
    fun lower(e: E): E? = delegate.lower(e)
    fun floor(e: E): E? = delegate.floor(e)
    fun ceiling(e: E): E? = delegate.ceiling(e)
    fun higher(e: E): E? = delegate.higher(e)
    fun pollFirst(): E? = delegate.pollFirst()
    fun pollLast(): E? = delegate.pollLast()
    fun descendingSet(): ConcurrentSkipListSet<E> = ConcurrentSkipListSet(delegate.descendingSet())
    fun descendingIterator(): MutableIterator<E> = delegate.descendingIterator()
    fun subSet(fromElement: E, fromInclusive: Boolean, toElement: E, toInclusive: Boolean): ConcurrentSkipListSet<E> = 
        ConcurrentSkipListSet(delegate.subSet(fromElement, fromInclusive, toElement, toInclusive))
    fun headSet(toElement: E, inclusive: Boolean): ConcurrentSkipListSet<E> = 
        ConcurrentSkipListSet(delegate.headSet(toElement, inclusive))
    fun tailSet(fromElement: E, inclusive: Boolean): ConcurrentSkipListSet<E> = 
        ConcurrentSkipListSet(delegate.tailSet(fromElement, inclusive))
    fun comparator(): Comparator<in E>? = delegate.comparator()
    fun headSet(toElement: E): SortedSet<E> = delegate.headSet(toElement)
    fun tailSet(fromElement: E): SortedSet<E> = delegate.tailSet(fromElement)
    fun subSet(fromElement: E, toElement: E): SortedSet<E> = delegate.subSet(fromElement, toElement)
}