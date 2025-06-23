package borg.trikeshed.collections.v2

/**
 * A NavigableSet implementation backed by a TreeMap.
 */
class TreeSet<E> private constructor(
    private val map: NavigableMap<E, Unit>
) : NavigableSet<E>, MutableSet<E> {

    constructor(comparator: Comparator<in E>? = null) : this(TreeMap(comparator))
    
    internal constructor(m: TreeMap<E, *>) : this(TreeMap<E, Unit>(m.comparator()).apply {
        for (e in m.keys) {
            put(e, Unit)
        }
    })

    override fun comparator(): Comparator<in E>? = map.comparator()

    override val size: Int get() = map.size
    override fun isEmpty(): Boolean = map.isEmpty()
    override fun contains(element: E): Boolean = map.containsKey(element)
    override fun add(element: E): Boolean = map.put(element, Unit) == null
    override fun remove(element: E): Boolean = map.remove(element) != null
    override fun clear() { map.clear() }

    override fun iterator(): MutableIterator<E> = object : MutableIterator<E> {
        private val it = map.entries.iterator()
        override fun hasNext(): Boolean = it.hasNext()
        override fun next(): E = it.next().key
        override fun remove() { it.remove() }
    }

    override fun lower(e: E): E? = map.lowerKey(e)
    override fun floor(e: E): E? = map.floorKey(e)
    override fun ceiling(e: E): E? = map.ceilingKey(e)
    override fun higher(e: E): E? = map.higherKey(e)
    override fun pollFirst(): E? = map.pollFirstEntry()?.key
    override fun pollLast(): E? = map.pollLastEntry()?.key

    override fun descendingSet(): NavigableSet<E> = TreeSet(map.descendingMap())
    override fun descendingIterator(): Iterator<E> = descendingSet().iterator()

    override fun subSet(fromElement: E, fromInclusive: Boolean, toElement: E, toInclusive: Boolean): NavigableSet<E> =
        TreeSet(map.subMap(fromElement, fromInclusive, toElement, toInclusive))

    override fun headSet(toElement: E, inclusive: Boolean): NavigableSet<E> =
        TreeSet(map.headMap(toElement, inclusive))

    override fun tailSet(fromElement: E, inclusive: Boolean): NavigableSet<E> =
        TreeSet(map.tailMap(fromElement, inclusive))

    override fun subSet(fromElement: E, toElement: E): SortedSet<E> =
        subSet(fromElement, true, toElement, false)

    override fun headSet(toElement: E): SortedSet<E> =
        headSet(toElement, false)

    override fun tailSet(fromElement: E): SortedSet<E> =
        tailSet(fromElement, true)

    override fun first(): E = map.firstKey()
    override fun last(): E = map.lastKey()

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is Set<*>) return false
        if (other.size != size) return false
        return containsAll(other as Collection<E>)
    }

    override fun hashCode(): Int {
        var h = 0
        val i = iterator()
        while (i.hasNext()) {
            val e = i.next()
            h += e?.hashCode() ?: 0
        }
        return h
    }

    override fun toString(): String = map.keys.toString()
} 