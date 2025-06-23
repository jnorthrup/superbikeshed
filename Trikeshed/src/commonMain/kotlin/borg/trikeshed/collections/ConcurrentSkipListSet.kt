package borg.trikeshed.collections

/**
 * A thread-safe, lock-free skip list implementation of NavigableSet.
 * 
 * This implementation provides expected average log(n) time cost for the
 * basic operations (add, remove and contains). Insertion, removal, and access
 * operations safely execute concurrently by multiple threads.
 */
class ConcurrentSkipListSet<E>(
    comparator: Comparator<in E>? = null
) : NavigableSet<E> {

    companion object {
        private val PRESENT = Any()
    }

    // Delegate to ConcurrentSkipListMap
    @Suppress("UNCHECKED_CAST")
    private val map = ConcurrentSkipListMap<E, Any>(comparator)

    constructor(c: Collection<E>) : this() {
        addAll(c)
    }

    constructor(s: NavigableSet<E>) : this(s.comparator()) {
        addAll(s)
    }

    override val size: Int get() = map.size

    override fun isEmpty(): Boolean = map.isEmpty()

    override fun contains(element: E): Boolean = map.containsKey(element)

    override fun add(element: E): Boolean {
        return map.put(element, PRESENT) == null
    }

    override fun remove(element: E): Boolean {
        return map.remove(element) != null
    }

    override fun clear() {
        map.clear()
    }

    override fun iterator(): MutableIterator<E> = object : MutableIterator<E> {
        private val mapIterator = map.keys.iterator()
        
        override fun hasNext(): Boolean = mapIterator.hasNext()
        override fun next(): E = mapIterator.next()
        override fun remove() = mapIterator.remove()
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

    override fun containsAll(elements: Collection<E>): Boolean {
        for (element in elements) {
            if (!contains(element)) {
                return false
            }
        }
        return true
    }

    // NavigableSet methods
    override fun comparator(): Comparator<in E>? = map.comparator()

    override fun first(): E? {
        return map.firstKey()
    }

    override fun last(): E? {
        return map.lastKey()
    }

    override fun lower(e: E): E? {
        return map.lowerKey(e)
    }

    override fun floor(e: E): E? {
        return map.floorKey(e)
    }

    override fun ceiling(e: E): E? {
        return map.ceilingKey(e)
    }

    override fun higher(e: E): E? {
        return map.higherKey(e)
    }

    override fun pollFirst(): E? {
        val entry = map.pollFirstEntry()
        return entry?.key
    }

    override fun pollLast(): E? {
        val entry = map.pollLastEntry()
        return entry?.key
    }

    override fun descendingSet(): NavigableSet<E> {
        return DescendingSet(this)
    }

    override fun descendingIterator(): MutableIterator<E> {
        return descendingSet().iterator()
    }

    override fun subSet(fromElement: E, fromInclusive: Boolean, toElement: E, toInclusive: Boolean): NavigableSet<E> {
        return SubSet(this, fromElement, fromInclusive, toElement, toInclusive)
    }

    override fun headSet(toElement: E, inclusive: Boolean): NavigableSet<E> {
        return SubSet(this, null, false, toElement, inclusive)
    }

    override fun tailSet(fromElement: E, inclusive: Boolean): NavigableSet<E> {
        return SubSet(this, fromElement, inclusive, null, false)
    }

    // Helper classes for views
    private class DescendingSet<E>(
        private val originalSet: ConcurrentSkipListSet<E>
    ) : NavigableSet<E> {
        
        override val size: Int get() = originalSet.size
        
        override fun isEmpty(): Boolean = originalSet.isEmpty()
        
        override fun contains(element: E): Boolean = originalSet.contains(element)
        
        override fun add(element: E): Boolean = originalSet.add(element)
        
        override fun remove(element: E): Boolean = originalSet.remove(element)
        
        override fun clear() = originalSet.clear()
        
        override fun iterator(): MutableIterator<E> {
            val list = originalSet.toMutableList()
            list.reverse()
            return list.iterator()
        }
        
        override fun addAll(elements: Collection<E>): Boolean = originalSet.addAll(elements)
        
        override fun removeAll(elements: Collection<E>): Boolean = originalSet.removeAll(elements)
        
        override fun retainAll(elements: Collection<E>): Boolean = originalSet.retainAll(elements)
        
        override fun containsAll(elements: Collection<E>): Boolean = originalSet.containsAll(elements)
        
        override fun comparator(): Comparator<in E>? {
            val comp = originalSet.comparator()
            return if (comp == null) {
                @Suppress("UNCHECKED_CAST")
                Comparator { a, b -> (b as Comparable<E>).compareTo(a) }
            } else {
                Comparator { a, b -> comp.compare(b, a) }
            }
        }
        
        override fun first(): E? = originalSet.last()
        
        override fun last(): E? = originalSet.first()
        
        override fun lower(e: E): E? = originalSet.higher(e)
        
        override fun floor(e: E): E? = originalSet.ceiling(e)
        
        override fun ceiling(e: E): E? = originalSet.floor(e)
        
        override fun higher(e: E): E? = originalSet.lower(e)
        
        override fun pollFirst(): E? = originalSet.pollLast()
        
        override fun pollLast(): E? = originalSet.pollFirst()
        
        override fun descendingSet(): NavigableSet<E> = originalSet
        
        override fun descendingIterator(): MutableIterator<E> = originalSet.iterator()
        
        override fun subSet(fromElement: E, fromInclusive: Boolean, toElement: E, toInclusive: Boolean): NavigableSet<E> {
            return originalSet.subSet(toElement, toInclusive, fromElement, fromInclusive).descendingSet()
        }
        
        override fun headSet(toElement: E, inclusive: Boolean): NavigableSet<E> {
            return originalSet.tailSet(toElement, inclusive).descendingSet()
        }
        
        override fun tailSet(fromElement: E, inclusive: Boolean): NavigableSet<E> {
            return originalSet.headSet(fromElement, inclusive).descendingSet()
        }
    }

    private class SubSet<E>(
        private val originalSet: ConcurrentSkipListSet<E>,
        private val fromElement: E?,
        private val fromInclusive: Boolean,
        private val toElement: E?,
        private val toInclusive: Boolean
    ) : NavigableSet<E> {
        
        @Suppress("UNCHECKED_CAST")
        private val comp = originalSet.comparator() ?: Comparator { a, b -> (a as Comparable<E>).compareTo(b) }
        
        private fun inRange(element: E): Boolean {
            if (fromElement != null) {
                val c = comp.compare(element, fromElement)
                if (c < 0 || (!fromInclusive && c == 0)) return false
            }
            if (toElement != null) {
                val c = comp.compare(element, toElement)
                if (c > 0 || (!toInclusive && c == 0)) return false
            }
            return true
        }
        
        override val size: Int
            get() = count { inRange(it) }
        
        override fun isEmpty(): Boolean = !iterator().hasNext()
        
        override fun contains(element: E): Boolean = inRange(element) && originalSet.contains(element)
        
        override fun add(element: E): Boolean {
            if (!inRange(element)) throw IllegalArgumentException("Element out of range")
            return originalSet.add(element)
        }
        
        override fun remove(element: E): Boolean {
            return if (inRange(element)) originalSet.remove(element) else false
        }
        
        override fun clear() {
            val iter = iterator()
            while (iter.hasNext()) {
                iter.next()
                iter.remove()
            }
        }
        
        override fun iterator(): MutableIterator<E> = object : MutableIterator<E> {
            private val originalIter = originalSet.iterator()
            private var nextElement: E? = null
            private var currentElement: E? = null
            private var hasNextCached = false
            
            override fun hasNext(): Boolean {
                if (!hasNextCached) {
                    while (originalIter.hasNext()) {
                        val elem = originalIter.next()
                        if (inRange(elem)) {
                            nextElement = elem
                            hasNextCached = true
                            return true
                        }
                    }
                    hasNextCached = true
                    nextElement = null
                }
                return nextElement != null
            }
            
            override fun next(): E {
                if (!hasNext()) throw NoSuchElementException()
                currentElement = nextElement
                hasNextCached = false
                return currentElement!!
            }
            
            override fun remove() {
                val elem = currentElement ?: throw IllegalStateException()
                originalSet.remove(elem)
            }
        }
        
        override fun addAll(elements: Collection<E>): Boolean {
            var modified = false
            for (element in elements) {
                if (add(element)) modified = true
            }
            return modified
        }
        
        override fun removeAll(elements: Collection<E>): Boolean {
            var modified = false
            for (element in elements) {
                if (remove(element)) modified = true
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
        
        override fun containsAll(elements: Collection<E>): Boolean {
            for (element in elements) {
                if (!contains(element)) return false
            }
            return true
        }
        
        override fun comparator(): Comparator<in E>? = originalSet.comparator()
        
        override fun first(): E? {
            val iter = iterator()
            return if (iter.hasNext()) iter.next() else null
        }
        
        override fun last(): E? {
            var last: E? = null
            val iter = iterator()
            while (iter.hasNext()) {
                last = iter.next()
            }
            return last
        }
        
        override fun lower(e: E): E? {
            if (!inRange(e)) return null
            return originalSet.lower(e)?.takeIf { inRange(it) }
        }
        
        override fun floor(e: E): E? {
            if (!inRange(e)) return null
            return originalSet.floor(e)?.takeIf { inRange(it) }
        }
        
        override fun ceiling(e: E): E? {
            if (!inRange(e)) return null
            return originalSet.ceiling(e)?.takeIf { inRange(it) }
        }
        
        override fun higher(e: E): E? {
            if (!inRange(e)) return null
            return originalSet.higher(e)?.takeIf { inRange(it) }
        }
        
        override fun pollFirst(): E? {
            val first = first()
            return if (first != null && remove(first)) first else null
        }
        
        override fun pollLast(): E? {
            val last = last()
            return if (last != null && remove(last)) last else null
        }
        
        override fun descendingSet(): NavigableSet<E> = DescendingSet(originalSet)
        
        override fun descendingIterator(): MutableIterator<E> = descendingSet().iterator()
        
        override fun subSet(fromElement: E, fromInclusive: Boolean, toElement: E, toInclusive: Boolean): NavigableSet<E> {
            return SubSet(originalSet, fromElement, fromInclusive, toElement, toInclusive)
        }
        
        override fun headSet(toElement: E, inclusive: Boolean): NavigableSet<E> {
            return SubSet(originalSet, fromElement, fromInclusive, toElement, inclusive)
        }
        
        override fun tailSet(fromElement: E, inclusive: Boolean): NavigableSet<E> {
            return SubSet(originalSet, fromElement, inclusive, toElement, toInclusive)
        }
    }
}