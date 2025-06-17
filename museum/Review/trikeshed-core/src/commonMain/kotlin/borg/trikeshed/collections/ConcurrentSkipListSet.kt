package borg.trikeshed.collections

import kotlin.collections.NavigableSet // Ensure this is the correct import based on Kotlin version and target

class ConcurrentSkipListSet<E>(
    private val comparator: Comparator<in E>? = null
) : MutableSet<E>, NavigableSet<E> {

    private val PRESENT = true // Dummy value for the map
    private val internalMap: ConcurrentSkipListMap<E, Boolean> = ConcurrentSkipListMap(comparator)

    // --- MutableSet<E> Overrides ---

    override val size: Int
        get() = internalMap.size

    override fun isEmpty(): Boolean = internalMap.isEmpty()

    override fun contains(element: E): Boolean = internalMap.containsKey(element)

    override fun iterator(): MutableIterator<E> {
        // Returns an iterator over the keys of the internal map.
        // The map's keySet().iterator() should be sufficient.
        return internalMap.keys.iterator()
    }

    override fun add(element: E): Boolean {
        // putIfAbsent is safer for sets, but ConcurrentSkipListMap doesn't have it yet explicitly.
        // put returns null if the key was not present.
        return internalMap.put(element, PRESENT) == null
    }

    override fun remove(element: E): Boolean {
        return internalMap.remove(element) != null // remove returns old value, non-null if present
    }

    override fun containsAll(elements: Collection<E>): Boolean {
        return elements.all { internalMap.containsKey(it) }
    }

    override fun addAll(elements: Collection<E>): Boolean {
        var modified = false
        for (element in elements) {
            if (add(element)) {
                modified = true
            }
        }
        return modified
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        var modified = false
        for (element in elements) {
            if (remove(element)) {
                modified = true
            }
        }
        return modified
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        var modified = false
        val iter = iterator()
        while (iter.hasNext()) {
            if (!elements.contains(iter.next())) {
                iter.remove()
                modified = true
            }
        }
        return modified
    }

    override fun clear() {
        internalMap.clear()
    }

    // --- NavigableSet<E> Overrides ---

    override fun lower(e: E): E? = internalMap.lowerKey(e)

    override fun floor(e: E): E? = internalMap.floorKey(e)

    override fun ceiling(e: E): E? = internalMap.ceilingKey(e)

    override fun higher(e: E): E? = internalMap.higherKey(e)

    override fun pollFirst(): E? {
        val entry = internalMap.pollFirstEntry()
        return entry?.key
    }

    override fun pollLast(): E? {
        val entry = internalMap.pollLastEntry()
        return entry?.key
    }

    override fun comparator(): Comparator<in E>? = internalMap.comparator()

    override fun first(): E {
        return internalMap.firstKey() ?: throw NoSuchElementException("Set is empty")
    }

    override fun last(): E {
        return internalMap.lastKey() ?: throw NoSuchElementException("Set is empty")
    }

    override fun descendingSet(): NavigableSet<E> {
        // Depends on internalMap.descendingMap().navigableKeySet()
        // Since descendingMap() in ConcurrentSkipListMap is a TODO, this will be too.
        throw UnsupportedOperationException("descendingSet is not yet implemented")
        // val descendingMap = internalMap.descendingMap()
        // return descendingMap.navigableKeySet() // This would be the ideal delegation
    }

    override fun descendingIterator(): Iterator<E> {
        // Depends on descendingSet() or a direct descending key iterator from the map
        throw UnsupportedOperationException("descendingIterator is not yet implemented")
        // return descendingSet().iterator()
    }

    override fun subSet(
        fromElement: E,
        fromInclusive: Boolean,
        toElement: E,
        toInclusive: Boolean
    ): NavigableSet<E> {
        // Depends on internalMap.subMap().navigableKeySet()
        // Since subMap() in ConcurrentSkipListMap is a TODO, this will be too.
        throw UnsupportedOperationException("subSet is not yet implemented")
        // val subMap = internalMap.subMap(fromElement, fromInclusive, toElement, toInclusive)
        // return subMap.navigableKeySet()
    }

    override fun headSet(toElement: E, inclusive: Boolean): NavigableSet<E> {
        throw UnsupportedOperationException("headSet is not yet implemented")
        // val headMap = internalMap.headMap(toElement, inclusive)
        // return headMap.navigableKeySet()
    }

    override fun tailSet(fromElement: E, inclusive: Boolean): NavigableSet<E> {
        throw UnsupportedOperationException("tailSet is not yet implemented")
        // val tailMap = internalMap.tailMap(fromElement, inclusive)
        // return tailMap.navigableKeySet()
    }

    // --- Standard methods (equals, hashCode) ---
    // AbstractMutableSet provides default implementations for equals and hashCode
    // which rely on iterator, size, contains.
    // toString also has a default implementation.
}
