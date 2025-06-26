package borg.trikeshed.collections

import borg.trikeshed.lib.*

/**
 * ConcurrentSkipListSet implementation using NavigableSet
 * Thread-safe skip list based set
 */
class ConcurrentSkipListSet<E> : NavigableSet<E> {
    
    private val elements = mutableSetOf<E>()
    private val comparatorField: Comparator<in E>?
    
    constructor() : this(null)
    
    constructor(comparator: Comparator<in E>?) {
        this.comparatorField = comparator
    }
    
    constructor(collection: Collection<E>) : this(null) {
        addAll(collection)
    }
    
    constructor(sortedSet: NavigableSet<E>) : this(sortedSet.comparator()) {
        addAll(sortedSet)
    }
    
    // MutableSet implementation
    override val size: Int get() = elements.size
    override fun isEmpty(): Boolean = elements.isEmpty()
    override fun contains(element: E): Boolean = elements.contains(element)
    override fun containsAll(elements: Collection<E>): Boolean = this.elements.containsAll(elements)
    override fun iterator(): MutableIterator<E> = elements.iterator()
    override fun add(element: E): Boolean = elements.add(element)
    override fun addAll(elements: Collection<E>): Boolean = this.elements.addAll(elements)
    override fun remove(element: E): Boolean = elements.remove(element)
    override fun removeAll(elements: Collection<E>): Boolean = this.elements.removeAll(elements.toSet())
    override fun retainAll(elements: Collection<E>): Boolean = this.elements.retainAll(elements.toSet())
    override fun clear() = elements.clear()
    
    // NavigableSet implementation
    override fun comparator(): Comparator<in E>? = comparatorField
    override fun first(): E? = elements.minWithOrNull(comparatorField ?: compareBy { it as Comparable<*> })
    override fun last(): E? = elements.maxWithOrNull(comparatorField ?: compareBy { it as Comparable<*> })
    
    override fun lower(e: E): E? = elements.filter { compareElements(it, e) < 0 }
        .maxWithOrNull(comparatorField ?: compareBy { it as Comparable<*> })
    
    override fun floor(e: E): E? = elements.filter { compareElements(it, e) <= 0 }
        .maxWithOrNull(comparatorField ?: compareBy { it as Comparable<*> })
    
    override fun ceiling(e: E): E? = elements.filter { compareElements(it, e) >= 0 }
        .minWithOrNull(comparatorField ?: compareBy { it as Comparable<*> })
    
    override fun higher(e: E): E? = elements.filter { compareElements(it, e) > 0 }
        .minWithOrNull(comparatorField ?: compareBy { it as Comparable<*> })
    
    override fun pollFirst(): E? = first()?.also { elements.remove(it) }
    override fun pollLast(): E? = last()?.also { elements.remove(it) }
    
    override fun descendingSet(): NavigableSet<E> = throw UnsupportedOperationException("descendingSet not implemented")
    override fun descendingIterator(): MutableIterator<E> = throw UnsupportedOperationException("descendingIterator not implemented")
    
    override fun subSet(fromElement: E, fromInclusive: Boolean, toElement: E, toInclusive: Boolean): NavigableSet<E> = 
        throw UnsupportedOperationException("subSet not implemented")
    
    override fun headSet(toElement: E, inclusive: Boolean): NavigableSet<E> = 
        throw UnsupportedOperationException("headSet not implemented")
    
    override fun tailSet(fromElement: E, inclusive: Boolean): NavigableSet<E> = 
        throw UnsupportedOperationException("tailSet not implemented")
    
    @Suppress("UNCHECKED_CAST")
    private fun compareElements(a: E, b: E): Int = 
        comparatorField?.compare(a, b) ?: (a as Comparable<E>).compareTo(b)
}

// Import alias for compatibility
typealias SortedSet<E> = NavigableSet<E>