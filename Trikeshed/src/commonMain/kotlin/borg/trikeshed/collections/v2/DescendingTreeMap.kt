package borg.trikeshed.collections.v2

/**
 * A descending view of a TreeMap.
 */
internal class DescendingTreeMap<K, V>(private val m: TreeMap<K, V>) : NavigableMap<K, V>, AbstractMutableMap<K, V>() {
    private val reverseComparator: Comparator<in K>? = m.comparator()?.reversed() ?: 
        Comparator { k1, k2 -> (k2 as Comparable<K>).compareTo(k1) }

    override fun comparator(): Comparator<in K>? = reverseComparator
    override val size: Int get() = m.size
    override fun isEmpty(): Boolean = m.isEmpty()
    override fun containsKey(key: K): Boolean = m.containsKey(key)
    override fun containsValue(value: V): Boolean = m.containsValue(value)
    override fun get(key: K): V? = m.get(key)
    override fun put(key: K, value: V): V? = m.put(key, value)
    override fun remove(key: K): V? = m.remove(key)
    override fun clear() = m.clear()

    override fun firstKey(): K = m.lastKey()
    override fun lastKey(): K = m.firstKey()
    override fun firstEntry(): Map.Entry<K, V>? = m.lastEntry()
    override fun lastEntry(): Map.Entry<K, V>? = m.firstEntry()
    override fun pollFirstEntry(): Map.Entry<K, V>? = m.pollLastEntry()
    override fun pollLastEntry(): Map.Entry<K, V>? = m.pollFirstEntry()

    override fun lowerEntry(key: K): Map.Entry<K, V>? = m.higherEntry(key)
    override fun lowerKey(key: K): K? = m.higherKey(key)
    override fun floorEntry(key: K): Map.Entry<K, V>? = m.ceilingEntry(key)
    override fun floorKey(key: K): K? = m.ceilingKey(key)
    override fun ceilingEntry(key: K): Map.Entry<K, V>? = m.floorEntry(key)
    override fun ceilingKey(key: K): K? = m.floorKey(key)
    override fun higherEntry(key: K): Map.Entry<K, V>? = m.lowerEntry(key)
    override fun higherKey(key: K): K? = m.lowerKey(key)

    override fun descendingMap(): NavigableMap<K, V> = m
    override fun navigableKeySet(): NavigableSet<K> = TreeSet(this)
    override fun descendingKeySet(): NavigableSet<K> = m.navigableKeySet()

    override fun subMap(fromKey: K, fromInclusive: Boolean, toKey: K, toInclusive: Boolean): NavigableMap<K, V> =
        m.subMap(toKey, toInclusive, fromKey, fromInclusive).descendingMap()

    override fun headMap(toKey: K, inclusive: Boolean): NavigableMap<K, V> =
        m.tailMap(toKey, inclusive).descendingMap()

    override fun tailMap(fromKey: K, inclusive: Boolean): NavigableMap<K, V> =
        m.headMap(fromKey, inclusive).descendingMap()

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = object : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
            override val size: Int get() = this@DescendingTreeMap.size
            override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> =
                m.entries.iterator()
        }

    override val keys: MutableSet<K>
        get() = navigableKeySet()

    override val values: MutableCollection<V>
        get() = object : AbstractMutableCollection<V>() {
            override val size: Int get() = this@DescendingTreeMap.size
            override fun iterator(): MutableIterator<V> =
                object : MutableIterator<V> {
                    private val entryIt = entries.iterator()
                    override fun hasNext(): Boolean = entryIt.hasNext()
                    override fun next(): V = entryIt.next().value
                    override fun remove() = entryIt.remove()
                }
        }
} 